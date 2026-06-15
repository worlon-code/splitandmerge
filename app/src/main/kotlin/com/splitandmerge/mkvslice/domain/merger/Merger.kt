package com.splitandmerge.mkvslice.domain.merger

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.engine.EngineEvent
import com.splitandmerge.mkvslice.engine.FfmpegEngine
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// ── Phase model ───────────────────────────────────────────────────────────────

enum class MergePhase { STAGING, CONCAT, COPYING_TO_OUTPUT }

data class MergeProgress(
    val phase: MergePhase,
    val partIndex: Int?,            // STAGING only; null for CONCAT and COPYING_TO_OUTPUT
    val totalParts: Int,
    val phaseBytesCopied: Long,
    val phaseBytesTotal: Long,
    val overallPct: Int,            // 0..100 across all three phases
    val speedBytesPerSec: Long,
    val etaSeconds: Long,
)

// ─────────────────────────────────────────────────────────────────────────────

private const val TAG = "MERGE"

/** Buffer size for all copy loops (A4). */
private const val COPY_BUFFER_SIZE = 65_536          // 64 KB

/** Log a tick in the SAF copy-out every 128 MB (A8). */
private const val LOG_TICK_BYTES = 128L * 1024 * 1024

/** ensureActive() every 8 MB (A4). */
private const val CANCEL_CHECK_BYTES = 8L * 1024 * 1024

/** Emit progress every 1 second OR every 256 MB, whichever comes first. */
private const val EMIT_INTERVAL_MS = 1_000L
private const val EMIT_INTERVAL_BYTES = 256L * 1024 * 1024

/** Exponential moving average factor for speed. */
private const val EMA_ALPHA = 0.2

