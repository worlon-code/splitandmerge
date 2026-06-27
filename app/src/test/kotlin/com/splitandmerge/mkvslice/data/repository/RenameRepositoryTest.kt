package com.splitandmerge.mkvslice.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for RenameRepository on the JVM.
 *
 * All Android SDK concrete classes (Uri, MatrixCursor, ContentResolver) must be
 * replaced with mockk stubs — only compile-time string constants (OpenableColumns,
 * DocumentsContract.Document.COLUMN_FLAGS) can be used directly.
 */
class RenameRepositoryTest {

    @Test
    fun testScanPickedFiles() {
        val context = mockk<Context>()
        val contentResolver = mockk<ContentResolver>()
        every { context.contentResolver } returns contentResolver

        // Uri.parse() is an Android method — not available in JVM unit tests.
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://mock/video"

        // MatrixCursor is an Android class — mock Cursor directly instead.
        // VideoScanner.scanPickedFiles calls cursor.use { ... } which calls close().
        val cursor = mockk<Cursor>()
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor.getColumnIndex(OpenableColumns.SIZE) } returns 1
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns "movie.mkv"
        every { cursor.getLong(1) } returns 1024L
        every { cursor.close() } just Runs

        every { contentResolver.query(uri, null, null, null, null) } returns cursor
        // Second query for COLUMN_FLAGS returns null → flags default to 0
        every {
            contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                null,
                null,
                null
            )
        } returns null

        val repository = RenameRepository(context)
        val results = repository.scanPickedFiles(listOf(uri))

        assertEquals(1, results.size)
        val row = results.first()
        assertEquals("movie.mkv", row.displayName)
        assertEquals(1024L, row.sizeBytes)
        assertEquals("movie", row.originalBaseName)
        assertEquals(".mkv", row.extension)
        assertEquals("movie", row.newBaseName)
        // scanPickedFiles sets parentKnown=true so planner can assign RENAME.
        // Disk collision is deferred to STEP-9 apply (try-then-retry-(N)).
        assertEquals(true, row.parentKnown)
        assertEquals("picked-session", row.parentKey)
        assertEquals(true, row.isPickedFile)
    }

    @Test
    fun testDisplayNameFromDocId() {
        assertEquals("New Name.mkv", displayNameFromDocId("primary:Download/1DM/General/New Name.mkv"))
        assertEquals("simple.mkv", displayNameFromDocId("simple.mkv"))
        assertEquals("No Extension", displayNameFromDocId("primary:Folder/No Extension"))
    }
}
