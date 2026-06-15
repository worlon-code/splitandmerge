package com.splitandmerge.mkvslice.util.migration

import android.content.SharedPreferences
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * One-shot migration: copies the legacy SharedPreferences key "defaultOutputFolder"
 * into DataStore (SettingsRepository) if DataStore is still blank.
 *
 * Rules:
 * - Runs once; after the first successful run the legacy key is deleted.
 * - Idempotent: if the legacy key is already absent, it is a no-op.
 * - Does NOT overwrite an existing DataStore value.
 * - Call from App.onCreate via appScope.launch(Dispatchers.IO).
 */
object FolderUriMigration {

    private const val TAG = "FolderMigration"
    private const val LEGACY_KEY = "defaultOutputFolder"

    suspend fun run(
        sharedPreferences: SharedPreferences,
        settingsRepository: SettingsRepository
    ) {
        val legacy = sharedPreferences.getString(LEGACY_KEY, "") ?: ""
        if (legacy.isBlank()) {
            // Nothing to migrate — either new install or already migrated.
            return
        }

        val currentInDataStore = settingsRepository.settingsFlow.first().defaultOutputFolderUri
        if (currentInDataStore.isBlank()) {
            Timber.tag(TAG).i("Migrating legacy folder URI → DataStore")
            settingsRepository.setDefaultOutputFolderUri(legacy)
        } else {
            Timber.tag(TAG).d("DataStore already has a folder; skipping overwrite")
        }

        // Always remove the legacy key so this branch never runs again.
        sharedPreferences.edit().remove(LEGACY_KEY).apply()
        Timber.tag(TAG).i("Legacy folder URI migration complete")
    }
}
