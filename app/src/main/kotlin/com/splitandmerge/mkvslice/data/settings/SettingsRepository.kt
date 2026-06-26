package com.splitandmerge.mkvslice.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsRepository {
    val settingsFlow: Flow<SettingsState>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDefaultCapGb(capGb: Double)
    suspend fun setImproveReliability(improve: Boolean)
    suspend fun setKeepScreenOn(keep: Boolean)
    suspend fun setDefaultOutputFolderUri(uri: String)
    suspend fun setLastOfferedVersionCode(versionCode: Int)
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_CAP_GB = doublePreferencesKey("default_cap_gb")
        val IMPROVE_RELIABILITY = booleanPreferencesKey("improve_reliability")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val DEFAULT_OUTPUT_FOLDER_URI = stringPreferencesKey("default_output_folder_uri")
        val LAST_OFFERED_VERSION_CODE = intPreferencesKey("last_offered_version_code")
    }

    override val settingsFlow: Flow<SettingsState> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeModeStr = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DYNAMIC.name
            val themeMode = try {
                ThemeMode.valueOf(themeModeStr)
            } catch (e: IllegalArgumentException) {
                ThemeMode.DYNAMIC
            }
            SettingsState(
                themeMode = themeMode,
                defaultCapGb = preferences[PreferencesKeys.DEFAULT_CAP_GB] ?: 9.0,
                improveReliability = preferences[PreferencesKeys.IMPROVE_RELIABILITY] ?: true,
                keepScreenOn = preferences[PreferencesKeys.KEEP_SCREEN_ON] ?: false,
                defaultOutputFolderUri = preferences[PreferencesKeys.DEFAULT_OUTPUT_FOLDER_URI] ?: "",
                lastOfferedVersionCode = preferences[PreferencesKeys.LAST_OFFERED_VERSION_CODE] ?: 0
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    override suspend fun setDefaultCapGb(capGb: Double) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_CAP_GB] = capGb
        }
    }

    override suspend fun setImproveReliability(improve: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IMPROVE_RELIABILITY] = improve
        }
    }

    override suspend fun setKeepScreenOn(keep: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_SCREEN_ON] = keep
        }
    }

    override suspend fun setDefaultOutputFolderUri(uri: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_OUTPUT_FOLDER_URI] = uri
        }
    }

    override suspend fun setLastOfferedVersionCode(versionCode: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_OFFERED_VERSION_CODE] = versionCode
        }
    }
}
