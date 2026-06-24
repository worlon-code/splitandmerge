package com.splitandmerge.mkvslice.domain.merger

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.domain.transport.FrameCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class PreFlightResult {
    data class Ok(
        val sortedUris: List<String>,
        val originalTotalSize: Long,
        val totalParts: Int
    ) : PreFlightResult()

    data class Block(
        val reason: String
    ) : PreFlightResult()
}

/**
 * DETECTION DECISION TABLE
 *
 * SIGNAL SEEN                                                  | CHECKS RUN                         | ROUTE                         | EXACT USER MESSAGE
 * -------------------------------------------------------------|------------------------------------|-------------------------------|-----------------------------------------
 * 1. Valid full set (complete, contiguous, sizes match)        | Magic, size, offsets, completeness | Byte merge                    | N/A (proceed to merge)
 * 2. Missing some interior (non-last) PART_INDEX               | Completeness                       | BLOCK                         | "missing part(s)"
 * 3. Missing the LAST part                                     | Completeness                       | BLOCK                         | "cannot reconstruct — last part missing"
 * 4. Duplicate PART_INDEX                                      | Duplicate detection                | BLOCK                         | "duplicate part"
 * 5. Out-of-order SELECTION                                    | Reordering                         | Sort & Proceed                | N/A
 * 6a. Foreign / no-magic, non-EBML present in byte selection   | Magic check                        | BLOCK                         | "unrecognized file / not a byte part"
 * 6b. Pure all-EBML selection (no MKVSLICE parts)              | Magic check                        | FFmpeg structural merge       | N/A
 * 7. Mixed byte (MKVSLICE) + normal (EBML) selection           | Magic check                        | BLOCK                         | "mixed selection"
 * 8. Two sessions (mismatched size or parts)                   | Header compatibility               | BLOCK                         | "different sessions"
 * 9. Truncated part (disk size != 64 + payload + trailer)      | Truncation check                   | BLOCK                         | "truncated / corrupt part"
 * 10. Unknown FORMAT_VERSION                                   | Version check                      | BLOCK                         | "unsupported format version"
 * 11. Per-part SHA-256 mismatch                                | Post-copy part SHA                 | FAIL                          | "corrupted part"
 * 12. Whole-file SHA-256 mismatch                              | Finalize whole SHA                 | FAIL                          | "source changed / corrupt"
 */
@Singleton
class PreFlightEvaluator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val partModeDetector: PartModeDetector
) {
    fun evaluate(uris: List<String>): PreFlightResult {
        if (uris.isEmpty()) {
            return PreFlightResult.Block("No files selected")
        }

        // Sniff modes
        val modes = uris.map { partModeDetector.detectMode(it) }
        val hasMkvslice = modes.any { it == PartMode.MKVSLICE }
        val hasEbml = modes.any { it == PartMode.EBML }
        val hasOther = modes.any { it == PartMode.OTHER }

        // Row 7: Mixed byte (MKVSLICE) + normal (EBML) selection
        if (hasMkvslice && hasEbml) {
            return PreFlightResult.Block("mixed selection")
        }

        // Row 6a: Foreign / no-magic, non-EBML present in byte selection
        if (hasMkvslice && hasOther) {
            return PreFlightResult.Block("unrecognized file / not a byte part")
        }

        // Row 6b: Pure all-EBML selection (or other media selections without MKVSLICE) -> Proceed as Ok for Merger
        if (!hasMkvslice) {
            return PreFlightResult.Ok(uris, 0L, uris.size)
        }

        // Parse headers of MKVSLICE parts
        val partHeaders = mutableListOf<Pair<String, FrameCodec.FrameHeader>>()

        for (uriStr in uris) {
            val uri = Uri.parse(uriStr)
            val docFile = DocumentFile.fromSingleUri(context, uri)
            val onDiskSize = docFile?.length() ?: -1L
            if (onDiskSize <= 0L) {
                // Row 9: Truncated part
                return PreFlightResult.Block("truncated / corrupt part")
            }

            try {
                context.contentResolver.openInputStream(uri)?.use { inp ->
                    val header = FrameCodec.readHeader(inp)

                    // Row 9: Truncated part (disk size != 64 + payload + trailer)
                    val expectedDiskSize = 64L + header.payloadLen + header.trailerLen
                    if (onDiskSize != expectedDiskSize) {
                        return PreFlightResult.Block("truncated / corrupt part")
                    }

                    // Check isLastPart contiguity
                    val expectedLast = (header.partIndex == header.totalParts)
                    if (header.isLastPart != expectedLast) {
                        return PreFlightResult.Block("truncated / corrupt part")
                    }

                    partHeaders.add(Pair(uriStr, header))
                }
            } catch (e: Exception) {
                // Row 9: Truncated / corrupt header
                return PreFlightResult.Block("truncated / corrupt part")
            }
        }

        if (partHeaders.isEmpty()) {
            return PreFlightResult.Block("unrecognized file / not a byte part")
        }

        val firstHeader = partHeaders.first().second
        val originalTotalSize = firstHeader.originalTotalSize
        val totalParts = firstHeader.totalParts

        // Row 8: Two sessions (mismatched ORIGINAL_TOTAL_SIZE or TOTAL_PARTS)
        for (pair in partHeaders) {
            val header = pair.second
            if (header.originalTotalSize != originalTotalSize || header.totalParts != totalParts) {
                return PreFlightResult.Block("different sessions")
            }
        }

        // Row 10: Unknown FORMAT_VERSION
        for (pair in partHeaders) {
            val header = pair.second
            if (header.formatVersion != 1) {
                return PreFlightResult.Block("unsupported format version")
            }
        }

        // Row 4: Duplicate PART_INDEX
        val indices = partHeaders.map { it.second.partIndex }.toSet()
        if (indices.size < partHeaders.size) {
            return PreFlightResult.Block("duplicate part")
        }

        // Row 3: Missing the LAST part
        if (!indices.contains(totalParts)) {
            return PreFlightResult.Block("cannot reconstruct — last part missing")
        }

        // Row 2: Missing some interior (non-last) PART_INDEX
        for (i in 1..totalParts) {
            if (!indices.contains(i)) {
                return PreFlightResult.Block("missing part(s)")
            }
        }

        // Row 5: Sort by partIndex
        val sortedPairs = partHeaders.sortedBy { it.second.partIndex }
        val sortedUris = sortedPairs.map { it.first }

        // Contiguity check: offset contiguity
        if (sortedPairs[0].second.payloadOffset != 0L) {
            return PreFlightResult.Block("different sessions")
        }
        for (i in 1 until sortedPairs.size) {
            val prev = sortedPairs[i - 1].second
            val curr = sortedPairs[i].second
            if (curr.payloadOffset != prev.payloadOffset + prev.payloadLen) {
                return PreFlightResult.Block("different sessions")
            }
        }

        // Payload sum check
        val sumPayloadLen = sortedPairs.sumOf { it.second.payloadLen }
        if (sumPayloadLen != originalTotalSize) {
            return PreFlightResult.Block("different sessions")
        }

        return PreFlightResult.Ok(sortedUris, originalTotalSize, totalParts)
    }
}
