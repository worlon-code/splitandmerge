package com.splitandmerge.mkvslice.ui.result

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.SavedStateHandle
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import com.splitandmerge.mkvslice.engine.FormatInfo
import com.splitandmerge.mkvslice.engine.ProbeResult
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MergeResultViewModelTest {

    private val savedStateHandle = mockk<SavedStateHandle>(relaxed = true)
    private val jobDao = mockk<JobDao>(relaxed = true)
    private val ffprobeEngine = mockk<FfprobeEngine>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(DocumentFile::class)
        mockkStatic(Uri::class)
        val mockUri = mockk<Uri>(relaxed = true)
        every { mockUri.scheme } returns "content"
        every { Uri.parse(any()) } returns mockUri
        every { savedStateHandle.get<String>("jobId") } returns "job1"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(DocumentFile::class)
        unmockkStatic(Uri::class)
    }

    private fun createJob(): JobEntity {
        return JobEntity(
            id = "job1",
            type = JobType.MERGE,
            sourceUri = "",
            outputDirUri = "content://outdir",
            outputBaseName = "Bahubali",
            outputContainer = ".mkv",
            status = JobStatus.DONE,
            progressPct = 100,
            createdAt = 1000L,
            updatedAt = 2000L
        )
    }

    @Test
    fun `Path A obvious filename - finds expected file directly`() = runTest {
        val job = createJob()
        coEvery { jobDao.getById("job1") } returns job

        val baseOutDir = mockk<DocumentFile>(relaxed = true)
        val subDir = mockk<DocumentFile>(relaxed = true)
        val expectedFile = mockk<DocumentFile>(relaxed = true)

        every { DocumentFile.fromTreeUri(context, any()) } returns baseOutDir
        
        // Setup subdirectory finding
        every { baseOutDir.listFiles() } returns arrayOf(subDir)
        every { subDir.isDirectory } returns true
        every { subDir.name } returns "Bahubali"
        every { subDir.lastModified() } returns 100L

        // Setup file finding inside subDir
        every { subDir.findFile("Bahubali.mkv") } returns expectedFile
        every { expectedFile.name } returns "Bahubali.mkv"
        every { expectedFile.length() } returns 5000L
        every { expectedFile.uri } returns Uri.parse("content://outdir/Bahubali/Bahubali.mkv")

        val probeResult = ProbeResult(FormatInfo("video", 1, "mkv", 30.0, 5000L, 0L), emptyList())
        coEvery { ffprobeEngine.probe(any()) } returns probeResult

        val viewModel = MergeResultViewModel(savedStateHandle, jobDao, ffprobeEngine, context)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Bahubali.mkv", state.outputFilename)
        assertEquals(5000L, state.outputSizeBytes)
        assertEquals(null, state.error)
    }

    @Test
    fun `Path B collision - folder has suffix, finds file with suffix`() = runTest {
        val job = createJob()
        coEvery { jobDao.getById("job1") } returns job

        val baseOutDir = mockk<DocumentFile>(relaxed = true)
        val subDir = mockk<DocumentFile>(relaxed = true)
        val expectedFile = mockk<DocumentFile>(relaxed = true)

        every { DocumentFile.fromTreeUri(context, any()) } returns baseOutDir
        
        // Setup subdirectory finding (it finds the collision folder)
        every { baseOutDir.listFiles() } returns arrayOf(subDir)
        every { subDir.isDirectory } returns true
        every { subDir.name } returns "Bahubali (1)"
        every { subDir.lastModified() } returns 100L

        // Path A fails
        every { subDir.findFile("Bahubali.mkv") } returns null
        // Path B succeeds
        every { subDir.findFile("Bahubali (1).mkv") } returns expectedFile
        
        every { expectedFile.name } returns "Bahubali (1).mkv"
        every { expectedFile.length() } returns 5000L
        every { expectedFile.uri } returns Uri.parse("content://outdir/Bahubali%20(1)/Bahubali%20(1).mkv")

        val probeResult = ProbeResult(FormatInfo("video", 1, "mkv", 30.0, 5000L, 0L), emptyList())
        coEvery { ffprobeEngine.probe(any()) } returns probeResult

        val viewModel = MergeResultViewModel(savedStateHandle, jobDao, ffprobeEngine, context)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Bahubali (1).mkv", state.outputFilename)
        assertEquals(5000L, state.outputSizeBytes)
        assertEquals(null, state.error)
    }

    @Test
    fun `Path C fallback - picks largest mkv when names don't match`() = runTest {
        val job = createJob()
        coEvery { jobDao.getById("job1") } returns job

        val baseOutDir = mockk<DocumentFile>(relaxed = true)
        val subDir = mockk<DocumentFile>(relaxed = true)
        val fileSmall = mockk<DocumentFile>(relaxed = true)
        val fileLarge = mockk<DocumentFile>(relaxed = true)

        every { DocumentFile.fromTreeUri(context, any()) } returns baseOutDir
        
        // Setup subdirectory finding
        every { baseOutDir.listFiles() } returns arrayOf(subDir)
        every { subDir.isDirectory } returns true
        every { subDir.name } returns "Bahubali"
        every { subDir.lastModified() } returns 100L

        // Path A and B fail
        every { subDir.findFile("Bahubali.mkv") } returns null
        
        // Path C
        every { subDir.listFiles() } returns arrayOf(fileSmall, fileLarge)
        
        every { fileSmall.name } returns "junk.mkv"
        every { fileSmall.length() } returns 1000L
        
        every { fileLarge.name } returns "real_output.mkv"
        every { fileLarge.length() } returns 9000L
        every { fileLarge.uri } returns Uri.parse("content://outdir/Bahubali/real_output.mkv")

        val probeResult = ProbeResult(FormatInfo("video", 1, "mkv", 30.0, 9000L, 0L), emptyList())
        coEvery { ffprobeEngine.probe(any()) } returns probeResult

        val viewModel = MergeResultViewModel(savedStateHandle, jobDao, ffprobeEngine, context)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("real_output.mkv", state.outputFilename)
        assertEquals(9000L, state.outputSizeBytes)
        assertEquals(null, state.error)
    }

    @Test
    fun `Failure - throws IllegalStateException when empty`() = runTest {
        val job = createJob()
        coEvery { jobDao.getById("job1") } returns job

        val baseOutDir = mockk<DocumentFile>(relaxed = true)
        val subDir = mockk<DocumentFile>(relaxed = true)

        every { DocumentFile.fromTreeUri(context, any()) } returns baseOutDir
        
        every { baseOutDir.listFiles() } returns arrayOf(subDir)
        every { subDir.isDirectory } returns true
        every { subDir.name } returns "Bahubali"
        every { subDir.lastModified() } returns 100L
        every { subDir.uri } returns Uri.parse("content://outdir/Bahubali")

        // Paths A, B, C fail
        every { subDir.findFile(any()) } returns null
        every { subDir.listFiles() } returns emptyArray()

        val viewModel = MergeResultViewModel(savedStateHandle, jobDao, ffprobeEngine, context)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.error != null)
        assertTrue(state.error!!.contains("Output file not found"))
    }
}
