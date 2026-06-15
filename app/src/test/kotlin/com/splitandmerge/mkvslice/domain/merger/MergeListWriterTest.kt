package com.splitandmerge.mkvslice.domain.merger

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MergeListWriterTest {

    @Test
    fun `writeSafList writes unquoted saf paths`() {
        val tempFile = File.createTempFile("concat", ".txt")
        tempFile.deleteOnExit()

        val safPaths = listOf("saf:18.mkv", "saf:19.mkv", "saf:20.mkv")
        MergeListWriter.writeSafList(tempFile, safPaths)

        val content = tempFile.readText()
        val expected = """
            file saf:18.mkv
            file saf:19.mkv
            file saf:20.mkv
        """.trimIndent()

        assertEquals(expected, content)
    }
}
