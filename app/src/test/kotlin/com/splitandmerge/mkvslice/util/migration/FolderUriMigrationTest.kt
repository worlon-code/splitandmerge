package com.splitandmerge.mkvslice.util.migration

import android.content.SharedPreferences
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.data.settings.SettingsState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FolderUriMigrationTest {

    private val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    @Test
    fun `migrates legacy folder URI to DataStore when DataStore is blank`() = runTest {
        every { sharedPreferences.getString("defaultOutputFolder", "") } returns "content://legacy_folder"
        every { settingsRepository.settingsFlow } returns flowOf(SettingsState(defaultOutputFolderUri = ""))

        FolderUriMigration.run(sharedPreferences, settingsRepository)

        coVerify { settingsRepository.setDefaultOutputFolderUri("content://legacy_folder") }
        verify { sharedPreferences.edit().remove("defaultOutputFolder").apply() }
    }

    @Test
    fun `does not overwrite existing DataStore value when already set`() = runTest {
        every { sharedPreferences.getString("defaultOutputFolder", "") } returns "content://legacy_folder"
        every { settingsRepository.settingsFlow } returns flowOf(
            SettingsState(defaultOutputFolderUri = "content://already_set")
        )

        FolderUriMigration.run(sharedPreferences, settingsRepository)

        coVerify(exactly = 0) { settingsRepository.setDefaultOutputFolderUri(any()) }
        // Legacy key is still removed (idempotent cleanup even when no write occurred).
        verify { sharedPreferences.edit().remove("defaultOutputFolder").apply() }
    }

    @Test
    fun `is no-op when legacy key is absent or blank`() = runTest {
        every { sharedPreferences.getString("defaultOutputFolder", "") } returns ""

        FolderUriMigration.run(sharedPreferences, settingsRepository)

        coVerify(exactly = 0) { settingsRepository.setDefaultOutputFolderUri(any()) }
        verify(exactly = 0) { sharedPreferences.edit() }
    }

    @Test
    fun `subsequent call after key removed is a no-op`() = runTest {
        // Simulate: key was already removed on a prior launch.
        every { sharedPreferences.getString("defaultOutputFolder", "") } returns null

        FolderUriMigration.run(sharedPreferences, settingsRepository)

        coVerify(exactly = 0) { settingsRepository.setDefaultOutputFolderUri(any()) }
        verify(exactly = 0) { sharedPreferences.edit() }
    }
}
