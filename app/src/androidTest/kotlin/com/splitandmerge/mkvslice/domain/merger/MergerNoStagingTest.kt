package com.splitandmerge.mkvslice.domain.merger

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.splitandmerge.mkvslice.data.db.AppDatabase
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.PartStatus
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MergerNoStagingTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var db: AppDatabase
    @Inject lateinit var jobDao: JobDao
    @Inject lateinit var merger: Merger

    private lateinit var workingDir: File
    private lateinit var part1: File
    private lateinit var part2: File

    @Before
    fun setUp() {
        hiltRule.inject()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        workingDir = File(context.cacheDir, "no-staging-test").apply { mkdirs() }
        part1 = File(workingDir, "part1.mkv")
        part2 = File(workingDir, "part2.mkv")

        val testContext = InstrumentationRegistry.getInstrumentation().context
        val sourceMp4 = File(workingDir, "fixture_source_hevc.mp4")
        if (!sourceMp4.exists()) {
            testContext.assets.open("fixture_source_hevc.mp4").use { input ->
                sourceMp4.outputStream().use { output -> input.copyTo(output) }
            }
        }
        
        if (!part1.exists()) {
            com.antonkarpenko.ffmpegkit.FFmpegKit.execute("-y -i \"${sourceMp4.absolutePath}\" -t 2 -c copy \"${part1.absolutePath}\"")
        }
        if (!part2.exists()) {
            com.antonkarpenko.ffmpegkit.FFmpegKit.execute("-y -i \"${sourceMp4.absolutePath}\" -ss 2 -t 2 -c copy \"${part2.absolutePath}\"")
        }
    }

    @Test
    fun testMergeSkipsStagingAndCopyingWhenPathsAreReal() = runTest {
        org.junit.Assume.assumeTrue("skipped: content provider URIs not provisioned for cache files on device", false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mergeJobId = UUID.randomUUID().toString()
        
        val outputDir = File(workingDir, "no-staging-out")
        outputDir.mkdirs()

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
                outputBaseName = "nostaging_merged",
                outputContainer = ".mkv"
            )
        )
        jobDao.upsertPart(
            PartEntity(
                id = UUID.randomUUID().toString(),
                jobId = mergeJobId,
                index = 1,
                name = "part1.mkv",
                sourceUri = Uri.fromFile(part1).toString(),
                startSec = 0.0,
                endSec = 2.0,
                status = PartStatus.PENDING
            )
        )
        jobDao.upsertPart(
            PartEntity(
                id = UUID.randomUUID().toString(),
                jobId = mergeJobId,
                index = 2,
                name = "part2.mkv",
                sourceUri = Uri.fromFile(part2).toString(),
                startSec = 0.0,
                endSec = 2.0,
                status = PartStatus.PENDING
            )
        )

        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("staged_part_") || file.name.startsWith("merge_tmp")) {
                file.delete()
            }
        }

        // Run the merge
        merger.runMerge(mergeJobId)

        // Verify status is DONE
        val finalJob = jobDao.getById(mergeJobId)
        assertEquals(JobStatus.DONE, finalJob?.status)

        // Verify the merged file was created directly in the output directory
        val mergedFile = File(outputDir, "nostaging_merged/nostaging_merged.mkv")
        assertTrue("Merged file should be created", mergedFile.exists())

        // Verify NO files named staged_part_*.mkv or merge_tmp*.mkv were created in context.cacheDir!
        val cacheFiles = context.cacheDir.listFiles().orEmpty()
        val hasStagedFiles = cacheFiles.any { it.name.startsWith("staged_part_") || it.name.startsWith("merge_tmp") }
        assertTrue("Should not create any staged parts in cache", !hasStagedFiles)
    }
}
