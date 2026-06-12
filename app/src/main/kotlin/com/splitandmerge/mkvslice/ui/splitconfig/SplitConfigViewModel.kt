package com.splitandmerge.mkvslice.ui.splitconfig

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.SplitMode
import com.splitandmerge.mkvslice.service.JobService
import com.splitandmerge.mkvslice.domain.cleanup.TitleCleaner
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val titleCleaner: TitleCleaner
) : ViewModel() {

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
            fileSizeBytes = sizeBytes,
            fileDurationSec = durationSec
        )
        recalculatePredictions()
    }
        recalculatePredictions()
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
        _state.value = _state.value.copy(outputFolder = folder)
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
