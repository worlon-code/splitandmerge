package com.splitandmerge.mkvslice.domain.merger

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.data.settings.SettingsState
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.engine.EngineEvent
import com.splitandmerge.mkvslice.engine.FfmpegEngine
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import com.splitandmerge.mkvslice.engine.FormatInfo
import com.splitandmerge.mkvslice.engine.ProbeResult
import com.splitandmerge.mkvslice.platform.io.FileSystem
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class MergerFastPathTest {

    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<android.content.ContentResolver>(relaxed = true)
    private val jobDao = mockk<JobDao>(relaxed = true)
    private val ffmpegEngine = mockk<FfmpegEngine>(relaxed = true)
    private val ffprobeEngine = mockk<FfprobeEngine>(relaxed = true)
    private val mergeValidator = mockk<MergeValidator>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val fileSystem = mockk<FileSystem>(relaxed = true)

    private lateinit var classUnderTest: Merger
    private var capturedConcatContents: String? = null
    private var capturedTempOutputFileExists: Boolean = false

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val cacheDir get() = tempFolder.root
    private val testOutputDir get() = File(tempFolder.root, "testOutputDir")
    private val testMergedDir get() = File(testOutputDir, "merged")
    private val tempOutputFile get() = File(cacheDir, "merge_tmp.mkv")
    private val streams = mutableListOf<java.io.FileInputStream>()

    private lateinit var uriPart1: Uri
    private lateinit var uriPart2: Uri
    private lateinit var uriOutputDir: Uri
    private lateinit var uriOut: Uri

    private fun mockUri(uriStr: String): Uri {
        val m = mockk<Uri>(relaxed = true)
        every { m.scheme } returns if (uriStr.startsWith("content")) "content" else "file"
        every { m.toString() } returns uriStr
        every { m.path } returns uriStr.substringAfter("://")
        return m
    }

    @Before
    fun setup() {
        capturedConcatContents = null
        capturedTempOutputFileExists = false

        mockkStatic(DocumentFile::class)
        mockkStatic(Uri::class)
        mockkObject(MergePathResolver)
        mockkStatic(android.util.Log::class)

        every { android.util.Log.isLoggable(any(), any()) } returns true
        every { android.util.Log.v(any(), any()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } answers {
            0
        }
        every { android.util.Log.e(any(), any(), any()) } answers {
            0
        }
        every { android.util.Log.wtf(any(), any<String>()) } returns 0

        every { context.contentResolver } returns contentResolver
        every { context.getContentResolver() } returns contentResolver
        every { context.cacheDir } returns tempFolder.root
        every { contentResolver.openFileDescriptor(any(), "r") } answers {
            val dummyFile = File(tempFolder.root, "dummy_part_${System.nanoTime()}.mkv")
            dummyFile.writeText("dummy content")
            dummyFile.deleteOnExit()
            val fis = java.io.FileInputStream(dummyFile)
            streams.add(fis)
            val fd = fis.fd
            val pfd = mockk<android.os.ParcelFileDescriptor>(relaxed = true)
            every { pfd.fileDescriptor } returns fd
            pfd
        }

        every { fileSystem.cacheDir() } returns tempFolder.root

        // Pre-create distinct Uri mock instances per string to avoid nested MockK every{} calls
        uriPart1 = mockUri("content://part1")
        uriPart2 = mockUri("content://part2")
        uriOutputDir = mockUri("content://outputDir")
        uriOut = mockUri("content://out")

        val uriMocks = mapOf(
            "content://part1" to uriPart1,
            "content://part2" to uriPart2,
            "content://outputDir" to uriOutputDir,
            "content://out" to uriOut
        )

        every { Uri.parse(any()) } answers {
            val uriStr = firstArg<String>()
            uriMocks[uriStr] ?: mockk<Uri>(relaxed = true)
        }

        classUnderTest = Merger(context, jobDao, ffmpegEngine, ffprobeEngine, mergeValidator, settingsRepository, fileSystem)
        testMergedDir.mkdirs()
    }

    @After
    fun teardown() {
        unmockkStatic(DocumentFile::class)
        unmockkStatic(Uri::class)
        unmockkObject(MergePathResolver)
        unmockkStatic(android.util.Log::class)
        
        streams.forEach { try { it.close() } catch (_: Exception) {} }
        streams.clear()
    }

    private fun setupMockJob(jobId: String): JobEntity {
        val job = JobEntity(
            id = jobId,
            type = JobType.MERGE,
            sourceUri = "",
            outputDirUri = "content://outputDir",
            outputBaseName = "merged",
            outputContainer = ".mkv",
            status = JobStatus.QUEUED,
            progressPct = 0,
            createdAt = 0L,
            updatedAt = 0L
        )
        coEvery { jobDao.getById(jobId) } returns job

        val parts = listOf(
            PartEntity(
                id = "part1",
                jobId = jobId,
                index = 1,
                name = "part1.mkv",
                sourceUri = "content://part1",
                startSec = 0.0,
                endSec = 5.0,
                sizeBytes = 1024L,
                status = com.splitandmerge.mkvslice.domain.model.PartStatus.DONE
            ),
            PartEntity(
                id = "part2",
                jobId = jobId,
                index = 2,
                name = "part2.mkv",
                sourceUri = "content://part2",
                startSec = 5.0,
                endSec = 10.0,
                sizeBytes = 2048L,
                status = com.splitandmerge.mkvslice.domain.model.PartStatus.DONE
            )
        )
        coEvery { jobDao.getPartsForJob(jobId) } returns parts

        val partFile1 = mockk<DocumentFile>(relaxed = true)
        val partFile2 = mockk<DocumentFile>(relaxed = true)
        every { partFile1.length() } returns 1024L
        every { partFile2.length() } returns 2048L

        every { DocumentFile.fromSingleUri(context, any()) } answers {
            val uri = secondArg<Uri>()
            when (uri.toString()) {
                "content://part1" -> partFile1
                "content://part2" -> partFile2
                else -> null
            }
        }

        val baseOutDir = mockk<DocumentFile>(relaxed = true)
        val outDir = mockk<DocumentFile>(relaxed = true)
        val newFile = mockk<DocumentFile>(relaxed = true)

        every { DocumentFile.fromTreeUri(context, any()) } answers {
            val uri = secondArg<Uri>()
            if (uri.toString() == "content://outputDir") baseOutDir else null
        }
        every { baseOutDir.createDirectory("merged") } returns outDir
        every { outDir.name } returns "merged"
        every { outDir.createFile("video/x-matroska", "merged.mkv") } returns newFile
        every { newFile.name } returns "merged.mkv"
        every { newFile.uri } returns uriOut

        coEvery { ffprobeEngine.probe(any()) } returns ProbeResult(FormatInfo("", 1, "", 5.0, 0L, 0L), emptyList())
        every { ffmpegEngine.execute(any()) } answers {
            val concatFile = File(cacheDir, "concat.txt")
            capturedConcatContents = if (concatFile.exists()) concatFile.readText() else null
            capturedTempOutputFileExists = fileSystem.exists(tempOutputFile)

            flowOf(EngineEvent.Completed(0))
        }

        return job
    }

    @Test
    fun test_default_improveReliabilityON_stagesEverything() = runTest {
        val jobId = "job1"
        setupMockJob(jobId)

        // Given: settings.improveReliability = true (default)
        every { settingsRepository.settingsFlow } returns flowOf(SettingsState(improveReliability = true))

        // Mock resolver to return paths
        every { MergePathResolver.resolveUriToPath(context, "content://part1") } returns "/resolved/part1.mkv"
        every { MergePathResolver.resolveUriToPath(context, "content://part2") } returns "/resolved/part2.mkv"
        every { MergePathResolver.resolveUriToPath(context, "content://outputDir") } returns testOutputDir.absolutePath



        // Mock FileSystem calls
        every { fileSystem.exists(any()) } answers { firstArg<File>() == tempOutputFile }
        every { fileSystem.delete(any()) } returns true
        every { fileSystem.createNewFile(any()) } returns true
        every { fileSystem.length(any()) } returns 1024L
        every { fileSystem.canRead(any()) } returns true
        every { fileSystem.openInput(any()) } returns ByteArrayInputStream(byteArrayOf(0, 1, 2, 3, 4))

        val outputCapture = mutableListOf<File>()
        every { fileSystem.openOutput(capture(outputCapture)) } returns ByteArrayOutputStream()

        // When
        classUnderTest.runMerge(jobId)

        // Then: Staging copying is executed (not skipped), so we open input streams
        val uriCapture = mutableListOf<Uri>()
        verify(exactly = 2) { contentResolver.openFileDescriptor(capture(uriCapture), any()) }
        assertTrue(uriCapture.any { it.toString() == "content://part1" })
        assertTrue(uriCapture.any { it.toString() == "content://part2" })

        // FFmpeg command receives concat list and temp output file
        val cmdSlot = slot<List<String>>()
        verify { ffmpegEngine.execute(capture(cmdSlot)) }
        val cmd = cmdSlot.captured
        assert(capturedConcatContents != null)
        assertTrue(capturedConcatContents!!.contains(File(cacheDir, "staged_part_0.mkv").absolutePath))
        assertTrue(capturedConcatContents!!.contains(File(cacheDir, "staged_part_1.mkv").absolutePath))
        assert(capturedTempOutputFileExists)
        assertTrue(cmd.contains(tempOutputFile.absolutePath))

        verify(exactly = 2) { fileSystem.openOutput(any()) }
        assertEquals(2, outputCapture.size)
        assertTrue(outputCapture.any { it.name.startsWith("staged_part_") })
    }

    @Test
    fun test_improveReliabilityOFF_allPathsResolve_skipsStaging() = runTest {
        val jobId = "job1"
        setupMockJob(jobId)

        // Given: settings.improveReliability = false
        every { settingsRepository.settingsFlow } returns flowOf(SettingsState(improveReliability = false))

        // All paths resolve to real files
        every { MergePathResolver.resolveUriToPath(context, "content://part1") } returns "/resolved/part1.mkv"
        every { MergePathResolver.resolveUriToPath(context, "content://part2") } returns "/resolved/part2.mkv"
        every { MergePathResolver.resolveUriToPath(context, "content://outputDir") } returns testOutputDir.absolutePath

        // Mock FileSystem calls
        every { fileSystem.exists(any()) } answers {
            val f = firstArg<File>()
            f.absolutePath.contains("merged")
        }
        every { fileSystem.canRead(any()) } returns true
        every { fileSystem.createNewFile(any()) } returns true
        every { fileSystem.delete(any()) } returns true
        every { fileSystem.openInput(any()) } returns ByteArrayInputStream(byteArrayOf(1))

        val outputCapture = mutableListOf<File>()
        every { fileSystem.openOutput(capture(outputCapture)) } returns ByteArrayOutputStream()

        // When
        classUnderTest.runMerge(jobId)

        // Then: Staging copy is skipped (no openFileDescriptor for inputs)
        verify(exactly = 0) { contentResolver.openFileDescriptor(any(), any()) }

        // FFmpeg command receives resolved paths directly
        val cmdSlot = slot<List<String>>()
        verify { ffmpegEngine.execute(capture(cmdSlot)) }
        val cmd = cmdSlot.captured

        assert(capturedConcatContents != null)
        assertTrue(capturedConcatContents!!.contains("/resolved/part1.mkv"))
        assertTrue(capturedConcatContents!!.contains("/resolved/part2.mkv"))
        assert(!capturedTempOutputFileExists)

        val expectedDest = File(testMergedDir, "merged.mkv").absolutePath
        assertTrue(cmd.contains(expectedDest))

        verify(exactly = 0) { fileSystem.openOutput(any()) }
    }

    @Test
    fun test_improveReliabilityOFF_oneInputUnresolved_stillStages() = runTest {
        val jobId = "job1"
        setupMockJob(jobId)

        // Given: settings.improveReliability = false
        every { settingsRepository.settingsFlow } returns flowOf(SettingsState(improveReliability = false))

        // One input path returns null from MergePathResolver
        every { MergePathResolver.resolveUriToPath(context, "content://part1") } returns "/resolved/part1.mkv"
        every { MergePathResolver.resolveUriToPath(context, "content://part2") } returns null
        every { MergePathResolver.resolveUriToPath(context, "content://outputDir") } returns testOutputDir.absolutePath



        // Mock FileSystem calls
        every { fileSystem.exists(any()) } answers { firstArg<File>() == tempOutputFile }
        every { fileSystem.delete(any()) } returns true
        every { fileSystem.createNewFile(any()) } returns true
        every { fileSystem.length(any()) } returns 1024L
        every { fileSystem.canRead(any()) } returns true
        every { fileSystem.openInput(any()) } returns ByteArrayInputStream(byteArrayOf(0, 1, 2, 3, 4))

        val outputCapture = mutableListOf<File>()
        every { fileSystem.openOutput(capture(outputCapture)) } returns ByteArrayOutputStream()

        // When
        classUnderTest.runMerge(jobId)

        // Then: Stages files normally since fast-path is disabled
        val uriCapture = mutableListOf<Uri>()
        verify(exactly = 2) { contentResolver.openFileDescriptor(capture(uriCapture), any()) }
        assertTrue(uriCapture.any { it.toString() == "content://part1" })
        assertTrue(uriCapture.any { it.toString() == "content://part2" })
        assert(capturedConcatContents != null)
        assertTrue(capturedConcatContents!!.contains(File(cacheDir, "staged_part_0.mkv").absolutePath))
        assertTrue(capturedConcatContents!!.contains(File(cacheDir, "staged_part_1.mkv").absolutePath))
        assert(capturedTempOutputFileExists)

        verify(exactly = 2) { fileSystem.openOutput(any()) }
        assertEquals(2, outputCapture.size)
    }

    @Test
    fun test_improveReliabilityOFF_outputUnresolved_stillStages() = runTest {
        val jobId = "job1"
        setupMockJob(jobId)

        // Given: settings.improveReliability = false
        every { settingsRepository.settingsFlow } returns flowOf(SettingsState(improveReliability = false))

        // Output dir returns null from MergePathResolver
        every { MergePathResolver.resolveUriToPath(context, "content://part1") } returns "/resolved/part1.mkv"
        every { MergePathResolver.resolveUriToPath(context, "content://part2") } returns "/resolved/part2.mkv"
        every { MergePathResolver.resolveUriToPath(context, "content://outputDir") } returns null



        // Mock FileSystem calls
        every { fileSystem.exists(any()) } answers { firstArg<File>() == tempOutputFile }
        every { fileSystem.delete(any()) } returns true
        every { fileSystem.createNewFile(any()) } returns true
        every { fileSystem.length(any()) } returns 1024L
        every { fileSystem.canRead(any()) } returns true
        every { fileSystem.openInput(any()) } returns ByteArrayInputStream(byteArrayOf(0, 1, 2, 3, 4))

        val outputCapture = mutableListOf<File>()
        every { fileSystem.openOutput(capture(outputCapture)) } returns ByteArrayOutputStream()

        // When
        classUnderTest.runMerge(jobId)

        // Then: Stages files normally
        val uriCapture = mutableListOf<Uri>()
        verify(exactly = 2) { contentResolver.openFileDescriptor(capture(uriCapture), any()) }
        assertTrue(uriCapture.any { it.toString() == "content://part1" })
        assertTrue(uriCapture.any { it.toString() == "content://part2" })
        assert(capturedConcatContents != null)
        assertTrue(capturedConcatContents!!.contains(File(cacheDir, "staged_part_0.mkv").absolutePath))
        assertTrue(capturedConcatContents!!.contains(File(cacheDir, "staged_part_1.mkv").absolutePath))
        assert(capturedTempOutputFileExists)

        verify(exactly = 2) { fileSystem.openOutput(any()) }
        assertEquals(2, outputCapture.size)
    }
}
