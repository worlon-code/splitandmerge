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
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import java.io.File

class MergerFastPathTest {

    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<android.content.ContentResolver>(relaxed = true)
    private val jobDao = mockk<JobDao>(relaxed = true)
    private val ffmpegEngine = mockk<FfmpegEngine>(relaxed = true)
    private val ffprobeEngine = mockk<FfprobeEngine>(relaxed = true)
    private val mergeValidator = mockk<MergeValidator>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    private lateinit var classUnderTest: Merger
    private var capturedConcatContents: String? = null
    private var capturedTempOutputFileExists: Boolean = false

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val cacheDir get() = tempFolder.root
    private lateinit var testOutputDir: File
    private lateinit var testMergedDir: File

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
            println("LOG.E: ${firstArg<String>()} - ${secondArg<String>()}")
            0
        }
        every { android.util.Log.e(any(), any(), any()) } answers {
            val t = thirdArg<Throwable>()
            println("LOG.E WITH THROWABLE: ${firstArg<String>()} - ${secondArg<String>()}")
            t.printStackTrace()
            0
        }
        every { android.util.Log.wtf(any(), any<String>()) } returns 0

        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
        every { context.getContentResolver() } returns contentResolver

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

        // Setup real temporary output directory on disk for parent file existence checks
        testOutputDir = File(cacheDir, "outputDir_${System.currentTimeMillis()}")
        testMergedDir = File(testOutputDir, "merged")

        classUnderTest = Merger(context, jobDao, ffmpegEngine, ffprobeEngine, mergeValidator, settingsRepository)
    }

    @After
    fun teardown() {
        unmockkStatic(DocumentFile::class)
        unmockkStatic(Uri::class)
        unmockkObject(MergePathResolver)
        unmockkStatic(android.util.Log::class)

        try {
            unmockkConstructor(java.io.FileInputStream::class)
        } catch (e: Exception) {}
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

        every { DocumentFile.fromSingleUri(context, uriPart1) } returns partFile1
        every { DocumentFile.fromSingleUri(context, uriPart2) } returns partFile2

        val baseOutDir = mockk<DocumentFile>(relaxed = true)
        val outDir = mockk<DocumentFile>(relaxed = true)
        val newFile = mockk<DocumentFile>(relaxed = true)

        every { DocumentFile.fromTreeUri(context, uriOutputDir) } returns baseOutDir
        every { baseOutDir.createDirectory("merged") } returns outDir
        every { outDir.name } returns "merged"
        every { outDir.createFile("video/x-matroska", "merged.mkv") } returns newFile
        every { newFile.name } returns "merged.mkv"
        every { newFile.uri } returns uriOut

        coEvery { ffprobeEngine.probe(any()) } returns ProbeResult(FormatInfo("", 1, "", 5.0, 0L, 0L), emptyList())
        every { ffmpegEngine.execute(any()) } answers {
            val cmdList = firstArg<List<String>>()
            val outPath = cmdList.last()
            File(outPath).createNewFile()

            val concatFile = File(cacheDir, "concat.txt")
            capturedConcatContents = if (concatFile.exists()) concatFile.readText() else null
            capturedTempOutputFileExists = File(cacheDir, "merge_tmp.mkv").exists()

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

        val pfd = mockk<android.os.ParcelFileDescriptor>(relaxed = true)
        every { pfd.fileDescriptor } returns java.io.FileDescriptor()
        every { contentResolver.openFileDescriptor(any(), any()) } returns pfd

        mockkConstructor(java.io.FileInputStream::class)
        every { anyConstructed<java.io.FileInputStream>().read(any<ByteArray>()) } returns -1

        // Pre-create temp output file so Phase 3 copy-out does not fail
        File(cacheDir, "merge_tmp.mkv").createNewFile()

        // When
        classUnderTest.runMerge(jobId)

        // Then: Staging copying is executed (not skipped), so we open input streams
        verify(exactly = 1) { contentResolver.openFileDescriptor(uriPart1, any()) }
        verify(exactly = 1) { contentResolver.openFileDescriptor(uriPart2, any()) }

        // FFmpeg command receives concat list and temp output file
        val cmdSlot = slot<List<String>>()
        verify { ffmpegEngine.execute(capture(cmdSlot)) }
        val cmd = cmdSlot.captured
        assert(capturedConcatContents != null)
        assert(capturedConcatContents!!.contains(File(cacheDir, "staged_part_0.mkv").absolutePath))
        assert(capturedConcatContents!!.contains(File(cacheDir, "staged_part_1.mkv").absolutePath))
        assert(capturedTempOutputFileExists)
        assert(cmd.contains(File(cacheDir, "merge_tmp.mkv").absolutePath))
    }

    @Test
    fun test_improveReliabilityOFF_allPathsResolve_skipsStaging() = runTest {
        val jobId = "job1"
        setupMockJob(jobId)

        // Given: settings.improveReliability = false
        every { settingsRepository.settingsFlow } returns flowOf(SettingsState(improveReliability = false))

        // Create actual local files with non-zero content under tempFolder
        val part1File = tempFolder.newFile("part1.mkv").apply {
            writeBytes(byteArrayOf(0, 1, 2, 3, 4))
        }
        val part2File = tempFolder.newFile("part2.mkv").apply {
            writeBytes(byteArrayOf(5, 6, 7, 8, 9))
        }

        // All paths resolve to real files
        every { MergePathResolver.resolveUriToPath(context, "content://part1") } returns part1File.absolutePath
        every { MergePathResolver.resolveUriToPath(context, "content://part2") } returns part2File.absolutePath
        every { MergePathResolver.resolveUriToPath(context, "content://outputDir") } returns testOutputDir.absolutePath

        // Create the directory on host so File.parentFile.exists() returns true
        testMergedDir.mkdirs()

        // When
        classUnderTest.runMerge(jobId)

        // Then: Staging copy is skipped (no openFileDescriptor for inputs)
        verify(exactly = 0) { contentResolver.openFileDescriptor(uriPart1, "r") }
        verify(exactly = 0) { contentResolver.openFileDescriptor(uriPart2, "r") }

        // FFmpeg command receives resolved paths directly
        val cmdSlot = slot<List<String>>()
        verify { ffmpegEngine.execute(capture(cmdSlot)) }
        val cmd = cmdSlot.captured

        assert(capturedConcatContents != null)
        assert(capturedConcatContents!!.contains(part1File.absolutePath))
        assert(capturedConcatContents!!.contains(part2File.absolutePath))
        assert(!capturedTempOutputFileExists)

        val expectedDest = File(testMergedDir, "merged.mkv").absolutePath
        assert(cmd.contains(expectedDest))
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

        val pfd = mockk<android.os.ParcelFileDescriptor>(relaxed = true)
        every { pfd.fileDescriptor } returns java.io.FileDescriptor()
        every { contentResolver.openFileDescriptor(any(), any()) } returns pfd

        mockkConstructor(java.io.FileInputStream::class)
        every { anyConstructed<java.io.FileInputStream>().read(any<ByteArray>()) } returns -1

        // Pre-create temp output file so Phase 3 copy-out does not fail
        File(cacheDir, "merge_tmp.mkv").createNewFile()

        // When
        classUnderTest.runMerge(jobId)

        // Then: Stages files normally since fast-path is disabled
        verify(exactly = 1) { contentResolver.openFileDescriptor(uriPart1, any()) }
        verify(exactly = 1) { contentResolver.openFileDescriptor(uriPart2, any()) }
        assert(capturedConcatContents != null)
        assert(capturedConcatContents!!.contains(File(cacheDir, "staged_part_0.mkv").absolutePath))
        assert(capturedConcatContents!!.contains(File(cacheDir, "staged_part_1.mkv").absolutePath))
        assert(capturedTempOutputFileExists)
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

        val pfd = mockk<android.os.ParcelFileDescriptor>(relaxed = true)
        every { pfd.fileDescriptor } returns java.io.FileDescriptor()
        every { contentResolver.openFileDescriptor(any(), any()) } returns pfd

        mockkConstructor(java.io.FileInputStream::class)
        every { anyConstructed<java.io.FileInputStream>().read(any<ByteArray>()) } returns -1

        // Pre-create temp output file so Phase 3 copy-out does not fail
        File(cacheDir, "merge_tmp.mkv").createNewFile()

        // When
        classUnderTest.runMerge(jobId)

        // Then: Stages files normally
        verify(exactly = 1) { contentResolver.openFileDescriptor(uriPart1, any()) }
        verify(exactly = 1) { contentResolver.openFileDescriptor(uriPart2, any()) }
        assert(capturedConcatContents != null)
        assert(capturedConcatContents!!.contains(File(cacheDir, "staged_part_0.mkv").absolutePath))
        assert(capturedConcatContents!!.contains(File(cacheDir, "staged_part_1.mkv").absolutePath))
        assert(capturedTempOutputFileExists)
    }
}
