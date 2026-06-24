package com.splitandmerge.mkvslice.domain.merger

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.PartStatus
import com.splitandmerge.mkvslice.engine.EngineEvent
import com.splitandmerge.mkvslice.engine.FfmpegEngine
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import com.splitandmerge.mkvslice.engine.FormatInfo
import com.splitandmerge.mkvslice.engine.ProbeResult
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import java.io.File

class MergerArgvTest {

    private lateinit var context: Context
    private val jobDao: JobDao = mockk(relaxed = true)
    private val ffmpegEngine: FfmpegEngine = mockk(relaxed = true)
    private val ffprobeEngine: FfprobeEngine = mockk(relaxed = true)
    private val mergeValidator: MergeValidator = mockk(relaxed = true)
    private val settingsRepository: com.splitandmerge.mkvslice.data.settings.SettingsRepository = mockk(relaxed = true)
    private val fileSystem: com.splitandmerge.mkvslice.platform.io.FileSystem = mockk(relaxed = true)
    private val partModeDetector: PartModeDetector = mockk(relaxed = true)
    private val transportMerger: TransportMerger = mockk(relaxed = true)
    private lateinit var docFile: DocumentFile

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var merger: Merger

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        every { context.cacheDir } returns tempFolder.root
        every { fileSystem.cacheDir() } returns tempFolder.root
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(com.splitandmerge.mkvslice.data.settings.SettingsState())
        every { fileSystem.exists(any()) } answers { firstArg<File>().exists() }
        every { fileSystem.delete(any()) } answers { firstArg<File>().delete() }
        every { fileSystem.createNewFile(any()) } returns true
        every { fileSystem.canRead(any()) } returns true
        every { fileSystem.openInput(any()) } returns java.io.ByteArrayInputStream(ByteArray(0))
        every { fileSystem.openOutput(any()) } returns java.io.ByteArrayOutputStream()
        merger = Merger(context, jobDao, ffmpegEngine, ffprobeEngine, mergeValidator, settingsRepository, fileSystem, partModeDetector, transportMerger)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0

        mockkStatic(android.net.Uri::class)
        val mockUri: android.net.Uri = mockk(relaxed = true)
        every { mockUri.scheme } returns "content"
        every { mockUri.authority } returns "com.dummy.provider"
        every { mockUri.path } returns "/dummy"
        every { android.net.Uri.parse(any()) } returns mockUri

        mockkStatic(DocumentFile::class)
        docFile = mockk(relaxed = true)
        every { DocumentFile.fromFile(any()) } returns docFile
        every { DocumentFile.fromTreeUri(any(), any()) } returns docFile
        every { DocumentFile.fromSingleUri(any(), any()) } returns docFile
        every { docFile.length() } returns 1000L
        every { docFile.createDirectory(any()) } returns docFile
        every { docFile.createFile(any(), any()) } returns docFile

        val outStream = java.io.ByteArrayOutputStream()
        val contentResolver: android.content.ContentResolver = mockk(relaxed = true)
        every { context.contentResolver } returns contentResolver
        every { contentResolver.openOutputStream(any()) } returns outStream

