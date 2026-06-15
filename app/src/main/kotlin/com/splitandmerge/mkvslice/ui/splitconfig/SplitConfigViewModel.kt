package com.splitandmerge.mkvslice.ui.splitconfig

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.domain.cleanup.TitleCleaner
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.SplitMode
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidation
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidator
import com.splitandmerge.mkvslice.service.JobService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SplitConfigState(
    val uri: String = "",
    val filename: String = "",
    val mode: SplitMode = SplitMode.BOTH,
    val partsCount: Int = 3,
    val sizeCapGb: Float = 9.0f,
    val baseName: String = "",
    val outputFolder: String = "",
    val fileSizeBytes: Long = 0L,
    val fileDurationSec: Double = 0.0,
    val predictedPartCount: Int = 3,
    val predictedPartSizeGb: Float = 0f
)

@HiltViewModel
class SplitConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jobDao: JobDao,
    private val titleCleaner: TitleCleaner,
    private val settingsRepository: SettingsRepository,
    private val outputFolderValidator: OutputFolderValidator,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _validationResult = MutableStateFlow<OutputFolderValidation?>(null)
    val validationResult: StateFlow<OutputFolderValidation?> = _validationResult.asStateFlow()

    private val _state = MutableStateFlow(SplitConfigState())
    val state: StateFlow<SplitConfigState> = _state.asStateFlow()

    init {
        val uri = savedStateHandle.get<String>("uri") ?: ""
        val filename = savedStateHandle.get<String>("filename") ?: "unknown.mkv"
        val sizeBytesStr = savedStateHandle.get<String>("sizeBytes") ?: "0"
        val durationSecStr = savedStateHandle.get<String>("durationSec") ?: "0.0"

        val sizeBytes = sizeBytesStr.toLongOrNull() ?: 0L
        val durationSec = durationSecStr.toDoubleOrNull() ?: 0.0

        val baseName = titleCleaner.cleanTitle(filename)

        _state.value = SplitConfigState(
            uri = uri,
            filename = filename,
            baseName = baseName,
            outputFolder = "", // DataStore flow seeds this immediately below
            fileSizeBytes = sizeBytes,
            fileDurationSec = durationSec
        )
        recalculatePredictions()

        // Keep outputFolder in sync with the single source of truth (DataStore).
        // When the user updates the folder here, we write to DataStore, which
        // re-emits here with the new value — the local copy stays consistent.
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _state.value = _state.value.copy(outputFolder = settings.defaultOutputFolderUri)
            }
        }
    }

    fun updateMode(mode: SplitMode) {
        _state.value = _state.value.copy(mode = mode)
        recalculatePredictions()
    }

    fun updatePartsCount(parts: Int) {
        _state.value = _state.value.copy(partsCount = parts)
        recalculatePredictions()
    }

    fun updateSizeCap(sizeGb: Float) {
        _state.value = _state.value.copy(sizeCapGb = sizeGb)
        recalculatePredictions()
    }

    fun updateBaseName(name: String) {
        _state.value = _state.value.copy(baseName = name)
    }

    fun updateOutputFolder(folder: String) {
        val needed = _state.value.fileSizeBytes
        val result = outputFolderValidator.validate(folder, needed, assumePermissionPersisted = false)
        _validationResult.value = result
        if (result is OutputFolderValidation.Ok) {
            try {
                val uri = Uri.parse(folder)
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                // ignore — permission take may fail for some URIs; validation already passed
            }
            // Update local state immediately for responsive UI; DataStore write
            // will re-emit the same value through the collector above.
            _state.value = _state.value.copy(outputFolder = folder)
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

    private fun recalculatePredictions() {
        val currentMode = _state.value.mode
        val parts = _state.value.partsCount
        val cap = _state.value.sizeCapGb
        val totalSizeGb = _state.value.fileSizeBytes.toFloat() / (1024f * 1024f * 1024f)

        val actualSizeGb = if (totalSizeGb > 0f) totalSizeGb else 0.1f // prevent div by zero

        val (predCount, predSize) = when (currentMode) {
            SplitMode.EXACT_PARTS -> Pair(parts, actualSizeGb / parts)
            SplitMode.SIZE_CAP_ONLY -> {
                val count = Math.ceil((actualSizeGb / cap).toDouble()).toInt().coerceAtLeast(1)
                Pair(count, actualSizeGb / count)
            }
            SplitMode.BOTH -> {
                val countFromCap = Math.ceil((actualSizeGb / cap).toDouble()).toInt()
                val count = Math.max(parts, countFromCap).coerceAtLeast(1)
                Pair(count, actualSizeGb / count)
            }
        }
        _state.value = _state.value.copy(
            predictedPartCount = predCount,
            predictedPartSizeGb = predSize
        )
    }

    fun startSplitJob(context: Context): String {
        val jobId = UUID.randomUUID().toString()
        val currentState = _state.value

        viewModelScope.launch {
            val jobEntity = JobEntity(
                id = jobId,
                type = JobType.SPLIT,
                status = JobStatus.QUEUED,
                sourceUri = currentState.uri,
                outputBaseName = currentState.baseName,
                outputDirUri = currentState.outputFolder.ifEmpty { "content://unknown" },
                outputContainer = ".mkv",
                mode = currentState.mode,
                requestedParts = currentState.partsCount,
                targetCapBytes = (currentState.sizeCapGb * 1024L * 1024L * 1024L).toLong(),
                progressPct = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            jobDao.upsert(jobEntity)

            // Start Foreground Service
            val intent = Intent(context, JobService::class.java)
            context.startForegroundService(intent)
        }

        return jobId
    }
}
