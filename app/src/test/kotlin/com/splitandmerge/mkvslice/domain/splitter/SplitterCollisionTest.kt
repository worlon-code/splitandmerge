package com.splitandmerge.mkvslice.domain.splitter

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.SplitMode
import org.junit.After
import com.splitandmerge.mkvslice.domain.model.Manifest
import com.splitandmerge.mkvslice.domain.progress.JobProgressTracker
import com.splitandmerge.mkvslice.engine.EngineEvent
import com.splitandmerge.mkvslice.engine.FfmpegEngine
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import com.splitandmerge.mkvslice.engine.FormatInfo
import com.splitandmerge.mkvslice.engine.ProbeResult
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SplitterCollisionTest {

    private val context = mockk<Context>(relaxed = true)
    private val jobDao = mockk<JobDao>(relaxed = true)
    private val ffprobeEngine = mockk<FfprobeEngine>(relaxed = true)
    private val ffmpegEngine = mockk<FfmpegEngine>(relaxed = true)
    private val cutPlanner = mockk<CutPlanner>(relaxed = true)
    private val manifestWriter = mockk<ManifestWriter>(relaxed = true)
    private val jobProgressTracker = mockk<JobProgressTracker>(relaxed = true)

    private lateinit var classUnderTest: Splitter

    @Before
    fun setup() {
        mockkStatic(DocumentFile::class)
        mockkStatic(Uri::class)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.isLoggable(any(), any()) } returns true
        every { android.util.Log.v(any(), any()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.wtf(any(), any<String>()) } returns 0

        timber.log.Timber.plant(object : timber.log.Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                println("TIMBER LOG [$tag]: $message")
                t?.printStackTrace()
            }
        })

        every { Uri.parse(any()) } answers {
            val uriStr = firstArg<String>()
            val mockUri = mockk<Uri>(relaxed = true)
            if (uriStr.startsWith("content://")) {
                every { mockUri.scheme } returns "content"
            } else {
                every { mockUri.scheme } returns "file"
                every { mockUri.path } returns "/mock/path"
            }
            mockUri
        }
        classUnderTest = Splitter(
            context, jobDao, ffprobeEngine, ffmpegEngine, cutPlanner, manifestWriter, jobProgressTracker
        )
    }

    @After
    fun teardown() {
        timber.log.Timber.uprootAll()
        unmockkStatic(DocumentFile::class)
        unmockkStatic(Uri::class)
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `when SAF creates folder with collision suffix, manifest uses the suffix for part names`() = runTest {
        val jobId = "job1"
        val job = JobEntity(
            id = jobId,
            type = JobType.SPLIT,
            mode = SplitMode.SIZE_CAP_ONLY,
            sourceUri = "file:///source",
            outputDirUri = "content://outdir",
            outputBaseName = "Bahubali",
            outputContainer = ".mkv",
            status = JobStatus.QUEUED,
            progressPct = 0,
            createdAt = 0L,
            updatedAt = 0L
        )

        coEvery { jobDao.getById(jobId) } returns job

        val sourceFile = mockk<DocumentFile>(relaxed = true)
        every { sourceFile.exists() } returns true
        every { DocumentFile.fromSingleUri(context, any()) } returns sourceFile
        
        val baseOutDir = mockk<DocumentFile>(relaxed = true)
        val collisionDir = mockk<DocumentFile>(relaxed = true)
        val destDocFile = mockk<DocumentFile>(relaxed = true)

        every { DocumentFile.fromTreeUri(context, any()) } returns baseOutDir
        every { baseOutDir.createDirectory("Bahubali") } returns collisionDir
        every { collisionDir.name } returns "Bahubali (1)"
        every { collisionDir.uri } returns Uri.parse("content://outdir/Bahubali%20(1)")
        every { collisionDir.createFile("video/x-matroska", any()) } returns destDocFile
        every { collisionDir.findFile(any()) } returns null
        every { destDocFile.uri } returns Uri.parse("content://outdir/Bahubali%20(1)/part.mkv")

        val probeResult = ProbeResult(FormatInfo("video", 1, "mkv", 30.0, 100L, 0L), emptyList())
        coEvery { ffprobeEngine.probe(any()) } returns probeResult
        coEvery { ffprobeEngine.keyframes(any()) } returns emptyList()

        // Mock planner returning 2 cuts -> 3 parts
        val plan = CutPlan(listOf(10.0, 20.0), 3, SplitMode.SIZE_CAP_ONLY, 3, 50L, 50L)
        every { cutPlanner.plan(any(), any(), any(), any(), any(), any(), any()) } returns plan

        val cacheDir = File(System.getProperty("java.io.tmpdir"))
        every { context.cacheDir } returns cacheDir
        every { context.contentResolver.openOutputStream(any()) } returns java.io.ByteArrayOutputStream()
        every { ffmpegEngine.execute(any()) } answers {
            val cmd = firstArg<List<String>>()
            val tempPath = cmd.last()
            File(tempPath).apply {
                parentFile?.mkdirs()
                writeText("fake video data")
            }
            flowOf(EngineEvent.Completed(0))
        }
        coEvery { ffmpegEngine.version() } returns "mock-version"

        val manifestSlot = slot<Manifest>()
        val baseNameSlot = slot<String>()
        coEvery { manifestWriter.writeManifest(any(), capture(baseNameSlot), capture(manifestSlot)) } returns Result.success(Unit)

        classUnderTest.runSplit(jobId)

        // Verify base name passed to manifest writer is the collision name
        assertTrue("Expected 'Bahubali (1)', got '${baseNameSlot.captured}'", baseNameSlot.captured == "Bahubali (1)")

        // Verify parts in manifest have the collision suffix
        val manifest = manifestSlot.captured
        assertTrue("Manifest should have 3 parts", manifest.parts.size == 3)
        assertTrue("Part 1 name should match pattern", manifest.parts[0].name == "Bahubali (1).part001.mkv")
        assertTrue("Part 2 name should match pattern", manifest.parts[1].name == "Bahubali (1).part002.mkv")
        assertTrue("Part 3 name should match pattern", manifest.parts[2].name == "Bahubali (1).part003.mkv")
    }
}
