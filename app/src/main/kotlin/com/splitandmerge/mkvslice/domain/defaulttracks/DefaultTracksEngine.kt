package com.splitandmerge.mkvslice.domain.defaulttracks

import com.splitandmerge.mkvslice.domain.defaulttracks.model.EditSpec
import com.splitandmerge.mkvslice.domain.defaulttracks.model.WriteStrategy
import com.splitandmerge.mkvslice.platform.io.FileSystem
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class DefaultTracksEngineResult(
    val status: String,
    val reason: String = "",
    val writeStrategy: String = "SKIPPED"
)

@Singleton
class DefaultTracksEngine @Inject constructor(
    private val fileSystem: FileSystem
) {
    private val reader = EbmlReader()
    private val editor = FlagEditor()
    private val verifier = FlagVerifier()

    fun processFile(
        uri: String,
        spec: EditSpec,
        jobId: String,
        fileIndex: Int,
        onProgress: (Int) -> Unit = {}
    ): DefaultTracksEngineResult {
        onProgress(5)
        // 1. Analyse phase (read-only)
        val readFd = fileSystem.openFileDescriptor(uri, "r")
            ?: return DefaultTracksEngineResult("SKIPPED", "provider-rejects-r")
            
        val parsed = try {
            readFd.use { fd ->
                reader.parse(fd)
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val reason = if (msg.contains("Not a Matroska/EBML file")) "not-mkv" else "parse-error: $msg"
            return DefaultTracksEngineResult("SKIPPED", reason)
        }

        onProgress(35)
        // Build EditPlan
        val plan = editor.planEdits(parsed, spec)
        if (plan.writeStrategy == WriteStrategy.SKIPPED) {
            return DefaultTracksEngineResult("SKIPPED", plan.skipReason ?: "unknown-skip", WriteStrategy.SKIPPED.name)
        }
        if (plan.fileEdits.isEmpty()) {
            return DefaultTracksEngineResult("UNCHANGED", "", plan.writeStrategy.name)
        }

        // Compute the span
        val sortedEdits = plan.fileEdits.sortedBy { it.originalOffset }
        val firstChangedOffset = sortedEdits.first().originalOffset
        val lastChangedOffset = sortedEdits.last().originalOffset + sortedEdits.last().originalBytes.size
        val spanSize = (lastChangedOffset - firstChangedOffset).toInt()

        // Read the analysis signature of the span
        val rSpanBytes = ByteArray(spanSize)
        val readFd2 = fileSystem.openFileDescriptor(uri, "r")
            ?: return DefaultTracksEngineResult("SKIPPED", "provider-rejects-r")
        try {
            readFd2.use { fd ->
                fd.seek(firstChangedOffset, 0)
                var totalRead = 0
                while (totalRead < spanSize) {
                    val r = fd.read(rSpanBytes, totalRead, spanSize - totalRead)
                    if (r <= 0) break
                    totalRead += r
                }
                if (totalRead != spanSize) {
                    return DefaultTracksEngineResult("SKIPPED", "signature-read-failed")
                }
            }
        } catch (e: Exception) {
            return DefaultTracksEngineResult("SKIPPED", "signature-read-error: ${e.message}")
        }

        // 2. Write phase (rw)
        val rwFd = fileSystem.openFileDescriptor(uri, "rw")
            ?: return DefaultTracksEngineResult("SKIPPED", "provider-rejects-rw", plan.writeStrategy.name)

        var closed = false
        try {
            // Writability gate
            if (!rwFd.isRegularFile() || !rwFd.isSeekable() || !rwFd.isWritable()) {
                rwFd.close()
                closed = true
                return DefaultTracksEngineResult("SKIPPED", "non-regular/non-seekable/read-only/provider-rejects-rw", plan.writeStrategy.name)
            }

            val originalLength = rwFd.size()

            // TOCTOU re-read + compare
            // Re-read tracks layout and compare
            val reParsed = try {
                reader.parse(rwFd)
            } catch (e: Exception) {
                rwFd.close()
                closed = true
                return DefaultTracksEngineResult("SKIPPED", "content-signature-mismatch", plan.writeStrategy.name)
            }

            if (reParsed.tracks.size != parsed.tracks.size) {
                rwFd.close()
                closed = true
                return DefaultTracksEngineResult("SKIPPED", "content-signature-mismatch", plan.writeStrategy.name)
            }
            for (i in parsed.tracks.indices) {
                val t1 = parsed.tracks[i]
                val t2 = reParsed.tracks[i]
                if (t1.trackNumber != t2.trackNumber || t1.byteOffset != t2.byteOffset || t1.trackType != t2.trackType) {
                    rwFd.close()
                    closed = true
                    return DefaultTracksEngineResult("SKIPPED", "content-signature-mismatch", plan.writeStrategy.name)
                }
            }

            // Re-read the span bytes and compare to signature
            val rwSpanBytes = ByteArray(spanSize)
            rwFd.seek(firstChangedOffset, 0)
            var totalRead = 0
            while (totalRead < spanSize) {
                val r = rwFd.read(rwSpanBytes, totalRead, spanSize - totalRead)
                if (r <= 0) break
                totalRead += r
            }
            if (totalRead != spanSize || !rwSpanBytes.contentEquals(rSpanBytes)) {
                rwFd.close()
                closed = true
                return DefaultTracksEngineResult("SKIPPED", "content-signature-mismatch", plan.writeStrategy.name)
            }

            onProgress(60)
            // Prepare replacement span bytes
            val out = java.io.ByteArrayOutputStream()
            var lastOrigOffset = firstChangedOffset
            for (edit in sortedEdits) {
                val untouchedLen = (edit.originalOffset - lastOrigOffset).toInt()
                if (untouchedLen > 0) {
                    out.write(rSpanBytes, (lastOrigOffset - firstChangedOffset).toInt(), untouchedLen)
                }
                out.write(edit.replacementBytes)
                lastOrigOffset = edit.originalOffset + edit.originalBytes.size
            }
            val remainingLen = (lastChangedOffset - lastOrigOffset).toInt()
            if (remainingLen > 0) {
                out.write(rSpanBytes, (lastOrigOffset - firstChangedOffset).toInt(), remainingLen)
            }
            val replacementSpanBytes = out.toByteArray()

            // Setup Journal
            val journal = FlagJournal(fileSystem.cacheDir(), jobId, fileIndex)
            journal.writeJournal(rwFd, originalLength, firstChangedOffset, rSpanBytes)

            // Perform single contiguous write
            rwFd.seek(firstChangedOffset, 0)
            rwFd.write(replacementSpanBytes, 0, replacementSpanBytes.size)
            rwFd.fdatasync()
            onProgress(85)

            // Verify
            val expectedFlipsCount = if (plan.writeStrategy == WriteStrategy.IN_PLACE_PATCH) plan.fileEdits.size else 0
            val verified = verifier.verify(
                fd = rwFd,
                originalMkv = parsed,
                originalLength = originalLength,
                spec = spec,
                writeStrategy = plan.writeStrategy,
                spanOffset = firstChangedOffset,
                originalBytes = rSpanBytes,
                expectedFlipsCount = expectedFlipsCount
            )

            if (verified) {
                onProgress(100)
                journal.deleteJournal()
                rwFd.close()
                closed = true
                return DefaultTracksEngineResult("DONE", "", plan.writeStrategy.name)
            } else {
                // Rollback
                val rollbackRes = journal.rollback(rwFd)
                rwFd.close()
                closed = true
                return when (rollbackRes) {
                    RollbackResult.SUCCESS -> DefaultTracksEngineResult("FAILED", "verification-failed-rolled-back", plan.writeStrategy.name)
                    RollbackResult.REFUSED -> DefaultTracksEngineResult("NEEDS_MANUAL_REVIEW", "verification-failed-rollback-refused", plan.writeStrategy.name)
                    is RollbackResult.FAILED -> DefaultTracksEngineResult("PARTIAL", "verification-failed-rollback-error: ${rollbackRes.message}", plan.writeStrategy.name)
                }
            }
        } catch (e: Exception) {
            if (!closed) {
                try {
                    val journal = FlagJournal(fileSystem.cacheDir(), jobId, fileIndex)
                    if (journal.exists()) {
                        val rollbackRes = journal.rollback(rwFd)
                        rwFd.close()
                        closed = true
                        return when (rollbackRes) {
                            RollbackResult.SUCCESS -> DefaultTracksEngineResult("FAILED", "write-error-rolled-back: ${e.message}", plan.writeStrategy.name)
                            RollbackResult.REFUSED -> DefaultTracksEngineResult("NEEDS_MANUAL_REVIEW", "write-error-rollback-refused: ${e.message}", plan.writeStrategy.name)
                            is RollbackResult.FAILED -> DefaultTracksEngineResult("PARTIAL", "write-error-rollback-error: ${rollbackRes.message}", plan.writeStrategy.name)
                        }
                    } else {
                        rwFd.close()
                        closed = true
                    }
                } catch (ex: Exception) {
                    try { rwFd.close() } catch (ignored: Exception) {}
                    closed = true
                }
            }
            return DefaultTracksEngineResult("FAILED", "write-exception: ${e.message}", plan.writeStrategy.name)
        } finally {
            if (!closed) {
                try { rwFd.close() } catch (ignored: Exception) {}
            }
        }
    }
}
