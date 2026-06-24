package com.splitandmerge.mkvslice.domain.splitter

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.SplitMode
import com.splitandmerge.mkvslice.domain.progress.JobPhaseHint
import com.splitandmerge.mkvslice.domain.progress.JobProgressTracker
import com.splitandmerge.mkvslice.engine.FfmpegEngine
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import com.splitandmerge.mkvslice.engine.ProbeResult
import com.splitandmerge.mkvslice.engine.FormatInfo
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class SplitterTest {

    private val context = mockk<Context>(relaxed = true)
    private val jobDao = mockk<JobDao>(relaxed = true)
    private val ffprobeEngine = mockk<FfprobeEngine>(relaxed = true)
    private val ffmpegEngine = mockk<FfmpegEngine>(relaxed = true)
    private val cutPlanner = CutPlanner()
    private val manifestWriter = mockk<ManifestWriter>(relaxed = true)
    private val jobProgressTracker = mockk<JobProgressTracker>(relaxed = true)
    private val transportSplitter = mockk<com.splitandmerge.mkvslice.domain.transport.TransportSplitter>(relaxed = true)

    private lateinit var splitter: Splitter

    @Before
    fun setup() {
        splitter = Splitter(
            context = context,
            jobDao = jobDao,
            ffprobeEngine = ffprobeEngine,
            ffmpegEngine = ffmpegEngine,
            cutPlanner = cutPlanner,
            manifestWriter = manifestWriter,
            jobProgressTracker = jobProgressTracker,
            transportSplitter = transportSplitter
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class, DocumentFile::class)
    }

    @Test
    fun `runSplit bails early if job not found`() = runTest {
        coEvery { jobDao.getById(any()) } returns null
        splitter.runSplit("missing_job")
    }

    @Test
    fun `runSplit sets phase hint and clears it in sequence on success`() = runTest {
        val jobId = "job_123"
        val job = JobEntity(
            id = jobId,
            type = JobType.SPLIT,
            createdAt = 1000L,
            updatedAt = 1000L,
            status = JobStatus.QUEUED,
            sourceUri = "file:///tmp/source.mkv",
            outputDirUri = "file:///tmp/out",
            outputBaseName = "out",
            outputContainer = ".mkv",
            mode = SplitMode.EXACT_PARTS,
            requestedParts = 2,
            progressPct = 0
        )
        coEvery { jobDao.getById(jobId) } returns job

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true) {
            every { scheme } returns "file"
            every { path } returns "/tmp"
        }

        mockkStatic(DocumentFile::class)
        val mockDocFile = mockk<DocumentFile>(relaxed = true)
        every { DocumentFile.fromFile(any()) } returns mockDocFile

        val mockFormat = mockk<FormatInfo>(relaxed = true) {
            every { durationSeconds } returns 60.0
            every { sizeBytes } returns 1024L * 1024L
            every { filename } returns "source.mkv"
        }
        val mockProbeResult = mockk<ProbeResult>(relaxed = true) {
            every { format } returns mockFormat
            every { streams } returns emptyList()
        }
        coEvery { ffprobeEngine.probe(any()) } returns mockProbeResult
        coEvery { ffprobeEngine.keyframes(any()) } returns emptyList()

        splitter.runSplit(jobId)

        coVerifyOrder {
            jobProgressTracker.setPhaseHint(jobId, JobPhaseHint.Analyzing)
            ffprobeEngine.keyframes(any())
            jobProgressTracker.setPhaseHint(jobId, null)
        }
    }

    @Test
    fun `runSplit clears phase hint on exception path`() = runTest {
        val jobId = "job_123"
        val job = JobEntity(
            id = jobId,
            type = JobType.SPLIT,
            createdAt = 1000L,
            updatedAt = 1000L,
            status = JobStatus.QUEUED,
            sourceUri = "file:///tmp/source.mkv",
            outputDirUri = "file:///tmp/out",
            outputBaseName = "out",
            outputContainer = ".mkv",
            mode = SplitMode.EXACT_PARTS,
            requestedParts = 2,
            progressPct = 0
        )
        coEvery { jobDao.getById(jobId) } returns job
        coEvery { ffprobeEngine.probe(any()) } throws RuntimeException("Probe failed")

        splitter.runSplit(jobId)

        coVerifyOrder {
            jobProgressTracker.setPhaseHint(jobId, JobPhaseHint.Analyzing)
            jobProgressTracker.setPhaseHint(jobId, null)
        }
    }
}
