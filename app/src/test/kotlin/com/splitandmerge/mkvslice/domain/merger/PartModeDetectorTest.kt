package com.splitandmerge.mkvslice.domain.merger

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.domain.transport.FrameCodec
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PartModeDetectorTest {

    @get:Rule @JvmField val globalTimeout = org.junit.rules.Timeout.seconds(15)

    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val uriCache = mutableMapOf<String, Uri>()
    private val fileLengths = mutableMapOf<String, Long>()
    private val fileStreams = mutableMapOf<String, ByteArray>()

    private lateinit var detector: PartModeDetector
    private lateinit var evaluator: PreFlightEvaluator

    @Before
    fun setUp() {
        uriCache.clear()
        fileLengths.clear()
        fileStreams.clear()
        detector = PartModeDetector(context)
        evaluator = PreFlightEvaluator(context, detector)

        every { context.contentResolver } returns contentResolver
        mockkStatic(Uri::class, DocumentFile::class)

        every { Uri.parse(any()) } answers {
            val uriStr = arg<String>(0)
            uriCache.getOrPut(uriStr) {
                val mockUri = mockk<Uri>(relaxed = true)
                every { mockUri.toString() } returns uriStr
                mockUri
            }
        }

        every { DocumentFile.fromSingleUri(any(), any()) } answers {
            val uri = arg<Uri>(1)
            val uriStr = uri.toString()
            val mockFile = mockk<DocumentFile>(relaxed = true)
            every { mockFile.length() } answers { fileLengths[uriStr] ?: -1L }
            every { mockFile.exists() } returns true
            every { mockFile.isDirectory } returns false
            mockFile
        }

        every { contentResolver.openInputStream(any()) } answers {
            val uri = arg<Uri>(0)
            val uriStr = uri.toString()
            val bytes = fileStreams[uriStr] ?: ByteArray(0)
            ByteArrayInputStream(bytes)
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class, DocumentFile::class)
    }

    private fun registerFile(uriStr: String, content: ByteArray) {
        fileStreams[uriStr] = content
        fileLengths[uriStr] = content.size.toLong()
    }

    private fun createPartBytes(
        formatVersion: Int = 1,
        partIndex: Int,
        totalParts: Int,
        originalTotalSize: Long,
        payloadOffset: Long,
        payloadLen: Long,
        trailerLen: Long = 32L,
        flags: Long = if (partIndex == totalParts) 1L else 0L,
        corruptCrc: Boolean = false,
        actualDiskSizeDelta: Long = 0L
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val header = FrameCodec.FrameHeader(
            formatVersion = formatVersion,
            headerLen = 64,
            partIndex = partIndex,
            totalParts = totalParts,
            originalTotalSize = originalTotalSize,
            payloadOffset = payloadOffset,
            payloadLen = payloadLen,
            trailerLen = trailerLen,
            flags = flags,
            headerCrc = 0L
        )
        FrameCodec.writeHeader(out, header)

        // Payload
        val payload = ByteArray(payloadLen.toInt()) { (it % 256).toByte() }
        out.write(payload)

        // Trailer
        val trailer = if (partIndex == totalParts) {
            FrameCodec.FrameTrailer(
                partPayloadSha256 = ByteArray(32) { 9 },
                wholeFileSha256 = ByteArray(32) { 7 },
                originalName = "test.mkv"
            )
        } else {
            FrameCodec.FrameTrailer(partPayloadSha256 = ByteArray(32) { 9 })
        }
        FrameCodec.writeTrailer(out, trailer, partIndex == totalParts)

        val bytes = out.toByteArray()
        if (corruptCrc) {
            // Corrupt stored CRC field [48..56) at offset 55
            bytes[55] = (bytes[55].toInt() xor 0xFF).toByte()
        }
        if (actualDiskSizeDelta != 0L) {
            return bytes.sliceArray(0 until (bytes.size + actualDiskSizeDelta).toInt())
        }
        return bytes
    }

    @Test
    fun testDetectMode() {
        // MKVSLICE magic
        val mkvsliceContent = "MKVSLICE".toByteArray(Charsets.US_ASCII) + ByteArray(10)
        registerFile("content://file1", mkvsliceContent)
        assertEquals(PartMode.MKVSLICE, detector.detectMode("content://file1"))

        // EBML magic
        val ebmlContent = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(), 0, 0, 0, 0)
        registerFile("content://file2", ebmlContent)
        assertEquals(PartMode.EBML, detector.detectMode("content://file2"))

        // OTHER
        val otherContent = ByteArray(8) { 1 }
        registerFile("content://file3", otherContent)
        assertEquals(PartMode.OTHER, detector.detectMode("content://file3"))
    }

    @Test
    fun testRow1_ValidFullSet() {
        val part1 = createPartBytes(partIndex = 1, totalParts = 2, originalTotalSize = 30L, payloadOffset = 0L, payloadLen = 15L)
        val part2 = createPartBytes(partIndex = 2, totalParts = 2, originalTotalSize = 30L, payloadOffset = 15L, payloadLen = 15L, trailerLen = 32L + 32L + 4L + 8L)

        registerFile("content://part1", part1)
        registerFile("content://part2", part2)

        val result = evaluator.evaluate(listOf("content://part1", "content://part2"))
        assertTrue(result is PreFlightResult.Ok)
        val ok = result as PreFlightResult.Ok
        assertEquals(30L, ok.originalTotalSize)
        assertEquals(2, ok.totalParts)
        assertEquals(listOf("content://part1", "content://part2"), ok.sortedUris)
    }

    @Test
    fun testRow2_MissingInteriorPart() {
        // We have part 1 and 3, but missing part 2
        val part1 = createPartBytes(partIndex = 1, totalParts = 3, originalTotalSize = 30L, payloadOffset = 0L, payloadLen = 10L)
        val part3 = createPartBytes(partIndex = 3, totalParts = 3, originalTotalSize = 30L, payloadOffset = 20L, payloadLen = 10L, trailerLen = 32L + 32L + 4L + 8L)

        registerFile("content://part1", part1)
        registerFile("content://part3", part3)

        val result = evaluator.evaluate(listOf("content://part1", "content://part3"))
        assertTrue(result is PreFlightResult.Block)
        assertEquals("missing part(s)", (result as PreFlightResult.Block).reason)
    }

    @Test
    fun testRow3_MissingLastPart() {
        val part1 = createPartBytes(partIndex = 1, totalParts = 2, originalTotalSize = 30L, payloadOffset = 0L, payloadLen = 15L)

        registerFile("content://part1", part1)

        val result = evaluator.evaluate(listOf("content://part1"))
        assertTrue(result is PreFlightResult.Block)
        assertEquals("cannot reconstruct — last part missing", (result as PreFlightResult.Block).reason)
    }

    @Test
    fun testRow4_DuplicatePartIndex() {
        val part1 = createPartBytes(partIndex = 1, totalParts = 2, originalTotalSize = 30L, payloadOffset = 0L, payloadLen = 15L)
        val part1Dup = createPartBytes(partIndex = 1, totalParts = 2, originalTotalSize = 30L, payloadOffset = 0L, payloadLen = 15L)

        registerFile("content://part1", part1)
        registerFile("content://part1dup", part1Dup)

        val result = evaluator.evaluate(listOf("content://part1", "content://part1dup"))
        assertTrue(result is PreFlightResult.Block)
        assertEquals("duplicate part", (result as PreFlightResult.Block).reason)
    }

    @Test
    fun testRow5_OutOfOrderSelection() {
        val part1 = createPartBytes(partIndex = 1, totalParts = 2, originalTotalSize = 30L, payloadOffset = 0L, payloadLen = 15L)
        val part2 = createPartBytes(partIndex = 2, totalParts = 2, originalTotalSize = 30L, payloadOffset = 15L, payloadLen = 15L, trailerLen = 32L + 32L + 4L + 8L)

        registerFile("content://part1", part1)
        registerFile("content://part2", part2)

        // Pass out-of-order selection [part2, part1]
        val result = evaluator.evaluate(listOf("content://part2", "content://part1"))
        assertTrue(result is PreFlightResult.Ok)
        val ok = result as PreFlightResult.Ok
        assertEquals(listOf("content://part1", "content://part2"), ok.sortedUris)
    }

    @Test
    fun testRow6a_ForeignFilePresentInByteSelection() {
        val part1 = createPartBytes(partIndex = 1, totalParts = 2, originalTotalSize = 30L, payloadOffset = 0L, payloadLen = 15L)
        val otherContent = ByteArray(10) { 5 }

        registerFile("content://part1", part1)
        registerFile("content://other", otherContent)

        val result = evaluator.evaluate(listOf("content://part1", "content://other"))
        assertTrue(result is PreFlightResult.Block)
        assertEquals("unrecognized file / not a byte part", (result as PreFlightResult.Block).reason)
    }

    @Test
    fun testRow6b_PureAllEbmlSelection() {
        val ebmlContent = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(), 0, 0, 0, 0)
        registerFile("content://ebml1", ebmlContent)

        val result = evaluator.evaluate(listOf("content://ebml1"))
        assertTrue(result is PreFlightResult.Ok) // Pure EBML passes pre-flight evaluator to run FFmpeg merge
    }

    @Test
    fun testRow7_MixedByteAndNormalSelection() {
        val part1 = createPartBytes(partIndex = 1, totalParts = 2, originalTotalSize = 30L, payloadOffset = 0L, payloadLen = 15L)
        val ebmlContent = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(), 0, 0, 0, 0)

        registerFile("content://part1", part1)
        registerFile("content://ebml1", ebmlContent)

        val result = evaluator.evaluate(listOf("content://part1", "content://ebml1"))
        assertTrue(result is PreFlightResult.Block)
        assertEquals("mixed selection", (result as PreFlightResult.Block).reason)
    }

    @Test
    fun testRow8_TwoSessionsMismatchedTotalSize() {
        val part1 = createPartBytes(partIndex = 1, totalParts = 2, originalTotalSize = 30L, payloadOffset = 0L, payloadLen = 15L)
        val part2Mismatch = createPartBytes(partIndex = 2, totalParts = 2, originalTotalSize = 40L, payloadOffset = 15L, payloadLen = 15L, trailerLen = 32L + 32L + 4L + 8L)

        registerFile("content://part1", part1)
        registerFile("content://part2", part2Mismatch)

        val result = evaluator.evaluate(listOf("content://part1", "content://part2"))
        assertTrue(result is PreFlightResult.Block)
        assertEquals("different sessions", (result as PreFlightResult.Block).reason)
    }

    @Test
    fun testRow9_TruncatedPart() {
        val part1Trunc = createPartBytes(partIndex = 1, totalParts = 2, originalTotalSize = 30L, payloadOffset = 0L, payloadLen = 15L, actualDiskSizeDelta = -5L)

        registerFile("content://part1", part1Trunc)

        val result = evaluator.evaluate(listOf("content://part1"))
        assertTrue(result is PreFlightResult.Block)
        assertEquals("truncated / corrupt part", (result as PreFlightResult.Block).reason)
    }

    @Test
    fun testRow10_UnknownFormatVersion() {
        val part1BadVer = createPartBytes(formatVersion = 99, partIndex = 1, totalParts = 2, originalTotalSize = 30L, payloadOffset = 0L, payloadLen = 15L)
        val part2 = createPartBytes(partIndex = 2, totalParts = 2, originalTotalSize = 30L, payloadOffset = 15L, payloadLen = 15L, trailerLen = 32L + 32L + 4L + 8L)

        registerFile("content://part1", part1BadVer)
        registerFile("content://part2", part2)

        val result = evaluator.evaluate(listOf("content://part1", "content://part2"))
        assertTrue(result is PreFlightResult.Block)
        assertEquals("unsupported format version", (result as PreFlightResult.Block).reason)
    }

    @Test
    fun testMkvNamedMkvsliceRoutesByte() {
        val part1 = createPartBytes(partIndex = 1, totalParts = 1, originalTotalSize = 10L, payloadOffset = 0L, payloadLen = 10L, trailerLen = 76L)
        registerFile("content://mock_dir/movie.part_01_01.mkv", part1)

        val result = evaluator.evaluate(listOf("content://mock_dir/movie.part_01_01.mkv"))
        assertTrue(result is PreFlightResult.Ok)
        assertEquals(PartMode.MKVSLICE, detector.detectMode("content://mock_dir/movie.part_01_01.mkv"))
    }

    @Test
    fun testMkvNamedEbmlRoutesStructural() {
        val ebmlContent = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(), 0, 0, 0, 0)
        registerFile("content://mock_dir/movie.part_01_01.mkv", ebmlContent)

        val result = evaluator.evaluate(listOf("content://mock_dir/movie.part_01_01.mkv"))
        assertTrue(result is PreFlightResult.Ok)
        assertEquals(PartMode.EBML, detector.detectMode("content://mock_dir/movie.part_01_01.mkv"))
    }

    @Test
    fun testMixedMkvSetBlocksMixedSelection() {
        val part1 = createPartBytes(partIndex = 1, totalParts = 2, originalTotalSize = 20L, payloadOffset = 0L, payloadLen = 10L)
        val ebmlContent = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(), 0, 0, 0, 0)

        registerFile("content://mock_dir/part1.mkv", part1)
        registerFile("content://mock_dir/part2.mkv", ebmlContent)

        val result = evaluator.evaluate(listOf("content://mock_dir/part1.mkv", "content://mock_dir/part2.mkv"))
        assertTrue(result is PreFlightResult.Block)
        assertEquals("mixed selection", (result as PreFlightResult.Block).reason)
    }
}
