package com.splitandmerge.mkvslice.domain.splitter

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.Manifest
import com.splitandmerge.mkvslice.domain.model.ManifestAudio
import com.splitandmerge.mkvslice.domain.model.ManifestPart
import com.splitandmerge.mkvslice.domain.model.ManifestSource
import com.splitandmerge.mkvslice.domain.model.ManifestSub
import com.splitandmerge.mkvslice.domain.model.ManifestVideo
import com.splitandmerge.mkvslice.domain.model.PartStatus
import com.splitandmerge.mkvslice.engine.EngineEvent
import com.splitandmerge.mkvslice.engine.FfmpegEngine
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject

class Splitter @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val jobDao: JobDao,
    private val ffprobeEngine: FfprobeEngine,
    private val ffmpegEngine: FfmpegEngine,
    private val cutPlanner: CutPlanner,
    private val manifestWriter: ManifestWriter
) {

    suspend fun runSplit(jobId: String) = withContext(Dispatchers.IO) {
        val job = jobDao.getById(jobId) ?: return@withContext
        if (job.status == JobStatus.CANCELLED) return@withContext

        try {
            jobDao.updateProgress(jobId, JobStatus.RUNNING, 0, System.currentTimeMillis())

            // 1. Probe input
            val uri = Uri.parse(job.sourceUri)
            val probeResult = ffprobeEngine.probe(job.sourceUri)
            val keyframes = ffprobeEngine.keyframes(job.sourceUri)

            // 2. Decide cut points
            val mode = requireNotNull(job.mode) { "Split mode must be set for split jobs" }
            val plan = cutPlanner.plan(
                mode = mode,
                requestedParts = job.requestedParts,
                targetCapBytes = job.targetCapBytes ?: (9L * 1024 * 1024 * 1024),
                ceilingBytes = job.ceilingCapBytes ?: (9L * 1024 * 1024 * 1024 + 500 * 1024 * 1024),
                durationSeconds = probeResult.format.durationSeconds,
                totalSizeBytes = probeResult.format.sizeBytes,
                keyframes = keyframes
            )

            // Update DB with actual totalParts determined by the planner
            val actualTotalParts = plan.cuts.size + 1
            jobDao.updateProgress(jobId, JobStatus.RUNNING, 0, null, null, actualTotalParts, System.currentTimeMillis())

            // 3. Create or resume parts
            val outDirUri = Uri.parse(job.outputDirUri)
            val outDir = if (outDirUri.scheme == "file") {
                DocumentFile.fromFile(File(outDirUri.path!!))
            } else {
                DocumentFile.fromTreeUri(context, outDirUri)
            } ?: throw IllegalStateException("Cannot access output directory")

            val manifestParts = mutableListOf<ManifestPart>()

            // Ensure subfolder is created
            val subfolder = outDir.createDirectory(job.outputBaseName) ?: outDir

            // Ensure part entities exist
            var lastCut = 0.0
            for ((i, cut) in plan.cuts.withIndex()) {
                val index = i + 1
                val partStart = lastCut
                val partEnd = cut
                processPart(
                    jobId = jobId,
                    index = index,
                    startSec = partStart,
                    endSec = partEnd,
                    sourceUri = job.sourceUri,
                    outDir = subfolder,
                    baseName = job.outputBaseName,
                    manifestParts = manifestParts,
                    ceilingBytes = plan.ceilingBytes,
                    totalDurationSec = probeResult.format.durationSeconds,
                    totalParts = actualTotalParts
                )
                lastCut = cut
            }
            // Final part
            val index = plan.cuts.size + 1
            processPart(
                jobId = jobId,
                index = index,
                startSec = lastCut,
                endSec = probeResult.format.durationSeconds,
                sourceUri = job.sourceUri,
                outDir = subfolder,
                baseName = job.outputBaseName,
                manifestParts = manifestParts,
                ceilingBytes = plan.ceilingBytes,
                totalDurationSec = probeResult.format.durationSeconds,
                totalParts = actualTotalParts
            )

            // 4. Emit JSON manifest
            val ffmpegVer = ffmpegEngine.version()
            val videoStream = probeResult.streams.firstOrNull { it.codecType == "video" }
            val manifest = Manifest(
                schema = 1,
                source = ManifestSource(
                    name = probeResult.format.filename,
                    size = probeResult.format.sizeBytes,
                    durationSeconds = probeResult.format.durationSeconds,
                    sha256First64MB = "", // Not implemented in v1
                    video = ManifestVideo(
                        codec = videoStream?.codecName ?: "unknown",
                        width = videoStream?.width ?: 0,
                        height = videoStream?.height ?: 0,
                        hdr = "SDR" // Extracted later if needed
                    ),
                    audio = probeResult.streams.filter { it.codecType == "audio" }
                        .map { ManifestAudio(it.codecName, it.language) },
                    subs = probeResult.streams.filter { it.codecType == "subtitle" }
                        .map { ManifestSub(it.codecName, it.language) }
                ),
                parts = manifestParts,
                ffmpegVersion = ffmpegVer,
                appVersion = "0.0.4"
            )

            manifestWriter.writeManifest(subfolder.uri, job.outputBaseName, manifest)

            jobDao.updateProgress(jobId, JobStatus.DONE, 100, 0.0, 0, actualTotalParts, System.currentTimeMillis())

        } catch (e: Exception) {
            e.printStackTrace()
            Timber.e(e, "Split job failed")
            // Check if cancelled vs actual error
            val currentJob = jobDao.getById(jobId)
            if (currentJob?.status != JobStatus.CANCELLED) {
                jobDao.updateProgress(jobId, JobStatus.FAILED, 0, null, null, null, System.currentTimeMillis())
            }
        }
    }

    private suspend fun processPart(
        jobId: String,
        index: Int,
        startSec: Double,
        endSec: Double,
        sourceUri: String,
        outDir: DocumentFile,
        baseName: String,
        manifestParts: MutableList<ManifestPart>,
        ceilingBytes: Long,
        totalDurationSec: Double,
        totalParts: Int
    ) {
        val partName = String.format("%s.part%03d.mkv", baseName, index)
        
        // Find or create PartEntity
        val partId = UUID.randomUUID().toString()
        jobDao.upsertPart(PartEntity(
            id = partId,
            jobId = jobId,
            index = index,
            name = partName,
            startSec = startSec,
            endSec = endSec,
            status = PartStatus.RUNNING
        ))

        val tmpFileName = "$partName.tmp"
        
        // Use com.arthenica.ffmpegkit.FFmpegKitConfig.getSafParameterForWrite if available,
        // but for now we write to app cache, then move it, as FFmpeg requires a physical file or proper SAF path.
        // Actually FFmpegKit can write to SAF URIs directly. We will assume FFmpegKit's SAF path logic is used.
        // For simplicity, we write to a physical temp file and then copy it to the SAF directory using ContentResolver.
        val tempFile = File(context.cacheDir, tmpFileName)
        if (tempFile.exists()) tempFile.delete()

        // SAF source can be passed as content:// or mapped to pipe.
        // Resolve SAF URIs using FFmpegKitConfig
        val resolvedSourceUri = if (sourceUri.startsWith("content://")) {
            com.antonkarpenko.ffmpegkit.FFmpegKitConfig.getSafParameterForRead(context, Uri.parse(sourceUri)) ?: sourceUri
        } else {
            sourceUri
        }

        val cmd = listOf(
            "-hide_banner", "-y",
            "-ss", startSec.toString(),
            "-i", resolvedSourceUri,
            "-to", endSec.toString(),
            "-map", "0", "-c", "copy",
            "-avoid_negative_ts", "make_zero",
            "-copyts",
            "-map_metadata", "0",
            "-map_chapters", "0",
            "-f", "matroska",
            tempFile.absolutePath
        )

        ffmpegEngine.execute(cmd).collect { event ->
            when (event) {
                is EngineEvent.Progress -> {
                    // Update part progress
                    val currentTime = startSec + event.timeSeconds
                    val pct = ((currentTime / totalDurationSec) * 100).toInt().coerceIn(0, 99)
                    
                    // ETA = (remaining duration in seconds) / speed
                    val remainingDurationSec = (totalDurationSec - currentTime).coerceAtLeast(0.0)
                    val speed = if (event.speed > 0) event.speed else 1.0
                    val etaSeconds = (remainingDurationSec / speed).toInt()

                    jobDao.updateProgress(
                        id = jobId,
                        status = JobStatus.RUNNING,
                        pct = pct,
                        speed = event.speed,
                        eta = etaSeconds,
                        parts = totalParts,
                        now = System.currentTimeMillis()
                    )
                }
                is EngineEvent.Completed -> {
                    if (event.exitCode != 0) {
                        Timber.e("FFmpeg part $index error, exit code: ${event.exitCode}")
                        throw IllegalStateException("FFmpeg part $index failed with exit code ${event.exitCode}")
                    }
                }
                else -> {}
            }
        }

        // Verify output size against ceiling
        if (tempFile.length() > ceilingBytes) {
            // Need re-split (Fallback logic not fully implemented, we will just warn for now)
            Timber.w("Part $index exceeds ceiling cap! ${tempFile.length()} > $ceilingBytes")
        }

        // Move temp file to final output directory
        val outDirUri = outDir.uri
        val finalSize: Long
        if (outDirUri.scheme == "file") {
            // Direct file operations — avoids DocumentFile.createFile() which mangles filenames
            val destFile = File(outDirUri.path!!, partName)
            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                // renameTo can fail across filesystems; fall back to copy
                tempFile.inputStream().use { inp ->
                    destFile.outputStream().use { out -> inp.copyTo(out) }
                }
                tempFile.delete()
            }
            finalSize = destFile.length()
        } else {
            // SAF (content://) path — use DocumentFile + ContentResolver
            outDir.findFile(partName)?.delete()
            val destDocFile = outDir.createFile("video/x-matroska", partName)
                ?: throw IllegalStateException("Could not create output file for part $index")
            context.contentResolver.openOutputStream(destDocFile.uri)?.use { outStream ->
                tempFile.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
            }
            tempFile.delete()
            finalSize = destDocFile.length()
        }

        manifestParts.add(ManifestPart(index, partName, startSec, endSec, finalSize))

        jobDao.upsertPart(PartEntity(
            id = partId,
            jobId = jobId,
            index = index,
            name = partName,
            startSec = startSec,
            endSec = endSec,
            sizeBytes = finalSize,
            status = PartStatus.DONE
        ))
    }
}
