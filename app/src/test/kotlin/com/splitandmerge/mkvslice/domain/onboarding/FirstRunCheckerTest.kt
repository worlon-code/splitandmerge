package com.splitandmerge.mkvslice.domain.onboarding

import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.data.settings.SettingsState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunCheckerTest {

    private val settingsRepository = mockk<SettingsRepository>()

    @Test
    fun `isFirstRun returns true when defaultOutputFolderUri is blank`() = runTest {
        every { settingsRepository.settingsFlow } returns
                flowOf(SettingsState(defaultOutputFolderUri = ""))

        val checker = FirstRunChecker(settingsRepository)

        assertTrue(checker.isFirstRun())
    }

    @Test
    fun `isFirstRun returns false when defaultOutputFolderUri is set`() = runTest {
        every { settingsRepository.settingsFlow } returns
                flowOf(SettingsState(defaultOutputFolderUri = "content://some_folder"))

        val checker = FirstRunChecker(settingsRepository)

        assertFalse(checker.isFirstRun())
    }

    @Test
    fun `isFirstRunFlow emits true when folder is blank`() = runTest {
        every { settingsRepository.settingsFlow } returns
                flowOf(SettingsState(defaultOutputFolderUri = ""))

        val checker = FirstRunChecker(settingsRepository)
        val values = checker.isFirstRunFlow.toList()

        assertTrue(values.single())
    }

    @Test
    fun `isFirstRunFlow emits false when folder is set`() = runTest {
        every { settingsRepository.settingsFlow } returns
                flowOf(SettingsState(defaultOutputFolderUri = "content://some_folder"))

        val checker = FirstRunChecker(settingsRepository)
        val values = checker.isFirstRunFlow.toList()

        assertFalse(values.single())
    }

    @Test
    fun `isFirstRunFlow transitions from true to false when folder is picked`() = runTest {
        // Simulate: initial blank → then folder set (two emissions)
        every { settingsRepository.settingsFlow } returns
                flowOf(
                    SettingsState(defaultOutputFolderUri = ""),
                    SettingsState(defaultOutputFolderUri = "content://my_folder")
                )

        val checker = FirstRunChecker(settingsRepository)
        val values = checker.isFirstRunFlow.toList()

        // First emission: folder blank → true; Second: folder set → false.
        assertTrue(values[0])
        assertFalse(values[1])
    }
}
