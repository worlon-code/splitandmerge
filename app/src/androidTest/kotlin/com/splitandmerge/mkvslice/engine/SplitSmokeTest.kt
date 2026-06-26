package com.splitandmerge.mkvslice.engine

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.splitandmerge.mkvslice.data.db.AppDatabase
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.SplitMode
import com.splitandmerge.mkvslice.domain.splitter.CutPlanner
import com.splitandmerge.mkvslice.domain.splitter.ManifestWriter
import com.splitandmerge.mkvslice.domain.splitter.Splitter
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
class SplitSmokeTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var db: AppDatabase
    @Inject lateinit var jobDao: JobDao
    @Inject lateinit var splitter: Splitter

    private lateinit var workingDir: File
    private lateinit var sourceMp4: File

    @Before
    fun setUp() {
        hiltRule.inject()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        workingDir = File(context.cacheDir, "split-smoke").apply { mkdirs() }
        sourceMp4 = File(context.getExternalFilesDir(null), "Dridam.mkv")
    }

    @Test
    fun splitCreatesPartsAndManifest() = runTest(timeout = kotlin.time.Duration.parse("15m")) {
        org.junit.Assume.assumeTrue("Skipping: Dridam.mkv fixture is not present on the device", sourceMp4.exists())
        // Enqueue a split job
        val jobId = UUID.randomUUID().toString()
        jobDao.upsert(
            JobEntity(
                id = jobId,
                type = JobType.SPLIT,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                status = JobStatus.QUEUED,
                progressPct = 0,
                sourceUri = sourceMp4.absolutePath, // file:// path natively accepted by FFmpeg
                outputDirUri = Uri.fromFile(workingDir).toString(),
                outputBaseName = "fixture",
                outputContainer = ".mkv",
                mode = SplitMode.EXACT_PARTS,
                requestedParts = 2
            )
        )

        // Run the splitter
        splitter.runSplit(jobId)

        // Verify status
        val job = jobDao.getById(jobId)
        assertEquals(JobStatus.DONE, job?.status)

        // Verify parts created
        val part1 = File(workingDir, "fixture.part001.mkv")
        val part2 = File(workingDir, "fixture.part002.mkv")
        val manifest = File(workingDir, "fixture.split.json")

        assertTrue("Part 1 not found", part1.exists() && part1.length() > 0L)
        assertTrue("Part 2 not found", part2.exists() && part2.length() > 0L)
        assertTrue("Manifest not found", manifest.exists() && manifest.length() > 0L)
    }

    private fun copyAsset(assetName: String): File {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val outFile = File(workingDir, assetName)
        if (!outFile.exists()) {
            testContext.assets.open(assetName).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return outFile
    }
}
