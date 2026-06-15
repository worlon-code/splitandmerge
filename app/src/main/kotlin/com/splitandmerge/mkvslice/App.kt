package com.splitandmerge.mkvslice

import android.app.Application
import android.content.SharedPreferences
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.di.ApplicationScope
import com.splitandmerge.mkvslice.domain.merger.MergeCacheSweeper
import com.splitandmerge.mkvslice.util.log.FileLoggingTree
import com.splitandmerge.mkvslice.util.log.LogPurger
import com.splitandmerge.mkvslice.util.migration.FolderUriMigration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.splitandmerge.mkvslice.domain.progress.JobProgressTracker
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import java.io.File

@HiltAndroidApp
class App : Application() {

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var jobDao: JobDao
    @Inject lateinit var startupReadyDeferred: CompletableDeferred<Unit>
    @Inject lateinit var jobProgressTracker: JobProgressTracker
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        appScope.launch(Dispatchers.IO) {
            try {
                val logsDir = File(cacheDir, "logs")
                Timber.plant(FileLoggingTree(logsDir))
                LogPurger.purgeOldLogs(logsDir, 7)
            } catch (e: Exception) {
                android.util.Log.e("MKVSliceApp", "Failed to plant FileLoggingTree or purge logs", e)
            }
        }

        // Migrate legacy SharedPreferences folder URI → DataStore (once, idempotent).
        appScope.launch(Dispatchers.IO) {
            try {
                FolderUriMigration.run(sharedPreferences, settingsRepository)
            } catch (e: Exception) {
                Timber.tag("FolderMigration").e(e, "Migration failed (non-fatal)")
            }
        }

        // Run startup recovery sweep. JobService.onStartCommand() awaits the
        // startupReadyDeferred so it won't poll the queue until this completes (A6).
        appScope.launch(Dispatchers.IO) {
            try {
                // Fix 2: mark stuck RUNNING jobs and parts as FAILED (A5).
                jobDao.recoverStuckJobs()
                jobDao.recoverStuckParts()
                val allJobs = jobDao.observeAll().first()
                allJobs.forEach { jobProgressTracker.setPhaseHint(it.id, null) }
                Timber.tag("STARTUP").i("Recovery sweep complete.")

                // Fix 3: delete orphaned merge cache artefacts (A7).
                MergeCacheSweeper.sweep(cacheDir)
            } catch (e: Exception) {
                Timber.tag("STARTUP").e(e, "Startup sweep failed (non-fatal)")
            } finally {
                // Signal JobService that the queue is safe to read (A6).
                startupReadyDeferred.complete(Unit)
            }
        }
    }
}
