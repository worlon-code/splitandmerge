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
class Migration_4_5Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate_4_to_5_succeeds() {
        helper.createDatabase("test-migration-4-5.db", 4).apply {
            execSQL("""
                INSERT INTO jobs 
                (id, type, createdAt, updatedAt, status, progressPct, sourceUri, outputDirUri, outputBaseName, outputContainer) 
                VALUES 
                ('job-1', 'SPLIT', 1718300000000, 1718300000000, 'QUEUED', 0, 'content://source', 'content://output', 'test_file', '.mkv')
            """.trimIndent())
            execSQL("""
                INSERT INTO parts 
                (id, jobId, `index`, name, startSec, endSec, status) 
                VALUES 
                ('part-1', 'job-1', 1, 'test_file.part001.mkv', 0.0, 10.0, 'DONE')
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(
            "test-migration-4-5.db", 
            5, 
            true, 
            Migration_4_5
        )

        // Verify splitFormat was added to jobs table and has default value 'STRUCTURAL'
        val jobsCursor = db.query("SELECT id, splitFormat FROM jobs WHERE id = 'job-1'")
        jobsCursor.moveToFirst()
        assertEquals("job-1", jobsCursor.getString(0))
        assertEquals("STRUCTURAL", jobsCursor.getString(1))
        jobsCursor.close()

        // Verify byteOffset and byteSize were added to parts table and are null
        val partsCursor = db.query("SELECT id, byteOffset, byteSize FROM parts WHERE id = 'part-1'")
        partsCursor.moveToFirst()
        assertEquals("part-1", partsCursor.getString(0))
        assert(partsCursor.isNull(1))
        assert(partsCursor.isNull(2))
        partsCursor.close()
    }
}
