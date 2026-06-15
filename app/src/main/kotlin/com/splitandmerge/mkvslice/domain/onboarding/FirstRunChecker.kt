package com.splitandmerge.mkvslice.domain.onboarding

import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Determines whether the user still needs to pick a default output folder.
 *
 * "First run" is defined purely as: defaultOutputFolderUri is blank.
 * Once the folder is saved the flow emits false and any blocking UI auto-dismisses.
 *
 * No separate "has-onboarded" flag is needed — folder presence IS the flag.
 */
@Singleton
class FirstRunChecker @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    /** Hot flow: emits true while no default folder is set, false once it is. */
    val isFirstRunFlow: Flow<Boolean> =
        settingsRepository.settingsFlow.map { it.defaultOutputFolderUri.isBlank() }

    /** Suspend helper for one-shot checks outside a compose context. */
    suspend fun isFirstRun(): Boolean =
        settingsRepository.settingsFlow.first().defaultOutputFolderUri.isBlank()
}
