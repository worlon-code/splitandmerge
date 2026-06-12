package com.splitandmerge.mkvslice.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ThemeMode {
    LIGHT,
    DARK,
    AMOLED,
    DYNAMIC
}

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.DYNAMIC,
    val defaultCapGb: Float = 9.0f,
    val defaultOutputFolder: String = "/storage/emulated/0/Movies",
    val improveReliability: Boolean = false,
    val keepScreenOn: Boolean = false,
    val currentVersion: String = "0.0.1",
    val updateAvailable: Boolean = false,
    val checkingForUpdates: Boolean = false,
    val updateMessage: String = "App is up to date"
)

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun updateTheme(mode: ThemeMode) {
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun updateCapGb(cap: Float) {
        _state.value = _state.value.copy(defaultCapGb = cap)
    }

    fun updateOutputFolder(folder: String) {
        _state.value = _state.value.copy(defaultOutputFolder = folder)
    }

    fun toggleReliability(enabled: Boolean) {
        _state.value = _state.value.copy(improveReliability = enabled)
    }

    fun toggleKeepScreenOn(enabled: Boolean) {
        _state.value = _state.value.copy(keepScreenOn = enabled)
    }

    fun checkForUpdates() {
        _state.value = _state.value.copy(checkingForUpdates = true, updateMessage = "Checking...")
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            _state.value = _state.value.copy(
                checkingForUpdates = false,
                updateAvailable = false,
                updateMessage = "You have the latest version (v0.0.1)."
            )
        }
    }
}
