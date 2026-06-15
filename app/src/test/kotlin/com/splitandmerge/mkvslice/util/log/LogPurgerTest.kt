package com.splitandmerge.mkvslice.util.log

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LogPurgerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `purges old log files and preserves new log files`() {
        val logsDir = tempFolder.newFolder("logs")
        
        val oldFile = File(logsDir, "app-2026-06-01.log").apply {
            createNewFile()
            setLastModified(System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L)) // 8 days ago
        }
        val newFile = File(logsDir, "app-2026-06-14.log").apply {
            createNewFile()
            setLastModified(System.currentTimeMillis() - (2 * 24 * 60 * 60 * 1000L)) // 2 days ago
        }
        val nonLogFile = File(logsDir, "foo.txt").apply {
            createNewFile()
            setLastModified(System.currentTimeMillis() - (10 * 24 * 60 * 60 * 1000L)) // 10 days ago
        }

        LogPurger.purgeOldLogs(logsDir, 7)

        assertFalse("Old log file should be purged", oldFile.exists())
        assertTrue("New log file should be preserved", newFile.exists())
        assertTrue("Non-log file should not be touched", nonLogFile.exists())
    }

    @Test
    fun `empty or missing logsDir does not throw`() {
        val missingDir = File(tempFolder.root, "non_existent_directory")
        LogPurger.purgeOldLogs(missingDir, 7)
        // Should complete without exceptions
    }
}
