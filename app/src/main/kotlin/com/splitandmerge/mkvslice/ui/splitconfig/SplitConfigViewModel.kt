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

enum class SizeUnit { MB, GB }

data class SplitConfigState(
    val uri: String = "",
    val filename: String = "",
    val mode: SplitMode = SplitMode.BOTH,
    val partsCount: Int = 3,
    val sizeCapGb: Float = 9.0f,
    val byteSizeCapInput: String = "100",
    val byteSplitSizeUnit: SizeUnit = SizeUnit.MB,
    val baseName: String = "",
    val outputFolder: String = "",
    val fileSizeBytes: Long = 0L,
    val fileDurationSec: Double = 0.0,
    val predictedPartCount: Int = 3,
    val predictedPartSizeGb: Float = 0f,
    val isByteSplit: Boolean = false
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

        // Seed outputFolder and sizeCapGb from DataStore.
        // sizeCapGb is seeded only on the FIRST emission so a manual user edit is not
        // overwritten by a subsequent DataStore re-emission.  outputFolder always stays
        // in sync because its source of truth is DataStore.
        viewModelScope.launch {
            var firstEmission = true
            settingsRepository.settingsFlow.collect { settings ->
                _state.value = if (firstEmission) {
                    firstEmission = false
                    _state.value.copy(
                        outputFolder = settings.defaultOutputFolderUri,
                        sizeCapGb    = settings.defaultCapGb.toFloat()
                    ).also { recalculatePredictions() }
                } else {
                    _state.value.copy(outputFolder = settings.defaultOutputFolderUri)
                }
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
        val currentState = _state.value
        val currentMode = currentState.mode
        val parts = currentState.partsCount
        val capGb = currentState.sizeCapGb
        val isByte = currentState.isByteSplit
        val fileSizeBytes = currentState.fileSizeBytes
        val fileSizeBytesBd = java.math.BigDecimal(fileSizeBytes)

        var predCount = 3
        var predSize = 0f

        if (isByte) {
            when (currentMode) {
                SplitMode.EXACT_PARTS -> {
                    predCount = parts
                    if (parts > 0) {
                        val partsBd = java.math.BigDecimal(parts)
                        val sizeBytesBd = fileSizeBytesBd.divide(partsBd, 9, java.math.RoundingMode.HALF_UP)
                        val sizeGbBd = sizeBytesBd.divide(java.math.BigDecimal(1024L * 1024L * 1024L), 9, java.math.RoundingMode.HALF_UP)
                        predSize = sizeGbBd.toFloat()
                    }
                }
                else -> { // SIZE_CAP_ONLY
                    val capBytes = parseTargetCapBytes(currentState.byteSizeCapInput, currentState.byteSplitSizeUnit)
                    if (capBytes != null && capBytes > 0L) {
                        val capBytesBd = java.math.BigDecimal(capBytes)
                        val countBd = fileSizeBytesBd.divide(capBytesBd, 0, java.math.RoundingMode.CEILING)
                        predCount = countBd.toInt().coerceAtLeast(1)
                        val sizeBytesBd = fileSizeBytesBd.divide(java.math.BigDecimal(predCount), 9, java.math.RoundingMode.HALF_UP)
                        val sizeGbBd = sizeBytesBd.divide(java.math.BigDecimal(1024L * 1024L * 1024L), 9, java.math.RoundingMode.HALF_UP)
                        predSize = sizeGbBd.toFloat()
                    } else {
                        predCount = 0
                        predSize = 0f
                    }
                }
            }
        } else {
            val totalSizeGb = fileSizeBytes.toFloat() / (1024f * 1024f * 1024f)
            val actualSizeGb = if (totalSizeGb > 0f) totalSizeGb else 0.1f // prevent div by zero
            when (currentMode) {
                SplitMode.EXACT_PARTS -> {
                    predCount = parts
                    predSize = actualSizeGb / parts
                }
                SplitMode.SIZE_CAP_ONLY -> {
                    val count = Math.ceil((actualSizeGb / capGb).toDouble()).toInt().coerceAtLeast(1)
                    predCount = count
                    predSize = actualSizeGb / count
                }
                SplitMode.BOTH -> {
                    val countFromCap = Math.ceil((actualSizeGb / capGb).toDouble()).toInt()
                    val count = Math.max(parts, countFromCap).coerceAtLeast(1)
                    predCount = count
                    predSize = actualSizeGb / count
                }
            }
        }
        _state.value = _state.value.copy(
            predictedPartCount = predCount,
            predictedPartSizeGb = predSize
        )
    }

    fun updateByteSplit(isByteSplit: Boolean) {
        val currentMode = _state.value.mode
        val newMode = if (isByteSplit) {
            if (currentMode == SplitMode.BOTH) SplitMode.SIZE_CAP_ONLY else currentMode
        } else {
            currentMode
        }
        _state.value = _state.value.copy(
            isByteSplit = isByteSplit,
            mode = newMode
        )
        recalculatePredictions()
    }

    fun updateByteSizeCapInput(input: String) {
        _state.value = _state.value.copy(byteSizeCapInput = input)
        recalculatePredictions()
    }

    fun updateByteSplitSizeUnit(unit: SizeUnit) {
        _state.value = _state.value.copy(byteSplitSizeUnit = unit)
        recalculatePredictions()
    }

    private fun parseTargetCapBytes(input: String, unit: SizeUnit): Long? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed == ".") return null

        if (trimmed.contains("+") || trimmed.contains("-")) return null
        if (trimmed.contains("e", ignoreCase = true)) return null

        val commaCount = trimmed.count { it == ',' }
        val normalized = if (commaCount == 1) {
            trimmed.replace(',', '.')
        } else if (commaCount > 1) {
            return null
        } else {
            trimmed
        }

        val dotCount = normalized.count { it == '.' }
        if (dotCount > 1) return null

        return try {
            val bd = java.math.BigDecimal(normalized)
            val factor = if (unit == SizeUnit.GB) {
                1024L * 1024L * 1024L
            } else {
                1024L * 1024L
            }
            val resultBd = (bd * java.math.BigDecimal(factor))
                .setScale(0, java.math.RoundingMode.FLOOR)
            val bytes = resultBd.longValueExact()
            if (bytes <= 0L) null else bytes
        } catch (e: Exception) {
            null
        }
    }

    fun isByteSizeCapValid(): Boolean {
        val state = _state.value
        if (!state.isByteSplit || state.mode != SplitMode.SIZE_CAP_ONLY) return true
        return parseTargetCapBytes(state.byteSizeCapInput, state.byteSplitSizeUnit) != null
    }

    fun getByteSizeCapError(): String? {
        val state = _state.value
        if (!state.isByteSplit || state.mode != SplitMode.SIZE_CAP_ONLY) return null
        val input = state.byteSizeCapInput.trim()
        if (input.isEmpty()) return "Size cap cannot be empty"

        if (input.contains("+") || input.contains("-")) return "Signs (+ or -) are not allowed"
        if (input.contains("e", ignoreCase = true)) return "Scientific notation is not allowed"

        val commaCount = input.count { it == ',' }
        val normalized = if (commaCount == 1) {
            input.replace(',', '.')
        } else if (commaCount > 1) {
            return "Please enter a valid decimal number"
        } else {
            input
        }

        val dotCount = normalized.count { it == '.' }
        if (dotCount > 1) return "Please enter a valid decimal number"

        if (normalized == ".") return "Please enter digits"

        val bd = try {
            java.math.BigDecimal(normalized)
        } catch (e: Exception) {
            return "Please enter a valid decimal number"
        }

        val factor = if (state.byteSplitSizeUnit == SizeUnit.GB) {
            1024L * 1024L * 1024L
        } else {
            1024L * 1024L
        }

        return try {
            val resultBd = (bd * java.math.BigDecimal(factor))
                .setScale(0, java.math.RoundingMode.FLOOR)
            val bytes = resultBd.longValueExact()
            if (bytes <= 0L) {
                "Size must be greater than 0"
            } else {
                null
            }
        } catch (e: java.lang.ArithmeticException) {
            "Value is too large (overflows)"
        } catch (e: Exception) {
            "Please enter a valid decimal number"
        }
    }

    fun isConfigValid(): Boolean {
        val currentState = _state.value
        if (currentState.isByteSplit) {
            if (currentState.mode == SplitMode.SIZE_CAP_ONLY) {
                return isByteSizeCapValid()
            }
            if (currentState.mode == SplitMode.EXACT_PARTS) {
                return currentState.partsCount > 0
            }
        } else {
            if (currentState.mode == SplitMode.EXACT_PARTS) {
                return currentState.partsCount > 0
            }
            if (currentState.mode == SplitMode.SIZE_CAP_ONLY) {
                return currentState.sizeCapGb > 0
            }
            if (currentState.mode == SplitMode.BOTH) {
                return currentState.partsCount > 0 && currentState.sizeCapGb > 0
            }
        }
        return true
    }

    fun startSplitJob(context: Context): String {
        val jobId = UUID.randomUUID().toString()
        val currentState = _state.value

        if (!isConfigValid()) {
            return jobId
        }

        viewModelScope.launch {
            val targetCapBytes = if (currentState.isByteSplit) {
                if (currentState.mode == SplitMode.SIZE_CAP_ONLY) {
                    parseTargetCapBytes(currentState.byteSizeCapInput, currentState.byteSplitSizeUnit)
                } else null
            } else {
                if (currentState.mode == SplitMode.SIZE_CAP_ONLY || currentState.mode == SplitMode.BOTH) {
                    (currentState.sizeCapGb * 1024L * 1024L * 1024L).toLong()
                } else null
            }

            val requestedParts = if (currentState.isByteSplit) {
                if (currentState.mode == SplitMode.EXACT_PARTS) {
                    currentState.partsCount
                } else null
            } else {
                if (currentState.mode == SplitMode.EXACT_PARTS || currentState.mode == SplitMode.BOTH) {
                    currentState.partsCount
                } else null
            }

            val jobEntity = JobEntity(
                id = jobId,
                type = JobType.SPLIT,
                status = JobStatus.QUEUED,
                sourceUri = currentState.uri,
                outputBaseName = currentState.baseName,
                outputDirUri = currentState.outputFolder.ifEmpty { "content://unknown" },
                outputContainer = ".mkv",
                mode = currentState.mode,
                requestedParts = requestedParts,
                targetCapBytes = targetCapBytes,
                progressPct = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                splitFormat = if (currentState.isByteSplit) "BYTE" else "STRUCTURAL"
            )
            jobDao.upsert(jobEntity)

            // Start Foreground Service
            val intent = Intent(context, JobService::class.java)
            context.startForegroundService(intent)
        }

        return jobId
    }
}
