package com.splitandmerge.mkvslice.util.log

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class FileLoggingTreeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.v(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `writes logs and formats correctly`() {
        val logsDir = tempFolder.newFolder("logs")
        val tree = FileLoggingTree(logsDir)
        
        tree.logForTest(4, "TEST_TAG", "Hello from test log", null)
        tree.close()

        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val logFile = File(logsDir, "app-$dateStr.log")
        
        assertTrue("Log file must exist", logFile.exists())
        val content = logFile.readText()
        assertTrue("Content was: '$content'", content.contains("Hello from test log"))
        assertTrue("Content must contain tag", content.contains("TEST_TAG"))
        assertTrue("Content must contain priority level", content.contains("  I  "))
    }

    @Test
    fun `size rollover creates sequel files`() {
        val logsDir = tempFolder.newFolder("logs")
        val tree = FileLoggingTree(logsDir)
        
        val chunkSize = 2 * 1024 * 1024 // 2 MB
        val chunkString = "a".repeat(chunkSize)
        
        for (i in 0..5) {
            tree.logForTest(3, "SIZE_TEST", chunkString, null)
        }
        tree.close()

        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val baseFile = File(logsDir, "app-$dateStr.log")
        val sequelFile = File(logsDir, "app-$dateStr-001.log")

        assertTrue("Base log file must exist", baseFile.exists())
        assertTrue("Sequel log file must exist after exceeding 10MB", sequelFile.exists())
    }

    @Test
    fun `thread safety test under concurrent logging`() = runBlocking {
        val logsDir = tempFolder.newFolder("logs")
        val tree = FileLoggingTree(logsDir)
        
        val coroutinesCount = 10
        val logsPerCoroutine = 100
        
        val jobs = List(coroutinesCount) { cIndex ->
            launch(Dispatchers.Default) {
                for (i in 0 until logsPerCoroutine) {
                    tree.logForTest(4, "CONCURRENT", "Msg from thread $cIndex index $i", null)
                }
            }
        }
        jobs.forEach { it.join() }
        tree.close()

        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val logFile = File(logsDir, "app-$dateStr.log")
        
        val lines = logFile.readLines()
        assertEquals(coroutinesCount * logsPerCoroutine, lines.size)
    }

    @Test
    fun `fails-closed on write error and does not throw`() {
        val invalidLogsDir = tempFolder.newFile("dummy_file_acting_as_dir")
        val tree = FileLoggingTree(invalidLogsDir)
        
        tree.logForTest(6, "ERROR", "This write will fail", null)
        tree.close()
    }

    @Test
    fun `day rollover creates new file`() {
        val logsDir = tempFolder.newFolder("logs")
        var mockTime = ZonedDateTime.of(2026, 6, 14, 23, 59, 59, 0, java.time.ZoneId.systemDefault())
        val tree = FileLoggingTree(logsDir) { mockTime }
        
        tree.logForTest(4, "TEST", "Log on day 1", null)
        
        // Rollover the day by adding 2 seconds
        mockTime = mockTime.plusSeconds(2)
        
        tree.logForTest(4, "TEST", "Log on day 2", null)
        tree.close()
        
        val file1 = File(logsDir, "app-2026-06-14.log")
        val file2 = File(logsDir, "app-2026-06-15.log")
        
        assertTrue("Day 1 log file must exist", file1.exists())
        assertTrue("Day 2 log file must exist after day rollover", file2.exists())
        assertTrue("Day 1 log file contains day 1 message", file1.readText().contains("Log on day 1"))
        assertTrue("Day 2 log file contains day 2 message", file2.readText().contains("Log on day 2"))
    }
}
