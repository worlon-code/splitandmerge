package com.splitandmerge.mkvslice.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.splitandmerge.mkvslice.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_5_6Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate_5_to_6_succeeds() {
        // Create database in version 5
        helper.createDatabase("test-migration-5-6.db", 5).apply {
            execSQL("""
                INSERT INTO jobs 
                (id, type, createdAt, updatedAt, status, progressPct, sourceUri, outputDirUri, outputBaseName, outputContainer, splitFormat) 
                VALUES 
                ('job-dt-1', 'SPLIT', 1718300000000, 1718300000000, 'QUEUED', 0, 'content://source', 'content://output', 'test_file', '.mkv', 'STRUCTURAL')
            """.trimIndent())
            close()
        }

        // Migrate to version 6
        val db = helper.runMigrationsAndValidate(
            "test-migration-5-6.db", 
            6, 
            true, 
            Migration_5_6
        )

        // Insert into the new default_track_file_results table to verify it exists and foreign key constraints work
        db.execSQL("""
            INSERT INTO default_track_file_results
            (id, jobId, uri, displayName, createdAt)
            VALUES
            ('res-1', 'job-dt-1', 'content://file1', 'file1.mkv', 1718300001000)
        """.trimIndent())

        // Verify inserted data and defaults
        val cursor = db.query("SELECT id, jobId, uri, displayName, status, reason, writeStrategy, appliedSpecJson, createdAt FROM default_track_file_results WHERE id = 'res-1'")
        cursor.moveToFirst()
        assertEquals("res-1", cursor.getString(0))
        assertEquals("job-dt-1", cursor.getString(1))
        assertEquals("content://file1", cursor.getString(2))
        assertEquals("file1.mkv", cursor.getString(3))
        assertEquals("UNKNOWN", cursor.getString(4))
        assertEquals("", cursor.getString(5))
        assertEquals("SKIPPED", cursor.getString(6))
        assertEquals("", cursor.getString(7))
        assertEquals(1718300001000L, cursor.getLong(8))
        cursor.close()
    }
}
