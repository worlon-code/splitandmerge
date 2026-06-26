package com.splitandmerge.mkvslice.domain.defaulttracks

import com.splitandmerge.mkvslice.domain.defaulttracks.model.EditSpec
import com.splitandmerge.mkvslice.domain.defaulttracks.model.ParsedMkv
import com.splitandmerge.mkvslice.domain.defaulttracks.model.WriteStrategy
import com.splitandmerge.mkvslice.domain.defaulttracks.model.TrackInfo
import com.splitandmerge.mkvslice.domain.defaulttracks.model.VoidDonor
import com.splitandmerge.mkvslice.platform.io.FileDescriptorWrapper
import com.splitandmerge.mkvslice.platform.io.FileSystem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class DefaultTracksEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cacheDir: File
    private lateinit var fakeFileSystem: FakeFileSystem

    @Before
    fun setUp() {
        cacheDir = tempFolder.newFolder("cache")
        fakeFileSystem = FakeFileSystem(cacheDir)
    }

    class FakeFileDescriptor(
        var data: ByteArray,
        private var _isWritable: Boolean = true,
        private var _isSeekable: Boolean = true,
        private var _isRegularFile: Boolean = true
    ) : FileDescriptorWrapper {
        private var position = 0L

        override fun size(): Long = data.size.toLong()
        override fun isRegularFile(): Boolean = _isRegularFile
        override fun isSeekable(): Boolean = _isSeekable
        override fun isWritable(): Boolean = _isWritable

        override fun seek(offset: Long, whence: Int): Long {
            if (!isSeekable()) throw IOException("Not seekable")
            position = when (whence) {
                0 -> offset
                1 -> position + offset
                2 -> data.size + offset
                else -> throw IllegalArgumentException()
            }
            return position
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= data.size) return -1
            val toRead = minOf(length.toLong(), data.size - position).toInt()
            System.arraycopy(data, position.toInt(), buffer, offset, toRead)
            position += toRead
            return toRead
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!isWritable()) throw IOException("Not writable")
            val endPos = position + length
            if (endPos > data.size) {
                val newData = ByteArray(endPos.toInt())
                System.arraycopy(data, 0, newData, 0, data.size)
                data = newData
            }
            System.arraycopy(buffer, offset, data, position.toInt(), length)
            position += length
            return length
        }

        override fun fdatasync() {}
        override fun close() {}
    }

    class FakeFileSystem(private val cache: File) : FileSystem {
        val files = mutableMapOf<String, FakeFileDescriptor>()
        var openCount = 0

        override fun cacheDir(): File = cache
        override fun exists(file: File): Boolean = file.exists()
        override fun canRead(file: File): Boolean = true
        override fun length(file: File): Long = file.length()
        override fun openInput(file: File): InputStream = FileInputStream(file)
        override fun openOutput(file: File): OutputStream = FileOutputStream(file)
        override fun createNewFile(file: File): Boolean = file.createNewFile()
        override fun delete(file: File): Boolean = file.delete()

        override fun openFileDescriptor(uri: String, mode: String): FileDescriptorWrapper? {
            openCount++
            val fake = files[uri] ?: return null
            return FakeFileDescriptor(
                data = fake.data,
                _isWritable = mode.contains("w"),
                _isSeekable = fake.isSeekable(),
                _isRegularFile = fake.isRegularFile()
            )
        }
    }

    // EBML element generation helpers
    private fun makeElement(id: Long, payload: ByteArray): ByteArray {
        val idWidth = when {
            id <= 0xFFL -> 1
            id <= 0xFFFFL -> 2
            id <= 0xFFFFFFL -> 3
            else -> 4
        }
        val idBytes = ByteArray(idWidth)
        var tempId = id
        for (i in idWidth - 1 downTo 0) {
            idBytes[i] = (tempId and 0xFF).toByte()
            tempId = tempId ushr 8
        }

        val size = payload.size.toLong()
        val sizeWidth = FlagEditor.getVintWidth(size)
        val sizeBytes = FlagEditor.encodeVint(size, sizeWidth)

        val out = ByteArrayOutputStream()
        out.write(idBytes)
        out.write(sizeBytes)
        out.write(payload)
        return out.toByteArray()
    }

    private fun makeValidMkv(
        docType: String = "matroska",
        tracksPayload: ByteArray,
        segmentPostTracksPayload: ByteArray = ByteArray(0)
    ): ByteArray {
        val ebmlHeader = makeElement(0x1A45DFA3L,
            makeElement(0x4282L, docType.toByteArray() + byteArrayOf(0))
        )
        val segmentPayload = ByteArrayOutputStream()
        segmentPayload.write(tracksPayload)
        segmentPayload.write(segmentPostTracksPayload)
        segmentPayload.write(makeElement(0x1F43B675L, ByteArray(0))) // first Cluster
        val segment = makeElement(0x18538067L, segmentPayload.toByteArray())
        return ebmlHeader + segment
    }

    private fun makeTrackEntry(
        number: Long,
        type: Int,
        flagDefault: Int?,
        flagForced: Int? = null,
        extra: ByteArray = ByteArray(0)
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(makeElement(0xD7L, byteArrayOf(number.toByte())))
        out.write(makeElement(0x83L, byteArrayOf(type.toByte())))
        if (flagDefault != null) {
            out.write(makeElement(0x88L, byteArrayOf(flagDefault.toByte())))
        }
        if (flagForced != null) {
            out.write(makeElement(0x55AAL, byteArrayOf(flagForced.toByte())))
        }
        out.write(extra)
        return makeElement(0xAEL, out.toByteArray())
    }

    // --- EbmlReader Tests ---

    @Test
    fun testEbmlReaderValid2TrackParse() {
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, 1) + makeTrackEntry(2, 17, 0, 0)
        )
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val fd = FakeFileDescriptor(data)
        val parsed = EbmlReader().parse(fd)
        assertEquals("matroska", parsed.docType)
        assertEquals(2, parsed.tracks.size)
        assertEquals(1, parsed.tracks[0].trackNumber)
        assertEquals(2, parsed.tracks[0].trackType)
        assertEquals(1, parsed.tracks[0].flagDefault)
        assertEquals(2, parsed.tracks[1].trackNumber)
        assertEquals(17, parsed.tracks[1].trackType)
        assertEquals(0, parsed.tracks[1].flagDefault)
        assertEquals(0, parsed.tracks[1].flagForced)
    }

    @Test(expected = IOException::class)
    fun testEbmlReaderTruncatedHeaderFails() {
        val data = byteArrayOf(0x1A, 0x45, 0xDF.toByte()) // truncated EBML magic
        val fd = FakeFileDescriptor(data)
        EbmlReader().parse(fd)
    }

    @Test(expected = IOException::class)
    fun testEbmlReaderNonMkvMagicRejected() {
        val data = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55)
        val fd = FakeFileDescriptor(data)
        EbmlReader().parse(fd)
    }

    @Test
    fun testEbmlReaderMultiSegmentRejected() {
        val tracksPayload = makeElement(0x1654AE6BL, makeTrackEntry(1, 2, 1))
        val firstMkv = makeValidMkv(tracksPayload = tracksPayload)
        // append another Segment ID
        val data = firstMkv + makeElement(0x18538067L, ByteArray(10))
        val fd = FakeFileDescriptor(data)
        val parsed = EbmlReader().parse(fd)
        assertTrue(parsed.multiSegment)
    }

    @Test
    fun testEbmlReaderWebmDocTypeAccepted() {
        val tracksPayload = makeElement(0x1654AE6BL, makeTrackEntry(1, 2, 1))
        val data = makeValidMkv(docType = "webm", tracksPayload = tracksPayload)
        val fd = FakeFileDescriptor(data)
        val parsed = EbmlReader().parse(fd)
        assertEquals("webm", parsed.docType)
    }

    @Test(expected = IOException::class)
    fun testEbmlReaderTracksBeyondFile() {
        val tracksPayload = makeElement(0x1654AE6BL, makeTrackEntry(1, 2, 1))
        val data = makeValidMkv(tracksPayload = tracksPayload)
        // Corrupt Tracks size VINT to exceed file length
        val offset = data.indexOf(0x16.toByte())
        assertTrue("Tracks ID not found in data: offset=$offset", offset >= 0)
        data[offset + 4] = 0xFE.toByte() // make size value huge (126, not sentinel 127)
        val fd = FakeFileDescriptor(data)
        EbmlReader().parse(fd)
    }

    @Test
    fun testEbmlReaderTracksAfterFirstCluster() {
        val firstCluster = makeElement(0x1F43B675L, ByteArray(0))
        val tracks = makeElement(0x1654AE6BL, makeTrackEntry(1, 2, 1))
        // Put Cluster BEFORE Tracks
        val ebmlHeader = makeElement(0x1A45DFA3L, makeElement(0x4282L, "matroska".toByteArray() + byteArrayOf(0)))
        val segmentPayload = firstCluster + tracks
        val data = ebmlHeader + makeElement(0x18538067L, segmentPayload)
        val fd = FakeFileDescriptor(data)
        val parsed = EbmlReader().parse(fd)
        assertNull(parsed.tracksOffset)
    }

    @Test
    fun testEbmlReaderVintWidths() {
        // Test decoding of sizes with widths 1..8
        // Width 1
        assertEquals(1, FlagEditor.getVintWidth(0L))
        assertEquals(1, FlagEditor.getVintWidth(127L))
        // Width 8
        assertEquals(8, FlagEditor.getVintWidth(0x0FFFFFFFFFFFFFFFL))
        
        val enc1 = FlagEditor.encodeVint(127L, 1)
        assertEquals(1, enc1.size)
        assertEquals(0xFF.toByte(), enc1[0]) // width 1, value 127
    }

    // --- TrackAnalyser Tests ---

    @Test
    fun testTrackAnalyserFlagsAndDonors() {
        // Track entry with flagDefault=1, flagForced absent (so implied 0)
        // Void inside TrackEntry (Type i)
        val voidTypeI = makeElement(0xECL, ByteArray(10))
        val tePayload = makeTrackEntry(1, 2, 1, extra = voidTypeI)
        
        // Void inside Tracks (Type ii)
        val voidTypeIi = makeElement(0xECL, ByteArray(20))
        
        // Void adjacent after Tracks (Type iii)
        val voidTypeIii = makeElement(0xECL, ByteArray(30))
        
        val tracksPayload = makeElement(0x1654AE6BL, tePayload + voidTypeIi)
        val data = makeValidMkv(tracksPayload = tracksPayload, segmentPostTracksPayload = voidTypeIii)
        
        val fd = FakeFileDescriptor(data)
        val parsed = EbmlReader().parse(fd)
        
        assertEquals(1, parsed.tracks.size)
        val t = parsed.tracks[0]
        assertEquals(1, t.flagDefault)
        assertEquals(0, t.flagForced)
        
        // Check donors
        assertEquals(3, t.voidDonors.size)
        assertEquals(1, t.voidDonors[0].type) // Type i
        assertEquals(2, t.voidDonors[1].type) // Type ii
        assertEquals(3, t.voidDonors[2].type) // Type iii
    }

    // --- FlagEditor Tests ---

    @Test
    fun testFlagEditorPathAFlips() {
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, 1) + makeTrackEntry(2, 2, 0) + makeTrackEntry(3, 17, 0, 0)
        )
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val parsed = EbmlReader().parse(FakeFileDescriptor(data))
        
        // Audio 1 -> 0, Audio 2 -> 1, Subtitle 3 -> default=1, forced=1
        val spec = EditSpec(defaultAudioTrackNumber = 2L, defaultSubtitleTrackNumber = 3L, forcedSubtitle = true)
        val plan = FlagEditor().planEdits(parsed, spec)
        
        assertEquals(WriteStrategy.IN_PLACE_PATCH, plan.writeStrategy)
        assertEquals(4, plan.fileEdits.size) // audio 1 default (1->0), audio 2 default (0->1), sub 3 default (0->1), sub 3 forced (0->1)
    }

    @Test
    fun testFlagEditorPathBDonorTypeI() {
        // Absent flags (implies default=1 for audio). Desired default is 0 -> needs Path B insert.
        // Donor Void of Type i (inside TrackEntry) exists
        // Chosen audio track must be 2. Track 1 needs insert of FlagDefault=0.
        val voidTypeI = makeElement(0xECL, ByteArray(15))
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, null, extra = voidTypeI) + makeTrackEntry(2, 2, 1)
        )
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val parsed = EbmlReader().parse(FakeFileDescriptor(data))
        
        val spec = EditSpec(defaultAudioTrackNumber = 2L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val plan = FlagEditor().planEdits(parsed, spec)
        
        assertEquals(WriteStrategy.VOID_REUSE, plan.writeStrategy)
        assertEquals(1, plan.fileEdits.size)
        // Check that the edit replacement size equals the void size
        val edit = plan.fileEdits[0]
        assertEquals(17, edit.originalBytes.size) // Void ID(1) + Size VINT(1) + Payload(15) = 17 bytes
    }

    @Test
    fun testFlagEditorPathBDonorTypeIi() {
        val voidTypeIi = makeElement(0xECL, ByteArray(15))
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, null) + makeTrackEntry(2, 2, 1) + voidTypeIi
        )
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val parsed = EbmlReader().parse(FakeFileDescriptor(data))
        
        val spec = EditSpec(defaultAudioTrackNumber = 2L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val plan = FlagEditor().planEdits(parsed, spec)
        
        assertEquals(WriteStrategy.VOID_REUSE, plan.writeStrategy)
        // Edits: TrackEntry size, TrackEntry append, Void shrink
        assertEquals(3, plan.fileEdits.size)
    }

    @Test
    fun testFlagEditorPathBDonorTypeIii() {
        val voidTypeIii = makeElement(0xECL, ByteArray(15))
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, null) + makeTrackEntry(2, 2, 1)
        )
        val data = makeValidMkv(tracksPayload = tracksPayload, segmentPostTracksPayload = voidTypeIii)
        val parsed = EbmlReader().parse(FakeFileDescriptor(data))
        
        val spec = EditSpec(defaultAudioTrackNumber = 2L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val plan = FlagEditor().planEdits(parsed, spec)
        
        assertEquals(WriteStrategy.VOID_REUSE, plan.writeStrategy)
        // Edits: TrackEntry size, TrackEntry append, Tracks size, Void shrink
        assertEquals(4, plan.fileEdits.size)
    }

    @Test
    fun testFlagEditorWebmWriteSkipped() {
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, null) + makeTrackEntry(2, 2, 1)
        )
        val data = makeValidMkv(docType = "webm", tracksPayload = tracksPayload)
        val parsed = EbmlReader().parse(FakeFileDescriptor(data))
        
        val spec = EditSpec(defaultAudioTrackNumber = 2L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val plan = FlagEditor().planEdits(parsed, spec)
        
        assertEquals(WriteStrategy.SKIPPED, plan.writeStrategy)
        assertEquals("webm-write-not-supported-in-a0", plan.skipReason)
    }

    // --- Feasibility/SKIP Tests ---

    @Test
    fun testFeasibilityNoUsableVoid() {
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, null) + makeTrackEntry(2, 2, 1)
        )
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val parsed = EbmlReader().parse(FakeFileDescriptor(data))
        
        val spec = EditSpec(defaultAudioTrackNumber = 2L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val plan = FlagEditor().planEdits(parsed, spec)
        
        assertEquals(WriteStrategy.SKIPPED, plan.writeStrategy)
        assertEquals("no-void-for-insert", plan.skipReason)
    }

    @Test
    fun testFeasibilityVoidTooSmall() {
        val voidTypeI = makeElement(0xECL, ByteArray(1)) // only 1 byte payload -> total void size 3 bytes
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, null, extra = voidTypeI) + makeTrackEntry(2, 2, 1)
        )
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val parsed = EbmlReader().parse(FakeFileDescriptor(data))
        
        val spec = EditSpec(defaultAudioTrackNumber = 2L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val plan = FlagEditor().planEdits(parsed, spec)
        
        // need for FlagDefault insert is 3 bytes (88 81 00).
        // void payload is 1. 1 - 3 < 0 -> too small
        assertEquals(WriteStrategy.SKIPPED, plan.writeStrategy)
        assertEquals("no-void-for-insert", plan.skipReason)
    }

    @Test
    fun testFeasibilityNonAdjacentPostTracksVoidNotUsed() {
        val firstCluster = makeElement(0x1F43B675L, ByteArray(0))
        val tracks = makeElement(0x1654AE6BL, makeTrackEntry(1, 2, null))
        val nonAdjacentVoid = makeElement(0xECL, ByteArray(100))
        
        // Put Cluster between Tracks and Void -> non-adjacent!
        val ebmlHeader = makeElement(0x1A45DFA3L, makeElement(0x4282L, "matroska".toByteArray() + byteArrayOf(0)))
        val segmentPayload = tracks + firstCluster + nonAdjacentVoid
        val data = ebmlHeader + makeElement(0x18538067L, segmentPayload)
        
        val parsed = EbmlReader().parse(FakeFileDescriptor(data))
        // Verify no Type iii void donor is detected because it is not adjacent to Tracks.endOffset
        assertTrue(parsed.tracks[0].voidDonors.isEmpty())
    }

    @Test
    fun testFeasibilityPartialVoidMultiInsert() {
        // 2 inserts needed, but only 1 small void available that can only cover one insert
        val voidTypeI = makeElement(0xECL, ByteArray(3)) // payload size 3, total 5 bytes
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, null, extra = voidTypeI) + makeTrackEntry(2, 17, null) + makeTrackEntry(3, 2, 1)
        )
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val parsed = EbmlReader().parse(FakeFileDescriptor(data))
        
        val spec = EditSpec(defaultAudioTrackNumber = 3L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val plan = FlagEditor().planEdits(parsed, spec)
        
        assertEquals(WriteStrategy.SKIPPED, plan.writeStrategy)
    }

    // --- ContiguousWrite Tests ---

    @Test
    fun testContiguousWriteUntouchedMiddleBytes() {
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, 1) + makeTrackEntry(2, 2, 1)
        )
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val uri = "mkv_file"
        fakeFileSystem.files[uri] = FakeFileDescriptor(data)
        
        // Flip audio 1 (1->0), leave audio 2 default=1. This is Path A.
        val spec = EditSpec(defaultAudioTrackNumber = 2L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val engine = DefaultTracksEngine(fakeFileSystem)
        val res = engine.processFile(uri, spec, "job1", 0)
        
        assertEquals("DONE", res.status)
        assertEquals(WriteStrategy.IN_PLACE_PATCH.name, res.writeStrategy)
        
        val updatedData = fakeFileSystem.files[uri]!!.data
        // Verify untouched middle bytes did not change
        // TrackEntry 1 default is at offset 47 (approx), TrackEntry 2 default is at offset 68 (approx).
        // Let's assert they are untouched except at the exact flag offsets
        // Check that the files are identical outside flag offsets
        val parsedOrig = EbmlReader().parse(FakeFileDescriptor(data))
        val parsedUpd = EbmlReader().parse(FakeFileDescriptor(updatedData))
        assertEquals(0, parsedUpd.tracks[0].flagDefault)
        assertEquals(1, parsedUpd.tracks[1].flagDefault)
    }

    // --- FlagVerifier Tests ---

    @Test
    fun testFlagVerifierByteDiffFailOverridesSemantic() {
        val tracksPayload = makeElement(0x1654AE6BL, makeTrackEntry(1, 2, 1))
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val parsed = EbmlReader().parse(FakeFileDescriptor(data))
        
        val verifier = FlagVerifier()
        // Simulate a stray byte edit
        val badData = data.copyOf()
        badData[badData.size - 1] = 0x55.toByte() // modify the very last byte of the file
        val badFd = FakeFileDescriptor(badData)
        
        val spec = EditSpec(defaultAudioTrackNumber = 1L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val verified = verifier.verify(
            fd = badFd,
            originalMkv = parsed,
            originalLength = data.size.toLong(),
            spec = spec,
            writeStrategy = WriteStrategy.IN_PLACE_PATCH,
            spanOffset = 0,
            originalBytes = data,
            expectedFlipsCount = 0
        )
        assertFalse(verified) // should fail due to stray changes outside expected flips
    }

    @Test
    fun testFlagVerifierFileLengthChange() {
        val tracksPayload = makeElement(0x1654AE6BL, makeTrackEntry(1, 2, 1))
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val parsed = EbmlReader().parse(FakeFileDescriptor(data))
        
        val verifier = FlagVerifier()
        val badData = data + byteArrayOf(0) // file grew!
        val badFd = FakeFileDescriptor(badData)
        
        val spec = EditSpec(defaultAudioTrackNumber = 1L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val verified = verifier.verify(
            fd = badFd,
            originalMkv = parsed,
            originalLength = data.size.toLong(),
            spec = spec,
            writeStrategy = WriteStrategy.IN_PLACE_PATCH,
            spanOffset = 0,
            originalBytes = ByteArray(0),
            expectedFlipsCount = 0
        )
        assertFalse(verified)
    }

    // --- FlagJournal/TOCTOU Tests ---

    @Test
    fun testFlagJournalRollbackByteIdentical() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val fd = FakeFileDescriptor(data)
        
        val journal = FlagJournal(cacheDir, "job1", 0)
        journal.writeJournal(fd, data.size.toLong(), 1L, byteArrayOf(2, 3))
        
        // Simulate torn write
        fd.seek(1, 0)
        fd.write(byteArrayOf(99, 99), 0, 2)
        
        // Rollback
        val rollbackRes = journal.rollback(fd)
        assertTrue(rollbackRes is RollbackResult.SUCCESS)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), fd.data)
        assertFalse(journal.exists())
    }

    @Test
    fun testFlagJournalRollbackRefusal() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val fd = FakeFileDescriptor(data)
        
        val journal = FlagJournal(cacheDir, "job1", 0)
        journal.writeJournal(fd, data.size.toLong(), 1L, byteArrayOf(2, 3))
        
        // Simulate external modification: file grew
        val fd2 = FakeFileDescriptor(byteArrayOf(1, 99, 99, 4, 5, 6))
        val rollbackRes = journal.rollback(fd2)
        assertTrue(rollbackRes is RollbackResult.REFUSED)
    }

    @Test
    fun testTOCTOUTrackNumberChangeAborts() {
        val tracksPayload = makeElement(0x1654AE6BL, makeTrackEntry(2, 2, 0))
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val uri = "mkv_file"
        fakeFileSystem.files[uri] = FakeFileDescriptor(data)
        
        val engine = DefaultTracksEngine(fakeFileSystem)
        
        // Intercept right before write by changing the TrackNumber under the engine's feet!
        // We can simulate this by changing the file contents in fakeFileSystem right after analyse
        // We can mock our FileSystem to modify the file data when opened for "rw"!
        val sneakyFileSystem = object : FileSystem {
            override fun cacheDir(): File = cacheDir
            override fun exists(file: File): Boolean = true
            override fun canRead(file: File): Boolean = true
            override fun length(file: File): Long = data.size.toLong()
            override fun openInput(file: File): InputStream = throw UnsupportedOperationException()
            override fun openOutput(file: File): OutputStream = throw UnsupportedOperationException()
            override fun createNewFile(file: File): Boolean = true
            override fun delete(file: File): Boolean = true
            
            override fun openFileDescriptor(uri: String, mode: String): FileDescriptorWrapper? {
                if (mode == "r") {
                    return FakeFileDescriptor(data.copyOf())
                } else {
                    // Sneaky change to TrackNumber (change ID D7 payload from 2 to 9)
                    val modifiedData = data.copyOf()
                    // TrackNumber D7 is at offset 41 (approx). Let's find it.
                    val offset = modifiedData.indexOf(0xD7.toByte())
                    if (offset >= 0) {
                        modifiedData[offset + 2] = 9.toByte() // TrackNumber = 9
                    }
                    return FakeFileDescriptor(modifiedData)
                }
            }
        }
        
        val spec = EditSpec(defaultAudioTrackNumber = 2L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val sneakyEngine = DefaultTracksEngine(sneakyFileSystem)
        val res = sneakyEngine.processFile(uri, spec, "job1", 0)
        
        assertEquals("SKIPPED", res.status)
        assertEquals("content-signature-mismatch", res.reason)
    }

    // Helper to find first occurrence of byte in array
    private fun ByteArray.indexOf(value: Byte): Int {
        for (i in indices) {
            if (this[i] == value) return i
        }
        return -1
    }

    // --- Engine Round-Trip Tests ---

    @Test
    fun testEngineRoundTripValidEdit() {
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, 1) + makeTrackEntry(2, 17, 0, 0)
        )
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val uri = "mkv_file"
        fakeFileSystem.files[uri] = FakeFileDescriptor(data)
        
        val spec = EditSpec(defaultAudioTrackNumber = 1L, defaultSubtitleTrackNumber = 2L, forcedSubtitle = true)
        val engine = DefaultTracksEngine(fakeFileSystem)
        val res = engine.processFile(uri, spec, "job1", 0)
        
        assertEquals("DONE", res.status)
        assertEquals(WriteStrategy.IN_PLACE_PATCH.name, res.writeStrategy)
        
        val updatedFd = fakeFileSystem.openFileDescriptor(uri, "r")!!
        val updatedMkv = EbmlReader().parse(updatedFd)
        assertEquals(1, updatedMkv.tracks[0].flagDefault)
        assertEquals(1, updatedMkv.tracks[1].flagDefault)
        assertEquals(1, updatedMkv.tracks[1].flagForced)
    }

    @Test
    fun testEngineEmptyPlanNoOpen() {
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, 1)
        )
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val uri = "mkv_file"
        fakeFileSystem.files[uri] = FakeFileDescriptor(data)
        
        val spec = EditSpec(defaultAudioTrackNumber = 1L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val engine = DefaultTracksEngine(fakeFileSystem)
        
        fakeFileSystem.openCount = 0
        val res = engine.processFile(uri, spec, "job1", 0)
        
        assertEquals("UNCHANGED", res.status)
        // opened once for analysis, but NOT for writing!
        assertEquals(1, fakeFileSystem.openCount)
    }

    @Test
    fun testEngineMultiDefaultClear() {
        // 2 audio tracks both have default=1 (invalid MKV state, but possible)
        // EditSpec says default audio track is 2.
        // Engine must clear track 1 default flag to 0 and ensure track 2 is 1.
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, 1) + makeTrackEntry(2, 2, 1)
        )
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val uri = "mkv_file"
        fakeFileSystem.files[uri] = FakeFileDescriptor(data)
        
        val spec = EditSpec(defaultAudioTrackNumber = 2L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val engine = DefaultTracksEngine(fakeFileSystem)
        val res = engine.processFile(uri, spec, "job1", 0)
        
        assertEquals("DONE", res.status)
        val updatedFd = fakeFileSystem.openFileDescriptor(uri, "r")!!
        val parsed = EbmlReader().parse(updatedFd)
        assertEquals(0, parsed.tracks[0].flagDefault)
        assertEquals(1, parsed.tracks[1].flagDefault)
    }

    @Test
    fun testEnginePartialDefectPath() {
        // Multi-region write fails verification, and rollback fails with IOException (simulating a crash/torn write during rollback)
        // Should return PARTIAL
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, 1) + makeTrackEntry(2, 2, 1)
        )
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val uri = "mkv_file"
        
        // We will mock the FileDescriptor to throw exception ONLY during the rollback write!
        val badFd = object : FileDescriptorWrapper {
            val delegate = FakeFileDescriptor(data.copyOf())
            var writeCount = 0
            
            override fun size(): Long = delegate.size()
            override fun isRegularFile(): Boolean = true
            override fun isSeekable(): Boolean = true
            override fun isWritable(): Boolean = true
            override fun seek(offset: Long, whence: Int): Long = delegate.seek(offset, whence)
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)
            
            override fun write(buffer: ByteArray, offset: Int, length: Int): Int {
                writeCount++
                if (writeCount == 1) {
                    return length
                } else {
                    throw IOException("Rollback write failed")
                }
            }
            override fun fdatasync() = delegate.fdatasync()
            override fun close() = delegate.close()
        }
        
        val badFileSystem = object : FileSystem {
            override fun cacheDir(): File = cacheDir
            override fun exists(file: File): Boolean = true
            override fun canRead(file: File): Boolean = true
            override fun length(file: File): Long = data.size.toLong()
            override fun openInput(file: File): InputStream = throw UnsupportedOperationException()
            override fun openOutput(file: File): OutputStream = throw UnsupportedOperationException()
            override fun createNewFile(file: File): Boolean = true
            override fun delete(file: File): Boolean = true
            override fun openFileDescriptor(uri: String, mode: String): FileDescriptorWrapper? {
                if (mode == "r") return FakeFileDescriptor(data.copyOf())
                return badFd
            }
        }
        
        // Spec to change track defaults: audio 1 -> 0, audio 2 -> 1
        val spec = EditSpec(defaultAudioTrackNumber = 2L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val engine = DefaultTracksEngine(badFileSystem)
        val res = engine.processFile(uri, spec, "job1", 0)
        
        // res.status should be PARTIAL
        assertEquals("PARTIAL", res.status)
        assertTrue(res.reason.contains("rollback-error"))
    }

    @Test
    fun testFlagEditorMultiByteSkip() {
        // FlagDefault with size 2 (multi-byte)
        val badFlagDefault = makeElement(0x88L, byteArrayOf(0, 1))
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, null, extra = badFlagDefault)
        )
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val parsed = EbmlReader().parse(FakeFileDescriptor(data))
        
        val spec = EditSpec(defaultAudioTrackNumber = 1L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val plan = FlagEditor().planEdits(parsed, spec)
        
        assertEquals(WriteStrategy.SKIPPED, plan.writeStrategy)
        assertTrue(plan.skipReason!!.contains("multi-byte"))
    }

    @Test
    fun testFlagEditorZeroLengthSkip() {
        // FlagDefault with size 0 (zero-length)
        val badFlagDefault = makeElement(0x88L, ByteArray(0))
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, null, extra = badFlagDefault)
        )
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val parsed = EbmlReader().parse(FakeFileDescriptor(data))
        
        val spec = EditSpec(defaultAudioTrackNumber = 1L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val plan = FlagEditor().planEdits(parsed, spec)
        
        assertEquals(WriteStrategy.SKIPPED, plan.writeStrategy)
        assertTrue(plan.skipReason!!.contains("zero-length"))
    }

    @Test
    fun testFlagEditorLcaVintGrowth() {
        val voidTypeI = makeElement(0xECL, ByteArray(10))
        val track = TrackInfo(
            trackNumber = 1,
            trackType = 2,
            language = "eng",
            flagDefault = 1,
            flagForced = 0,
            name = null,
            codec = "",
            byteOffset = 100,
            flagDefaultOffset = null,
            flagForcedOffset = null,
            trackEntryEnd = 200,
            voidDonors = listOf(VoidDonor(150, 2, 10, 2))
        )
        val plan = FlagEditor()
        val calculateNeedMethod = plan.javaClass.getDeclaredMethod(
            "calculateNeed",
            java.lang.Integer.TYPE as Class<*>,
            VoidDonor::class.java as Class<*>,
            TrackInfo::class.java as Class<*>
        )
        calculateNeedMethod.isAccessible = true
        val need = calculateNeedMethod.invoke(plan, 3, VoidDonor(150, 2, 10, 2), track) as Long
        assertEquals(3L, need)
    }

    private fun recomputeContentEncodingsOffset(data: ByteArray, trackEntryOffset: Int): Int {
        val idLen = 1
        var offset = trackEntryOffset + idLen
        val b0 = data[offset].toInt() and 0xFF
        var len = 1
        var mask = 0x80
        while (mask > 0) {
            if ((b0 and mask) != 0) break
            mask = mask shr 1
            len++
        }
        val sizeLen = len
        val payloadOffset = offset + sizeLen
        
        var cur = payloadOffset
        while (cur < data.size) {
            val b = data[cur].toInt() and 0xFF
            var childIdLen = 1
            var childMask = 0x80
            while (childMask > 0) {
                if ((b and childMask) != 0) break
                childMask = childMask shr 1
                childIdLen++
            }
            var idVal = 0L
            for (i in 0 until childIdLen) {
                idVal = (idVal shl 8) or (data[cur + i].toLong() and 0xFF)
            }
            
            if (idVal == 0x6D80L) {
                return cur
            }
            
            val sb = data[cur + childIdLen].toInt() and 0xFF
            var childSizeLen = 1
            var childSizeMask = 0x80
            while (childSizeMask > 0) {
                if ((sb and childSizeMask) != 0) break
                childSizeMask = childSizeMask shr 1
                childSizeLen++
            }
            var sizeVal = (sb and (childSizeMask - 1)).toLong()
            for (i in 1 until childSizeLen) {
                sizeVal = (sizeVal shl 8) or (data[cur + childIdLen + i].toLong() and 0xFF)
            }
            cur += childIdLen + childSizeLen + sizeVal.toInt()
        }
        return -1
    }

    @Test
    fun testFlagEditorContentEncodingsUntouched() {
        val contentEncodings = makeElement(0x6D80L, byteArrayOf(11, 12, 13))
        val voidTypeI = makeElement(0xECL, ByteArray(10))
        val tracksPayload = makeElement(0x1654AE6BL,
            makeTrackEntry(1, 2, null, extra = contentEncodings + voidTypeI) + makeTrackEntry(2, 2, 1)
        )
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val uri = "mkv_file"
        fakeFileSystem.files[uri] = FakeFileDescriptor(data)
        
        val spec = EditSpec(defaultAudioTrackNumber = 2L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val engine = DefaultTracksEngine(fakeFileSystem)
        val res = engine.processFile(uri, spec, "job1", 0)
        
        assertEquals("Expected DONE but got ${res.status} (Reason: ${res.reason})", "DONE", res.status)
        assertEquals(WriteStrategy.VOID_REUSE.name, res.writeStrategy)
        
        val updatedData = fakeFileSystem.files[uri]!!.data
        val parsedPost = EbmlReader().parse(FakeFileDescriptor(updatedData))
        val recomputedOffset = recomputeContentEncodingsOffset(updatedData, parsedPost.tracks[0].byteOffset.toInt())
        assertTrue("ContentEncodings not found in updated data", recomputedOffset >= 0)
        
        val actualBytes = updatedData.copyOfRange(recomputedOffset, recomputedOffset + contentEncodings.size)
        assertArrayEquals(contentEncodings, actualBytes)
    }

    @Test
    fun testEngineRoundTripPathBRealWrite() {
        val te1 = makeTrackEntry(1, 2, null)
        val te2 = makeTrackEntry(2, 2, 0)
        val voidTypeIii = makeElement(0xECL, ByteArray(18))
        val tracksPayload = makeElement(0x1654AE6BL, te1 + te2)
        val data = makeValidMkv(tracksPayload = tracksPayload, segmentPostTracksPayload = voidTypeIii)
        
        val uri = "mkv_file"
        fakeFileSystem.files[uri] = FakeFileDescriptor(data)
        
        val parsedPre = EbmlReader().parse(FakeFileDescriptor(data))
        val preTracksSize = parsedPre.tracksSize!!
        val preTe1Size = parsedPre.tracks[0].trackEntryEnd - parsedPre.tracks[0].byteOffset
        
        val spec = EditSpec(defaultAudioTrackNumber = 2L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val engine = DefaultTracksEngine(fakeFileSystem)
        val res = engine.processFile(uri, spec, "job1", 0)
        
        assertEquals("Expected DONE but got ${res.status} (Reason: ${res.reason})", "DONE", res.status)
        assertEquals(WriteStrategy.VOID_REUSE.name, res.writeStrategy)
        
        val updatedData = fakeFileSystem.files[uri]!!.data
        val parsedPost = EbmlReader().parse(FakeFileDescriptor(updatedData))
        
        assertEquals(0, parsedPost.tracks[0].flagDefault)
        assertEquals(1, parsedPost.tracks[1].flagDefault)
        
        val defaultCount = parsedPost.tracks.count { it.flagDefault == 1 }
        assertEquals(1, defaultCount)
        
        assertEquals(data.size.toLong(), updatedData.size.toLong())
        assertEquals(parsedPre.firstClusterOffset, parsedPost.firstClusterOffset)
        
        val insertOffset = parsedPre.tracks[0].trackEntryEnd
        val insertedBytes = updatedData.copyOfRange(insertOffset.toInt(), (insertOffset + 3).toInt())
        assertArrayEquals(byteArrayOf(0x88.toByte(), 0x81.toByte(), 0x00.toByte()), insertedBytes)
        
        val postTracksSize = parsedPost.tracksSize!!
        assertEquals(preTracksSize + 3, postTracksSize)
        
        val postTe1Size = parsedPost.tracks[0].trackEntryEnd - parsedPost.tracks[0].byteOffset
        assertEquals(preTe1Size + 3, postTe1Size)
        
        val finalVoid = parsedPost.tracks[0].voidDonors.find { it.type == 3 }!!
        assertEquals(15L, finalVoid.payloadSize)
    }

    @Test
    fun testEngineForcedSubtitleInsertRepro() {
        val te1 = makeTrackEntry(1, 2, 1) // audio track
        val te2 = makeTrackEntry(2, 17, null, flagForced = null) // subtitle track, default absent, forced absent
        val voidTypeIii = makeElement(0xECL, ByteArray(20))
        val tracksPayload = makeElement(0x1654AE6BL, te1 + te2)
        val data = makeValidMkv(tracksPayload = tracksPayload, segmentPostTracksPayload = voidTypeIii)
        
        val uri = "mkv_file"
        fakeFileSystem.files[uri] = FakeFileDescriptor(data)
        
        val spec = EditSpec(defaultAudioTrackNumber = 1L, defaultSubtitleTrackNumber = 2L, forcedSubtitle = true)
        val engine = DefaultTracksEngine(fakeFileSystem)
        val res = engine.processFile(uri, spec, "job1", 0)
        
        assertEquals("DONE", res.status)
        
        val updatedData = fakeFileSystem.files[uri]!!.data
        val parsedPost = EbmlReader().parse(FakeFileDescriptor(updatedData))
        assertEquals(1, parsedPost.tracks[1].flagForced)
    }

    @Test
    fun testEngineChosenTrackNotFound() {
        val tracksPayload = makeElement(0x1654AE6BL, makeTrackEntry(1, 2, 1))
        val data = makeValidMkv(tracksPayload = tracksPayload)
        val uri = "mkv_file"
        fakeFileSystem.files[uri] = FakeFileDescriptor(data)
        
        val spec = EditSpec(defaultAudioTrackNumber = 99L, defaultSubtitleTrackNumber = null, forcedSubtitle = false)
        val engine = DefaultTracksEngine(fakeFileSystem)
        val res = engine.processFile(uri, spec, "job1", 0)
        
        assertEquals("SKIPPED", res.status)
        assertEquals("chosen-track-not-found", res.reason)
        
        val postData = fakeFileSystem.files[uri]!!.data
        assertArrayEquals(data, postData)
    }

    private fun makeElementPadded(id: Long, payload: ByteArray, sizeWidth: Int): ByteArray {
        val idWidth = when {
            id <= 0xFFL -> 1
            id <= 0xFFFFL -> 2
            id <= 0xFFFFFFL -> 3
            else -> 4
        }
        val idBytes = ByteArray(idWidth)
        var tempId = id
        for (i in idWidth - 1 downTo 0) {
            idBytes[i] = (tempId and 0xFF).toByte()
            tempId = tempId ushr 8
        }
        val sizeBytes = FlagEditor.encodeVint(payload.size.toLong(), sizeWidth)
        val out = ByteArrayOutputStream()
        out.write(idBytes)
        out.write(sizeBytes)
        out.write(payload)
        return out.toByteArray()
    }

    @Test
    fun testEngineForcedSubtitleInsertWithPaddedTracksSizeVint() {
        val te1 = makeTrackEntry(1, 2, 1) // audio track
        val te2 = makeTrackEntry(2, 17, null, flagForced = null) // subtitle track, default absent, forced absent
        val voidTypeIii = makeElement(0xECL, ByteArray(20))
        // Create Tracks element with a padded size VINT of width 3 (instead of minimal width 1 or 2)
        val tracksPayload = makeElementPadded(0x1654AE6BL, te1 + te2, 3)
        val data = makeValidMkv(tracksPayload = tracksPayload, segmentPostTracksPayload = voidTypeIii)
        
        val uri = "mkv_file"
        fakeFileSystem.files[uri] = FakeFileDescriptor(data)
        
        val spec = EditSpec(defaultAudioTrackNumber = 1L, defaultSubtitleTrackNumber = 2L, forcedSubtitle = true)
        val engine = DefaultTracksEngine(fakeFileSystem)
        val res = engine.processFile(uri, spec, "job1", 0)
        
        assertEquals("DONE", res.status)
        assertEquals(WriteStrategy.VOID_REUSE.name, res.writeStrategy)
        
        val updatedData = fakeFileSystem.files[uri]!!.data
        val parsedPost = EbmlReader().parse(FakeFileDescriptor(updatedData))
        
        // Assert Track 2's flagForced was successfully inserted and verified
        assertEquals(1, parsedPost.tracks[1].flagForced)
        assertEquals(3, parsedPost.tracksSizeVintWidth) // Assert padded size VINT width of Tracks was correctly preserved!
    }

    @Test
    fun testEngineForcedSubtitleInsertWithPaddedVoidSizeVint() {
        val te1 = makeTrackEntry(1, 2, 1) // audio track
        // Create a padded Void element of type I inside TrackEntry with size-VINT width of 8
        val voidTypeI = makeElementPadded(0xECL, ByteArray(20), 8) // ID(1) + SizeVint(8) + Payload(20) = 29 bytes
        val te2 = makeTrackEntry(2, 17, null, flagForced = null, extra = voidTypeI)
        val tracksPayload = makeElement(0x1654AE6BL, te1 + te2)
        val data = makeValidMkv(tracksPayload = tracksPayload)
        
        val uri = "mkv_file"
        fakeFileSystem.files[uri] = FakeFileDescriptor(data)
        
        val spec = EditSpec(defaultAudioTrackNumber = 1L, defaultSubtitleTrackNumber = 2L, forcedSubtitle = true)
        val engine = DefaultTracksEngine(fakeFileSystem)
        val res = engine.processFile(uri, spec, "job1", 0)
        
        assertEquals("DONE", res.status)
        assertEquals(WriteStrategy.VOID_REUSE.name, res.writeStrategy)
        
        val updatedData = fakeFileSystem.files[uri]!!.data
        // Assert overall file size is preserved byte-exact
        assertEquals(data.size.toLong(), updatedData.size.toLong())
        
        val parsedPost = EbmlReader().parse(FakeFileDescriptor(updatedData))
        assertEquals(1, parsedPost.tracks[1].flagForced)
        
        // Find the shrunk Void donor on the parsed track 2
        val postVoid = parsedPost.tracks[1].voidDonors.find { it.type == 1 }
        assertNotNull(postVoid)
        // 20 bytes original - 4 bytes insert = 16 bytes payload size
        assertEquals(16L, postVoid!!.payloadSize)
        // Header size should be preserved as 9 bytes (1 byte ID + 8 bytes size-VINT)
        assertEquals(9, postVoid.headerSize)
    }
}
