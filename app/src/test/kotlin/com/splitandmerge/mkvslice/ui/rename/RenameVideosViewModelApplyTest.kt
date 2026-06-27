package com.splitandmerge.mkvslice.ui.rename

import com.splitandmerge.mkvslice.data.repository.CleanupRepository
import com.splitandmerge.mkvslice.data.repository.RenameRepository
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.data.settings.SettingsState
import com.splitandmerge.mkvslice.domain.rename.DocumentRenamer
import com.splitandmerge.mkvslice.domain.rename.RenameOutcome
import com.splitandmerge.mkvslice.domain.rename.RenameStatus
import com.splitandmerge.mkvslice.domain.rename.RenameDecision
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class RenameVideosViewModelApplyTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockRenameRepository = mockk<RenameRepository>(relaxed = true)
    private val mockCleanupRepository = mockk<CleanupRepository>(relaxed = true)
    private val mockSettingsRepository = mockk<SettingsRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockCleanupRepository.observePatterns() } returns MutableStateFlow(emptyList())
        every { mockSettingsRepository.settingsFlow } returns MutableStateFlow(SettingsState())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    class FakeDocumentRenamer(
        val renameHandler: (uri: String, newName: String) -> RenameOutcome,
        val supportsRenameHandler: (uri: String) -> Boolean = { true }
    ) : DocumentRenamer {
        val renameCalls = mutableListOf<Pair<String, String>>()
        override fun rename(uri: String, newName: String): RenameOutcome {
            renameCalls.add(uri to newName)
            return renameHandler(uri, newName)
        }
        override fun supportsRename(uri: String): Boolean = supportsRenameHandler(uri)
    }

    @Test
    fun test1_folderSuccessAndCollisionFailure() = runTest {
        val viewModel = RenameVideosViewModel(mockRenameRepository, mockCleanupRepository, mockSettingsRepository, testDispatcher)

        // Populate filesList with folder rows (isPickedFile = false)
        val initialFiles = listOf(
            RenameFileRowState(
                id = "row-1",
                uri = "content://folder/file1.mkv",
                displayName = "file1.mkv",
                sizeBytes = 100L,
                parentKey = "parent-1",
                parentKnown = true,
                supportsRename = true,
                existingNamesInParent = emptyList(),
                isChecked = true,
                originalBaseName = "file1",
                extension = ".mkv",
                newBaseName = "file1",
                decision = RenameDecision.NO_CHANGE,
                targetName = "file1.mkv",
                isPickedFile = false
            ),
            RenameFileRowState(
                id = "row-2",
                uri = "content://folder/file2.mkv",
                displayName = "file2.mkv",
                sizeBytes = 200L,
                parentKey = "parent-1",
                parentKnown = true,
                supportsRename = true,
                existingNamesInParent = emptyList(),
                isChecked = true,
                originalBaseName = "file2",
                extension = ".mkv",
                newBaseName = "file2",
                decision = RenameDecision.NO_CHANGE,
                targetName = "file2.mkv",
                isPickedFile = false
            )
        )
        every { mockRenameRepository.scanFolder(any(), any(), any()) } returns com.splitandmerge.mkvslice.domain.rename.VideoScanner.ScanResult(initialFiles, false)
        viewModel.processPickedFolder(mockk(relaxed = true), false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Set manual names to trigger RENAME planned decision
        viewModel.updateManualName("row-1", "target-exists")
        viewModel.updateManualName("row-2", "target-success")
        testDispatcher.scheduler.advanceUntilIdle()

        // Double check checked count and decisions
        assertEquals(2, viewModel.filesList.value.size)
        assertTrue(viewModel.filesList.value[0].isChecked)
        assertEquals(RenameDecision.RENAME, viewModel.filesList.value[0].decision)

        val fakeRenamer = FakeDocumentRenamer(
            renameHandler = { uri, name ->
                if (uri == "content://folder/file1.mkv") {
                    RenameOutcome.Failure("File already exists")
                } else {
                    RenameOutcome.Success(name)
                }
            }
        )

        viewModel.executeRename(fakeRenamer)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as RenameVideosUiState.Results
        val results = state.batchResult.results
        assertEquals(2, results.size)

        // Assert row-1 (failed)
        val res1 = results.find { it.uri == "content://folder/file1.mkv" }
        assertNotNull(res1)
        assertTrue(res1?.status is RenameStatus.Failed)
        assertEquals("File already exists", (res1?.status as RenameStatus.Failed).reason)

        // Assert row-2 (succeeded)
        val res2 = results.find { it.uri == "content://folder/file2.mkv" }
        assertNotNull(res2)
        assertTrue(res2?.status is RenameStatus.Success)

        // Succeeded file is updated in-memory (displayed name = target-success.mkv, isChecked = false, decision = NO_CHANGE)
        val updatedRow2 = viewModel.filesList.value.find { it.id == "row-2" }
        assertEquals("target-success.mkv", updatedRow2?.displayName)
        assertEquals(false, updatedRow2?.isChecked)
        assertEquals(RenameDecision.NO_CHANGE, updatedRow2?.decision)

        // Failed file remains unchanged in filesList
        val updatedRow1 = viewModel.filesList.value.find { it.id == "row-1" }
        assertEquals("file1.mkv", updatedRow1?.displayName)
        assertEquals(true, updatedRow1?.isChecked)

        // Assert fakeRenamer calls (folder rows have no retries)
        assertEquals(2, fakeRenamer.renameCalls.size)
    }

    @Test
    fun test2_pickedRowNoCollision() = runTest {
        val viewModel = RenameVideosViewModel(mockRenameRepository, mockCleanupRepository, mockSettingsRepository, testDispatcher)
        val initialFiles = listOf(
            RenameFileRowState(
                id = "row-1",
                uri = "content://picked/file1.mkv",
                displayName = "file1.mkv",
                sizeBytes = 100L,
                parentKey = "picked-session",
                parentKnown = true,
                supportsRename = true,
                existingNamesInParent = emptyList(),
                isChecked = true,
                originalBaseName = "file1",
                extension = ".mkv",
                newBaseName = "file1",
                decision = RenameDecision.NO_CHANGE,
                targetName = "file1.mkv",
                isPickedFile = true
            )
        )
        every { mockRenameRepository.scanPickedFiles(any()) } returns initialFiles
        viewModel.processPickedFiles(listOf(mockk(relaxed = true)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateManualName("row-1", "target-ok")
        testDispatcher.scheduler.advanceUntilIdle()

        val fakeRenamer = FakeDocumentRenamer(
            renameHandler = { _, name -> RenameOutcome.Success(name) }
        )

        viewModel.executeRename(fakeRenamer)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as RenameVideosUiState.Results
        assertEquals(1, state.batchResult.successCount)
        assertEquals(RenameStatus.Success, state.batchResult.results[0].status)
        assertEquals(1, fakeRenamer.renameCalls.size)
    }

    @Test
    fun test3_pickedRowCollisionRetriesToSuffix() = runTest {
        val viewModel = RenameVideosViewModel(mockRenameRepository, mockCleanupRepository, mockSettingsRepository, testDispatcher)
        val initialFiles = listOf(
            RenameFileRowState(
                id = "row-1",
                uri = "content://picked/file1.mkv",
                displayName = "file1.mkv",
                sizeBytes = 100L,
                parentKey = "picked-session",
                parentKnown = true,
                supportsRename = true,
                existingNamesInParent = emptyList(),
                isChecked = true,
                originalBaseName = "file1",
                extension = ".mkv",
                newBaseName = "file1",
                decision = RenameDecision.NO_CHANGE,
                targetName = "file1.mkv",
                isPickedFile = true
            )
        )
        every { mockRenameRepository.scanPickedFiles(any()) } returns initialFiles
        viewModel.processPickedFiles(listOf(mockk(relaxed = true)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateManualName("row-1", "target")
        testDispatcher.scheduler.advanceUntilIdle()

        val fakeRenamer = FakeDocumentRenamer(
            renameHandler = { _, name ->
                if (name == "target.mkv") {
                    RenameOutcome.Failure("Already taken")
                } else {
                    RenameOutcome.Success(name)
                }
            }
        )

        viewModel.executeRename(fakeRenamer)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as RenameVideosUiState.Results
        val res = state.batchResult.results[0]

        // Verify suffix (1) was correctly appended upon collision (never-overwrite)
        assertEquals("target (1).mkv", res.newName)
    }

    @Test
    fun test4_pickedRowProviderReturnsDifferentOrOldName() = runTest {
        val viewModel = RenameVideosViewModel(mockRenameRepository, mockCleanupRepository, mockSettingsRepository, testDispatcher)
        val initialFiles = listOf(
            RenameFileRowState(
                id = "row-1",
                uri = "content://picked/file1.mkv",
                displayName = "file1.mkv",
                sizeBytes = 100L,
                parentKey = "picked-session",
                parentKnown = true,
                supportsRename = true,
                existingNamesInParent = emptyList(),
                isChecked = true,
                originalBaseName = "file1",
                extension = ".mkv",
                newBaseName = "file1",
                decision = RenameDecision.NO_CHANGE,
                targetName = "file1.mkv",
                isPickedFile = true
            )
        )
        every { mockRenameRepository.scanPickedFiles(any()) } returns initialFiles
        viewModel.processPickedFiles(listOf(mockk(relaxed = true)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateManualName("row-1", "target")
        testDispatcher.scheduler.advanceUntilIdle()

        var callCount = 0
        val fakeRenamer = FakeDocumentRenamer(
            renameHandler = { _, name ->
                callCount++
                if (callCount == 1) {
                    // Try 1: provider no-ops and returns oldName
                    RenameOutcome.Success("file1.mkv")
                } else {
                    // Try 2: target (1).mkv -> provider returns target (1) suffixed
                    RenameOutcome.Success(name)
                }
            }
        )

        viewModel.executeRename(fakeRenamer)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as RenameVideosUiState.Results
        assertEquals("target (1).mkv", state.batchResult.results[0].newName)
        assertEquals(2, fakeRenamer.renameCalls.size)
    }

    @Test
    fun test5_pickedRowFailsToMaxRetries() = runTest {
        val viewModel = RenameVideosViewModel(mockRenameRepository, mockCleanupRepository, mockSettingsRepository, testDispatcher)
        val initialFiles = listOf(
            RenameFileRowState(
                id = "row-1",
                uri = "content://picked/file1.mkv",
                displayName = "file1.mkv",
                sizeBytes = 100L,
                parentKey = "picked-session",
                parentKnown = true,
                supportsRename = true,
                existingNamesInParent = emptyList(),
                isChecked = true,
                originalBaseName = "file1",
                extension = ".mkv",
                newBaseName = "file1",
                decision = RenameDecision.NO_CHANGE,
                targetName = "file1.mkv",
                isPickedFile = true
            )
        )
        every { mockRenameRepository.scanPickedFiles(any()) } returns initialFiles
        viewModel.processPickedFiles(listOf(mockk(relaxed = true)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateManualName("row-1", "target")
        testDispatcher.scheduler.advanceUntilIdle()

        val fakeRenamer = FakeDocumentRenamer(
            renameHandler = { _, _ -> RenameOutcome.Failure("Always locked") }
        )

        viewModel.executeRename(fakeRenamer)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as RenameVideosUiState.Results
        assertEquals(0, state.batchResult.successCount)
        assertEquals(1, state.batchResult.failedCount)
        // 1 initial try + 50 retry suffixes = 51 calls total
        assertEquals(51, fakeRenamer.renameCalls.size)
    }

    @Test
    fun test6_cancelMidBatch() = runTest {
        val viewModel = RenameVideosViewModel(mockRenameRepository, mockCleanupRepository, mockSettingsRepository, testDispatcher)
        val initialFiles = listOf(
            RenameFileRowState(
                id = "row-1",
                uri = "content://folder/file1.mkv",
                displayName = "file1.mkv",
                sizeBytes = 100L,
                parentKey = "parent-1",
                parentKnown = true,
                supportsRename = true,
                existingNamesInParent = emptyList(),
                isChecked = true,
                originalBaseName = "file1",
                extension = ".mkv",
                newBaseName = "file1",
                decision = RenameDecision.NO_CHANGE,
                targetName = "file1.mkv",
                isPickedFile = false
            ),
            RenameFileRowState(
                id = "row-2",
                uri = "content://folder/file2.mkv",
                displayName = "file2.mkv",
                sizeBytes = 200L,
                parentKey = "parent-1",
                parentKnown = true,
                supportsRename = true,
                existingNamesInParent = emptyList(),
                isChecked = true,
                originalBaseName = "file2",
                extension = ".mkv",
                newBaseName = "file2",
                decision = RenameDecision.NO_CHANGE,
                targetName = "file2.mkv",
                isPickedFile = false
            ),
            RenameFileRowState(
                id = "row-3",
                uri = "content://folder/file3.mkv",
                displayName = "file3.mkv",
                sizeBytes = 300L,
                parentKey = "parent-1",
                parentKnown = true,
                supportsRename = true,
                existingNamesInParent = emptyList(),
                isChecked = true,
                originalBaseName = "file3",
                extension = ".mkv",
                newBaseName = "file3",
                decision = RenameDecision.NO_CHANGE,
                targetName = "file3.mkv",
                isPickedFile = false
            )
        )
        every { mockRenameRepository.scanFolder(any(), any(), any()) } returns com.splitandmerge.mkvslice.domain.rename.VideoScanner.ScanResult(initialFiles, false)
        viewModel.processPickedFolder(mockk(relaxed = true), false)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateManualName("row-1", "new-file1")
        viewModel.updateManualName("row-2", "new-file2")
        viewModel.updateManualName("row-3", "new-file3")
        testDispatcher.scheduler.advanceUntilIdle()

        // Get the active job from viewModel and cancel on row-2 rename call
        var activeJob: Job? = null
        val fakeRenamer = FakeDocumentRenamer(
            renameHandler = { uri, name ->
                if (uri == "content://folder/file2.mkv") {
                    activeJob?.cancel()
                }
                RenameOutcome.Success(name)
            }
        )

        viewModel.executeRename(fakeRenamer)
        // Set activeJob reference
        val vmClass = viewModel.javaClass
        val field = vmClass.getDeclaredField("renameJob")
        field.isAccessible = true
        activeJob = field.get(viewModel) as? Job

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as RenameVideosUiState.Results
        val results = state.batchResult.results

        // Row 1 succeeded. Row 2 was cancelled.
        assertTrue(results.size in 1..2)
        assertEquals("content://folder/file1.mkv", results[0].uri)
        assertEquals(RenameStatus.Success, results[0].status)
    }

    @Test
    fun test7_countsMixedBatch() = runTest {
        val viewModel = RenameVideosViewModel(mockRenameRepository, mockCleanupRepository, mockSettingsRepository, testDispatcher)
        val initialFiles = listOf(
            RenameFileRowState(
                id = "row-1",
                uri = "content://folder/file1.mkv",
                displayName = "file1.mkv",
                sizeBytes = 100L,
                parentKey = "parent-1",
                parentKnown = true,
                supportsRename = true,
                existingNamesInParent = emptyList(),
                isChecked = true,
                originalBaseName = "file1",
                extension = ".mkv",
                newBaseName = "file1",
                decision = RenameDecision.NO_CHANGE,
                targetName = "file1.mkv",
                isPickedFile = false
            ),
            RenameFileRowState(
                id = "row-2",
                uri = "content://folder/file2.mkv",
                displayName = "file2.mkv",
                sizeBytes = 200L,
                parentKey = "parent-1",
                parentKnown = true,
                supportsRename = true,
                existingNamesInParent = emptyList(),
                isChecked = true,
                originalBaseName = "file2",
                extension = ".mkv",
                newBaseName = "file2",
                decision = RenameDecision.NO_CHANGE,
                targetName = "file2.mkv",
                isPickedFile = false
            ),
            RenameFileRowState(
                id = "row-3",
                uri = "content://folder/file3.mkv",
                displayName = "file3.mkv",
                sizeBytes = 300L,
                parentKey = "parent-1",
                parentKnown = true,
                supportsRename = false, // TOCTOU check fails
                existingNamesInParent = emptyList(),
                isChecked = true,
                originalBaseName = "file3",
                extension = ".mkv",
                newBaseName = "file3",
                decision = RenameDecision.NO_CHANGE,
                targetName = "file3.mkv",
                isPickedFile = false
            )
        )
        every { mockRenameRepository.scanFolder(any(), any(), any()) } returns com.splitandmerge.mkvslice.domain.rename.VideoScanner.ScanResult(initialFiles, false)
        viewModel.processPickedFolder(mockk(relaxed = true), false)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateManualName("row-1", "target1")
        viewModel.updateManualName("row-2", "target2")
        viewModel.updateManualName("row-3", "target3")
        testDispatcher.scheduler.advanceUntilIdle()

        val fakeRenamer = FakeDocumentRenamer(
            renameHandler = { uri, name ->
                if (uri == "content://folder/file1.mkv") {
                    RenameOutcome.Success(name)
                } else {
                    RenameOutcome.Failure("Already exists")
                }
            },
            supportsRenameHandler = { uri ->
                uri != "content://folder/file3.mkv"
            }
        )

        viewModel.executeRename(fakeRenamer)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as RenameVideosUiState.Results
        val batch = state.batchResult
        println("DEBUG TEST7 BATCH RESULTS: " + batch.results)
        assertEquals(1, batch.successCount)
        assertEquals(1, batch.failedCount)
        assertEquals(1, batch.excludedCount)
        assertEquals(0, batch.skippedCount)
    }

    @Test
    fun test8_pickedRowPermissionErrorFailFast() = runTest {
        val viewModel = RenameVideosViewModel(mockRenameRepository, mockCleanupRepository, mockSettingsRepository, testDispatcher)
        val initialFiles = listOf(
            RenameFileRowState(
                id = "row-1",
                uri = "content://picked/file1.mkv",
                displayName = "file1.mkv",
                sizeBytes = 100L,
                parentKey = "picked-session",
                parentKnown = true,
                supportsRename = true,
                existingNamesInParent = emptyList(),
                isChecked = true,
                originalBaseName = "file1",
                extension = ".mkv",
                newBaseName = "file1",
                decision = RenameDecision.NO_CHANGE,
                targetName = "file1.mkv",
                isPickedFile = true
            )
        )
        every { mockRenameRepository.scanPickedFiles(any()) } returns initialFiles
        viewModel.processPickedFiles(listOf(mockk(relaxed = true)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateManualName("row-1", "target")
        testDispatcher.scheduler.advanceUntilIdle()

        val fakeRenamer = FakeDocumentRenamer(
            renameHandler = { _, _ -> RenameOutcome.Failure("Permission Denial", isPermissionError = true) }
        )

        viewModel.executeRename(fakeRenamer)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as RenameVideosUiState.Results
        val batch = state.batchResult
        assertEquals(0, batch.successCount)
        assertEquals(1, batch.failedCount)
        assertEquals(RenameStatus.Failed("Permission Denial"), batch.results[0].status)
        // Assert rename was called exactly once (no 50x suffix retry)
        assertEquals(1, fakeRenamer.renameCalls.size)
    }
}
