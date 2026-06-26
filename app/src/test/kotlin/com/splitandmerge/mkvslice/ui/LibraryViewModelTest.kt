package com.splitandmerge.mkvslice.ui

import android.content.Context
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.PartStatus
import com.splitandmerge.mkvslice.ui.library.LibraryIntent
import com.splitandmerge.mkvslice.ui.library.LibraryViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockDao = mockk<JobDao>(relaxed = true)
    private val mockDefaultTrackFileResultDao = mockk<com.splitandmerge.mkvslice.data.db.DefaultTrackFileResultDao>(relaxed = true)
    private val mockFileSystem = mockk<com.splitandmerge.mkvslice.platform.io.FileSystem>(relaxed = true)
    private val mockContext = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockContext.cacheDir } returns java.io.File(".")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testMockJobsAreLoadedOnInit() = runTest {
        val mockJobs = listOf(
            JobEntity(
                id = "1", type = JobType.SPLIT, 
                createdAt = 1718150400000L, updatedAt = 1718150400000L, 
                status = JobStatus.DONE, 
                progressPct = 100, sourceUri = "", outputDirUri = "", 
                outputBaseName = "Kantara Chapter 1 (2024)", outputContainer = ".mkv"
            )
        )
        every { mockDao.observeAll() } returns flowOf(mockJobs)
        val viewModel = LibraryViewModel(mockDao, mockDefaultTrackFileResultDao, mockFileSystem, mockContext)
        val jobs = viewModel.jobs.first()
        assertNotNull(jobs)
        assertEquals(1, jobs.size)
        assertEquals("Kantara Chapter 1 (2024)", jobs[0].outputBaseName)
    }

    @Test
    fun testRowTappedIntentTransitionsCorrectly() = runTest {
        val runningJob = JobEntity(
            id = "run-1", type = JobType.SPLIT, 
            createdAt = 100L, updatedAt = 100L, 
            status = JobStatus.RUNNING, 
            progressPct = 50, sourceUri = "", outputDirUri = "", 
            outputBaseName = "Test Run", outputContainer = ".mkv"
        )
        val doneMergeJob = JobEntity(
            id = "done-merge", type = JobType.MERGE, 
            createdAt = 100L, updatedAt = 100L, 
            status = JobStatus.DONE, 
            progressPct = 100, sourceUri = "", outputDirUri = "", 
            outputBaseName = "Test Merge", outputContainer = ".mkv"
        )
        val failedJob = JobEntity(
            id = "failed-1", type = JobType.SPLIT, 
            createdAt = 100L, updatedAt = 100L, 
            status = JobStatus.FAILED, 
            progressPct = 10, sourceUri = "", outputDirUri = "", 
            outputBaseName = "Test Fail", outputContainer = ".mkv"
        )

        coEvery { mockDao.getById("run-1") } returns runningJob
        coEvery { mockDao.getById("done-merge") } returns doneMergeJob
        coEvery { mockDao.getById("failed-1") } returns failedJob
        every { mockDao.observeAll() } returns flowOf(emptyList())

        val viewModel = LibraryViewModel(mockDao, mockDefaultTrackFileResultDao, mockFileSystem, mockContext)

        // Test RUNNING -> Progress
        viewModel.handleIntent(LibraryIntent.RowTapped("run-1"))
        val cmd1 = viewModel.navigationEvents.first()
        assertEquals(LibraryViewModel.NavCommand.ToProgress("run-1"), cmd1)

        // Test DONE MERGE -> Merge Result
        viewModel.handleIntent(LibraryIntent.RowTapped("done-merge"))
        val cmd2 = viewModel.navigationEvents.first()
        assertEquals(LibraryViewModel.NavCommand.ToMergeResult("done-merge"), cmd2)

        // Test FAILED -> Detail Sheet
        viewModel.handleIntent(LibraryIntent.RowTapped("failed-1"))
        val cmd3 = viewModel.navigationEvents.first()
        assertEquals(LibraryViewModel.NavCommand.ToDetailSheet("failed-1", JobStatus.FAILED), cmd3)
    }

    @Test
    fun testRetryJobIntentReplicatesMergeParts() = runTest {
        val oldMergeJob = JobEntity(
            id = "old-merge", type = JobType.MERGE, 
            createdAt = 100L, updatedAt = 100L, 
            status = JobStatus.FAILED, 
            progressPct = 40, sourceUri = "", outputDirUri = "", 
            outputBaseName = "Test Merge", outputContainer = ".mkv",
            errorMessage = "Something failed"
        )
        val oldPart = PartEntity(
            id = "old-part-1", jobId = "old-merge", index = 1,
            name = "part1.mkv", sourceUri = "content://parts/1",
            startSec = 0.0, endSec = 10.0, status = PartStatus.FAILED
        )

        coEvery { mockDao.getById("old-merge") } returns oldMergeJob
        coEvery { mockDao.getPartsForJob("old-merge") } returns listOf(oldPart)
        every { mockDao.observeAll() } returns flowOf(emptyList())

        val viewModel = LibraryViewModel(mockDao, mockDefaultTrackFileResultDao, mockFileSystem, mockContext)
        viewModel.handleIntent(LibraryIntent.RetryJob("old-merge"))

        coVerify { mockDao.upsert(any()) }
        coVerify { mockDao.upsertPart(any()) }
    }

    @Test
    fun testDeleteJobIntentDeletesRow() = runTest {
        every { mockDao.observeAll() } returns flowOf(emptyList())
        val viewModel = LibraryViewModel(mockDao, mockDefaultTrackFileResultDao, mockFileSystem, mockContext)
        viewModel.handleIntent(LibraryIntent.DeleteJob("job-to-delete"))
        coVerify { mockDao.deleteById("job-to-delete") }
    }

    @Test
    fun test_init_isInitialLoadTrue() = runTest {
        val flow = kotlinx.coroutines.flow.MutableSharedFlow<List<JobEntity>>()
        every { mockDao.observeAll() } returns flow
        val viewModel = LibraryViewModel(mockDao, mockDefaultTrackFileResultDao, mockFileSystem, mockContext)
        val currentState = viewModel.state.value
        assertEquals(true, currentState.isInitialLoad)
        assertEquals(0, currentState.jobs.size)
    }

    @Test
    fun test_afterFirstEmit_isInitialLoadFalse() = runTest {
        val mockJobs = listOf(
            JobEntity(
                id = "1", type = JobType.SPLIT, 
                createdAt = 1718150400000L, updatedAt = 1718150400000L, 
                status = JobStatus.DONE, 
                progressPct = 100, sourceUri = "", outputDirUri = "", 
                outputBaseName = "Kantara Chapter 1 (2024)", outputContainer = ".mkv"
            )
        )
        every { mockDao.observeAll() } returns flowOf(mockJobs)
        val viewModel = LibraryViewModel(mockDao, mockDefaultTrackFileResultDao, mockFileSystem, mockContext)
        val stateVal = viewModel.state.first()
        assertEquals(false, stateVal.isInitialLoad)
        assertEquals(1, stateVal.jobs.size)
    }

    @Test
    fun test_emptyList_isInitialLoadFalse_emptyState() = runTest {
        every { mockDao.observeAll() } returns flowOf(emptyList())
        val viewModel = LibraryViewModel(mockDao, mockDefaultTrackFileResultDao, mockFileSystem, mockContext)
        val stateVal = viewModel.state.first()
        assertEquals(false, stateVal.isInitialLoad)
        assertEquals(0, stateVal.jobs.size)
    }
}
