package com.splitandmerge.mkvslice.domain.transport

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CorruptedHeaderException(message: String) : IOException(message)

object FrameCodec {
    const val MAGIC = "MKVSLICE"
    const val FIXED_HEADER_SIZE = 64

    data class FrameHeader(
        val formatVersion: Int,        // u16 masked to Int
        val headerLen: Int,            // u16 masked to Int
        val partIndex: Int,            // u16 masked to Int
        val totalParts: Int,           // u16 masked to Int
        val originalTotalSize: Long,   // u64 validated non-negative
        val payloadOffset: Long,       // u64 validated non-negative
        val payloadLen: Long,          // u64 validated non-negative
        val trailerLen: Long,          // u32 masked to Long
        val flags: Long,               // u32 masked to Long
        val headerCrc: Long            // u64 validated non-negative
    ) {
        val isLastPart: Boolean get() = (flags and 1L) == 1L
    }

    data class FrameTrailer(
        val partPayloadSha256: ByteArray,
        val wholeFileSha256: ByteArray? = null,
        val originalName: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as FrameTrailer
            if (!partPayloadSha256.contentEquals(other.partPayloadSha256)) return false
            if (wholeFileSha256 != null) {
                if (other.wholeFileSha256 == null) return false
                if (!wholeFileSha256.contentEquals(other.wholeFileSha256)) return false
            } else if (other.wholeFileSha256 != null) return false
            if (originalName != other.originalName) return false
            return true
        }

