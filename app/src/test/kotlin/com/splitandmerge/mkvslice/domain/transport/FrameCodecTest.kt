package com.splitandmerge.mkvslice.domain.transport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class FrameCodecTest {

    @Test
    fun testHeaderAndTrailerRoundTrip() {
        val originalName = "test_movie.part_01_03.mkv"
        val payloadSha = ByteArray(32) { it.toByte() }
        val wholeSha = ByteArray(32) { (it + 1).toByte() }

        val header = FrameCodec.FrameHeader(
            formatVersion = 1,
            headerLen = 64,
            partIndex = 1,
            totalParts = 3,
            originalTotalSize = 123456789L,
            payloadOffset = 0L,
            payloadLen = 50000000L,
            trailerLen = (32 + 32 + 4 + originalName.toByteArray(Charsets.UTF_8).size).toLong(),
            flags = 1L, // IS_LAST_PART
            headerCrc = 0L
        )

        val trailer = FrameCodec.FrameTrailer(
            partPayloadSha256 = payloadSha,
            wholeFileSha256 = wholeSha,
            originalName = originalName
        )

        val outStream = ByteArrayOutputStream()
        FrameCodec.writeHeader(outStream, header)
        FrameCodec.writeTrailer(outStream, trailer, true)

        val inpStream = ByteArrayInputStream(outStream.toByteArray())
        val decodedHeader = FrameCodec.readHeader(inpStream)
        val decodedTrailer = FrameCodec.readTrailer(inpStream, true, header.trailerLen)

        assertEquals(header.formatVersion, decodedHeader.formatVersion)
        assertEquals(header.headerLen, decodedHeader.headerLen)
        assertEquals(header.partIndex, decodedHeader.partIndex)
        assertEquals(header.totalParts, decodedHeader.totalParts)
        assertEquals(header.originalTotalSize, decodedHeader.originalTotalSize)
        assertEquals(header.payloadOffset, decodedHeader.payloadOffset)
        assertEquals(header.payloadLen, decodedHeader.payloadLen)
        assertEquals(header.trailerLen, decodedHeader.trailerLen)
        assertEquals(header.flags, decodedHeader.flags)

        assertArrayEquals(trailer.partPayloadSha256, decodedTrailer.partPayloadSha256)
        assertArrayEquals(trailer.wholeFileSha256, decodedTrailer.wholeFileSha256)
        assertEquals(trailer.originalName, decodedTrailer.originalName)
    }

    @Test
    fun testCrcMismatchThrowsException() {
        val header = FrameCodec.FrameHeader(
            formatVersion = 1,
            headerLen = 64,
            partIndex = 1,
            totalParts = 2,
            originalTotalSize = 1000L,
            payloadOffset = 0L,
            payloadLen = 500L,
            trailerLen = 32L,
            flags = 0L,
            headerCrc = 0L
        )

        val outStream = ByteArrayOutputStream()
        FrameCodec.writeHeader(outStream, header)
        val bytes = outStream.toByteArray()

        // Corrupt one header byte strictly inside the CRC-covered post-magic region [8..48)
        bytes[23] = (bytes[23].toInt() xor 0xFF).toByte()

        val inpStream = ByteArrayInputStream(bytes)
        try {
            FrameCodec.readHeader(inpStream)
            fail("Expected CorruptedHeaderException due to CRC mismatch")
        } catch (e: CorruptedHeaderException) {
            assertTrue(e.message?.contains("CRC mismatch") == true)
        }
    }

    @Test
    fun testTamperedCrcThrowsException() {
        val header = FrameCodec.FrameHeader(
            formatVersion = 1,
            headerLen = 64,
            partIndex = 1,
            totalParts = 2,
            originalTotalSize = 1000L,
            payloadOffset = 0L,
            payloadLen = 500L,
            trailerLen = 32L,
            flags = 0L,
            headerCrc = 0L
        )

        val outStream = ByteArrayOutputStream()
        FrameCodec.writeHeader(outStream, header)
        val bytes = outStream.toByteArray()

        // Stored CRC field is at [48..56). Corrupt one byte of it at offset 55.
        bytes[55] = (bytes[55].toInt() xor 0xFF).toByte()

        val inpStream = ByteArrayInputStream(bytes)
        try {
            FrameCodec.readHeader(inpStream)
            fail("Expected CorruptedHeaderException due to CRC mismatch")
        } catch (e: CorruptedHeaderException) {
            assertTrue(e.message?.contains("CRC mismatch") == true)
        }
    }

    @Test
    fun testLargeU16RoundTrip() {
        // Test values > 32767 for u16 fields
        val header = FrameCodec.FrameHeader(
            formatVersion = 40000,
            headerLen = 64,
            partIndex = 40000,
            totalParts = 50000,
            originalTotalSize = 1000L,
            payloadOffset = 0L,
            payloadLen = 500L,
            trailerLen = 32L,
            flags = 0L,
            headerCrc = 0L
        )

        val outStream = ByteArrayOutputStream()
        FrameCodec.writeHeader(outStream, header)

        val inpStream = ByteArrayInputStream(outStream.toByteArray())
        val decoded = FrameCodec.readHeader(inpStream)

        assertEquals(40000, decoded.formatVersion)
        assertEquals(40000, decoded.partIndex)
        assertEquals(50000, decoded.totalParts)
    }

    @Test
    fun testLargeU32TrailerLenRoundTrip() {
        // Test u32 fields with values > 2^31 - 1
        val largeTrailerLen = 3000000000L // > Integer.MAX_VALUE
        val header = FrameCodec.FrameHeader(
            formatVersion = 1,
            headerLen = 64,
            partIndex = 1,
            totalParts = 2,
            originalTotalSize = 1000L,
            payloadOffset = 0L,
            payloadLen = 500L,
            trailerLen = largeTrailerLen,
            flags = 4000000000L, // u32 large flags
            headerCrc = 0L
        )

        val outStream = ByteArrayOutputStream()
        FrameCodec.writeHeader(outStream, header)

        val inpStream = ByteArrayInputStream(outStream.toByteArray())
        val decoded = FrameCodec.readHeader(inpStream)

        assertEquals(largeTrailerLen, decoded.trailerLen)
        assertEquals(4000000000L, decoded.flags)
    }
}
