package com.splitandmerge.mkvslice.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.domain.model.JobStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgressState(
    val jobId: String = "",
    val fileName: String = "Baahubali The Epic 2025.mkv",
    val status: JobStatus = JobStatus.RUNNING,
    val progress: Float = 0.0f,
    val currentPart: Int = 1,
    val totalParts: Int = 3,
    val speedMbs: Float = 85.0f,
    val etaSeconds: Int = 120
)

@HiltViewModel
class JobProgressViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(ProgressState())
    val state: StateFlow<ProgressState> = _state.asStateFlow()

    init {
        simulateProgress()
    }

    private fun simulateProgress() {
        viewModelScope.launch {
            _state.value = _state.value.copy(jobId = "2")
            var currentProgress = 0.0f
            while (currentProgress < 1.0f) {
                delay(1000)
                currentProgress += 0.05f
                val progressVal = Math.min(1.0f, currentProgress)
                val part = when {
                    progressVal < 0.33f -> 1
                    progressVal < 0.66f -> 2
                    else -> 3
                }
                val remainingSeconds = ((1.0f - progressVal) * 120).toInt()
                _state.value = _state.value.copy(
                    progress = progressVal,
                    currentPart = part,
                    etaSeconds = remainingSeconds
                )
            }
            _state.value = _state.value.copy(status = JobStatus.DONE, progress = 1.0f)
        }
    }

    fun cancelJob() {
        _state.value = _state.value.copy(status = JobStatus.CANCELLED)
    }
}
