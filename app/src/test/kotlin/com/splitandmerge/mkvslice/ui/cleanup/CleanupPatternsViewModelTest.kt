package com.splitandmerge.mkvslice.ui.cleanup

import com.splitandmerge.mkvslice.data.db.entity.CleanupPatternEntity
import com.splitandmerge.mkvslice.data.repository.CleanupRepository
import com.splitandmerge.mkvslice.domain.cleanup.DEFAULT_CLEANUP_PATTERNS
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CleanupPatternsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockRepository: CleanupRepository
    private val patternsFlow = MutableStateFlow(DEFAULT_CLEANUP_PATTERNS)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockRepository = mockk(relaxed = true)
        every { mockRepository.observePatterns() } returns patternsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_init_observesRepository() = runTest {
        val vm = CleanupPatternsViewModel(mockRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(12, vm.state.value.patterns.size)
    }

    @Test
    fun test_togglePattern_callsRepository() = runTest {
        val vm = CleanupPatternsViewModel(mockRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.togglePattern("url_prefix", false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockRepository.upsert(match { it.id == "url_prefix" && !it.enabled }) }
    }

    @Test
    fun test_addPattern_callsRepository() = runTest {
        val vm = CleanupPatternsViewModel(mockRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.addPattern("test-regex", "test-label")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockRepository.upsert(match { it.regex == "test-regex" && it.label == "test-label" && !it.isBuiltIn }) }
    }

    @Test
    fun test_deletePattern_callsRepository() = runTest {
        val vm = CleanupPatternsViewModel(mockRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val customRule = CleanupPatternEntity(
            id = "custom-1",
            regex = "rule",
            replacement = "",
            enabled = true,
            isBuiltIn = false,
            orderIndex = 12,
            label = "Custom",
            createdAt = 12345L
        )
        patternsFlow.value = DEFAULT_CLEANUP_PATTERNS + customRule
        testDispatcher.scheduler.advanceUntilIdle()

        vm.deletePattern("custom-1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockRepository.delete("custom-1") }
    }

    @Test
    fun test_deletePattern_builtIn_doesNotCallRepository() = runTest {
        val vm = CleanupPatternsViewModel(mockRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.deletePattern("url_prefix")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { mockRepository.delete("url_prefix") }
    }
}
