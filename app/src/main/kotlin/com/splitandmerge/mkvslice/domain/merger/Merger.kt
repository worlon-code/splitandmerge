package com.splitandmerge.mkvslice.domain.merger

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.engine.EngineEvent
import com.splitandmerge.mkvslice.engine.FfmpegEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Merger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jobDao: JobDao,
    private val ffmpegEngine: FfmpegEngine
) {

    suspend fun runMerge(jobId: String) {
        val job = jobDao.getById(jobId) ?: throw IllegalArgumentException("Job $jobId not found")
        
        try {
            jobDao.updateProgress(jobId, JobStatus.RUNNING, 0, null, null, 1, System.currentTimeMillis())
            
            // For merge jobs, manifestPath stores a comma-separated list of part URIs
            val partsString = job.manifestPath ?: throw IllegalArgumentException("No parts to merge")
            val partUris = partsString.split(",")
            
            if (partUris.isEmpty()) throw IllegalArgumentException("Part list is empty")

            // Determine output directory
            val outDirUri = Uri.parse(job.outputDirUri)
            val outDir = if (outDirUri.scheme == "file") {
                DocumentFile.fromFile(File(outDirUri.path!!))
            } else {
                DocumentFile.fromTreeUri(context, outDirUri)
            } ?: throw IllegalStateException("Cannot access output directory")

            val outFileName = "${job.outputBaseName}${job.outputContainer}"
            
            // Create a temporary local file because FFmpeg often struggles writing concat directly to SAF
            val tempOutputFile = File(context.cacheDir, "merge_tmp${job.outputContainer}")
            if (tempOutputFile.exists()) tempOutputFile.delete()

            // Prepare concat.txt
            val concatLines = partUris.map { partUri ->
                val resolvedUri = if (partUri.startsWith("content://")) {
                    com.antonkarpenko.ffmpegkit.FFmpegKitConfig.getSafParameterForRead(context, Uri.parse(partUri)) ?: partUri
                } else {
                    partUri
                }
                // FFmpeg requires paths to be properly escaped or quoted in concat file.
                // We enclose it in single quotes. If the path contains single quotes, it would need escaping,
                // but Android SAF URIs (like saf:1) do not.
                "file '$resolvedUri'"
            }
            
            val concatFile = File(context.cacheDir, "concat.txt")
            concatFile.writeText(concatLines.joinToString("\n"))

            val cmd = listOf(
                "-hide_banner", "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", concatFile.absolutePath,
                "-c", "copy",
                tempOutputFile.absolutePath
            )

            // Calculate total size to estimate progress (speed is tricky with copy concat, but we can try)
            // Progress reporting during concat -c copy is extremely fast. We can just fake it or rely on engine's time= if available.
            // Concat actually outputs time=... which corresponds to the final output duration.
            // If we don't have total duration, we just display indeterminate or 50%.
            
            ffmpegEngine.execute(cmd).collect { event ->
                when (event) {
                    is EngineEvent.Progress -> {
                        // We could track duration if we probed all parts, but for simplicity:
                        // Concat -c copy is usually I/O bound and finishes in seconds.
                        jobDao.updateProgress(
                            id = jobId,
                            status = JobStatus.RUNNING,
                            pct = 50, // Indeterminate 50%
                            speed = event.speed,
                            eta = null,
                            parts = 1,
                            now = System.currentTimeMillis()
                        )
                    }
                    is EngineEvent.Completed -> {
                        if (event.exitCode != 0) {
                            Timber.e("FFmpeg merge error, exit code: ${event.exitCode}")
                            throw IllegalStateException("FFmpeg merge failed with exit code ${event.exitCode}")
                        }
                    }
                    else -> {}
                }
            }

            // Move from temp cache to SAF output directory
            val newFile = outDir.createFile("video/x-matroska", outFileName)
                ?: throw IllegalStateException("Could not create output file in SAF")
            
            context.contentResolver.openOutputStream(newFile.uri)?.use { outStream ->
                tempOutputFile.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
            }
            
            // Clean up temps
            tempOutputFile.delete()
            concatFile.delete()

            jobDao.updateProgress(jobId, JobStatus.DONE, 100, 0.0, 0, 1, System.currentTimeMillis())

        } catch (e: Exception) {
            Timber.e(e, "Merge Job $jobId failed")
            val currentJob = jobDao.getById(jobId)
            if (currentJob?.status != JobStatus.CANCELLED) {
                jobDao.updateProgress(jobId, JobStatus.FAILED, 0, null, null, null, System.currentTimeMillis())
                jobDao.upsert(currentJob!!.copy(status = JobStatus.FAILED, errorMessage = e.message))
            }
        }
    }
}
