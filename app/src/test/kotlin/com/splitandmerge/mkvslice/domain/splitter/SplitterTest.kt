package com.splitandmerge.mkvslice.domain.splitter

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.engine.FfmpegEngine
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class SplitterTest {

    private val context = mockk<Context>(relaxed = true)
    private val jobDao = mockk<JobDao>(relaxed = true)
    private val ffprobeEngine = mockk<FfprobeEngine>(relaxed = true)
    private val ffmpegEngine = mockk<FfmpegEngine>(relaxed = true)
    private val cutPlanner = CutPlanner()
    private val manifestWriter = mockk<ManifestWriter>(relaxed = true)

    private lateinit var splitter: Splitter

    @Before
    fun setup() {
        splitter = Splitter(
            context = context,
            jobDao = jobDao,
            ffprobeEngine = ffprobeEngine,
            ffmpegEngine = ffmpegEngine,
            cutPlanner = cutPlanner,
            manifestWriter = manifestWriter
        )
    }

    @Test
    fun `runSplit bails early if job not found`() = runTest {
        coEvery { jobDao.getById(any()) } returns null
        splitter.runSplit("missing_job")
        // Should complete without error
    }

    // Additional tests require mocking DocumentFile statics which is hard in JVM without Robolectric.
    // We rely mostly on the SplitSmokeTest for the end-to-end integration of the Splitter.
}
