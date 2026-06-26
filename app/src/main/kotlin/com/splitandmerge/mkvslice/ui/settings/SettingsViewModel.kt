package com.splitandmerge.mkvslice.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.BuildConfig
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.data.settings.ThemeMode
import com.splitandmerge.mkvslice.data.update.UpdateRepository
import com.splitandmerge.mkvslice.data.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import android.net.Uri
import android.content.Intent

import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidator
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidation

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.DYNAMIC,
    val defaultCapGb: Float = 9.0f,
    val defaultOutputFolder: String = "",
    val improveReliability: Boolean = true,
    val keepScreenOn: Boolean = false,
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val updateAvailable: Boolean = false,
    val checkingForUpdates: Boolean = false,
    val updateMessage: String = "App is up to date"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val outputFolderValidator: OutputFolderValidator,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    val updateState: StateFlow<UpdateState> = updateRepository.state

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(false)
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()

    private val _validationResult = MutableStateFlow<OutputFolderValidation?>(null)
    val validationResult: StateFlow<OutputFolderValidation?> = _validationResult.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collectLatest { repoState ->
                _state.value = _state.value.copy(
                    themeMode = repoState.themeMode,
                    defaultCapGb = repoState.defaultCapGb.toFloat(),
                    defaultOutputFolder = repoState.defaultOutputFolderUri,
                    improveReliability = repoState.improveReliability,
                    keepScreenOn = repoState.keepScreenOn
                )
            }
        }
        viewModelScope.launch {
            updateRepository.state.collectLatest { updateState ->
                val checking = updateState is UpdateState.Checking
                val available = updateState is UpdateState.Available
                
                val message = when (updateState) {
                    is UpdateState.Idle -> ""
                    is UpdateState.Checking -> "Checking…"
                    is UpdateState.UpToDate -> "You have the latest version (v${BuildConfig.VERSION_NAME})."
                    is UpdateState.Available -> "Update v${updateState.versionName} available."
                    is UpdateState.Downloading -> {
                        val pct = (updateState.progress * 100).toInt()
                        "Downloading… $pct%"
                    }
                    is UpdateState.Verifying -> "Verifying…"
                    is UpdateState.NeedsInstallPermission -> "Install permission required."
                    is UpdateState.ReadyToInstall -> "Ready to install."
                    is UpdateState.Installing -> "Installing update…"
                    is UpdateState.Installed -> "Installation complete."
                    is UpdateState.Error -> updateState.reason
                }

                _state.value = _state.value.copy(
                    checkingForUpdates = checking,
                    updateAvailable = available,
                    updateMessage = message
                )
            }
        }
        checkBatteryOptimizations()
    }

    fun checkBatteryOptimizations() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        _isIgnoringBatteryOptimizations.value = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    fun updateTheme(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun updateCapGb(cap: Float) {
        viewModelScope.launch {
            settingsRepository.setDefaultCapGb(cap.toDouble())
        }
    }

    fun updateOutputFolder(folder: String) {
        val needed = 1024L * 1024L * 1024L
        val result = outputFolderValidator.validate(folder, needed, assumePermissionPersisted = false)
        _validationResult.value = result
        if (result is OutputFolderValidation.Ok) {
            try {
                val uri = Uri.parse(folder)
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                // ignore
            }
            viewModelScope.launch {
                settingsRepository.setDefaultOutputFolderUri(folder)
            }
            _validationResult.value = null
        }
    }

    fun dismissValidation() {
        _validationResult.value = null
    }

    fun onPickFolderAgain() {
        _validationResult.value = null
    }

    fun toggleReliability(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setImproveReliability(enabled)
        }
    }

    fun toggleKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKeepScreenOn(enabled)
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            updateRepository.checkForUpdate()
        }
    }

    fun downloadUpdate(available: UpdateState.Available) {
        viewModelScope.launch {
            updateRepository.downloadUpdate(available)
        }
    }

    fun checkInstallPermission() {
        updateRepository.checkInstallPermissionAfterGrant()
    }

    fun launchInstallSettings() {
        updateRepository.launchInstallSettings()
    }

    fun installUpdate() {
        viewModelScope.launch {
            updateRepository.installUpdate()
        }
    }
}
