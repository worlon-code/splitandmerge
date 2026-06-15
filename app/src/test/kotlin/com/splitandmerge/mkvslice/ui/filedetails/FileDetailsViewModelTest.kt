package com.splitandmerge.mkvslice.ui.filedetails

import com.splitandmerge.mkvslice.engine.FfprobeEngine
import com.splitandmerge.mkvslice.engine.FormatInfo
import com.splitandmerge.mkvslice.engine.ProbeResult
import com.splitandmerge.mkvslice.engine.StreamInfo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileDetailsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockEngine = mockk<FfprobeEngine>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_probe_setsLoadingTrue_atStart() = runTest {
        val flow = kotlinx.coroutines.flow.MutableSharedFlow<ProbeResult>()
        coEvery { mockEngine.probe("content://test") } coAnswers {
            flow.first()
        }

        val viewModel = FileDetailsViewModel(mockEngine)
        viewModel.probeFile("content://test", "test.mkv")

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertNull(state.details)
        assertNull(state.error)
        assertEquals("content://test", state.uri)
        assertEquals("test.mkv", state.filename)
    }

    @Test
    fun test_probeSuccess_setsLoadingFalse_setsDetails() = runTest {
        val mockResult = ProbeResult(
            format = FormatInfo(
                filename = "test.mkv",
                nbStreams = 1,
                formatName = "matroska",
                durationSeconds = 120.0,
                sizeBytes = 1024L,
                bitRate = 100L
            ),
            streams = listOf(
                StreamInfo(
                    index = 0,
                    codecType = "video",
                    codecName = "h264",
                    width = 1920,
                    height = 1080,
                    language = "eng"
                )
            )
        )
        coEvery { mockEngine.probe("content://test") } returns mockResult

        val viewModel = FileDetailsViewModel(mockEngine)
        viewModel.probeFile("content://test", "test.mkv")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        val details = state.details
        assertNotNull(details)
        assertEquals("content://test", details!!.uri)
        assertEquals("test.mkv", details.name)
        assertEquals(1024L, details.sizeBytes)
        assertEquals(120.0, details.durationSec, 0.0)
        assertEquals("1920x1080", details.resolution)
        assertEquals("matroska", details.container)
        assertEquals(1, details.streams.size)
        assertEquals("Video", details.streams[0].type)
        assertEquals("h264", details.streams[0].codec)
    }

    @Test
    fun test_probeFailure_setsLoadingFalse_setsError() = runTest {
        coEvery { mockEngine.probe("content://test") } throws RuntimeException("Probe failed")

        val viewModel = FileDetailsViewModel(mockEngine)
        viewModel.probeFile("content://test", "test.mkv")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.details)
        assertEquals("Probe failed", state.error)
    }
}
