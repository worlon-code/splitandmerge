package com.splitandmerge.mkvslice.ui.result

import androidx.lifecycle.SavedStateHandle
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.PartStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplitResultViewModelTest {

    private val savedStateHandle = mockk<SavedStateHandle>(relaxed = true)
    private val jobDao = mockk<JobDao>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { savedStateHandle.get<String>("jobId") } returns "job1"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createJobEntity(): JobEntity = JobEntity(
        id = "job1",
        type = JobType.SPLIT,
        sourceUri = "content://com.android.externalstorage.documents/document/primary:Movies%2FDrida.mkv",
        outputDirUri = "content://com.android.externalstorage.documents/tree/primary:Downloads",
        outputBaseName = "Drida",
        outputContainer = ".mkv",
        status = JobStatus.DONE,
        progressPct = 100,
        createdAt = 1000L,
        updatedAt = 5000L
    )

    private fun createPartEntity(
        index: Int,
        name: String,
        sizeBytes: Long? = null
    ): PartEntity = PartEntity(
        id = "part$index",
        jobId = "job1",
        index = index,
        name = name,
        sourceUri = "content://com.android.externalstorage.documents/document/primary:Downloads%2FDrida.part$index.mkv",
        startSec = (index - 1) * 3600.0,
        endSec = index * 3600.0,
        sizeBytes = sizeBytes,
        status = PartStatus.DONE
    )

    // ──────────────────────────────────────────────────────────────────
    // Test 1 — jobId not found in DB → emits error state
    // ──────────────────────────────────────────────────────────────────
    @Test
    fun `test_loadResult_jobNotFound_emitsError`() = runTest {
        coEvery { jobDao.getById("job1") } returns null

        val viewModel = SplitResultViewModel(savedStateHandle, jobDao)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("Job not found"))
        assertTrue(state.outputs.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────
    // Test 2 — job exists with 3 parts → emits full outputs list with names + sizes
    // ──────────────────────────────────────────────────────────────────
    @Test
    fun `test_loadResult_jobExistsWithParts_emitsOutputsList`() = runTest {
        val job = createJobEntity()
        val parts = listOf(
            createPartEntity(1, "Drida.part1.mkv", sizeBytes = 9_669_664_768L),
            createPartEntity(2, "Drida.part2.mkv", sizeBytes = 9_669_664_768L),
            createPartEntity(3, "Drida.part3.mkv", sizeBytes = 7_516_192_768L)
        )

        coEvery { jobDao.getById("job1") } returns job
        coEvery { jobDao.getPartsForJob("job1") } returns parts

        val viewModel = SplitResultViewModel(savedStateHandle, jobDao)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("Drida", state.baseName)
        assertEquals(3, state.partCount)
        assertEquals(3, state.outputs.size)

        assertEquals("Drida.part1.mkv", state.outputs[0].name)
        assertEquals(9_669_664_768L, state.outputs[0].sizeBytes)

        assertEquals("Drida.part2.mkv", state.outputs[1].name)
        assertEquals(9_669_664_768L, state.outputs[1].sizeBytes)

        assertEquals("Drida.part3.mkv", state.outputs[2].name)
        assertEquals(7_516_192_768L, state.outputs[2].sizeBytes)
    }

    // ──────────────────────────────────────────────────────────────────
    // Test 3 — job exists but has zero parts → emits empty outputs list
    // ──────────────────────────────────────────────────────────────────
    @Test
    fun `test_loadResult_jobExistsZeroParts_emitsEmptyList`() = runTest {
        val job = createJobEntity()

        coEvery { jobDao.getById("job1") } returns job
        coEvery { jobDao.getPartsForJob("job1") } returns emptyList()

        val viewModel = SplitResultViewModel(savedStateHandle, jobDao)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("Drida", state.baseName)
        assertEquals(0, state.partCount)
        assertTrue(state.outputs.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────
    // Test 4 — parts with null sizeBytes → sizeBytes falls back to 0
    // ──────────────────────────────────────────────────────────────────
    @Test
    fun `test_loadResult_partsWithNullSize_fallsBackToZero`() = runTest {
        val job = createJobEntity()
        val parts = listOf(
            createPartEntity(1, "Drida.part1.mkv", sizeBytes = null),
            createPartEntity(2, "Drida.part2.mkv", sizeBytes = null)
        )

        coEvery { jobDao.getById("job1") } returns job
        coEvery { jobDao.getPartsForJob("job1") } returns parts

        val viewModel = SplitResultViewModel(savedStateHandle, jobDao)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(2, state.outputs.size)
        assertEquals(0L, state.outputs[0].sizeBytes)
        assertEquals(0L, state.outputs[1].sizeBytes)
    }
}
