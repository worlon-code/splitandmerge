package com.splitandmerge.mkvslice.platform.io

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class RealFileDescriptorWrapperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var fileSystem: RealFileSystem

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        fileSystem = RealFileSystem(context)
    }

    @Test
    fun testSafRwArbitraryOffsetWrite() {
        // Create a temporary file containing 32 bytes
        val file = File(tempFolder.newFolder("saf_rw"), "test_saf.bin")
        val originalBytes = ByteArray(32) { it.toByte() }
        FileOutputStream(file).use { it.write(originalBytes) }

        val uriString = Uri.fromFile(file).toString()

        // 1. Open in rw mode and modify a byte in the middle
        val fd = fileSystem.openFileDescriptor(uriString, "rw")
        assertNotNull(fd)
        fd!!.use { wrapper ->
            assertTrue(wrapper.isRegularFile())
            assertTrue(wrapper.isSeekable())
            assertTrue(wrapper.isWritable())
            assertEquals(32L, wrapper.size())

            // Seek to offset 16 and write a modified byte
            val newOffset = wrapper.seek(16L, 0)
            assertEquals(16L, newOffset)

            val written = wrapper.write(byteArrayOf(99), 0, 1)
            assertEquals(1, written)
            wrapper.fdatasync()
        }

        // 2. Open in r mode and verify the modification
        val fdRead = fileSystem.openFileDescriptor(uriString, "r")
        assertNotNull(fdRead)
        fdRead!!.use { wrapper ->
            assertEquals(32L, wrapper.size())
            wrapper.seek(16L, 0)
            val buf = ByteArray(1)
            val read = wrapper.read(buf, 0, 1)
            assertEquals(1, read)
            assertEquals(99.toByte(), buf[0])
        }

        // Verify other bytes are untouched
        val finalBytes = file.readBytes()
        assertEquals(32, finalBytes.size)
        for (i in finalBytes.indices) {
            if (i == 16) {
                assertEquals(99.toByte(), finalBytes[i])
            } else {
                assertEquals(i.toByte(), finalBytes[i])
            }
        }
    }
}
