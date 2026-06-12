package com.splitandmerge.mkvslice.ui.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.domain.model.JobStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgressState(
    val jobId: String = "",
    val fileName: String = "Loading...",
    val status: JobStatus = JobStatus.QUEUED,
    val progress: Float = 0.0f,
    val currentPart: Int = 1,
    val totalParts: Int = 1,
    val speedMbs: Float = 0.0f,
    val etaSeconds: Int = 0
)

@HiltViewModel
class JobProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jobDao: JobDao
) : ViewModel() {
    private val _state = MutableStateFlow(ProgressState())
    val state: StateFlow<ProgressState> = _state.asStateFlow()
    
    private val jobId: String = checkNotNull(savedStateHandle.get<String>("jobId"))

    init {
        observeJob()
    }

    private fun observeJob() {
        viewModelScope.launch {
            jobDao.observeById(jobId).collect { jobEntity ->
                if (jobEntity != null) {
                    val actualTotalParts = jobEntity.totalParts ?: (jobEntity.requestedParts ?: 1)
                    
                    _state.value = _state.value.copy(
                        jobId = jobEntity.id,
                        fileName = jobEntity.outputBaseName,
                        status = jobEntity.status,
                        progress = jobEntity.progressPct / 100f,
                        totalParts = actualTotalParts,
                        speedMbs = (jobEntity.speedMbs ?: 0.0).toFloat(),
                        etaSeconds = jobEntity.etaSeconds ?: 0,
                        currentPart = ((jobEntity.progressPct / 100f) * actualTotalParts).toInt().coerceIn(1, actualTotalParts)
                    )
                }
            }
        }
    }

    fun cancelJob() {
        viewModelScope.launch {
            _state.value = _state.value.copy(status = JobStatus.CANCELLED)
            jobDao.updateProgress(jobId, JobStatus.CANCELLED, _state.value.progress.toInt() * 100, null, null, null, System.currentTimeMillis())
            // Note: JobService handles listening for CANCELLED via intent.
        }
    }
}
