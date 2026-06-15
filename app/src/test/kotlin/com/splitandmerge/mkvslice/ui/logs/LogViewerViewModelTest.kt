package com.splitandmerge.mkvslice.ui.logs

import android.content.Context
import com.splitandmerge.mkvslice.util.log.FileLoggingTree
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewerViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var logsDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        val cacheDir = tempFolder.newFolder("cache")
        logsDir = File(cacheDir, "logs").apply { mkdirs() }
        every { context.cacheDir } returns cacheDir
        mockkObject(FileLoggingTree.Companion)
        every { FileLoggingTree.flushIfPlanted() } returns Unit
        every { FileLoggingTree.currentFileName() } returns "app-2026-06-15.log"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun loadingFiles_emitsListSortedNewestFirst() {
        val now = System.currentTimeMillis()
        File(logsDir, "app-2026-06-14.log").apply {
            writeText("yesterday log content")
            setLastModified(now - 24 * 60 * 60 * 1000)
        }
        File(logsDir, "app-2026-06-15.log").apply {
            writeText("today log content")
            setLastModified(now)
        }

        val viewModel = LogViewerViewModel(context, testDispatcher)
        val files = viewModel.state.value.files
        assertEquals(2, files.size)
        assertEquals("app-2026-06-15.log", files[0].name)
        assertEquals("app-2026-06-14.log", files[1].name)
    }

    @Test
    fun selectFile_underOneMB_loadsFullContent() {
        val file = File(logsDir, "app-2026-06-15.log").apply {
            writeText("line1\nline2\nline3")
        }
        val viewModel = LogViewerViewModel(context, testDispatcher)
        viewModel.selectFile(file.name)
        
        val state = viewModel.state.value
        assertEquals(3, state.lines.size)
        assertEquals("line1", state.lines[0].text)
        assertEquals(1, state.lines[0].lineNumber)
        assertFalse(state.truncated)
    }

    @Test
    fun selectFile_overOneMB_loadsLast500KB_setsTruncatedTrue() {
        val file = File(logsDir, "app-large.log")
        val bytes = ByteArray(1500 * 1024) { 'A'.code.toByte() }
        file.writeBytes(bytes)

        val viewModel = LogViewerViewModel(context, testDispatcher)
        viewModel.selectFile(file.name)
        
        val state = viewModel.state.value
        assertTrue(state.truncated)
        assertEquals(1, state.lines.size)
        assertEquals(500 * 1024, state.lines[0].text.length)
    }

    @Test
    fun loadFull_clearsTruncated_loadsFullContent() {
        val file = File(logsDir, "app-large.log")
        val bytes = ByteArray(1500 * 1024) { 'A'.code.toByte() }
        file.writeBytes(bytes)

        val viewModel = LogViewerViewModel(context, testDispatcher)
        viewModel.selectFile(file.name)
        assertTrue(viewModel.state.value.truncated)

        viewModel.loadFull()
        
        val state = viewModel.state.value
        assertFalse(state.truncated)
        assertEquals(1500 * 1024, state.lines[0].text.length)
    }

    @Test
    fun clearAllLogs_deletesOnlyAppLogsNotOtherFiles() {
        val activeFile = File(logsDir, "app-2026-06-15.log").apply {
            writeText("active log content")
        }
        val oldFile = File(logsDir, "app-2026-06-14.log").apply {
            writeText("old log content")
        }
        val otherFile = File(logsDir, "notes.txt").apply {
            writeText("other file content")
        }

        every { FileLoggingTree.currentFileName() } returns "app-2026-06-15.log"

        val viewModel = LogViewerViewModel(context, testDispatcher)
        viewModel.clearAllLogs()

        assertTrue(activeFile.exists())
        assertEquals(0L, activeFile.length())
        assertFalse(oldFile.exists())
        assertTrue(otherFile.exists())
    }
}