        override fun hashCode(): Int {
            var result = partPayloadSha256.contentHashCode()
            result = 31 * result + (wholeFileSha256?.contentHashCode() ?: 0)
            result = 31 * result + (originalName?.hashCode() ?: 0)
            return result
        }
    }

    // RESERVED RULE: RESERVED stays all-zero now but is "reserved for a
    // future split-session id; readers MUST treat unknown reserved bytes as zero."
    // This lets a session id be added later WITHOUT a FORMAT_VERSION bump.

    fun computeCrc(headerBytes: ByteArray): Long {
        val crc = java.util.zip.CRC32()
        crc.update(headerBytes, 0, 48)
        return crc.value // returns 32-bit unsigned value as Long (0..4294967295)
    }

    fun writeHeader(out: OutputStream, header: FrameHeader) {
        val buffer = ByteBuffer.allocate(FIXED_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        buffer.put(MAGIC.toByteArray(Charsets.US_ASCII))
        buffer.putShort((header.formatVersion and 0xFFFF).toShort())
        buffer.putShort((header.headerLen and 0xFFFF).toShort())
        buffer.putShort((header.partIndex and 0xFFFF).toShort())
        buffer.putShort((header.totalParts and 0xFFFF).toShort())
        buffer.putLong(header.originalTotalSize)
        buffer.putLong(header.payloadOffset)
        buffer.putLong(header.payloadLen)
        buffer.putInt((header.trailerLen and 0xFFFFFFFFL).toInt())
        buffer.putInt((header.flags and 0xFFFFFFFFL).toInt())
        
        // Zero-extend CRC into u64 field at offset 48
        val headerBytes = buffer.array()
        val computedCrc = computeCrc(headerBytes)
        buffer.putLong(48, computedCrc)
        buffer.putLong(56, 0L) // RESERVED
        
        out.write(headerBytes)
    }

    fun readHeader(inp: InputStream): FrameHeader {
        val bytes = ByteArray(FIXED_HEADER_SIZE)
        var totalRead = 0
        while (totalRead < FIXED_HEADER_SIZE) {
            val read = inp.read(bytes, totalRead, FIXED_HEADER_SIZE - totalRead)
            if (read == -1) throw IOException("Unexpected EOF reading frame header")
            totalRead += read
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val magicBytes = ByteArray(8)
        buffer.get(magicBytes)
        val magic = String(magicBytes, Charsets.US_ASCII)
        if (magic != MAGIC) throw CorruptedHeaderException("Invalid frame magic: $magic")

        val formatVersion = buffer.short.toInt() and 0xFFFF
        val headerLen = buffer.short.toInt() and 0xFFFF
        val partIndex = buffer.short.toInt() and 0xFFFF
        val totalParts = buffer.short.toInt() and 0xFFFF
        val originalTotalSize = buffer.long
        val payloadOffset = buffer.long
        val payloadLen = buffer.long
        val trailerLen = buffer.int.toLong() and 0xFFFFFFFFL
        val flags = buffer.int.toLong() and 0xFFFFFFFFL
        val headerCrc = buffer.long
        val reserved = buffer.long // RESERVED is read and ignored

        if (headerLen != FIXED_HEADER_SIZE) {
            throw CorruptedHeaderException("Invalid header length: $headerLen")
        }

        if (originalTotalSize < 0 || payloadOffset < 0 || payloadLen < 0 || headerCrc < 0) {
            throw CorruptedHeaderException("Header values cannot be negative")
        }

        // Verify CRC over bytes [0..48)
        val expectedCrc = computeCrc(bytes)
        if (headerCrc != expectedCrc) {
            throw CorruptedHeaderException("Header CRC mismatch: expected $expectedCrc, got $headerCrc")
        }

        return FrameHeader(
            formatVersion = formatVersion,
            headerLen = headerLen,
            partIndex = partIndex,
            totalParts = totalParts,
            originalTotalSize = originalTotalSize,
            payloadOffset = payloadOffset,
            payloadLen = payloadLen,
            trailerLen = trailerLen,
            flags = flags,
            headerCrc = headerCrc
        )
    }


    fun writeTrailer(out: OutputStream, trailer: FrameTrailer, isLastPart: Boolean) {
        out.write(trailer.partPayloadSha256)
        if (isLastPart) {
            val whole = requireNotNull(trailer.wholeFileSha256) { "wholeFileSha256 is required for last part" }
            val name = requireNotNull(trailer.originalName) { "originalName is required for last part" }
            out.write(whole)
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val lenBuffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            lenBuffer.putInt(nameBytes.size)
            out.write(lenBuffer.array())
            out.write(nameBytes)
        }
    }

    fun readTrailer(inp: InputStream, isLastPart: Boolean, trailerLen: Long): FrameTrailer {
        val partSha = ByteArray(32)
        var totalRead = 0
        while (totalRead < 32) {
            val read = inp.read(partSha, totalRead, 32 - totalRead)
            if (read == -1) throw IOException("Unexpected EOF reading part SHA")
            totalRead += read
        }
        if (!isLastPart) {
            return FrameTrailer(partSha)
        } else {
            val wholeSha = ByteArray(32)
            var totalWholeRead = 0
            while (totalWholeRead < 32) {
                val read = inp.read(wholeSha, totalWholeRead, 32 - totalWholeRead)
                if (read == -1) throw IOException("Unexpected EOF reading whole file SHA")
                totalWholeRead += read
            }
            val lenBytes = ByteArray(4)
            var totalLenRead = 0
            while (totalLenRead < 4) {
                val read = inp.read(lenBytes, totalLenRead, 4 - totalLenRead)
                if (read == -1) throw IOException("Unexpected EOF reading original name length")
                totalLenRead += read
            }
            val nameLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
            if (nameLen < 0 || nameLen > 1024 * 1024) {
                throw CorruptedHeaderException("Invalid original name length: $nameLen")
            }
            val nameBytes = ByteArray(nameLen.toInt())
            var totalNameRead = 0
            while (totalNameRead < nameLen.toInt()) {
                val read = inp.read(nameBytes, totalNameRead, nameLen.toInt() - totalNameRead)
                if (read == -1) throw IOException("Unexpected EOF reading original name")
                totalNameRead += read
            }
            val originalName = String(nameBytes, Charsets.UTF_8)
            return FrameTrailer(partSha, wholeSha, originalName)
        }
    }
}
