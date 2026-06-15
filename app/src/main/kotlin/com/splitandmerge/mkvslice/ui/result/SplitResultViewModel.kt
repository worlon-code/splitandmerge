package com.splitandmerge.mkvslice.ui.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.data.db.JobDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class OutputFile(
    val name: String,
    val sizeBytes: Long,
    val uri: String
)

data class SplitResultUiState(
    val isLoading: Boolean = true,
    val baseName: String = "",
    val partCount: Int = 0,
    val outputs: List<OutputFile> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class SplitResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jobDao: JobDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplitResultUiState())
    val uiState: StateFlow<SplitResultUiState> = _uiState.asStateFlow()

    private val jobId: String = checkNotNull(savedStateHandle.get<String>("jobId"))

    init {
        loadResult()
    }

    private fun loadResult() {
        viewModelScope.launch {
            try {
                val job = jobDao.getById(jobId)
                if (job == null) {
                    _uiState.value = SplitResultUiState(
                        isLoading = false,
                        error = "Job not found (id=$jobId)"
                    )
                    return@launch
                }

                val parts = jobDao.getPartsForJob(jobId)

                val outputs = parts.map { part ->
                    OutputFile(
                        name = part.name,
                        sizeBytes = part.sizeBytes ?: 0L,
                        uri = part.sourceUri ?: ""
                    )
                }

                _uiState.value = SplitResultUiState(
                    isLoading = false,
                    baseName = job.outputBaseName,
                    partCount = parts.size,
                    outputs = outputs,
                    error = null
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load split result for job $jobId")
                _uiState.value = SplitResultUiState(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }
}
