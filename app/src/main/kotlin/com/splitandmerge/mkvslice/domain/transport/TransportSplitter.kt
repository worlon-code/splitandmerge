package com.splitandmerge.mkvslice.domain.transport

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.PartStatus
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidation
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidator
import com.splitandmerge.mkvslice.platform.io.FileSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransportSplitter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jobDao: JobDao,
    private val outputFolderValidator: OutputFolderValidator,
    private val fileSystem: FileSystem
) {
    suspend fun runSplit(jobId: String): Unit = withContext(Dispatchers.IO) {
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

        val sourceUri = Uri.parse(job.sourceUri)
        val outDirUri = Uri.parse(job.outputDirUri)

        val originalFilename = DocumentFile.fromSingleUri(context, sourceUri)?.name
            ?: sourceUri.lastPathSegment
            ?: "source.mkv"

        val originalTotalSize = try {
            context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
                pfd.statSize
            } ?: throw IOException("Cannot resolve source size")
        } catch (e: Exception) {
            jobDao.updateProgress(jobId, JobStatus.FAILED, 0, null, null, null, System.currentTimeMillis())
            return@withContext
        }

        if (originalTotalSize < 0) {
            jobDao.upsert(job.copy(status = JobStatus.FAILED, errorMessage = "Could not determine source size"))
            return@withContext
        }

        val isByParts = job.mode == com.splitandmerge.mkvslice.domain.model.SplitMode.EXACT_PARTS

        val totalParts: Int
        val partPayloadSizes: LongArray

        if (isByParts) {
            val N = job.requestedParts ?: 0
            if (N <= 0) {
                jobDao.upsert(job.copy(status = JobStatus.FAILED, errorMessage = "number of parts must be >= 1"))
                return@withContext
            }
            if (N > originalTotalSize) {
                jobDao.upsert(job.copy(status = JobStatus.FAILED, errorMessage = "more parts than bytes"))
                return@withContext
            }
            if (N > 65535) {
                jobDao.upsert(job.copy(status = JobStatus.FAILED, errorMessage = "Total parts exceed the limit of 65535"))
                return@withContext
            }
            totalParts = N
            partPayloadSizes = LongArray(N)
            val base = originalTotalSize / N
            val remainder = (originalTotalSize % N).toInt()
            for (idx in 0 until N) {
                partPayloadSizes[idx] = if (idx < remainder) base + 1 else base
            }
        } else {
            // By size
            val capBytes = job.targetCapBytes
            if (capBytes == null || capBytes <= 0) {
                jobDao.upsert(job.copy(status = JobStatus.FAILED, errorMessage = "Byte split requires a target cap size"))
                return@withContext
            }
            totalParts = if (originalTotalSize == 0L) {
                1
            } else {
                Math.ceil(originalTotalSize.toDouble() / capBytes).toLong().toInt().coerceAtLeast(1)
            }
            if (totalParts > 65535) {
                jobDao.upsert(job.copy(status = JobStatus.FAILED, errorMessage = "Total parts exceed the limit of 65535"))
                return@withContext
            }
            partPayloadSizes = LongArray(totalParts)
            for (idx in 0 until totalParts) {
                partPayloadSizes[idx] = if (idx < totalParts - 1) {
                    capBytes
                } else {
                    originalTotalSize - (totalParts - 1) * capBytes
                }
            }
        }

        var currentOffset = 0L
        val partPayloadOffsets = LongArray(totalParts)
        for (idx in 0 until totalParts) {
            partPayloadOffsets[idx] = currentOffset
            currentOffset += partPayloadSizes[idx]
        }

        val nameBytes = originalFilename.toByteArray(Charsets.UTF_8)
        val nameLenBytes = nameBytes.size
        val overhead = totalParts * 64L + (totalParts - 1) * 32L + (32L + 32L + 4L + nameLenBytes)
        val requiredBytes = originalTotalSize + overhead

        // F2 Preflight Space check: validate using OutputFolderValidator
        val validation = outputFolderValidator.validate(job.outputDirUri, requiredBytes, assumePermissionPersisted = false)
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
            Timber.e("Storage pre-flight failed: $msg")
            return@withContext
        }

        val outDir = if (outDirUri.scheme == "file") {
            DocumentFile.fromFile(java.io.File(outDirUri.path!!))
        } else {
            DocumentFile.fromTreeUri(context, outDirUri)
        } ?: run {
            jobDao.updateProgress(jobId, JobStatus.FAILED, 0, null, null, null, System.currentTimeMillis())
            return@withContext
        }

        val subfolder = outDir.createDirectory(job.outputBaseName) ?: outDir
        val padWidth = totalParts.toString().length.coerceAtLeast(2)

        val wholeFileDigest = MessageDigest.getInstance("SHA-256")
        val partDigest = MessageDigest.getInstance("SHA-256")

        var totalBytesRead = 0L
        var finalWholeShaBytes: ByteArray? = null

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inp ->
                for (i in 1..totalParts) {
                    val currentJob = jobDao.getById(jobId)
                    if (currentJob?.status == JobStatus.CANCELLED) {
                        throw IOException("Job cancelled")
                    }

                    val partName = String.format("%s.part_%0${padWidth}d_%0${padWidth}d.mkv", job.outputBaseName, i, totalParts)
                    
                    // SAF atomicity: Write to a .tmp file then rename. D1: MIME = video/x-matroska
                    val tmpDocFile = subfolder.createFile("video/x-matroska", "$partName.tmp")
                        ?: throw IOException("Cannot create temp file for part $i")

                    val payloadLen = partPayloadSizes[i - 1]
                    val payloadOffset = partPayloadOffsets[i - 1]
                    val isLast = (i == totalParts)
                    val flags = if (isLast) 1L else 0L
                    val trailerLen = if (isLast) {
                        32L + 32L + 4L + nameLenBytes
                    } else {
                        32L
                    }

                    val header = FrameCodec.FrameHeader(
                        formatVersion = 1,
                        headerLen = 64,
                        partIndex = i,
                        totalParts = totalParts,
                        originalTotalSize = originalTotalSize,
                        payloadOffset = payloadOffset,
                        payloadLen = payloadLen,
                        trailerLen = trailerLen,
                        flags = flags,
                        headerCrc = 0L
                    )

                    context.contentResolver.openOutputStream(tmpDocFile.uri)?.use { out ->
                        FrameCodec.writeHeader(out, header)

                        val buffer = ByteArray(8 * 1024 * 1024) // 8 MB buffer
                        var remaining = payloadLen
                        partDigest.reset()

                        var lastProgressWriteTime = 0L
                        var lastProgressPct = -1

                        while (remaining > 0) {
                            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                            val read = inp.read(buffer, 0, toRead)
                            if (read < 0) {
                                throw IOException("Unexpected EOF reading payload bytes")
                            }
                            out.write(buffer, 0, read)
                            partDigest.update(buffer, 0, read)
                            wholeFileDigest.update(buffer, 0, read)
                            
                            remaining -= read
                            totalBytesRead += read

                            val now = System.currentTimeMillis()
                            val pct = if (originalTotalSize > 0) {
                                ((totalBytesRead.toDouble() / originalTotalSize) * 100).toInt().coerceIn(0, 99)
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

                        val partSha = partDigest.digest()
                        val trailer = if (isLast) {
                            val wholeSha = wholeFileDigest.digest()
                            finalWholeShaBytes = wholeSha
                            FrameCodec.FrameTrailer(
                                partPayloadSha256 = partSha,
                                wholeFileSha256 = wholeSha,
                                originalName = originalFilename
                            )
                        } else {
                            FrameCodec.FrameTrailer(partPayloadSha256 = partSha)
                        }

                        FrameCodec.writeTrailer(out, trailer, isLast)
                    }

                    // Rename tmp to final
                    val finalFile = try {
                        if (subfolder.uri.scheme == "file") {
                            val destFile = java.io.File(subfolder.uri.path!!, partName)
                            if (destFile.exists()) destFile.delete()
                            val tempFile = java.io.File(subfolder.uri.path!!, "$partName.tmp")
                            if (!tempFile.renameTo(destFile)) {
                                throw IOException("Rename failed from temp file to $partName")
                            }
                            DocumentFile.fromFile(destFile)
                        } else {
                            val renamedUri = android.provider.DocumentsContract.renameDocument(
                                context.contentResolver,
                                tmpDocFile.uri,
                                partName
                            ) ?: throw IOException("renameDocument returned null or failed")
                            DocumentFile.fromSingleUri(context, renamedUri)
                        }
                    } catch (e: Exception) {
                        try { tmpDocFile.delete() } catch (ignored: Exception) {}
                        Timber.e(e, "Rename failed for part $i")
                        throw e
                    }

                    if (finalFile == null || !finalFile.exists()) {
                        try { tmpDocFile.delete() } catch (ignored: Exception) {}
                        throw IOException("Rename check failed: final file does not exist for $partName")
                    }
                    if (finalFile.name != partName) {
                        try { finalFile.delete() } catch (ignored: Exception) {}
                        try { tmpDocFile.delete() } catch (ignored: Exception) {}
                        throw IOException("Rename check failed: expected name '$partName' but got '${finalFile.name}'")
                    }

                    jobDao.upsertPart(PartEntity(
                        id = UUID.randomUUID().toString(),
                        jobId = jobId,
                        index = i,
                        name = partName,
                        startSec = 0.0,
                        endSec = 0.0,
                        sizeBytes = header.headerLen + payloadLen + trailerLen,
                        status = PartStatus.DONE,
                        byteOffset = payloadOffset,
                        byteSize = payloadLen
                    ))
                }
            }

            // Write optional .split.json manifest last
            val manifestName = "${job.outputBaseName}.split.json"
            subfolder.findFile(manifestName)?.delete()
            val manifestDocFile = subfolder.createFile("application/json", manifestName)
            if (manifestDocFile != null) {
                val finalWholeSha = finalWholeShaBytes ?: ByteArray(32)
                val wholeFileShaHex = finalWholeSha.joinToString("") { "%02x".format(it) }
                context.contentResolver.openOutputStream(manifestDocFile.uri)?.use { out ->
                    val manifestJson = """
                    {
                      "schema": 1,
                      "mode": "byte",
                      "originalSha256": "$wholeFileShaHex"
                    }
                    """.trimIndent()
                    out.write(manifestJson.toByteArray(Charsets.UTF_8))
                }
            }

            jobDao.updateProgress(jobId, JobStatus.DONE, 100, null, null, totalParts, System.currentTimeMillis())

        } catch (e: Exception) {
            Timber.e(e, "Transport split failed")
            // Clean up partial files
            val actualTotal = totalParts
            for (i in 1..actualTotal) {
                val partName = String.format("%s.part_%0${padWidth}d_%0${padWidth}d.mkv", job.outputBaseName, i, actualTotal)
                subfolder.findFile("$partName.tmp")?.delete()
                subfolder.findFile(partName)?.delete()
            }
            jobDao.upsert(job.copy(status = JobStatus.FAILED, errorMessage = e.message))
        }
    }
}