        every { contentResolver.openFileDescriptor(any(), "r") } answers {
            val dummyFile = File(context.cacheDir, "dummy_part_${System.nanoTime()}.mkv")
            dummyFile.writeText("dummy content")
            dummyFile.deleteOnExit()
            val fis = java.io.FileInputStream(dummyFile)
            val fd = fis.fd
            val pfd = mockk<android.os.ParcelFileDescriptor>(relaxed = true)
            every { pfd.fileDescriptor } returns fd
            pfd
        }
    }

    @Test
    fun `Merger runMerge invokes canonical argv`() = runBlocking {
        val jobId = "job-123"
        val job = JobEntity(
            id = jobId,
            type = JobType.MERGE,
            createdAt = 1L,
            updatedAt = 1L,
            status = JobStatus.QUEUED,
            progressPct = 0,
            sourceUri = "content://dummy",
            outputDirUri = "content:///dummy",
            outputBaseName = "out",
            outputContainer = ".mkv"
        )
        val parts = listOf(
            PartEntity(id = "1", jobId = jobId, index = 1, name = "part1", sourceUri = "content://doc1", startSec = 0.0, endSec = 5.0, status = PartStatus.DONE),
            PartEntity(id = "2", jobId = jobId, index = 2, name = "part2", sourceUri = "content://doc2", startSec = 5.0, endSec = 10.0, status = PartStatus.DONE)
        )
        
        coEvery { jobDao.getById(jobId) } returns job
        coEvery { jobDao.getPartsForJob(jobId) } returns parts
        every { ffmpegEngine.execute(any()) } returns flowOf(EngineEvent.Completed(0))

        val format = FormatInfo("part.mkv", 2, "matroska", 10.0, 1000L, 800L)
        val probeResult = ProbeResult(format, emptyList())
        coEvery { ffprobeEngine.probe(any()) } returns probeResult

        merger.runMerge(jobId)

        val slot = slot<List<String>>()
        verify { ffmpegEngine.execute(capture(slot)) }

        val cmd = slot.captured
        val concatFile = File(context.cacheDir, "concat.txt")
        val tempFile = File(context.cacheDir, "merge_tmp.mkv")

        val expected = listOf(
            "-hide_banner", "-y",
            "-f", "concat",
            "-safe", "0",
            "-i", concatFile.absolutePath,
            "-c", "copy",
            tempFile.absolutePath
        )

        assertEquals(expected, cmd)
    }

    @Test
    fun `Merger runMerge fails when storage is insufficient`() = runBlocking {
        val jobId = "job-123"
        val job = JobEntity(
            id = jobId,
            type = JobType.MERGE,
            createdAt = 1L,
            updatedAt = 1L,
            status = JobStatus.QUEUED,
            progressPct = 0,
            sourceUri = "content://dummy",
            outputDirUri = "content:///dummy",
            outputBaseName = "out",
            outputContainer = ".mkv"
        )
        val parts = listOf(
            PartEntity(id = "1", jobId = jobId, index = 1, name = "part1", sourceUri = "content://doc1", startSec = 0.0, endSec = 5.0, status = PartStatus.DONE),
            PartEntity(id = "2", jobId = jobId, index = 2, name = "part2", sourceUri = "content://doc2", startSec = 5.0, endSec = 10.0, status = PartStatus.DONE)
        )
        
        coEvery { jobDao.getById(jobId) } returns job
        coEvery { jobDao.getPartsForJob(jobId) } returns parts

        // Mock DocumentFile to return 1 PB size for each part to trigger out of space check
        every { docFile.length() } returns 1_000_000_000_000_000L // 1 PB

        merger.runMerge(jobId)

        // The job status should be updated to FAILED
        coVerify { jobDao.updateProgress(jobId, JobStatus.FAILED, any(), any(), any(), any(), any()) }
        val slot = slot<JobEntity>()
        coVerify { jobDao.upsert(capture(slot)) }
        assertEquals(JobStatus.FAILED, slot.captured.status)
        assertTrue(slot.captured.errorMessage?.contains("Insufficient storage") == true)
    }

    @Test
    fun `Merger runMerge cleans up staged files and manifest on success`() = runBlocking {
        val jobId = "job-123"
        val job = JobEntity(
            id = jobId,
            type = JobType.MERGE,
            createdAt = 1L,
            updatedAt = 1L,
            status = JobStatus.QUEUED,
            progressPct = 0,
            sourceUri = "content://dummy",
            outputDirUri = "content:///dummy",
            outputBaseName = "out",
            outputContainer = ".mkv"
        )
        val parts = listOf(
            PartEntity(id = "1", jobId = jobId, index = 1, name = "part1", sourceUri = "content://doc1", startSec = 0.0, endSec = 5.0, status = PartStatus.DONE),
            PartEntity(id = "2", jobId = jobId, index = 2, name = "part2", sourceUri = "content://doc2", startSec = 5.0, endSec = 10.0, status = PartStatus.DONE)
        )
        
        coEvery { jobDao.getById(jobId) } returns job
        coEvery { jobDao.getPartsForJob(jobId) } returns parts
        every { ffmpegEngine.execute(any()) } returns flowOf(EngineEvent.Completed(0))

        val format = FormatInfo("part.mkv", 2, "matroska", 10.0, 1000L, 800L)
        val probeResult = ProbeResult(format, emptyList())
        coEvery { ffprobeEngine.probe(any()) } returns probeResult

        merger.runMerge(jobId)

        // Assert that temporary files are deleted
        val concatFile = File(context.cacheDir, "concat.txt")
        val tempFile = File(context.cacheDir, "merge_tmp.mkv")
        val stagedFile0 = File(context.cacheDir, "staged_part_0.mkv")
        val stagedFile1 = File(context.cacheDir, "staged_part_1.mkv")

        assertFalse(concatFile.exists())
        assertFalse(tempFile.exists())
        assertFalse(stagedFile0.exists())
        assertFalse(stagedFile1.exists())
    }

    @Test
    fun `Merger runMerge cleans up staged files and manifest on failure`() = runBlocking {
        val jobId = "job-123"
        val job = JobEntity(
            id = jobId,
            type = JobType.MERGE,
            createdAt = 1L,
            updatedAt = 1L,
            status = JobStatus.QUEUED,
            progressPct = 0,
            sourceUri = "content://dummy",
            outputDirUri = "content:///dummy",
            outputBaseName = "out",
            outputContainer = ".mkv"
        )
        val parts = listOf(
            PartEntity(id = "1", jobId = jobId, index = 1, name = "part1", sourceUri = "content://doc1", startSec = 0.0, endSec = 5.0, status = PartStatus.DONE),
            PartEntity(id = "2", jobId = jobId, index = 2, name = "part2", sourceUri = "content://doc2", startSec = 5.0, endSec = 10.0, status = PartStatus.DONE)
        )
        
        coEvery { jobDao.getById(jobId) } returns job
        coEvery { jobDao.getPartsForJob(jobId) } returns parts
        // Simulate FFmpeg failure
        every { ffmpegEngine.execute(any()) } returns flowOf(EngineEvent.Completed(1))

        val format = FormatInfo("part.mkv", 2, "matroska", 10.0, 1000L, 800L)
        val probeResult = ProbeResult(format, emptyList())
        coEvery { ffprobeEngine.probe(any()) } returns probeResult

        merger.runMerge(jobId)

        // Assert that temporary files are deleted even when merge fails
        val concatFile = File(context.cacheDir, "concat.txt")
        val tempFile = File(context.cacheDir, "merge_tmp.mkv")
        val stagedFile0 = File(context.cacheDir, "staged_part_0.mkv")
        val stagedFile1 = File(context.cacheDir, "staged_part_1.mkv")

        assertFalse(concatFile.exists())
        assertFalse(tempFile.exists())
        assertFalse(stagedFile0.exists())
        assertFalse(stagedFile1.exists())
    }
}
