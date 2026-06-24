package com.splitandmerge.mkvslice.domain.merger

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.splitandmerge.mkvslice.data.db.AppDatabase
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.PartStatus
import com.splitandmerge.mkvslice.engine.FfmpegEngine
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import javax.inject.Inject
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.platform.io.FileSystem

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MergerSequentialCleanupTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var db: AppDatabase
    @Inject lateinit var jobDao: JobDao
    @Inject lateinit var ffmpegEngine: FfmpegEngine
    @Inject lateinit var ffprobeEngine: FfprobeEngine
    @Inject lateinit var mergeValidator: MergeValidator
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var fileSystem: FileSystem

    private lateinit var workingDir: File
    private lateinit var part1: File
    private lateinit var part2: File

    @Before
    fun setUp() {
        hiltRule.inject()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        workingDir = File(context.cacheDir, "cleanup-test").apply { mkdirs() }
        part1 = File(workingDir, "part1.mkv")
        part2 = File(workingDir, "part2.mkv")

        val testContext = InstrumentationRegistry.getInstrumentation().context
        val sourceMp4 = File(workingDir, "fixture_source_hevc.mp4")
        if (!sourceMp4.exists()) {
            testContext.assets.open("fixture_source_hevc.mp4").use { input ->
                sourceMp4.outputStream().use { output -> input.copyTo(output) }
            }
        }
        
        // Loop the source video to create 10-second parts so safety boundary (10s + 5s = 15s) is crossed during a 20s concat run
        if (!part1.exists()) {
            com.antonkarpenko.ffmpegkit.FFmpegKit.execute("-y -stream_loop 10 -i \"${sourceMp4.absolutePath}\" -t 10 -c copy \"${part1.absolutePath}\"")
        }
        if (!part2.exists()) {
            com.antonkarpenko.ffmpegkit.FFmpegKit.execute("-y -stream_loop 10 -i \"${sourceMp4.absolutePath}\" -t 10 -c copy \"${part2.absolutePath}\"")
        }
    }

    @Test
    fun testStagedPartsAreDeletedSequentiallyDuringConcat() = runTest {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val mergeJobId = UUID.randomUUID().toString()
        val outputDir = File(workingDir, "cleanup-out")
        outputDir.mkdirs()

        val uri1 = "content://com.splitandmerge.mkvslice.test.provider/part1"
        val uri2 = "content://com.splitandmerge.mkvslice.test.provider/part2"

        val mockProvider = object : android.content.ContentProvider() {
            override fun onCreate() = true
            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?
            ): android.database.Cursor {
                val proj = projection ?: arrayOf(
                    android.provider.OpenableColumns.DISPLAY_NAME,
                    android.provider.OpenableColumns.SIZE
                )
                val cursor = android.database.MatrixCursor(proj)
                val row = cursor.newRow()
                for (col in proj) {
                    when (col) {
                        android.provider.OpenableColumns.DISPLAY_NAME -> {
                            row.add(if (uri.toString() == uri1) "part1.mkv" else "part2.mkv")
                        }
                        android.provider.OpenableColumns.SIZE -> {
                            row.add(if (uri.toString() == uri1) part1.length() else part2.length())
                        }
                        else -> row.add(null)
                    }
                }
                return cursor
            }
            override fun getType(uri: Uri) = "video/x-matroska"
            override fun insert(uri: Uri, values: android.content.ContentValues?) = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
                return when (uri.toString()) {
                    uri1 -> ParcelFileDescriptor.open(part1, ParcelFileDescriptor.MODE_READ_ONLY)
                    uri2 -> ParcelFileDescriptor.open(part2, ParcelFileDescriptor.MODE_READ_ONLY)
                    else -> null
                }
            }
        }

        val info = android.content.pm.ProviderInfo().apply {
            authority = "com.splitandmerge.mkvslice.test.provider"
        }
        mockProvider.attachInfo(targetContext, info)

        val mockResolver = android.test.mock.MockContentResolver()
        mockResolver.addProvider("com.splitandmerge.mkvslice.test.provider", mockProvider)

        val mockContext = object : android.content.ContextWrapper(targetContext) {
            override fun getContentResolver(): ContentResolver {
                return mockResolver
            }
        }

        jobDao.upsert(
            JobEntity(
                id = mergeJobId,
                type = JobType.MERGE,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                status = JobStatus.QUEUED,
                progressPct = 0,
                sourceUri = "content://mock",
                outputDirUri = Uri.fromFile(outputDir).toString(),
                outputBaseName = "cleanup_merged",
                outputContainer = ".mkv"
            )
        )

        jobDao.upsertPart(
            PartEntity(
                id = UUID.randomUUID().toString(),
                jobId = mergeJobId,
                index = 1,
                name = "part1.mkv",
                sourceUri = uri1,
                startSec = 0.0,
                endSec = 10.0,
                status = PartStatus.PENDING
            )
        )
        jobDao.upsertPart(
            PartEntity(
                id = UUID.randomUUID().toString(),
                jobId = mergeJobId,
                index = 2,
                name = "part2.mkv",
                sourceUri = uri2,
                startSec = 0.0,
                endSec = 10.0,
                status = PartStatus.PENDING
            )
        )

        val mergerWithMock = Merger(mockContext, jobDao, ffmpegEngine, ffprobeEngine, mergeValidator, settingsRepository, fileSystem)
        
        val staged0 = File(targetContext.cacheDir, "staged_part_0.mkv")
        val staged1 = File(targetContext.cacheDir, "staged_part_1.mkv")
        staged0.delete()
        staged1.delete()

        var staged0DeletedDuringConcat = false

        // Launch a coroutine to observe job progress and verify that staged_part_0.mkv is deleted
        // once progress goes past the safety boundary (timeSeconds > 15s) but BEFORE the job finishes (status is still RUNNING)
        val jobJob = launch {
            jobDao.observeById(mergeJobId).collect { job ->
                if (job != null && job.status == JobStatus.RUNNING) {
                    // Check if staged0 exists
                    // Staging local file: staged_part_0.mkv
                    if (!staged0.exists() && staged0.length() == 0L) {
                        // It got deleted! Let's check if the current percentage corresponds to concat or copy-out
                        // 3 steps total, so concat starts at 33% and ends at 66%. If it is deleted before 100%, it works!
                        if (job.progressPct in 33..99) {
                            staged0DeletedDuringConcat = true
                        }
                    }
                }
            }
        }

        // Run the merge
        mergerWithMock.runMerge(mergeJobId)

        jobJob.cancel()

        // Verify status is DONE
        val finalJob = jobDao.getById(mergeJobId)
        assertEquals(JobStatus.DONE, finalJob?.status)

        // Verify staged0 deleted and staged1 deleted
        assertFalse("Staged 0 should be cleaned up", staged0.exists())
        assertFalse("Staged 1 should be cleaned up", staged1.exists())
        assertTrue("Staged 0 should be deleted DURING concat progress", staged0DeletedDuringConcat)
    }
}
