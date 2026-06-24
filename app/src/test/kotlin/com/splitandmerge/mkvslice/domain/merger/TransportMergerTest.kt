package com.splitandmerge.mkvslice.domain.merger

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.PartStatus
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidation
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidator
import com.splitandmerge.mkvslice.domain.transport.FrameCodec
import com.splitandmerge.mkvslice.domain.transport.TransportSplitter
import com.splitandmerge.mkvslice.platform.io.FileSystem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest

class TransportMergerTest {

    @get:Rule @JvmField val globalTimeout = org.junit.rules.Timeout.seconds(15)

    private val context = mockk<Context>(relaxed = true)
    private val jobDao = mockk<JobDao>(relaxed = true)
    private val outputFolderValidator = mockk<OutputFolderValidator>(relaxed = true)
    private val fileSystem = mockk<FileSystem>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)

    private lateinit var detector: PartModeDetector
    private lateinit var evaluator: PreFlightEvaluator
    private lateinit var transportMerger: TransportMerger
    private lateinit var splitter: TransportSplitter

    private val uriCache = mutableMapOf<String, Uri>()
    private val fileLengths = mutableMapOf<String, Long>()
    private val fileStreams = mutableMapOf<String, ByteArray>()
    private val outputStreams = mutableMapOf<Uri, ByteArrayOutputStream>()
    private val deletedFiles = mutableSetOf<String>()

    @Before
    fun setUp() {
        uriCache.clear()
        fileLengths.clear()
        fileStreams.clear()
        outputStreams.clear()
        deletedFiles.clear()

        detector = PartModeDetector(context)
        evaluator = PreFlightEvaluator(context, detector)
        transportMerger = TransportMerger(context, jobDao, outputFolderValidator, evaluator, fileSystem)
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
            val mockFile = mockk<DocumentFile>(relaxed = true)
            every { mockFile.length() } answers { fileLengths[uriStr] ?: -1L }
            every { mockFile.exists() } answers { !deletedFiles.contains(uriStr) }
            every { mockFile.isDirectory } returns false
            every { mockFile.delete() } answers {
                deletedFiles.add(uriStr)
                true
            }
            every { mockFile.name } returns uriStr.substringAfterLast("/")
            every { mockFile.uri } returns uri
            mockFile
        }

        every { DocumentFile.fromTreeUri(any(), any()) } answers {
            val mockTree = mockk<DocumentFile>(relaxed = true)
            every { mockTree.createFile(any(), any()) } answers {
                val name = arg<String>(1)
                val fileUriStr = "content://mock_out_dir/$name"
                val mockFile = mockk<DocumentFile>(relaxed = true) {
                    every { uri } returns Uri.parse(fileUriStr)
                    every { length() } answers { fileLengths[fileUriStr] ?: 0L }
                    every { delete() } answers {
                        deletedFiles.add(fileUriStr)
                        true
                    }
                }
                mockFile
            }
            every { mockTree.findFile(any()) } answers {
                val name = arg<String>(0)
                val fileUriStr = "content://mock_out_dir/$name"
                if (fileStreams.containsKey(fileUriStr) && !deletedFiles.contains(fileUriStr)) {
                    mockk<DocumentFile>(relaxed = true) {
                        every { uri } returns Uri.parse(fileUriStr)
                        every { delete() } answers {
                            deletedFiles.add(fileUriStr)
                            true
                        }
                    }
                } else null
            }
            mockTree
        }

        every { contentResolver.openInputStream(any()) } answers {
            val uri = arg<Uri>(0)
            val uriStr = uri.toString()
            val bytes = fileStreams[uriStr] ?: ByteArray(0)
            ByteArrayInputStream(bytes)
        }

        every { contentResolver.openOutputStream(any()) } answers {
            val uri = arg<Uri>(0)
            val out = ByteArrayOutputStream()
            outputStreams[uri] = out
            out
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class, DocumentFile::class, android.provider.DocumentsContract::class)
    }

    private fun registerFile(uriStr: String, content: ByteArray) {
        fileStreams[uriStr] = content
        fileLengths[uriStr] = content.size.toLong()
        deletedFiles.remove(uriStr)
    }

    private fun createPartBytes(
        partIndex: Int,
        totalParts: Int,
        originalTotalSize: Long,
        payloadOffset: Long,
        payloadBytes: ByteArray,
        trailerName: String = "test.mkv",
        wholeSha: ByteArray = ByteArray(32) { 7 },
        partSha: ByteArray? = null,
        corruptPartSha: Boolean = false
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val computedPartSha = partSha ?: MessageDigest.getInstance("SHA-256").digest(payloadBytes)
        if (corruptPartSha) {
            computedPartSha[0] = (computedPartSha[0].toInt() xor 0xFF).toByte()
        }

        val trailerLen = if (partIndex == totalParts) {
            32L + 32L + 4L + trailerName.toByteArray(Charsets.UTF_8).size
        } else {
            32L
        }

        val header = FrameCodec.FrameHeader(
            formatVersion = 1,
            headerLen = 64,
            partIndex = partIndex,
            totalParts = totalParts,
            originalTotalSize = originalTotalSize,
            payloadOffset = payloadOffset,
            payloadLen = payloadBytes.size.toLong(),
            trailerLen = trailerLen,
            flags = if (partIndex == totalParts) 1L else 0L,
            headerCrc = 0L
        )

        FrameCodec.writeHeader(out, header)
        out.write(payloadBytes)

        val trailer = if (partIndex == totalParts) {
            FrameCodec.FrameTrailer(
                partPayloadSha256 = computedPartSha,
                wholeFileSha256 = wholeSha,
                originalName = trailerName
            )
        } else {
            FrameCodec.FrameTrailer(partPayloadSha256 = computedPartSha)
        }
        FrameCodec.writeTrailer(out, trailer, partIndex == totalParts)

        return out.toByteArray()
    }

    @Test
    fun testVerificationPass_HappyPathMerge() = runTest {
        val sourceBytes = ByteArray(30) { it.toByte() }
        val wholeSha = MessageDigest.getInstance("SHA-256").digest(sourceBytes)

        val p1Bytes = sourceBytes.sliceArray(0..14)
        val p2Bytes = sourceBytes.sliceArray(15..29)

        val part1 = createPartBytes(1, 2, 30L, 0L, p1Bytes, wholeSha = wholeSha)
        val part2 = createPartBytes(2, 2, 30L, 15L, p2Bytes, wholeSha = wholeSha)

        registerFile("content://part1", part1)
        registerFile("content://part2", part2)

        val job = JobEntity(
            id = "merge_job",
            type = JobType.MERGE,
            status = JobStatus.QUEUED,
            sourceUri = "content://mock",
            outputDirUri = "content://media/out",
            outputBaseName = "merged",
            outputContainer = ".mkv",
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        coEvery { jobDao.getById("merge_job") } returns job
        coEvery { jobDao.getPartsForJob("merge_job") } returns listOf(
            PartEntity("p1", "merge_job", 1, "p1", "content://part1", 0.0, 0.0, status = PartStatus.PENDING),
            PartEntity("p2", "merge_job", 2, "p2", "content://part2", 0.0, 0.0, status = PartStatus.PENDING)
        )
        every { outputFolderValidator.validate(any(), any(), any()) } returns OutputFolderValidation.Ok

        transportMerger.runMerge("merge_job")

        // Confirm output file was created with originalName "test.mkv"
        val outStream = outputStreams[Uri.parse("content://mock_out_dir/test.mkv")]
        assertTrue(outStream != null)
        assertArrayEquals(sourceBytes, outStream?.toByteArray())

        // Verify state is marked DONE
        val listStatus = mutableListOf<JobStatus>()
        val listPct = mutableListOf<Int>()
        coVerify { jobDao.updateProgress("merge_job", capture(listStatus), capture(listPct), any(), any(), any(), any()) }
        assertEquals(JobStatus.DONE, listStatus.last())
        assertEquals(100, listPct.last())
    }

    @Test
    fun testVerificationFail_PerPartShaMismatch() = runTest {
        val sourceBytes = ByteArray(30) { it.toByte() }
        val wholeSha = MessageDigest.getInstance("SHA-256").digest(sourceBytes)

        val p1Bytes = sourceBytes.sliceArray(0..14)
        val p2Bytes = sourceBytes.sliceArray(15..29)

        // Corrupt part 1 SHA
        val part1 = createPartBytes(1, 2, 30L, 0L, p1Bytes, wholeSha = wholeSha, corruptPartSha = true)
        val part2 = createPartBytes(2, 2, 30L, 15L, p2Bytes, wholeSha = wholeSha)

        registerFile("content://part1", part1)
        registerFile("content://part2", part2)

        val job = JobEntity(
            id = "merge_job",
            type = JobType.MERGE,
            status = JobStatus.QUEUED,
            sourceUri = "content://mock",
            outputDirUri = "content://media/out",
            outputBaseName = "merged",
            outputContainer = ".mkv",
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        coEvery { jobDao.getById("merge_job") } returns job
        coEvery { jobDao.getPartsForJob("merge_job") } returns listOf(
            PartEntity("p1", "merge_job", 1, "p1", "content://part1", 0.0, 0.0, status = PartStatus.PENDING),
            PartEntity("p2", "merge_job", 2, "p2", "content://part2", 0.0, 0.0, status = PartStatus.PENDING)
        )
        every { outputFolderValidator.validate(any(), any(), any()) } returns OutputFolderValidation.Ok

        transportMerger.runMerge("merge_job")

        // Fail policy: mid-stream/pre-completion failure MUST delete output file
        assertTrue(deletedFiles.contains("content://mock_out_dir/test.mkv"))

        val slotJob = slot<JobEntity>()
        coVerify { jobDao.upsert(capture(slotJob)) }
        assertEquals(JobStatus.FAILED, slotJob.captured.status)
        assertEquals("corrupted part", slotJob.captured.errorMessage)
    }

    @Test
    fun testVerificationFail_WholeFileShaMismatch() = runTest {
        val sourceBytes = ByteArray(30) { it.toByte() }
        // Wrong expected whole-file SHA
        val wrongWholeSha = ByteArray(32) { 4 }

        val p1Bytes = sourceBytes.sliceArray(0..14)
        val p2Bytes = sourceBytes.sliceArray(15..29)

        val part1 = createPartBytes(1, 2, 30L, 0L, p1Bytes, wholeSha = wrongWholeSha)
        val part2 = createPartBytes(2, 2, 30L, 15L, p2Bytes, wholeSha = wrongWholeSha)

        registerFile("content://part1", part1)
        registerFile("content://part2", part2)

        val job = JobEntity(
            id = "merge_job",
            type = JobType.MERGE,
            status = JobStatus.QUEUED,
            sourceUri = "content://mock",
            outputDirUri = "content://media/out",
            outputBaseName = "merged",
            outputContainer = ".mkv",
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        coEvery { jobDao.getById("merge_job") } returns job
        coEvery { jobDao.getPartsForJob("merge_job") } returns listOf(
            PartEntity("p1", "merge_job", 1, "p1", "content://part1", 0.0, 0.0, status = PartStatus.PENDING),
            PartEntity("p2", "merge_job", 2, "p2", "content://part2", 0.0, 0.0, status = PartStatus.PENDING)
        )
        every { outputFolderValidator.validate(any(), any(), any()) } returns OutputFolderValidation.Ok

        transportMerger.runMerge("merge_job")

        // Fail policy: complete-write verification FAIL (all bytes written but hash mismatch)
        // MUST keep the output file, mark FAILED, and show specific reason with output path.
        assertFalse(deletedFiles.contains("content://mock_out_dir/test.mkv"))

        val slotJob = slot<JobEntity>()
        coVerify { jobDao.upsert(capture(slotJob)) }
        assertEquals(JobStatus.FAILED, slotJob.captured.status)
        assertTrue(slotJob.captured.errorMessage?.contains("hash mismatch") == true)
        assertTrue(slotJob.captured.errorMessage?.contains("content://mock_out_dir/test.mkv") == true)
    }

    @Test
    fun testStoragePreFlightFailure() = runTest {
        val part1 = createPartBytes(1, 1, 15L, 0L, ByteArray(15))
        registerFile("content://part1", part1)

        val job = JobEntity(
            id = "merge_job",
            type = JobType.MERGE,
            status = JobStatus.QUEUED,
            sourceUri = "content://mock",
            outputDirUri = "content://media/out",
            outputBaseName = "merged",
            outputContainer = ".mkv",
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        coEvery { jobDao.getById("merge_job") } returns job
        coEvery { jobDao.getPartsForJob("merge_job") } returns listOf(
            PartEntity("p1", "merge_job", 1, "p1", "content://part1", 0.0, 0.0, status = PartStatus.PENDING)
        )
        // Mock space failure
        every { outputFolderValidator.validate(any(), any(), any()) } returns OutputFolderValidation.InsufficientSpace(100L, 50L)

        transportMerger.runMerge("merge_job")

        // No output file ever opened or created
        assertFalse(outputStreams.containsKey(Uri.parse("content://mock_out_dir/test.mkv")))

        val slotJob = slot<JobEntity>()
        coVerify { jobDao.upsert(capture(slotJob)) }
        assertEquals(JobStatus.FAILED, slotJob.captured.status)
        assertTrue(slotJob.captured.errorMessage?.contains("Insufficient storage space") == true)
    }

    @Test
    fun testMissingPart_NoOutputCreated() = runTest {
        // --- CASE 1: Drop an INTERIOR part (parts 1 and 3 of 3) ---
        val p1Bytes = ByteArray(10) { 1 }
        val p3Bytes = ByteArray(10) { 3 }
        val part1 = createPartBytes(1, 3, 30L, 0L, p1Bytes)
        val part3 = createPartBytes(3, 3, 30L, 20L, p3Bytes)

        registerFile("content://part1", part1)
        registerFile("content://part3", part3)

        val jobInterior = JobEntity(
            id = "merge_job_interior",
            type = JobType.MERGE,
            status = JobStatus.QUEUED,
            sourceUri = "content://mock",
            outputDirUri = "content://media/out",
            outputBaseName = "merged",
            outputContainer = ".mkv",
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        coEvery { jobDao.getById("merge_job_interior") } returns jobInterior
        coEvery { jobDao.getPartsForJob("merge_job_interior") } returns listOf(
            PartEntity("p1", "merge_job_interior", 1, "p1", "content://part1", 0.0, 0.0, status = PartStatus.PENDING),
            PartEntity("p3", "merge_job_interior", 3, "p3", "content://part3", 0.0, 0.0, status = PartStatus.PENDING)
        )
        every { outputFolderValidator.validate(any(), any(), any()) } returns OutputFolderValidation.Ok

        transportMerger.runMerge("merge_job_interior")

        assertFalse(outputStreams.containsKey(Uri.parse("content://mock_out_dir/test.mkv")))
        assertFalse(outputStreams.keys.any { it.toString().contains("test.mkv") })

        // Clear files / mocks for the next sub-case
        deletedFiles.clear()
        outputStreams.clear()

        // --- CASE 2: Drop the LAST part (parts 1 and 2 of 3) ---
        val part2 = createPartBytes(2, 3, 30L, 10L, ByteArray(10) { 2 })
        registerFile("content://part2", part2)
        registerFile("content://part1", part1)

        val jobLast = JobEntity(
            id = "merge_job_last",
            type = JobType.MERGE,
            status = JobStatus.QUEUED,
            sourceUri = "content://mock",
            outputDirUri = "content://media/out",
            outputBaseName = "merged",
            outputContainer = ".mkv",
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        coEvery { jobDao.getById("merge_job_last") } returns jobLast
        coEvery { jobDao.getPartsForJob("merge_job_last") } returns listOf(
            PartEntity("p1", "merge_job_last", 1, "p1", "content://part1", 0.0, 0.0, status = PartStatus.PENDING),
            PartEntity("p2", "merge_job_last", 2, "p2", "content://part2", 0.0, 0.0, status = PartStatus.PENDING)
        )

        transportMerger.runMerge("merge_job_last")

        assertFalse(outputStreams.containsKey(Uri.parse("content://mock_out_dir/test.mkv")))
        assertFalse(outputStreams.keys.any { it.toString().contains("test.mkv") })

        val capturedJobs = mutableListOf<JobEntity>()
        coVerify { jobDao.upsert(capture(capturedJobs)) }

        val interiorResult = capturedJobs.find { it.id == "merge_job_interior" }
        assertTrue(interiorResult != null)
        assertEquals(JobStatus.FAILED, interiorResult?.status)
        assertEquals("missing part(s)", interiorResult?.errorMessage)

        val lastResult = capturedJobs.find { it.id == "merge_job_last" }
        assertTrue(lastResult != null)
        assertEquals(JobStatus.FAILED, lastResult?.status)
        assertEquals("cannot reconstruct — last part missing", lastResult?.errorMessage)
    }

    @Test
    fun testRoundTrip_MultiPart() = runTest {
        val originalBytes = ByteArray(100) { it.toByte() }
        val wholeSha = MessageDigest.getInstance("SHA-256").digest(originalBytes)
        val sourceUri = Uri.parse("content://media/source.mkv")
        val outDirUri = Uri.parse("content://media/out")
        val mergeFileUri = Uri.parse("content://mock_out_dir/source.mkv")

        // Split originalBytes using TransportSplitter
        val jobSplitId = "split_job"
        val jobSplit = JobEntity(
            id = jobSplitId,
            type = JobType.SPLIT,
            status = JobStatus.QUEUED,
            sourceUri = "content://media/source.mkv",
            outputDirUri = "content://media/out",
            outputBaseName = "split_out",
            outputContainer = ".mkv",
            targetCapBytes = 40L, // Should create 3 parts (40, 40, 20)
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L,
            splitFormat = "BYTE"
        )

        coEvery { jobDao.getById(jobSplitId) } returns jobSplit
        val mockPfd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { statSize } returns originalBytes.size.toLong()
        }
        every { contentResolver.openFileDescriptor(sourceUri, "r") } returns mockPfd
        every { contentResolver.openInputStream(sourceUri) } answers {
            ByteArrayInputStream(originalBytes)
        }
        every { outputFolderValidator.validate("content://media/out", any(), any()) } returns OutputFolderValidation.Ok

        // Mock folder creation
        val mockTree = mockk<DocumentFile>(relaxed = true)
        val mockSubfolder = mockk<DocumentFile>(relaxed = true)
        every { DocumentFile.fromTreeUri(any(), any()) } returns mockTree
        every { mockTree.createDirectory("split_out") } returns mockSubfolder

        val splitCreatedFiles = mutableListOf<DocumentFile>()
        every { mockSubfolder.createFile(any(), any()) } answers {
            val name = arg<String>(1)
            val mockFile = mockk<DocumentFile>(relaxed = true) {
                every { this@mockk.name } returns name
                every { uri } returns Uri.parse("content://split_parts/$name")
            }
            splitCreatedFiles.add(mockFile)
            mockFile
        }

        val splitStreams = mutableMapOf<Uri, ByteArrayOutputStream>()
        every { contentResolver.openOutputStream(any()) } answers {
            val uri = arg<Uri>(0)
            val out = ByteArrayOutputStream()
            splitStreams[uri] = out
            out
        }

        splitter.runSplit(jobSplitId)

        // We now register the split part outputs as input files for Merger
        val part1Uri = "content://split_parts/split_out.part_01_03.mkv"
        val part2Uri = "content://split_parts/split_out.part_02_03.mkv"
        val part3Uri = "content://split_parts/split_out.part_03_03.mkv"

        registerFile(part1Uri, splitStreams[Uri.parse("$part1Uri.tmp")]?.toByteArray() ?: ByteArray(0))
        registerFile(part2Uri, splitStreams[Uri.parse("$part2Uri.tmp")]?.toByteArray() ?: ByteArray(0))
        registerFile(part3Uri, splitStreams[Uri.parse("$part3Uri.tmp")]?.toByteArray() ?: ByteArray(0))

        // Wire merge setup
        val jobMergeId = "merge_job"
        val jobMerge = JobEntity(
            id = jobMergeId,
            type = JobType.MERGE,
            status = JobStatus.QUEUED,
            sourceUri = "content://mock",
            outputDirUri = "content://media/out",
            outputBaseName = "merged",
            outputContainer = ".mkv",
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        coEvery { jobDao.getById(jobMergeId) } returns jobMerge
        coEvery { jobDao.getPartsForJob(jobMergeId) } returns listOf(
            PartEntity("p1", jobMergeId, 1, "p1", part1Uri, 0.0, 0.0, status = PartStatus.PENDING),
            PartEntity("p2", jobMergeId, 2, "p2", part2Uri, 0.0, 0.0, status = PartStatus.PENDING),
            PartEntity("p3", jobMergeId, 3, "p3", part3Uri, 0.0, 0.0, status = PartStatus.PENDING)
        )

        // Mock merge destination file creation
        val mockMergeTree = mockk<DocumentFile>(relaxed = true)
        val mockMergeFile = mockk<DocumentFile>(relaxed = true) {
            every { uri } returns mergeFileUri
        }
        every { DocumentFile.fromTreeUri(any(), outDirUri) } returns mockMergeTree
        every { mockMergeTree.createFile(any(), "source.mkv") } returns mockMergeFile

        val mergeOutStream = ByteArrayOutputStream()
        every { contentResolver.openOutputStream(mergeFileUri) } returns mergeOutStream

        // Run merge
        transportMerger.runMerge(jobMergeId)

        // Assert byte-identity
        assertArrayEquals(originalBytes, mergeOutStream.toByteArray())
    }

    @Test
    fun testRoundTrip_SinglePart() = runTest {
        val originalBytes = ByteArray(15) { it.toByte() }
        val sourceUri = Uri.parse("content://media/source.mkv")
        val outDirUri = Uri.parse("content://media/out")
        val mergeFileUri = Uri.parse("content://mock_out_dir/source.mkv")

        // Split single part (targetCap = 30L, size = 15L -> 1 part)
        val jobSplitId = "split_job"
        val jobSplit = JobEntity(
            id = jobSplitId,
            type = JobType.SPLIT,
            status = JobStatus.QUEUED,
            sourceUri = "content://media/source.mkv",
            outputDirUri = "content://media/out",
            outputBaseName = "split_out",
            outputContainer = ".mkv",
            targetCapBytes = 30L,
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L,
            splitFormat = "BYTE"
        )

        coEvery { jobDao.getById(jobSplitId) } returns jobSplit
        val mockPfd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { statSize } returns originalBytes.size.toLong()
        }
        every { contentResolver.openFileDescriptor(sourceUri, "r") } returns mockPfd
        every { contentResolver.openInputStream(sourceUri) } answers {
            ByteArrayInputStream(originalBytes)
        }
        every { outputFolderValidator.validate("content://media/out", any(), any()) } returns OutputFolderValidation.Ok

        val mockTree = mockk<DocumentFile>(relaxed = true)
        val mockSubfolder = mockk<DocumentFile>(relaxed = true)
        every { DocumentFile.fromTreeUri(any(), any()) } returns mockTree
        every { mockTree.createDirectory("split_out") } returns mockSubfolder

        val splitCreatedFiles = mutableListOf<DocumentFile>()
        every { mockSubfolder.createFile(any(), any()) } answers {
            val name = arg<String>(1)
            val mockFile = mockk<DocumentFile>(relaxed = true) {
                every { this@mockk.name } returns name
                every { uri } returns Uri.parse("content://split_parts/$name")
            }
            splitCreatedFiles.add(mockFile)
            mockFile
        }

        val splitStreams = mutableMapOf<Uri, ByteArrayOutputStream>()
        every { contentResolver.openOutputStream(any()) } answers {
            val uri = arg<Uri>(0)
            val out = ByteArrayOutputStream()
            splitStreams[uri] = out
            out
        }

        splitter.runSplit(jobSplitId)

        val part1Uri = "content://split_parts/split_out.part_01_01.mkv"
        registerFile(part1Uri, splitStreams[Uri.parse("$part1Uri.tmp")]?.toByteArray() ?: ByteArray(0))

        // Wire merge setup
        val jobMergeId = "merge_job"
        val jobMerge = JobEntity(
            id = jobMergeId,
            type = JobType.MERGE,
            status = JobStatus.QUEUED,
            sourceUri = "content://mock",
            outputDirUri = "content://media/out",
            outputBaseName = "merged",
            outputContainer = ".mkv",
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        coEvery { jobDao.getById(jobMergeId) } returns jobMerge
        coEvery { jobDao.getPartsForJob(jobMergeId) } returns listOf(
            PartEntity("p1", jobMergeId, 1, "p1", part1Uri, 0.0, 0.0, status = PartStatus.PENDING)
        )

        val mockMergeTree = mockk<DocumentFile>(relaxed = true)
        val mockMergeFile = mockk<DocumentFile>(relaxed = true) {
            every { uri } returns mergeFileUri
        }
        every { DocumentFile.fromTreeUri(any(), outDirUri) } returns mockMergeTree
        every { mockMergeTree.createFile(any(), "source.mkv") } returns mockMergeFile

        val mergeOutStream = ByteArrayOutputStream()
        every { contentResolver.openOutputStream(mergeFileUri) } returns mergeOutStream

        transportMerger.runMerge(jobMergeId)

        assertArrayEquals(originalBytes, mergeOutStream.toByteArray())
    }

    @Test
    fun testRoundTrip_ZeroByteInput() = runTest {
        val originalBytes = ByteArray(0)
        val sourceUri = Uri.parse("content://media/source.mkv")
        val outDirUri = Uri.parse("content://media/out")
        val mergeFileUri = Uri.parse("content://mock_out_dir/source.mkv")

        // Split zero bytes
        val jobSplitId = "split_job"
        val jobSplit = JobEntity(
            id = jobSplitId,
            type = JobType.SPLIT,
            status = JobStatus.QUEUED,
            sourceUri = "content://media/source.mkv",
            outputDirUri = "content://media/out",
            outputBaseName = "split_out",
            outputContainer = ".mkv",
            targetCapBytes = 30L,
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L,
            splitFormat = "BYTE"
        )

        coEvery { jobDao.getById(jobSplitId) } returns jobSplit
        val mockPfd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { statSize } returns 0L
        }
        every { contentResolver.openFileDescriptor(sourceUri, "r") } returns mockPfd
        every { contentResolver.openInputStream(sourceUri) } answers {
            ByteArrayInputStream(originalBytes)
        }
        every { outputFolderValidator.validate("content://media/out", any(), any()) } returns OutputFolderValidation.Ok

        val mockTree = mockk<DocumentFile>(relaxed = true)
        val mockSubfolder = mockk<DocumentFile>(relaxed = true)
        every { DocumentFile.fromTreeUri(any(), any()) } returns mockTree
        every { mockTree.createDirectory("split_out") } returns mockSubfolder

        val splitCreatedFiles = mutableListOf<DocumentFile>()
        every { mockSubfolder.createFile(any(), any()) } answers {
            val name = arg<String>(1)
            val mockFile = mockk<DocumentFile>(relaxed = true) {
                every { this@mockk.name } returns name
                every { uri } returns Uri.parse("content://split_parts/$name")
            }
            splitCreatedFiles.add(mockFile)
            mockFile
        }

        val splitStreams = mutableMapOf<Uri, ByteArrayOutputStream>()
        every { contentResolver.openOutputStream(any()) } answers {
            val uri = arg<Uri>(0)
            val out = ByteArrayOutputStream()
            splitStreams[uri] = out
            out
        }

        splitter.runSplit(jobSplitId)

        val part1Uri = "content://split_parts/split_out.part_01_01.mkv"
        registerFile(part1Uri, splitStreams[Uri.parse("$part1Uri.tmp")]?.toByteArray() ?: ByteArray(0))

        // Wire merge setup
        val jobMergeId = "merge_job"
        val jobMerge = JobEntity(
            id = jobMergeId,
            type = JobType.MERGE,
            status = JobStatus.QUEUED,
            sourceUri = "content://mock",
            outputDirUri = "content://media/out",
            outputBaseName = "merged",
            outputContainer = ".mkv",
            progressPct = 0,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        coEvery { jobDao.getById(jobMergeId) } returns jobMerge
        coEvery { jobDao.getPartsForJob(jobMergeId) } returns listOf(
            PartEntity("p1", jobMergeId, 1, "p1", part1Uri, 0.0, 0.0, status = PartStatus.PENDING)
        )

        val mockMergeTree = mockk<DocumentFile>(relaxed = true)
        val mockMergeFile = mockk<DocumentFile>(relaxed = true) {
            every { uri } returns mergeFileUri
        }
        every { DocumentFile.fromTreeUri(any(), outDirUri) } returns mockMergeTree
        every { mockMergeTree.createFile(any(), "source.mkv") } returns mockMergeFile

        val mergeOutStream = ByteArrayOutputStream()
        every { contentResolver.openOutputStream(mergeFileUri) } returns mergeOutStream

        transportMerger.runMerge(jobMergeId)

        assertArrayEquals(originalBytes, mergeOutStream.toByteArray())
    }
}
