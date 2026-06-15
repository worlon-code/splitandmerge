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
class Migration_3_4Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate_3_to_4_succeeds_andSeedsBuiltins() {
        helper.createDatabase("test-migration.db", 3).apply {
            execSQL("""
                INSERT INTO jobs 
                (id, type, createdAt, updatedAt, status, progressPct, sourceUri, outputDirUri, outputBaseName, outputContainer) 
                VALUES 
                ('job-1', 'SPLIT', 1718300000000, 1718300000000, 'QUEUED', 0, 'content://source', 'content://output', 'test_file', '.mkv')
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(
            "test-migration.db", 
            4, 
            true, 
            Migration_3_4
        )

        val cursor = db.query("SELECT COUNT(*) FROM cleanup_patterns")
        cursor.moveToFirst()
        assertEquals(12, cursor.getInt(0))
        cursor.close()

        val patternCursor = db.query("SELECT id, regex, label, orderIndex FROM cleanup_patterns ORDER BY orderIndex ASC LIMIT 1")
        patternCursor.moveToFirst()
        assertEquals("url_prefix", patternCursor.getString(0))
        assertEquals("^www\\.[^\\s\\-]+\\s*[-\\u2013]\\s*", patternCursor.getString(1))
        assertEquals("Strip leading URL prefix", patternCursor.getString(2))
        assertEquals(0, patternCursor.getInt(3))
        patternCursor.close()

        val jobsCursor = db.query("SELECT id, outputBaseName FROM jobs")
        jobsCursor.moveToFirst()
        assertEquals("job-1", jobsCursor.getString(0))
        assertEquals("test_file", jobsCursor.getString(1))
        jobsCursor.close()
    }
}
