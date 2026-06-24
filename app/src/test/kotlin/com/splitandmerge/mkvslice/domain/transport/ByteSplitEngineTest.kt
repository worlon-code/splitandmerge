package com.splitandmerge.mkvslice.domain.transport

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidation
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidator
import com.splitandmerge.mkvslice.platform.io.FileSystem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ByteSplitEngineTest {

    @get:Rule @JvmField val globalTimeout = org.junit.rules.Timeout.seconds(15)

    private val context = mockk<Context>(relaxed = true)
    private val jobDao = mockk<JobDao>(relaxed = true)
    private val outputFolderValidator = mockk<OutputFolderValidator>(relaxed = true)
    private val fileSystem = mockk<FileSystem>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)

    private lateinit var splitter: TransportSplitter

    private val uriCache = mutableMapOf<String, Uri>()

    @Before
    fun setUp() {
        uriCache.clear()
        splitter = TransportSplitter(context, jobDao, outputFolderValidator, fileSystem)
        every { context.contentResolver } returns contentResolver
        mockkStatic(Uri::class, DocumentFile::class, android.provider.DocumentsContract::class)
        every { android.provider.DocumentsContract.renameDocument(any(), any(), any()) } answers {
            val uri = arg<Uri>(1)
            val uriStr = uri.toString()
            if (uriStr.endsWith(".tmp")) {
                Uri.parse(uriStr.removeSuffix(".tmp"))
            } else {
                uri
            }
        }
        every { Uri.parse(any()) } answers {
            val uriStr = arg<String>(0)
            uriCache.getOrPut(uriStr) {
                val mockUri = mockk<Uri>(relaxed = true)
                every { mockUri.scheme } returns if (uriStr.startsWith("content://")) "content" else "file"
                every { mockUri.path } returns "/mock_path"
                every { mockUri.lastPathSegment } returns uriStr.substringAfterLast("/")
                every { mockUri.toString() } returns uriStr
                mockUri
            }
        }
        every { DocumentFile.fromSingleUri(any(), any()) } answers {
            val uri = arg<Uri>(1)
            val uriStr = uri.toString()
            val name = uriStr.substringAfterLast("/")
            mockk<DocumentFile>(relaxed = true) {
                every { this@mockk.name } returns name
                every { this@mockk.uri } returns uri
                every { exists() } returns true
            }
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class, DocumentFile::class, android.provider.DocumentsContract::class)
    }

    private fun setupMockSAF(
        sourceBytes: ByteArray,
        capBytes: Long,
        validationResult: OutputFolderValidation = OutputFolderValidation.Ok
    ): Pair<MutableList<DocumentFile>, MutableMap<Uri, ByteArrayOutputStream>> {
        val jobId = "test_job"
        val job = JobEntity(
            id = jobId,
            type = JobType.SPLIT,
            status = JobStatus.QUEUED,
            sourceUri = "content://media/source.mkv",
            outputDirUri = "content://media/out",
            outputBaseName = "fixture",
            outputContainer = ".mkv",
            targetCapBytes = capBytes,
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L,
            splitFormat = "BYTE"
        )

        coEvery { jobDao.getById(jobId) } returns job

        // Mock stat size
        val mockPfd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { statSize } returns sourceBytes.size.toLong()
        }
        every { contentResolver.openFileDescriptor(any(), "r") } returns mockPfd

        // Mock input stream
        every { contentResolver.openInputStream(any()) } answers {
            ByteArrayInputStream(sourceBytes)
        }

        // Mock space validator
        every { outputFolderValidator.validate(any(), any(), any()) } returns validationResult

        // Mock tree DocumentFile
        val mockTree = mockk<DocumentFile>(relaxed = true)
        val mockSubfolder = mockk<DocumentFile>(relaxed = true)
        every { DocumentFile.fromTreeUri(any(), any()) } returns mockTree
        every { mockTree.createDirectory(any()) } returns mockSubfolder

        val createdFiles = mutableListOf<DocumentFile>()
        val streams = mutableMapOf<Uri, ByteArrayOutputStream>()

        every { mockSubfolder.createFile(any(), any()) } answers {
            val name = arg<String>(1)
            val mockFile = mockk<DocumentFile>(relaxed = true) {
                every { this@mockk.name } returns name
                every { uri } returns Uri.parse("content://mock_part/$name")
                every { exists() } returns true
            }
            createdFiles.add(mockFile)
            mockFile
        }

        every { contentResolver.openOutputStream(any()) } answers {
            val uri = arg<Uri>(0)
            val out = ByteArrayOutputStream()
            streams[uri] = out
            out
        }

        return Pair(createdFiles, streams)
    }

    @Test
    fun testChunkingMathRemainder() = runTest {
        // T = 25 bytes, C = 10 bytes -> parts: 10, 10, 5
        val sourceBytes = ByteArray(25) { it.toByte() }
        val (createdFiles, streams) = setupMockSAF(sourceBytes, 10L)

        splitter.runSplit("test_job")

        // Should create 3 part files and 1 split.json manifest
        assertEquals(4, createdFiles.size)
        
        // Parts written:
        val part1 = streams[Uri.parse("content://mock_part/fixture.part_01_03.mkv.tmp")]?.toByteArray()
        val part2 = streams[Uri.parse("content://mock_part/fixture.part_02_03.mkv.tmp")]?.toByteArray()
        val part3 = streams[Uri.parse("content://mock_part/fixture.part_03_03.mkv.tmp")]?.toByteArray()

        assertTrue(part1 != null)
        assertTrue(part2 != null)
        assertTrue(part3 != null)

        // Read and verify part 1 header & payload
        val inp1 = ByteArrayInputStream(part1)
        val h1 = FrameCodec.readHeader(inp1)
        assertEquals(1, h1.partIndex)
        assertEquals(3, h1.totalParts)
        assertEquals(10L, h1.payloadLen)
        assertEquals(0L, h1.payloadOffset)
        assertEquals(32L, h1.trailerLen)
        val p1 = ByteArray(10)
        inp1.read(p1)
        assertArrayEquals(sourceBytes.sliceArray(0..9), p1)

        // Read and verify part 2 header & payload
        val inp2 = ByteArrayInputStream(part2)
        val h2 = FrameCodec.readHeader(inp2)
        assertEquals(2, h2.partIndex)
        assertEquals(10L, h2.payloadLen)
        assertEquals(10L, h2.payloadOffset)
        val p2 = ByteArray(10)
        inp2.read(p2)
        assertArrayEquals(sourceBytes.sliceArray(10..19), p2)

        // Read and verify part 3 header (last part)
        val inp3 = ByteArrayInputStream(part3)
        val h3 = FrameCodec.readHeader(inp3)
        assertEquals(3, h3.partIndex)
        assertEquals(5L, h3.payloadLen)
        assertEquals(20L, h3.payloadOffset)
        assertTrue(h3.isLastPart)
    }

    @Test
    fun testChunkingMathExactMultiple() = runTest {
        // T = 20 bytes, C = 10 bytes -> parts: 10, 10. NO zero-byte trailing part
        val sourceBytes = ByteArray(20) { it.toByte() }
        val (createdFiles, streams) = setupMockSAF(sourceBytes, 10L)

        splitter.runSplit("test_job")

        // 2 part files + 1 split.json
        assertEquals(3, createdFiles.size)

        val part1 = streams[Uri.parse("content://mock_part/fixture.part_1_2.mkv.tmp")]
            ?: streams[Uri.parse("content://mock_part/fixture.part_01_02.mkv.tmp")]
        val part2 = streams[Uri.parse("content://mock_part/fixture.part_2_2.mkv.tmp")]
            ?: streams[Uri.parse("content://mock_part/fixture.part_02_02.mkv.tmp")]

        assertTrue(part1 != null)
        assertTrue(part2 != null)

        val h2 = FrameCodec.readHeader(ByteArrayInputStream(part2!!.toByteArray()))
        assertEquals(2, h2.partIndex)
        assertEquals(2, h2.totalParts)
        assertEquals(10L, h2.payloadLen)
        assertTrue(h2.isLastPart)
    }

    @Test
    fun testSinglePart() = runTest {
        // T = 8 bytes, C = 10 bytes -> 1 part, IS_LAST_PART set
        val sourceBytes = ByteArray(8) { it.toByte() }
        val (createdFiles, streams) = setupMockSAF(sourceBytes, 10L)

        splitter.runSplit("test_job")

        assertEquals(2, createdFiles.size) // 1 part + 1 split.json

        val part1 = streams[Uri.parse("content://mock_part/fixture.part_01_01.mkv.tmp")]?.toByteArray()
        assertTrue(part1 != null)

        val inp = ByteArrayInputStream(part1)
        val h = FrameCodec.readHeader(inp)
        assertEquals(1, h.partIndex)
        assertEquals(1, h.totalParts)
        assertEquals(8L, h.payloadLen)
        assertTrue(h.isLastPart)

        val payload = ByteArray(8)
        inp.read(payload)
        assertArrayEquals(sourceBytes, payload)

        val trailer = FrameCodec.readTrailer(inp, true, h.trailerLen)
        val md = MessageDigest.getInstance("SHA-256")
        val expectedSha = md.digest(sourceBytes)
        assertArrayEquals(expectedSha, trailer.partPayloadSha256)
        assertArrayEquals(expectedSha, trailer.wholeFileSha256)
    }

    @Test
    fun testZeroByteOriginal() = runTest {
        // T = 0 bytes -> 1 part, payload 0, empty SHA
        val sourceBytes = ByteArray(0)
        val (createdFiles, streams) = setupMockSAF(sourceBytes, 10L)

        splitter.runSplit("test_job")

        val part1 = streams[Uri.parse("content://mock_part/fixture.part_01_01.mkv.tmp")]?.toByteArray()
        assertTrue(part1 != null)

        val inp = ByteArrayInputStream(part1)
        val h = FrameCodec.readHeader(inp)
        assertEquals(0L, h.payloadLen)
        assertTrue(h.isLastPart)

        val trailer = FrameCodec.readTrailer(inp, true, h.trailerLen)
        val emptySha = MessageDigest.getInstance("SHA-256").digest(ByteArray(0))
        assertArrayEquals(emptySha, trailer.partPayloadSha256)
        assertArrayEquals(emptySha, trailer.wholeFileSha256)
    }

    @Test
    fun testInsufficientSpaceBlocks() = runTest {
        val sourceBytes = ByteArray(20)
        val (createdFiles, _) = setupMockSAF(
            sourceBytes, 10L,
            OutputFolderValidation.InsufficientSpace(1000L, 500L)
        )

        splitter.runSplit("test_job")

        // Should write absolutely nothing (no files created)
        assertTrue(createdFiles.isEmpty())
    }

    @Test
    fun testCancellationCleanup() = runTest {
        // Simulate a job cancellation mid-write by mutating the database job status
        val sourceBytes = ByteArray(30)
        val jobId = "test_job"
        val job = JobEntity(
            id = jobId,
            type = JobType.SPLIT,
            status = JobStatus.QUEUED,
            sourceUri = "content://media/source.mkv",
            outputDirUri = "content://media/out",
            outputBaseName = "fixture",
            outputContainer = ".mkv",
            targetCapBytes = 10L,
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L,
            splitFormat = "BYTE"
        )

        coEvery { jobDao.getById(jobId) } returns job

        // Mock stat size
        val mockPfd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { statSize } returns sourceBytes.size.toLong()
        }
        every { contentResolver.openFileDescriptor(any(), "r") } returns mockPfd

        // Mock input stream
        every { contentResolver.openInputStream(any()) } answers {
            ByteArrayInputStream(sourceBytes)
        }

        // Mock space validator
        every { outputFolderValidator.validate(any(), any(), any()) } returns OutputFolderValidation.Ok

        // Mock tree DocumentFile
        val mockTree = mockk<DocumentFile>(relaxed = true)
        val mockSubfolder = mockk<DocumentFile>(relaxed = true)
        every { DocumentFile.fromTreeUri(any(), any()) } returns mockTree
        every { mockTree.createDirectory(any()) } returns mockSubfolder

        val createdFiles = mutableListOf<DocumentFile>()
        val deletedUris = mutableListOf<Uri>()

        every { mockSubfolder.createFile(any(), any()) } answers {
            val name = arg<String>(1)
            val mockFile = mockk<DocumentFile>(relaxed = true) {
                every { this@mockk.name } returns name
                every { uri } returns Uri.parse("content://mock_part/$name")
                every { exists() } returns true
                every { delete() } answers {
                    deletedUris.add(uri)
                    true
                }
            }
            createdFiles.add(mockFile)
            mockFile
        }

        // Setup mock subfolder findFile to return the mock parts for deletion
        val mockFiles = mutableMapOf<String, DocumentFile>()
        every { mockSubfolder.findFile(any()) } answers {
            val name = arg<String>(0)
            mockFiles.getOrPut(name) {
                mockk<DocumentFile>(relaxed = true) {
                    every { uri } returns Uri.parse("content://mock_part/$name")
                    every { delete() } answers {
                        deletedUris.add(uri)
                        true
                    }
                }
            }
        }

        every { contentResolver.openOutputStream(any()) } answers {
            // Cancel the job during writing of first part
            coEvery { jobDao.getById(jobId) } returns job.copy(status = JobStatus.CANCELLED)
            ByteArrayOutputStream()
        }

        try {
            splitter.runSplit(jobId)
        } catch (e: Exception) {
            // Handled
        }

        // Verify partial/temp files were cleaned up (deleted)
        assertTrue(deletedUris.isNotEmpty())
        val deletedUriStrings = deletedUris.map { it.toString() }
        for (i in 1..3) {
            val partName = String.format("fixture.part_%02d_%02d.mkv", i, 3)
            assertTrue("Should delete temp part $i", deletedUriStrings.contains("content://mock_part/$partName.tmp"))
            assertTrue("Should delete final part $i", deletedUriStrings.contains("content://mock_part/$partName"))
        }
    }

    @Test
    fun testManifestShaMatchesWholeFileShaAndSourceBytes() = runTest {
        // Multi-part file (T = 25 bytes, C = 10 bytes) -> parts: 10, 10, 5
        val sourceBytes = ByteArray(25) { it.toByte() }
        val (createdFiles, streams) = setupMockSAF(sourceBytes, 10L)

        splitter.runSplit("test_job")

        // 3 parts + 1 manifest
        assertEquals(4, createdFiles.size)

        // Capture last part trailer and manifest content
        val part3TmpUri = Uri.parse("content://mock_part/fixture.part_03_03.mkv.tmp")
        val part3Bytes = streams[part3TmpUri]?.toByteArray()
        assertTrue("part 3 stream exists", part3Bytes != null)

        val inp3 = ByteArrayInputStream(part3Bytes!!)
        val h3 = FrameCodec.readHeader(inp3)
        // Skip payload of part 3 (5 bytes)
        val skipped = inp3.skip(5)
        assertEquals(5, skipped)
        val trailer3 = FrameCodec.readTrailer(inp3, true, h3.trailerLen)

        // Read manifest
        val manifestUri = Uri.parse("content://mock_part/fixture.split.json")
        val manifestBytes = streams[manifestUri]?.toByteArray()
        assertTrue("manifest stream exists", manifestBytes != null)
        val manifestJson = String(manifestBytes!!, Charsets.UTF_8)

        // Parse SHA256 from JSON: "originalSha256": "HEX"
        val matchResult = Regex("\"originalSha256\"\\s*:\\s*\"([a-f0-9]+)\"").find(manifestJson)
        val manifestShaHex = matchResult?.groupValues?.get(1)
        assertTrue("manifest contains originalSha256", manifestShaHex != null)

        val md = MessageDigest.getInstance("SHA-256")
        val expectedSha = md.digest(sourceBytes)
        val expectedShaHex = expectedSha.joinToString("") { "%02x".format(it) }

        // Assert manifestShaHex == expectedShaHex == last part wholeFileSha256
        assertEquals(expectedShaHex, manifestShaHex)
        assertArrayEquals(expectedSha, trailer3.wholeFileSha256)
        
        // Assert it is NOT the empty-input hash (e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855)
        val emptyShaHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertTrue("SHA is not empty-input SHA", manifestShaHex != emptyShaHex)
    }

    @Test
    fun testUnknownSourceSizeFailure() = runTest {
        val jobId = "test_job"
        val job = JobEntity(
            id = jobId,
            type = JobType.SPLIT,
            status = JobStatus.QUEUED,
            sourceUri = "content://media/source.mkv",
            outputDirUri = "content://media/out",
            outputBaseName = "fixture",
            outputContainer = ".mkv",
            targetCapBytes = 10L,
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L,
            splitFormat = "BYTE"
        )

        coEvery { jobDao.getById(jobId) } returns job

        // Mock stat size to return -1 (unknown size)
        val mockPfd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { statSize } returns -1L
        }
        every { contentResolver.openFileDescriptor(any(), "r") } returns mockPfd

        // Mock tree DocumentFile
        val mockTree = mockk<DocumentFile>(relaxed = true)
        val mockSubfolder = mockk<DocumentFile>(relaxed = true)
        every { DocumentFile.fromTreeUri(any(), any()) } returns mockTree
        every { mockTree.createDirectory(any()) } returns mockSubfolder

        val createdFiles = mutableListOf<DocumentFile>()
        every { mockSubfolder.createFile(any(), any()) } answers {
            val name = arg<String>(1)
            val mockFile = mockk<DocumentFile>(relaxed = true) {
                every { this@mockk.name } returns name
                every { uri } returns Uri.parse("content://mock_part/$name")
                every { exists() } returns true
            }
            createdFiles.add(mockFile)
            mockFile
        }

        val upsertedJob = mutableListOf<JobEntity>()
        coEvery { jobDao.upsert(any()) } answers {
            upsertedJob.add(arg<JobEntity>(0))
        }

        splitter.runSplit(jobId)

        // Verify it failed and did not write anything
        assertTrue(createdFiles.isEmpty())
        assertTrue(upsertedJob.isNotEmpty())
        assertEquals(JobStatus.FAILED, upsertedJob.first().status)
        assertEquals("Could not determine source size", upsertedJob.first().errorMessage)
    }

    @Test
    fun testTotalPartsExceedLimitFails() = runTest {
        val jobId = "test_job"
        val job = JobEntity(
            id = jobId,
            type = JobType.SPLIT,
            status = JobStatus.QUEUED,
            sourceUri = "content://media/source.mkv",
            outputDirUri = "content://media/out",
            outputBaseName = "fixture",
            outputContainer = ".mkv",
            targetCapBytes = 1L, // 1 byte cap
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L,
            splitFormat = "BYTE"
        )

        coEvery { jobDao.getById(jobId) } returns job

        val mockPfd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { statSize } returns 100000L // 100k parts
        }
        every { contentResolver.openFileDescriptor(any(), "r") } returns mockPfd

        val upsertedJob = mutableListOf<JobEntity>()
        coEvery { jobDao.upsert(any()) } answers {
            upsertedJob.add(arg<JobEntity>(0))
        }

        splitter.runSplit(jobId)

        assertTrue(upsertedJob.isNotEmpty())
        assertEquals(JobStatus.FAILED, upsertedJob.first().status)
        assertEquals("Total parts exceed the limit of 65535", upsertedJob.first().errorMessage)
    }

    @Test
    fun testChunkingByParts() = runTest {
        // T = 25 bytes, N = 3 parts.
        // base = 25 / 3 = 8. R = 25 % 3 = 1.
        // Part 1: 9 bytes, Part 2: 8 bytes, Part 3: 8 bytes.
        val sourceBytes = ByteArray(25) { it.toByte() }
        
        val jobId = "test_job_parts"
        val job = JobEntity(
            id = jobId,
            type = JobType.SPLIT,
            status = JobStatus.QUEUED,
            sourceUri = "content://media/source.mkv",
            outputDirUri = "content://media/out",
            outputBaseName = "fixture",
            outputContainer = ".mkv",
            requestedParts = 3,
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L,
            splitFormat = "BYTE",
            mode = com.splitandmerge.mkvslice.domain.model.SplitMode.EXACT_PARTS
        )
        coEvery { jobDao.getById(jobId) } returns job

        // Setup mock SAF for N=3 parts
        val mockPfd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { statSize } returns sourceBytes.size.toLong()
        }
        every { contentResolver.openFileDescriptor(any(), "r") } returns mockPfd
        every { contentResolver.openInputStream(any()) } answers {
            ByteArrayInputStream(sourceBytes)
        }
        every { outputFolderValidator.validate(any(), any(), any()) } returns OutputFolderValidation.Ok

        val mockTree = mockk<DocumentFile>(relaxed = true)
        val mockSubfolder = mockk<DocumentFile>(relaxed = true)
        every { DocumentFile.fromTreeUri(any(), any()) } returns mockTree
        every { mockTree.createDirectory(any()) } returns mockSubfolder

        val createdFiles = mutableListOf<DocumentFile>()
        val streams = mutableMapOf<Uri, ByteArrayOutputStream>()

        every { mockSubfolder.createFile(any(), any()) } answers {
            val name = arg<String>(1)
            val mockFile = mockk<DocumentFile>(relaxed = true) {
                every { this@mockk.name } returns name
                every { uri } returns Uri.parse("content://mock_part/$name")
                every { exists() } returns true
            }
            createdFiles.add(mockFile)
            mockFile
        }
        every { contentResolver.openOutputStream(any()) } answers {
            val uri = arg<Uri>(0)
            val out = ByteArrayOutputStream()
            streams[uri] = out
            out
        }

        splitter.runSplit(jobId)

        // 3 parts + 1 manifest = 4 files
        assertEquals(4, createdFiles.size)

        val part1 = streams[Uri.parse("content://mock_part/fixture.part_01_03.mkv.tmp")]?.toByteArray()
        val part2 = streams[Uri.parse("content://mock_part/fixture.part_02_03.mkv.tmp")]?.toByteArray()
        val part3 = streams[Uri.parse("content://mock_part/fixture.part_03_03.mkv.tmp")]?.toByteArray()

        assertTrue(part1 != null)
        assertTrue(part2 != null)
        assertTrue(part3 != null)

        // Verify part 1: 9 bytes payload, offset 0
        val inp1 = ByteArrayInputStream(part1!!)
        val h1 = FrameCodec.readHeader(inp1)
        assertEquals(1, h1.partIndex)
        assertEquals(3, h1.totalParts)
        assertEquals(9L, h1.payloadLen)
        assertEquals(0L, h1.payloadOffset)

        // Verify part 2: 8 bytes payload, offset 9
        val inp2 = ByteArrayInputStream(part2!!)
        val h2 = FrameCodec.readHeader(inp2)
        assertEquals(2, h2.partIndex)
        assertEquals(3, h2.totalParts)
        assertEquals(8L, h2.payloadLen)
        assertEquals(9L, h2.payloadOffset)

        // Verify part 3: 8 bytes payload, offset 17
        val inp3 = ByteArrayInputStream(part3!!)
        val h3 = FrameCodec.readHeader(inp3)
        assertEquals(3, h3.partIndex)
        assertEquals(3, h3.totalParts)
        assertEquals(8L, h3.payloadLen)
        assertEquals(17L, h3.payloadOffset)
        assertTrue(h3.isLastPart)
    }

    @Test
    fun testChunkingByPartsGuards() = runTest {
        // N <= 0 guard
        val sourceBytes = ByteArray(25) { it.toByte() }
        val jobId = "test_job_parts_bad"
        val jobBad = JobEntity(
            id = jobId,
            type = JobType.SPLIT,
            status = JobStatus.QUEUED,
            sourceUri = "content://media/source.mkv",
            outputDirUri = "content://media/out",
            outputBaseName = "fixture",
            outputContainer = ".mkv",
            requestedParts = 0, // N = 0
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L,
            splitFormat = "BYTE",
            mode = com.splitandmerge.mkvslice.domain.model.SplitMode.EXACT_PARTS
        )
        coEvery { jobDao.getById(jobId) } returns jobBad

        val mockPfd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { statSize } returns sourceBytes.size.toLong()
        }
        every { contentResolver.openFileDescriptor(any(), "r") } returns mockPfd

        val upsertedJob = mutableListOf<JobEntity>()
        coEvery { jobDao.upsert(capture(upsertedJob)) } answers {
            true
        }

        val mockTree = mockk<DocumentFile>(relaxed = true)
        val mockSubfolder = mockk<DocumentFile>(relaxed = true)
        every { DocumentFile.fromTreeUri(any(), any()) } returns mockTree
        every { mockTree.createDirectory(any()) } returns mockSubfolder
        
        splitter.runSplit(jobId)

        assertEquals(JobStatus.FAILED, upsertedJob.last().status)
        assertEquals("number of parts must be >= 1", upsertedJob.last().errorMessage)

        // N > T guard
        val jobTooBig = jobBad.copy(id = "test_job_parts_too_big", requestedParts = 100) // N = 100 > T = 25
        coEvery { jobDao.getById("test_job_parts_too_big") } returns jobTooBig

        splitter.runSplit("test_job_parts_too_big")
        assertEquals(JobStatus.FAILED, upsertedJob.last().status)
        assertEquals("more parts than bytes", upsertedJob.last().errorMessage)
    }
}
