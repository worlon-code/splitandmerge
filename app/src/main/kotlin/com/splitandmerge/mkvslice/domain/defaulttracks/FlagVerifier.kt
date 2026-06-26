package com.splitandmerge.mkvslice.domain.defaulttracks

import com.splitandmerge.mkvslice.domain.defaulttracks.model.EditSpec
import com.splitandmerge.mkvslice.domain.defaulttracks.model.ParsedMkv
import com.splitandmerge.mkvslice.domain.defaulttracks.model.WriteStrategy
import com.splitandmerge.mkvslice.platform.io.FileDescriptorWrapper
import java.io.IOException

class FlagVerifier {

    private val reader = EbmlReader()

    fun verify(
        fd: FileDescriptorWrapper,
        originalMkv: ParsedMkv,
        originalLength: Long,
        spec: EditSpec,
        writeStrategy: WriteStrategy,
        spanOffset: Long,
        originalBytes: ByteArray,
        expectedFlipsCount: Int
    ): Boolean {
        // 1. Semantic verification
        val parsed = try {
            reader.parse(fd)
        } catch (e: Exception) {
            return false
        }

        // Semantic invariant:
        // - exactly the chosen audio track resolves default=1 and ALL other audio resolve 0
        // - at most one subtitle resolves default=1 (the chosen one, or none)
        // - forced-subtitle exactly as chosen
        val audioTracks = parsed.tracks.filter { it.trackType == 2 }
        val subTracks = parsed.tracks.filter { it.trackType == 17 }

        val chosenAudio = audioTracks.find { it.trackNumber == spec.defaultAudioTrackNumber }
        if (chosenAudio == null || chosenAudio.flagDefault != 1) {
            return false
        }
        for (track in audioTracks) {
            if (track.trackNumber != spec.defaultAudioTrackNumber && track.flagDefault != 0) {
                return false
            }
        }

        if (spec.defaultSubtitleTrackNumber != null) {
            val chosenSub = subTracks.find { it.trackNumber == spec.defaultSubtitleTrackNumber }
            if (chosenSub == null || chosenSub.flagDefault != 1 || chosenSub.flagForced != (if (spec.forcedSubtitle) 1 else 0)) {
                return false
            }
            for (track in subTracks) {
                if (track.trackNumber != spec.defaultSubtitleTrackNumber && track.flagDefault != 0) {
                    return false
                }
            }
        } else {
            for (track in subTracks) {
                if (track.flagDefault != 0) {
                    return false
                }
            }
        }

        // 2. Structural verification
        // Check file length unchanged
        val currentLength = fd.size()
        if (currentLength != originalLength) {
            return false
        }

        // Read current bytes of the write span
        val currentBytes = ByteArray(originalBytes.size)
        fd.seek(spanOffset, 0)
        var totalRead = 0
        while (totalRead < currentBytes.size) {
            val read = fd.read(currentBytes, totalRead, currentBytes.size - totalRead)
            if (read <= 0) break
            totalRead += read
        }
        if (totalRead != currentBytes.size) {
            return false
        }

        // Compute changed regions
        val changedRegions = mutableListOf<ClosedRange<Long>>()
        if (writeStrategy == WriteStrategy.VOID_REUSE) {
            var firstDiff = -1
            var lastDiff = -1
            for (i in originalBytes.indices) {
                if (originalBytes[i] != currentBytes[i]) {
                    if (firstDiff == -1) firstDiff = i
                    lastDiff = i
                }
            }
            if (firstDiff != -1) {
                changedRegions.add((spanOffset + firstDiff)..(spanOffset + lastDiff))
            }
        } else {
            var inDiff = false
            var diffStart = 0L
            for (i in originalBytes.indices) {
                val diff = originalBytes[i] != currentBytes[i]
                if (diff) {
                    if (!inDiff) {
                        inDiff = true
                        diffStart = spanOffset + i
                    }
                } else {
                    if (inDiff) {
                        inDiff = false
                        changedRegions.add(diffStart..(spanOffset + i - 1))
                    }
                }
            }
            if (inDiff) {
                changedRegions.add(diffStart..(spanOffset + originalBytes.size - 1))
            }
        }

        if (writeStrategy == WriteStrategy.IN_PLACE_PATCH) {
            // Path A: count == number of flips (each a 1-byte region)
            if (changedRegions.size != expectedFlipsCount) {
                return false
            }
            for (region in changedRegions) {
                val len = region.endInclusive - region.start + 1
                if (len != 1L) {
                    return false
                }
            }
        } else if (writeStrategy == WriteStrategy.VOID_REUSE) {
            // Void-reuse: count == exactly 1 contiguous region (the write span)
            if (changedRegions.size != 1) {
                return false
            }
            // For Void-reuse it is SUFFICIENT to assert:
            // file length unchanged + the FIRST Cluster offset is unchanged + the entire write span lies BEFORE the first Cluster
            val firstClusterOffset = parsed.firstClusterOffset
            if (firstClusterOffset == null || firstClusterOffset != originalMkv.firstClusterOffset) {
                return false
            }
            val spanEnd = spanOffset + originalBytes.size
            if (spanEnd > firstClusterOffset) {
                return false
            }
        }

        return true
    }
}