@Singleton
class Merger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jobDao: JobDao,
    private val ffmpegEngine: FfmpegEngine,
    private val ffprobeEngine: FfprobeEngine,
    private val mergeValidator: MergeValidator
) {

    suspend fun runMerge(jobId: String) {
        val job = jobDao.getById(jobId) ?: throw IllegalArgumentException("Job $jobId not found")
        val stagedFiles = mutableListOf<File>()
        val concatFile = File(context.cacheDir, "concat.txt")
        val tempOutputFile = File(context.cacheDir, "merge_tmp${job.outputContainer}")

        // Speed tracking state — shared across all phases.
        var lastEmitMs = System.currentTimeMillis()
        var bytesSinceLastEmit = 0L
        var emaSpeedBytesPerSec = 0L

        /** Update EMA speed and return current speed estimate. */
        fun updateSpeed(bytes: Long, elapsedMs: Long): Long {
            val elapsedSec = maxOf(elapsedMs, 1L) / 1000.0
            val instant = (bytes / elapsedSec).toLong()
            emaSpeedBytesPerSec = if (emaSpeedBytesPerSec == 0L) instant
            else ((EMA_ALPHA * instant) + ((1.0 - EMA_ALPHA) * emaSpeedBytesPerSec)).toLong()
            return emaSpeedBytesPerSec
        }

        suspend fun writeProgress(p: MergeProgress) {
            jobDao.updateProgress(
                id = jobId,
                status = JobStatus.RUNNING,
                pct = p.overallPct,
                speed = if (p.speedBytesPerSec > 0) p.speedBytesPerSec.toDouble() / (1024.0 * 1024) else null,
                eta = p.etaSeconds.toInt(),
                parts = p.totalParts,
                now = System.currentTimeMillis()
            )
        }

        try {
            jobDao.updateProgress(jobId, JobStatus.RUNNING, 0, null, null, 1, System.currentTimeMillis())

            val parts = jobDao.getPartsForJob(jobId)
            val partUris = parts.mapNotNull { it.sourceUri }.filter { it.isNotBlank() }
            if (partUris.isEmpty()) throw IllegalArgumentException("Part list is empty")

            val partSizes = partUris.map { uriStr ->
                try { DocumentFile.fromSingleUri(context, Uri.parse(uriStr))?.length() ?: 0L }
                catch (e: Exception) { 0L }
            }
            val totalSizeRequired = partSizes.sum()

            Timber.tag(TAG).i(
                "job=$jobId parts=${partUris.size} totalSizeRequired=$totalSizeRequired " +
                "cacheDir.usableSpace=${context.cacheDir.usableSpace}"
            )

            val outDirUri = Uri.parse(job.outputDirUri)
            val baseOutDir = if (outDirUri.scheme == "file") {
                DocumentFile.fromFile(File(outDirUri.path!!))
            } else {
                DocumentFile.fromTreeUri(context, outDirUri)
            } ?: throw IllegalStateException("Cannot access output directory")

            val outDir = baseOutDir.createDirectory(job.outputBaseName)
                ?: throw IllegalStateException("Cannot create output sub-directory")

            val actualOutDirName = outDir.name ?: job.outputBaseName
            if (actualOutDirName != job.outputBaseName) {
                Timber.tag(TAG).i("SAF rename detected: requested '${job.outputBaseName}', got '$actualOutDirName'")
            }
            val outFileName = "${actualOutDirName}${job.outputContainer}"

            // ── Storage pre-flight ────────────────────────────────────────────
            StoragePreflight.checkSpace(
                context = context,
                partSizes = partSizes,
                inputsStaged = true,
                outputStaged = true,
                resolvedOutputPath = null
            )

            if (tempOutputFile.exists()) tempOutputFile.delete()

            fun getOverallPct(phase: MergePhase, phasePct: Int): Int {
                return when (phase) {
                    MergePhase.STAGING -> (phasePct * 0.33).toInt().coerceIn(0, 33)
                    MergePhase.CONCAT -> (33 + phasePct * 0.33).toInt().coerceIn(33, 66)
                    MergePhase.COPYING_TO_OUTPUT -> (66 + phasePct * 0.34).toInt().coerceIn(66, 100)
                }
            }

            // ════════════════════════════════════════════════════════════════
            // PHASE 1 — STAGING
            // ════════════════════════════════════════════════════════════════
            var stagedBytesTotal = 0L
            lastEmitMs = System.currentTimeMillis()
            bytesSinceLastEmit = 0L
            emaSpeedBytesPerSec = 0L

            partUris.forEachIndexed { index, partUri ->
                    currentCoroutineContext().ensureActive()

                    val docFile = DocumentFile.fromSingleUri(context, Uri.parse(partUri))
                    val partSize = docFile?.length() ?: 0L
                    Timber.tag(TAG).i("staging part $index uri=$partUri size=$partSize")

                    val stagedFile = File(context.cacheDir, "staged_part_${index}.mkv")
                    if (stagedFile.exists()) stagedFile.delete()
                    stagedFiles.add(stagedFile)

                    val uri = Uri.parse(partUri)
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        java.io.FileInputStream(pfd.fileDescriptor).use { inStream ->
                            stagedFile.outputStream().use { outStream ->
                                val buffer = ByteArray(COPY_BUFFER_SIZE)
                                var bytesRead: Int
                                var cancelCheckBytes = 0L

                                while (inStream.read(buffer).also { bytesRead = it } >= 0) {
                                    outStream.write(buffer, 0, bytesRead)
                                    stagedBytesTotal += bytesRead
                                    bytesSinceLastEmit += bytesRead
                                    cancelCheckBytes += bytesRead

                                    if (cancelCheckBytes >= CANCEL_CHECK_BYTES) {
                                        currentCoroutineContext().ensureActive()
                                        cancelCheckBytes = 0L
                                    }

                                    val now = System.currentTimeMillis()
                                    val elapsedMs = now - lastEmitMs
                                    if (elapsedMs >= EMIT_INTERVAL_MS || bytesSinceLastEmit >= EMIT_INTERVAL_BYTES) {
                                        val speed = updateSpeed(bytesSinceLastEmit, elapsedMs)
                                        val localPct = if (totalSizeRequired > 0)
                                            (100.0 * stagedBytesTotal / totalSizeRequired).toInt().coerceIn(0, 100)
                                        else 0
                                        val overallPct = getOverallPct(MergePhase.STAGING, localPct)
                                        val remaining = if (totalSizeRequired > stagedBytesTotal)
                                            totalSizeRequired - stagedBytesTotal else 0L
                                        val eta = if (speed > 0) remaining / speed else 0L
                                        writeProgress(MergeProgress(
                                            phase = MergePhase.STAGING,
                                            partIndex = index + 1,
                                            totalParts = partUris.size,
                                            phaseBytesCopied = stagedBytesTotal,
                                            phaseBytesTotal = totalSizeRequired,
                                            overallPct = overallPct,
                                            speedBytesPerSec = speed,
                                            etaSeconds = eta
                                        ))
                                        lastEmitMs = now
                                        bytesSinceLastEmit = 0L
                                    }
                                }
                            }
                        }
                    } ?: throw com.splitandmerge.mkvslice.engine.EngineError.InputUnreadable(
                        partUri, "Could not open file descriptor"
                    )

                    Timber.tag(TAG).i(
                        "staged part $index -> ${stagedFile.absolutePath} " +
                        "size=${stagedFile.length()} diskFreeAfter=${context.cacheDir.usableSpace}"
                    )
                }

            val localPaths = stagedFiles.map { it.absolutePath }

            // ── Validate compatibility ────────────────────────────────────────
            mergeValidator.validate(localPaths)

            // ── Probe total duration for CONCAT phase ─────────────────────────
            val partDurations = localPaths.map { path ->
                try { ffprobeEngine.probe(path).format.durationSeconds }
                catch (e: Exception) { 0.0 }
            }
            val totalDurationSec = partDurations.sum()
            val totalDurationMs = (totalDurationSec * 1000).toLong()

            var cumulativeTime = 0.0
            val partEndTimesSeconds = partDurations.map { dur ->
                cumulativeTime += dur
                cumulativeTime
            }

            // Write concat list
            MergeListWriter.writeSafList(concatFile, localPaths)

            Timber.tag(TAG).i("concat.txt contents:\n${concatFile.readText()}")

            Timber.tag(TAG).i(
                "ffmpegOutputPath=${tempOutputFile.absolutePath} diskFree=${context.cacheDir.usableSpace}"
            )

            // ════════════════════════════════════════════════════════════════
            // PHASE 2 — CONCAT
            // ════════════════════════════════════════════════════════════════
            val cmd = listOf(
                "-hide_banner", "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", concatFile.absolutePath,
                "-c", "copy",
                tempOutputFile.absolutePath
            )

            // Emit a start-of-concat progress immediately so UI flips.
            val startConcatPct = getOverallPct(MergePhase.CONCAT, 0)
            jobDao.updateProgress(jobId, JobStatus.RUNNING, startConcatPct, null, null, partUris.size, System.currentTimeMillis())

            var deleteIndex = 0
            val cleanupGuardSeconds = 5.0

            ffmpegEngine.execute(cmd).collect { event ->
                when (event) {
                    is EngineEvent.Progress -> {
                        val timeMs = (event.timeSeconds * 1000).toLong()

                        // Sequential cleanup of staged parts (C2)
                        while (deleteIndex < stagedFiles.size - 1) { // Never delete the last staged part here
                            val safeBoundary = partEndTimesSeconds[deleteIndex] + cleanupGuardSeconds
                            if (timeMs / 1000.0 < safeBoundary) break
                            stagedFiles[deleteIndex].delete()
                            deleteIndex++
                        }

                        val localPct = if (totalDurationMs > 0)
                            ((100.0 * timeMs) / totalDurationMs).toInt().coerceIn(0, 100)
                        else 0
                        val overallPct = getOverallPct(MergePhase.CONCAT, localPct)

                        val speedBytesPerSec = if (totalDurationSec > 0 && event.speed > 0)
                            (event.speed * totalSizeRequired / totalDurationSec).toLong()
                        else 0L
                        val remainingMs = if (totalDurationMs > timeMs) totalDurationMs - timeMs else 0L
                        val etaSec = if (event.speed > 0) (remainingMs / 1000.0 / event.speed).toLong() else 0L

                        jobDao.updateProgress(
                            id = jobId,
                            status = JobStatus.RUNNING,
                            pct = overallPct,
                            speed = if (speedBytesPerSec > 0) speedBytesPerSec.toDouble() / (1024.0 * 1024) else null,
                            eta = etaSec.toInt(),
                            parts = partUris.size,
                            now = System.currentTimeMillis()
                        )
                    }
                    is EngineEvent.Completed -> {
                        Timber.tag(TAG).i(
                            "FFmpeg exit=${event.exitCode} " +
                            "outputPathLength=${tempOutputFile.length()} " +
                            "diskFree=${context.cacheDir.usableSpace}"
                        )
                        if (event.exitCode != 0) {
                            throw IllegalStateException(
                                "FFmpeg merge failed with exit code ${event.exitCode}"
                            )
                        }
                    }
                    else -> {}
                }
            }

            // ════════════════════════════════════════════════════════════════
            // PHASE 3 — COPYING_TO_OUTPUT
            // ════════════════════════════════════════════════════════════════
            val newFile = outDir.createFile("video/x-matroska", outFileName)
                ?: throw IllegalStateException("Could not create output file in SAF")

            val tmpSize = tempOutputFile.length()
            Timber.tag(TAG).i(
                "SAF copy-out start: src=${tempOutputFile.absolutePath} " +
                "size=$tmpSize destUri=${newFile.uri}"
            )

            // Emit phase-3 start immediately.
            val startCopyPct = getOverallPct(MergePhase.COPYING_TO_OUTPUT, 0)
            jobDao.updateProgress(jobId, JobStatus.RUNNING, startCopyPct, null, null, partUris.size, System.currentTimeMillis())

            lastEmitMs = System.currentTimeMillis()
            bytesSinceLastEmit = 0L
            emaSpeedBytesPerSec = 0L

            context.contentResolver.openOutputStream(newFile.uri)?.use { outStream ->
                tempOutputFile.inputStream().use { inStream ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    var bytesRead: Int
                    var bytesCopied = 0L
                    var cancelCheckBytes = 0L
                    var nextLogThreshold = LOG_TICK_BYTES

                    while (inStream.read(buffer).also { bytesRead = it } >= 0) {
                        outStream.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead
                        bytesSinceLastEmit += bytesRead
                        cancelCheckBytes += bytesRead

                        if (cancelCheckBytes >= CANCEL_CHECK_BYTES) {
                            currentCoroutineContext().ensureActive()
                            cancelCheckBytes = 0L
                        }

                        if (bytesCopied >= nextLogThreshold) {
                            Timber.tag(TAG).d(
                                "SAF copy-out tick: bytesCopied=$bytesCopied " +
                                "diskFree=${context.cacheDir.usableSpace}"
                            )
                            nextLogThreshold += LOG_TICK_BYTES
                        }

                        val now = System.currentTimeMillis()
                        val elapsedMs = now - lastEmitMs
                        if (elapsedMs >= EMIT_INTERVAL_MS || bytesSinceLastEmit >= EMIT_INTERVAL_BYTES) {
                            val speed = updateSpeed(bytesSinceLastEmit, elapsedMs)
                            val localPct = if (tmpSize > 0)
                                ((100.0 * bytesCopied) / tmpSize).toInt().coerceIn(0, 100)
                            else 0
                            val overallPct = getOverallPct(MergePhase.COPYING_TO_OUTPUT, localPct)
                            val remaining = if (tmpSize > bytesCopied) tmpSize - bytesCopied else 0L
                            val eta = if (speed > 0) remaining / speed else 0L
                            writeProgress(MergeProgress(
                                phase = MergePhase.COPYING_TO_OUTPUT,
                                partIndex = null,
                                totalParts = partUris.size,
                                phaseBytesCopied = bytesCopied,
                                phaseBytesTotal = tmpSize,
                                overallPct = overallPct,
                                speedBytesPerSec = speed,
                                etaSeconds = eta
                            ))
                            lastEmitMs = now
                            bytesSinceLastEmit = 0L
                        }
                    }

                    Timber.tag(TAG).i(
                        "SAF copy-out done: bytesCopied=$bytesCopied " +
                        "diskFreeAfter=${context.cacheDir.usableSpace}"
                    )
                }
            }

            jobDao.updateProgress(jobId, JobStatus.DONE, 100, 0.0, 0, partUris.size, System.currentTimeMillis())

        } catch (e: Exception) {
            Timber.e(e, "Merge Job $jobId failed")
            val currentJob = jobDao.getById(jobId)
            if (currentJob?.status != JobStatus.CANCELLED) {
                jobDao.updateProgress(
                    jobId, JobStatus.FAILED, 0, null, null, null, System.currentTimeMillis()
                )
                jobDao.upsert(currentJob!!.copy(status = JobStatus.FAILED, errorMessage = e.message))
            }
        } finally {
            stagedFiles.forEach { file -> if (file.exists()) file.delete() }
            if (concatFile.exists()) concatFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
        }
    }
}
