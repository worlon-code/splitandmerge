package com.splitandmerge.mkvslice.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun testDataStorePersistence() = runTest {
        val dataStoreFile = File(tmpFolder.newFolder(), "test_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { dataStoreFile }
        )
        val repository = DataStoreSettingsRepository(dataStore)

        // Default values
        val initial = repository.settingsFlow.first()
        assertEquals(ThemeMode.DYNAMIC, initial.themeMode)
        assertEquals(9.0, initial.defaultCapGb, 0.0)
        assertTrue(initial.improveReliability)

        // Write theme mode
        repository.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repository.settingsFlow.first().themeMode)

        // Write cap size
        repository.setDefaultCapGb(15.5)
        assertEquals(15.5, repository.settingsFlow.first().defaultCapGb, 0.0)

        // Write improve reliability
        repository.setImproveReliability(false)
        assertEquals(false, repository.settingsFlow.first().improveReliability)

        // Write keep screen on
        repository.setKeepScreenOn(true)
        assertEquals(true, repository.settingsFlow.first().keepScreenOn)

        // Write default folder uri
        repository.setDefaultOutputFolderUri("content://test_folder")
        assertEquals("content://test_folder", repository.settingsFlow.first().defaultOutputFolderUri)
    }
}
