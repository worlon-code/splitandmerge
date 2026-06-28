package com.splitandmerge.mkvslice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.splitandmerge.mkvslice.MainActivity
import com.splitandmerge.mkvslice.R
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.di.ApplicationScope
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.splitter.Splitter
import com.splitandmerge.mkvslice.engine.FfmpegEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import com.splitandmerge.mkvslice.domain.progress.JobProgressTracker
import com.splitandmerge.mkvslice.domain.defaulttracks.DefaultTracksEngine
import com.splitandmerge.mkvslice.domain.defaulttracks.model.EditSpec
import com.splitandmerge.mkvslice.data.db.DefaultTrackFileResultDao
import com.splitandmerge.mkvslice.data.db.entity.DefaultTrackFileResultEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.boolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class JobService : Service() {

    @Inject lateinit var jobDao: JobDao
    @Inject lateinit var splitter: Splitter
    @Inject lateinit var merger: com.splitandmerge.mkvslice.domain.merger.Merger
    @Inject lateinit var ffmpegEngine: FfmpegEngine
    @Inject lateinit var startupReadyDeferred: CompletableDeferred<Unit>
    @Inject lateinit var jobProgressTracker: JobProgressTracker
    @Inject lateinit var defaultTracksEngine: DefaultTracksEngine
    @Inject lateinit var defaultTrackFileResultDao: DefaultTrackFileResultDao

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var wakeLock: PowerManager.WakeLock? = null

    private var isProcessing = false
    private var currentJobId: String? = null
    private var currentJobType: com.splitandmerge.mkvslice.domain.model.JobType? = null

    /** Per-job coroutine handle — cancelled and joined by [cancelCurrentJob] (A3). */
    private var currentJobCoroutine: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_CANCEL) {
            val jobId = intent.getStringExtra(EXTRA_JOB_ID)
            if (jobId != null && jobId == currentJobId) {
                serviceScope.launch { cancelCurrentJob() }
            }
        } else {
            if (!isProcessing) {
                startProcessing()
            }
        }
        return START_STICKY
    }

    private fun startProcessing() {
        isProcessing = true
        acquireWakeLock()

        startForeground(NOTIFICATION_ID, buildNotification("Checking for jobs...", 0))

        serviceScope.launch {
            // A6: wait for App.onCreate() startup sweep to complete before touching the queue.
            startupReadyDeferred.await()

            while (isActive) {
                val nextJob = jobDao.nextQueued()
                if (nextJob == null) {
                    break
                }

                currentJobId = nextJob.id
                currentJobType = nextJob.type
                val processingText = when (nextJob.type) {
                    com.splitandmerge.mkvslice.domain.model.JobType.SPLIT -> "Processing: ${nextJob.outputBaseName}"
                    com.splitandmerge.mkvslice.domain.model.JobType.MERGE -> "Merging: ${nextJob.outputBaseName}"
                    com.splitandmerge.mkvslice.domain.model.JobType.SET_DEFAULT_TRACKS -> "Setting defaults: ${nextJob.outputBaseName}"
                }
                updateNotification(processingText, 0)

                // Store per-job coroutine handle so cancelCurrentJob() can join it (A3).
                currentJobCoroutine = serviceScope.launch {
                    try {
                        if (nextJob.type == com.splitandmerge.mkvslice.domain.model.JobType.SPLIT) {
                            splitter.runSplit(nextJob.id)
                        } else if (nextJob.type == com.splitandmerge.mkvslice.domain.model.JobType.MERGE) {
                            merger.runMerge(nextJob.id)
                        } else if (nextJob.type == com.splitandmerge.mkvslice.domain.model.JobType.SET_DEFAULT_TRACKS) {
                            runDefaultTracksJob(nextJob.id)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Job failed")
                    }
                }

                currentJobCoroutine?.join()
                currentJobId = null
                currentJobType = null
                currentJobCoroutine = null
            }

            stopProcessing()
        }
    }

    private suspend fun runDefaultTracksJob(jobId: String) {
        val rows = defaultTrackFileResultDao.getResultsForJob(jobId)
        val totalRows = rows.size
        if (totalRows == 0) {
            jobDao.updateProgress(jobId, com.splitandmerge.mkvslice.domain.model.JobStatus.DONE, 100, null, null, 0, System.currentTimeMillis())
            return
        }

        jobDao.updateProgress(jobId, com.splitandmerge.mkvslice.domain.model.JobStatus.RUNNING, 0, null, null, totalRows, System.currentTimeMillis())

        try {
            rows.forEachIndexed { index, row ->
                if (!kotlin.coroutines.coroutineContext.isActive) {
                    throw CancellationException("Job was cancelled")
                }

                if (row.status != "PENDING") {
                    return@forEachIndexed
                }

                val progressPct = (index * 100) / totalRows
                jobDao.updateProgress(jobId, com.splitandmerge.mkvslice.domain.model.JobStatus.RUNNING, progressPct, null, null, totalRows, System.currentTimeMillis())
                updateNotification("File ${index + 1} of $totalRows: ${row.displayName}", progressPct)

                val spec = deserializeEditSpec(row.appliedSpecJson)

                jobProgressTracker.setFileProgress(row.uri, 0)
                val result = defaultTracksEngine.processFile(row.uri, spec, jobId, index) { pct ->
                    jobProgressTracker.setFileProgress(row.uri, pct)
                }
                jobProgressTracker.setFileProgress(row.uri, 100)

                val updatedRow = row.copy(
                    status = result.status,
                    reason = result.reason,
                    writeStrategy = result.writeStrategy
                )
                defaultTrackFileResultDao.insert(updatedRow)
            }

            jobDao.updateProgress(jobId, com.splitandmerge.mkvslice.domain.model.JobStatus.DONE, 100, null, null, totalRows, System.currentTimeMillis())
            jobProgressTracker.clearFileProgress()
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                val remainingRows = defaultTrackFileResultDao.getResultsForJob(jobId)
                val updatedRows = remainingRows.map { r ->
                    if (r.status == "PENDING") {
                        r.copy(status = "SKIPPED", reason = "canceled")
                    } else {
                        r
                    }
                }
                defaultTrackFileResultDao.insertAll(updatedRows)
                jobDao.updateProgress(jobId, com.splitandmerge.mkvslice.domain.model.JobStatus.CANCELLED, 100, null, null, totalRows, System.currentTimeMillis())
                jobProgressTracker.clearFileProgress()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error running default tracks job")
            jobDao.updateProgress(jobId, com.splitandmerge.mkvslice.domain.model.JobStatus.FAILED, 100, null, null, totalRows, System.currentTimeMillis())
            jobProgressTracker.clearFileProgress()
        }
    }

    private fun deserializeEditSpec(jsonString: String): EditSpec {
        val json = Json.parseToJsonElement(jsonString).jsonObject
        val audio = json["defaultAudioTrackNumber"]?.jsonPrimitive?.long ?: 0L
        val sub = json["defaultSubtitleTrackNumber"]?.jsonPrimitive?.longOrNull
        val forced = json["forcedSubtitle"]?.jsonPrimitive?.boolean ?: false
        return EditSpec(audio, sub, forced)
    }

    private fun stopProcessing() {
        isProcessing = false
        currentJobId = null
        currentJobCoroutine = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Cancels the running job coroutine and waits for its [finally] block to complete
     * before allowing the queue loop to advance (A3). Also kills FFmpeg.
     */
    private suspend fun cancelCurrentJob() {
        val jobId = currentJobId ?: return
        Timber.i("Cancelling job $jobId")
        jobProgressTracker.setPhaseHint(jobId, null)
        // Write CANCELLED status first so Merger's catch block doesn't overwrite it with FAILED.
        jobDao.updateProgress(jobId, JobStatus.CANCELLED, 0, null, null, null, System.currentTimeMillis())
        // cancelAndJoin() blocks until finally{} in Merger.runMerge() completes (A3).
        currentJobCoroutine?.cancelAndJoin()
        // Belt-and-suspenders: also signal the FFmpeg engine.
        ffmpegEngine.cancel("all")
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MkvSlice::EngineWakeLock").apply {
                acquire(24 * 60 * 60 * 1000L)
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background Jobs",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress of splitting and merging"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String, progress: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = Intent(this, JobService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_JOB_ID, currentJobId)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = when (currentJobType) {
            com.splitandmerge.mkvslice.domain.model.JobType.SPLIT -> "Splitting Video"
            com.splitandmerge.mkvslice.domain.model.JobType.MERGE -> "Merging Videos"
            com.splitandmerge.mkvslice.domain.model.JobType.SET_DEFAULT_TRACKS -> "Setting Default Tracks"
            else -> "Video Splitter"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun updateNotification(text: String, progress: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text, progress))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        releaseWakeLock()
    }

    companion object {
        const val CHANNEL_ID = "job_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "com.splitandmerge.mkvslice.CANCEL_JOB"
        const val EXTRA_JOB_ID = "extra_job_id"
    }
}
