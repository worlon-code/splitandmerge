package com.splitandmerge.mkvslice.util.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UriRedactorTest {

    @Test
    fun `redacts different URI schemes`() {
        val input = "Paths content://com.android/file.mkv and file:///path/to/file.mkv"
        val redacted = UriRedactor.redact(input)
        
        assert(redacted.contains("content://"))
        assert(redacted.contains("file://"))
        assert(!redacted.contains("com.android"))
        assert(!redacted.contains("path/to/file.mkv"))
    }

    @Test
    fun `non-URI text passes through unchanged`() {
        val input = "Hello world, this has no URIs."
        assertEquals(input, UriRedactor.redact(input))
    }

    @Test
    fun `SHA-256 hash for the same URI is stable across calls`() {
        val uri1 = "tree://com.android.externalstorage.documents/tree/primary%3AMovies"
        val uri2 = "tree://com.android.externalstorage.documents/tree/primary%3AMovies"
        
        val redacted1 = UriRedactor.redact(uri1)
        val redacted2 = UriRedactor.redact(uri2)
        
        assertEquals(redacted1, redacted2)
    }

    @Test
    fun `different URIs produce different hashes`() {
        val uri1 = "tree://com.android.externalstorage.documents/tree/primary%3AMovies"
        val uri2 = "tree://com.android.externalstorage.documents/tree/primary%3ADownloads"
        
        assertNotEquals(UriRedactor.redact(uri1), UriRedactor.redact(uri2))
    }
}
