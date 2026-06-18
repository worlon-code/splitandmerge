package com.splitandmerge.mkvslice.domain.merger

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class MergerCollisionTest {

    private val context = mockk<Context>(relaxed = true)
    private val jobDao = mockk<JobDao>(relaxed = true)
    private val ffmpegEngine = mockk<FfmpegEngine>(relaxed = true)
    private val ffprobeEngine = mockk<FfprobeEngine>(relaxed = true)
    private val mergeValidator = mockk<MergeValidator>(relaxed = true)
    private val settingsRepository = mockk<com.splitandmerge.mkvslice.data.settings.SettingsRepository>(relaxed = true)

    private lateinit var classUnderTest: Merger

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

        every { settingsRepository.settingsFlow } returns flowOf(com.splitandmerge.mkvslice.data.settings.SettingsState())
        val mockUri = mockk<Uri>(relaxed = true)
        every { mockUri.scheme } returns "content"
        every { Uri.parse(any()) } returns mockUri
        classUnderTest = Merger(context, jobDao, ffmpegEngine, ffprobeEngine, mergeValidator, settingsRepository)
    }

    @After
    fun teardown() {
        timber.log.Timber.uprootAll()
        unmockkStatic(DocumentFile::class)
        unmockkStatic(Uri::class)
        unmockkStatic(android.util.Log::class)
        unmockkConstructor(java.io.FileInputStream::class)
    }

    @Test
    fun `when SAF creates folder with collision suffix, outFileName uses the suffix`() = runTest {
        val jobId = "job1"
        val job = JobEntity(
            id = jobId,
            type = JobType.MERGE,
            sourceUri = "",
            outputDirUri = "content://somedir",
            outputBaseName = "Bahubali",
            outputContainer = ".mkv",
            status = JobStatus.QUEUED,
            progressPct = 0,
            createdAt = 0L,
            updatedAt = 0L
        )

        coEvery { jobDao.getById(jobId) } returns job
        
        val part = PartEntity(
            id = "part1",
            jobId = jobId,
            index = 1,
            name = "part1.mkv",
            startSec = 0.0,
            endSec = 10.0,
            status = com.splitandmerge.mkvslice.domain.model.PartStatus.DONE,
            sourceUri = "content://part1"
        )
        coEvery { jobDao.getPartsForJob(jobId) } returns listOf(part)

        val baseOutDir = mockk<DocumentFile>(relaxed = true)
        val collisionDir = mockk<DocumentFile>(relaxed = true)
        val finalOutFile = mockk<DocumentFile>(relaxed = true)

        // Mock URI parsing and DocumentFile resolution
        val partFileMock = mockk<DocumentFile>(relaxed = true)
        every { partFileMock.exists() } returns true
        every { partFileMock.length() } returns 1024L
        every { DocumentFile.fromTreeUri(context, any()) } returns baseOutDir
        every { DocumentFile.fromSingleUri(context, any()) } returns partFileMock

        // Mock the directory creation collision
        every { baseOutDir.createDirectory("Bahubali") } returns collisionDir
        every { collisionDir.name } returns "Bahubali (1)"

        // Mock the file creation
        every { collisionDir.createFile("video/x-matroska", any()) } returns finalOutFile
        every { finalOutFile.uri } returns Uri.parse("content://out")

        // Mock the copy operations and engine
        val cacheDir = File(System.getProperty("java.io.tmpdir"))
        every { context.cacheDir } returns cacheDir
        every { context.contentResolver.openOutputStream(any()) } returns java.io.ByteArrayOutputStream()
        
        val pfd = mockk<android.os.ParcelFileDescriptor>(relaxed = true)
        every { pfd.fileDescriptor } returns java.io.FileDescriptor()
        every { context.contentResolver.openFileDescriptor(any(), any()) } returns pfd

        mockkConstructor(java.io.FileInputStream::class)
        every { anyConstructed<java.io.FileInputStream>().read(any<ByteArray>()) } returns -1

        coEvery { ffprobeEngine.probe(any()) } returns ProbeResult(FormatInfo("", 1, "", 0.0, 0L, 0L), emptyList())
        every { ffmpegEngine.execute(any()) } returns flowOf(EngineEvent.Completed(0))

        classUnderTest.runMerge(jobId)

        // Verify outFileName passed to outDir.createFile() is "Bahubali (1).mkv"
        verify { collisionDir.createFile("video/x-matroska", "Bahubali (1).mkv") }
    }
}
