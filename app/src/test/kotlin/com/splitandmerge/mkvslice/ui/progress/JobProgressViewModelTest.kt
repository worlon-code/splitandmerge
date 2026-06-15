package com.splitandmerge.mkvslice.ui.progress

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.progress.JobPhaseHint
import com.splitandmerge.mkvslice.domain.progress.JobProgressTracker
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JobProgressViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockDao = mockk<JobDao>(relaxed = true)
    private val mockContext = mockk<Context>(relaxed = true)
    private val jobProgressTracker = JobProgressTracker()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when phaseHint is Analyzing and jobType is SPLIT and status is RUNNING, state phaseHint is Analyzing and statusMessage matches`() = runTest {
        val jobId = "job_split"
        val jobEntity = JobEntity(
            id = jobId,
            type = JobType.SPLIT,
            createdAt = 1000L,
            updatedAt = 1000L,
            status = JobStatus.RUNNING,
            outputBaseName = "test_split",
            outputContainer = ".mkv",
            sourceUri = "content://source",
            outputDirUri = "content://out",
            progressPct = 10
        )
        every { mockDao.observeById(jobId) } returns MutableStateFlow(jobEntity)
        
        jobProgressTracker.setPhaseHint(jobId, JobPhaseHint.Analyzing)
        
        val viewModel = JobProgressViewModel(
            savedStateHandle = SavedStateHandle(mapOf("jobId" to jobId)),
            jobDao = mockDao,
            jobProgressTracker = jobProgressTracker,
            context = mockContext
        )
        
        val state = viewModel.state.first()
        assertEquals(JobPhaseHint.Analyzing, state.phaseHint)
        assertEquals("Analyzing video structure", state.phaseLabel)
    }

    @Test
    fun `when phaseHint is Analyzing and jobType is MERGE, state phaseHint is null`() = runTest {
        val jobId = "job_merge"
        val jobEntity = JobEntity(
            id = jobId,
            type = JobType.MERGE,
            createdAt = 1000L,
            updatedAt = 1000L,
            status = JobStatus.RUNNING,
            outputBaseName = "test_merge",
            outputContainer = ".mkv",
            sourceUri = "content://source",
            outputDirUri = "content://out",
            progressPct = 10
        )
        every { mockDao.observeById(jobId) } returns MutableStateFlow(jobEntity)
        
        jobProgressTracker.setPhaseHint(jobId, JobPhaseHint.Analyzing)
        
        val viewModel = JobProgressViewModel(
            savedStateHandle = SavedStateHandle(mapOf("jobId" to jobId)),
            jobDao = mockDao,
            jobProgressTracker = jobProgressTracker,
            context = mockContext
        )
        
        val state = viewModel.state.first()
        assertNull(state.phaseHint)
    }

    @Test
    fun `when phaseHint is null, state phaseHint is null`() = runTest {
        val jobId = "job_null_hint"
        val jobEntity = JobEntity(
            id = jobId,
            type = JobType.SPLIT,
            createdAt = 1000L,
            updatedAt = 1000L,
            status = JobStatus.RUNNING,
            outputBaseName = "test_split_null",
            outputContainer = ".mkv",
            sourceUri = "content://source",
            outputDirUri = "content://out",
            progressPct = 10
        )
        every { mockDao.observeById(jobId) } returns MutableStateFlow(jobEntity)
        
        jobProgressTracker.setPhaseHint(jobId, null)
        
        val viewModel = JobProgressViewModel(
            savedStateHandle = SavedStateHandle(mapOf("jobId" to jobId)),
            jobDao = mockDao,
            jobProgressTracker = jobProgressTracker,
            context = mockContext
        )
        
        val state = viewModel.state.first()
        assertNull(state.phaseHint)
    }
}
