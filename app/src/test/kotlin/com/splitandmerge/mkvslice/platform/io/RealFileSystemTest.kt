package com.splitandmerge.mkvslice.platform.io

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RealFileSystemTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>()
    private val fileSystem by lazy { RealFileSystem(context) }

    @Test
    fun testCacheDirDelegatesToContext() {
        val dummyCache = File("/dummy/cache")
        every { context.cacheDir } returns dummyCache
        assertEquals(dummyCache, fileSystem.cacheDir())
    }

    @Test
    fun testFileOperationsDelegateToDisk() {
        val root = tempFolder.newFolder("fs_test")
        val file = File(root, "test.txt")

        assertFalse(fileSystem.exists(file))

        // Create new file
        assertTrue(fileSystem.createNewFile(file))
        assertTrue(fileSystem.exists(file))

        // Write content
        val text = "Hello FileSystem Seam"
        fileSystem.openOutput(file).use { output ->
            output.write(text.toByteArray())
        }

        // Read content and length
        assertEquals(text.length.toLong(), fileSystem.length(file))
        assertTrue(fileSystem.canRead(file))

        fileSystem.openInput(file).use { input ->
            val content = input.readBytes().toString(Charsets.UTF_8)
            assertEquals(text, content)
        }

        // Delete file
        assertTrue(fileSystem.delete(file))
        assertFalse(fileSystem.exists(file))
    }
}
