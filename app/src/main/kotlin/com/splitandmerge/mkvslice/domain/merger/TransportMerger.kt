package com.splitandmerge.mkvslice.domain.merger

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.PartStatus
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidation
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidator
import com.splitandmerge.mkvslice.domain.transport.FrameCodec
import com.splitandmerge.mkvslice.platform.io.FileSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransportMerger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jobDao: JobDao,
    private val outputFolderValidator: OutputFolderValidator,
    private val preFlightEvaluator: PreFlightEvaluator,
    private val fileSystem: FileSystem
) {
    suspend fun runMerge(jobId: String): Unit = withContext(Dispatchers.IO) {
        val job = jobDao.getById(jobId) ?: return@withContext
        if (job.status == JobStatus.CANCELLED) return@withContext

        jobDao.updateProgress(
            id = jobId,
            status = JobStatus.RUNNING,
            pct = 0,
            speed = null,
            eta = null,
            parts = null,
            now = System.currentTimeMillis()
        )

        val parts = jobDao.getPartsForJob(jobId)
        val partUris = parts.mapNotNull { it.sourceUri }.filter { it.isNotBlank() }

        // 1. Pre-flight check (headers only, no output stream opened yet)
        val preFlight = preFlightEvaluator.evaluate(partUris)
        if (preFlight is PreFlightResult.Block) {
            jobDao.upsert(job.copy(status = JobStatus.FAILED, errorMessage = preFlight.reason))
            return@withContext
        }

        val ok = preFlight as PreFlightResult.Ok
        val sortedUris = ok.sortedUris
        val originalTotalSize = ok.originalTotalSize
        val totalParts = ok.totalParts

        // 2. Preflight Space Check: require free space >= originalTotalSize before opening output
        val validation = outputFolderValidator.validate(job.outputDirUri, originalTotalSize, assumePermissionPersisted = false)
        if (validation !is OutputFolderValidation.Ok) {
            val msg = when (validation) {
                OutputFolderValidation.Ok -> ""
                is OutputFolderValidation.NotReachable -> "Output folder not reachable"
                is OutputFolderValidation.PermissionRevoked -> "Write permission revoked"
                is OutputFolderValidation.NotWritable -> "Output folder is not writable: ${(validation as OutputFolderValidation.NotWritable).reason}"
                is OutputFolderValidation.InsufficientSpace -> {
                    val v = validation as OutputFolderValidation.InsufficientSpace
                    "Insufficient storage space: needed ${v.needed} bytes, but only ${v.have} bytes are available"
                }
            }
            jobDao.upsert(job.copy(status = JobStatus.FAILED, errorMessage = msg))
            Timber.e("Storage pre-flight failed for merge: $msg")
            return@withContext
        }

        // 3. Read the last part's trailer (originalName + wholeFileSha256) before creating output
        val lastPartUri = sortedUris[totalParts - 1]

        val lastHeader = try {
            context.contentResolver.openInputStream(Uri.parse(lastPartUri))?.use { inp ->
                FrameCodec.readHeader(inp)
            } ?: throw IOException("Failed to read header of last part")
        } catch (e: Exception) {
            jobDao.upsert(job.copy(status = JobStatus.FAILED, errorMessage = "cannot reconstruct — last part missing"))
            return@withContext
        }

        val trailer = try {
            var trailerRead = false
            var frameTrailer: FrameCodec.FrameTrailer? = null

            // Try openFileDescriptor and lseek first
            try {
                context.contentResolver.openFileDescriptor(Uri.parse(lastPartUri), "r")?.use { pfd ->
                    val fd = pfd.fileDescriptor
                    val seekOffset = 64L + lastHeader.payloadLen
                    android.system.Os.lseek(fd, seekOffset, android.system.OsConstants.SEEK_SET)
                    val fis = java.io.FileInputStream(fd)
                    frameTrailer = FrameCodec.readTrailer(fis, isLastPart = true, trailerLen = lastHeader.trailerLen)
                    trailerRead = true
                }
            } catch (e: Exception) {
                Timber.d(e, "lseek on FileDescriptor failed, falling back to read-and-discard")
            }

            if (!trailerRead) {
                context.contentResolver.openInputStream(Uri.parse(lastPartUri))?.use { inp ->
                    val skipBytes = 64L + lastHeader.payloadLen
                    var skipped = 0L
                    while (skipped < skipBytes) {
                        val n = inp.skip(skipBytes - skipped)
                        if (n <= 0) {
                            val tempBuf = ByteArray(minOf(8192L, skipBytes - skipped).toInt())
                            val r = inp.read(tempBuf)
                            if (r <= 0) throw IOException("EOF or zero-read skipping payload")
                            skipped += r
                        } else {
                            skipped += n
                        }
                    }
                    frameTrailer = FrameCodec.readTrailer(inp, isLastPart = true, trailerLen = lastHeader.trailerLen)
                }
            }
            frameTrailer ?: throw IOException("Failed to read trailer of last part")
        } catch (e: Exception) {
            jobDao.upsert(job.copy(status = JobStatus.FAILED, errorMessage = "cannot reconstruct — last part missing"))
            return@withContext
        }

        val originalName = trailer.originalName ?: "output.mkv"
        val expectedWholeSha = trailer.wholeFileSha256 ?: ByteArray(32)

        val outDirUri = Uri.parse(job.outputDirUri)
        val baseOutDir = if (outDirUri.scheme == "file") {
            DocumentFile.fromFile(java.io.File(outDirUri.path!!))
        } else {
            DocumentFile.fromTreeUri(context, outDirUri)
        } ?: run {
            jobDao.updateProgress(jobId, JobStatus.FAILED, 0, null, null, null, System.currentTimeMillis())
            return@withContext
        }

        // Delete existing file with target name to avoid SAF naming collisions
        baseOutDir.findFile(originalName)?.delete()

        val outputDocFile = baseOutDir.createFile("video/x-matroska", originalName)
            ?: run {
                jobDao.upsert(job.copy(status = JobStatus.FAILED, errorMessage = "Could not create output file"))
                return@withContext
            }

        val wholeFileDigest = MessageDigest.getInstance("SHA-256")
        val partDigest = MessageDigest.getInstance("SHA-256")
        var bytesWritten = 0L
        var writeCompleted = false

        try {
            context.contentResolver.openOutputStream(outputDocFile.uri)?.use { out ->
                val buffer = ByteArray(8 * 1024 * 1024) // 8MB buffer

                var lastProgressWriteTime = 0L
                var lastProgressPct = -1

                for (i in 1..totalParts) {
                    val currentJob = jobDao.getById(jobId)
                    if (currentJob?.status == JobStatus.CANCELLED) {
                        throw IOException("Job cancelled")
                    }

                    val uriStr = sortedUris[i - 1]
                    context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { inp ->
                        val header = FrameCodec.readHeader(inp)
                        var remaining = header.payloadLen
                        partDigest.reset()

                        while (remaining > 0) {
                            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                            val read = inp.read(buffer, 0, toRead)
                            if (read <= 0) {
                                throw IOException("Unexpected EOF or zero-read reading payload bytes")
                            }
                            out.write(buffer, 0, read)
                            partDigest.update(buffer, 0, read)
                            wholeFileDigest.update(buffer, 0, read)

                            remaining -= read
                            bytesWritten += read

                            val now = System.currentTimeMillis()
                            val pct = if (originalTotalSize > 0) {
                                ((bytesWritten.toDouble() / originalTotalSize) * 100).toInt().coerceIn(0, 99)
                            } else {
                                99
                            }

                            if (now - lastProgressWriteTime >= 250 || pct != lastProgressPct) {
                                val checkJob = jobDao.getById(jobId)
                                if (checkJob?.status == JobStatus.CANCELLED) {
                                    throw IOException("Job cancelled")
                                }
                                jobDao.updateProgress(
                                    id = jobId,
                                    status = JobStatus.RUNNING,
                                    pct = pct,
                                    speed = null,
                                    eta = null,
                                    parts = totalParts,
                                    now = now
                                )
                                lastProgressWriteTime = now
                                lastProgressPct = pct
                            }
                        }

                        // Verify part SHA
                        val partSha = partDigest.digest()
                        val partTrailer = FrameCodec.readTrailer(inp, isLastPart = (i == totalParts), trailerLen = header.trailerLen)
                        if (!partSha.contentEquals(partTrailer.partPayloadSha256)) {
                            // Row 11: Per-part SHA-256 mismatch
                            throw IOException("corrupted part")
                        }
                    }
                }
            }

            writeCompleted = (bytesWritten == originalTotalSize)

            // Finalize Verification
            val actualWholeSha = wholeFileDigest.digest()

            if (bytesWritten != originalTotalSize) {
                // Size mismatch
                jobDao.updateProgress(jobId, JobStatus.FAILED, 0, null, null, null, System.currentTimeMillis())
                jobDao.upsert(job.copy(
                    status = JobStatus.FAILED,
                    errorMessage = "size mismatch: expected $originalTotalSize bytes, wrote $bytesWritten bytes. Output path: ${outputDocFile.uri}"
                ))
            } else if (!actualWholeSha.contentEquals(expectedWholeSha)) {
                // Row 12: Whole-file SHA-256 mismatch
                jobDao.updateProgress(jobId, JobStatus.FAILED, 0, null, null, null, System.currentTimeMillis())
                jobDao.upsert(job.copy(
                    status = JobStatus.FAILED,
                    errorMessage = "hash mismatch: source changed / corrupt. Output path: ${outputDocFile.uri}"
                ))
            } else {
                jobDao.updateProgress(jobId, JobStatus.DONE, 100, null, null, totalParts, System.currentTimeMillis())
            }

        } catch (e: Exception) {
            Timber.e(e, "Transport merge failed")
            // On CANCEL or any PRE-COMPLETION failure: DELETE the partial output
            if (!writeCompleted) {
                try {
                    outputDocFile.delete()
                } catch (cleanupEx: Exception) {
                    Timber.w(cleanupEx, "Failed to clean up partial output file")
                }
            }
            val currentJob = jobDao.getById(jobId)
            if (currentJob?.status != JobStatus.CANCELLED) {
                val errorMsg = e.message ?: "Merge failed"
                jobDao.updateProgress(jobId, JobStatus.FAILED, 0, null, null, null, System.currentTimeMillis())
                jobDao.upsert(currentJob!!.copy(status = JobStatus.FAILED, errorMessage = errorMsg))
            }
        }
    }
}
