package com.splitandmerge.mkvslice.ui.mergeorder

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.domain.merger.MergeValidator
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import com.splitandmerge.mkvslice.engine.FormatInfo
import com.splitandmerge.mkvslice.engine.ProbeResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
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
class MergeOrderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockContext: Context
    private lateinit var mockFfprobeEngine: FfprobeEngine
    private lateinit var mockMergeValidator: MergeValidator

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockContext = mockk(relaxed = true)
        mockFfprobeEngine = mockk()
        mockMergeValidator = mockk(relaxed = true)

        mockkStatic(Uri::class)
        val uriMap = mutableMapOf<String, Uri>()
        every { Uri.parse(any()) } answers {
            val str = firstArg<String>()
            uriMap.getOrPut(str) {
                val mockUri = mockk<Uri>(relaxed = true)
                every { mockUri.toString() } returns str
                mockUri
            }
        }

        mockkStatic(DocumentFile::class)
        every { DocumentFile.fromSingleUri(any(), any()) } answers {
            val mockDoc = mockk<DocumentFile>(relaxed = true)
            every { mockDoc.name } returns "test_part"
            every { mockDoc.length() } returns 50000000L
            mockDoc
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun test_addParts_emitsVerifyingTrue_thenFalse_onSuccess() = runTest {
        val mockProbeResult = ProbeResult(
            format = FormatInfo(
                filename = "test.mkv",
                nbStreams = 2,
                formatName = "matroska",
                durationSeconds = 120.0,
                sizeBytes = 50000000L,
                bitRate = 3000000L
            ),
            streams = emptyList()
        )
        coEvery { mockFfprobeEngine.probe(any()) } coAnswers {
            delay(100)
            mockProbeResult
        }

        val viewModel = MergeOrderViewModel(mockContext, mockFfprobeEngine, mockMergeValidator)

        // Start addParts
        viewModel.addParts(listOf("content://uri1", "content://uri2"))
        
        assertTrue(viewModel.state.value.verifying)
        assertNull(viewModel.state.value.error)

        // Advance coroutine execution
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify state is successfully updated and verifying = false
        assertFalse(viewModel.state.value.verifying)
        assertNull(viewModel.state.value.error)
        assertEquals(2, viewModel.state.value.parts.size)
        assertEquals(120.0, viewModel.state.value.parts[0].durationSec, 0.0)
    }

    @Test
    fun test_addParts_emitsVerifyingFalse_onException() = runTest {
        coEvery { mockFfprobeEngine.probe(any()) } coAnswers {
            delay(100)
            throw RuntimeException("Probe failed!")
        }

        val viewModel = MergeOrderViewModel(mockContext, mockFfprobeEngine, mockMergeValidator)

        // Start addParts
        viewModel.addParts(listOf("content://uri1"))
        
        assertTrue(viewModel.state.value.verifying)
        assertNull(viewModel.state.value.error)

        // Advance coroutine execution
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify verifying = false and error state is set
        assertFalse(viewModel.state.value.verifying)
        assertNotNull(viewModel.state.value.error)
        assertEquals("Probe failed!", viewModel.state.value.error)
        assertEquals(0, viewModel.state.value.parts.size)
    }
}
