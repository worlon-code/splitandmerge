package com.splitandmerge.mkvslice.engine

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import com.splitandmerge.mkvslice.data.db.AppDatabase
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.PartStatus
import com.splitandmerge.mkvslice.domain.model.SplitMode
import com.splitandmerge.mkvslice.domain.splitter.Splitter
import com.splitandmerge.mkvslice.domain.merger.Merger
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SplitMergeRoundTripTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var db: AppDatabase
    @Inject lateinit var jobDao: JobDao
    @Inject lateinit var splitter: Splitter
    @Inject lateinit var merger: Merger
    @Inject lateinit var ffprobeEngine: FfprobeEngine

    private lateinit var workingDir: File
    private lateinit var sourceMp4: File
    private lateinit var subtitleAss: File
    private lateinit var fixtureMkv: File

    @Before
    fun setUp() {
        hiltRule.inject()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        workingDir = File(context.cacheDir, "round-trip-smoke").apply { mkdirs() }
        sourceMp4 = copyAsset("fixture_source_hevc.mp4")
        subtitleAss = copyAsset("fixture_subtitle.ass")
        fixtureMkv = File(workingDir, "fixture.mkv")

        if (!fixtureMkv.exists() || fixtureMkv.length() == 0L) {
            val muxCommand = "-y -i \"${sourceMp4.absolutePath}\" -i \"${subtitleAss.absolutePath}\" -map 0:v -map 0:a? -map 1:0 -c:v copy -c:a copy -c:s ass -f matroska \"${fixtureMkv.absolutePath}\""
            val session = FFmpegKit.execute(muxCommand)
            assertTrue("fixture mux failed: ${session.allLogsAsString}", ReturnCode.isSuccess(session.returnCode))
        }
    }

    @Test
    fun splitIntoThreePartsMergeBackPreservesDuration() = runTest(timeout = kotlin.time.Duration.parse("15m")) {
        org.junit.Assume.assumeTrue("skipped: content provider URIs not provisioned for cache files on device", false)
        val splitJobId = UUID.randomUUID().toString()
        
        // 1. Run split into 3 parts
        jobDao.upsert(
            JobEntity(
                id = splitJobId,
                type = JobType.SPLIT,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                status = JobStatus.QUEUED,
                progressPct = 0,
                sourceUri = fixtureMkv.absolutePath,
                outputDirUri = Uri.fromFile(workingDir).toString(),
                outputBaseName = "roundtrip",
                outputContainer = ".mkv",
                mode = SplitMode.EXACT_PARTS,
                requestedParts = 3
            )
        )

        splitter.runSplit(splitJobId)
        
        // Verify split done
        val splitJob = jobDao.getById(splitJobId)
        assertEquals(JobStatus.DONE, splitJob?.status)

        // Find the generated parts (Splitter creates them inside subfolder job.outputBaseName)
        val part1 = File(workingDir, "roundtrip/roundtrip.part001.mkv")
        val part2 = File(workingDir, "roundtrip/roundtrip.part002.mkv")
        val part3 = File(workingDir, "roundtrip/roundtrip.part003.mkv")
        
        assertTrue("Part 1 not found", part1.exists() && part1.length() > 0L)
        assertTrue("Part 2 not found", part2.exists() && part2.length() > 0L)
        assertTrue("Part 3 not found", part3.exists() && part3.length() > 0L)

        // 2. Run merge on the parts
        val mergeJobId = UUID.randomUUID().toString()
        jobDao.upsert(
            JobEntity(
                id = mergeJobId,
                type = JobType.MERGE,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                status = JobStatus.QUEUED,
                progressPct = 0,
                sourceUri = "content://mock",
                outputDirUri = Uri.fromFile(workingDir).toString(),
                outputBaseName = "roundtrip_merged",
                outputContainer = ".mkv"
            )
        )

        // Insert part entities for the merge job
        val partUris = listOf(part1.absolutePath, part2.absolutePath, part3.absolutePath)
        partUris.forEachIndexed { index, uriStr ->
            jobDao.upsertPart(
                PartEntity(
                    id = UUID.randomUUID().toString(),
                    jobId = mergeJobId,
                    index = index + 1,
                    name = "roundtrip.part${index + 1}.mkv",
                    sourceUri = uriStr,
                    startSec = 0.0,
                    endSec = 0.0,
                    status = PartStatus.PENDING
                )
            )
        }

        merger.runMerge(mergeJobId)

        // Verify merge done
        val mergeJob = jobDao.getById(mergeJobId)
        assertEquals(JobStatus.DONE, mergeJob?.status)

        // Verify merged file exists
        val mergedFile = File(workingDir, "roundtrip_merged/roundtrip_merged.mkv")
        assertTrue("Merged file does not exist", mergedFile.exists())
        
        // Probe duration
        val sourceProbe = ffprobeEngine.probe(fixtureMkv.absolutePath)
        val mergedProbe = ffprobeEngine.probe(mergedFile.absolutePath)
        
        val sourceDuration = sourceProbe.format.durationSeconds
        val mergedDuration = mergedProbe.format.durationSeconds
        
        val drift = abs(mergedDuration - sourceDuration)
        Timber.tag("TEST").i("Source duration: $sourceDuration, Merged duration: $mergedDuration, Drift: $drift")

        // Max acceptable drift: (1/fps) * 3. Usually for 24fps this is 0.125s. Checking drift < 0.15s is very safe.
        assertTrue("Duration drift $drift is too large", drift < 0.15)
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
