package com.splitandmerge.mkvslice.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.domain.model.Job
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor() : ViewModel() {
    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    init {
        loadMockJobs()
    }

    private fun loadMockJobs() {
        viewModelScope.launch {
            _jobs.value = listOf(
                Job(
                    id = "1",
                    type = JobType.SPLIT,
                    status = JobStatus.DONE,
                    createdAt = System.currentTimeMillis() - 3600000 * 2,
                    updatedAt = System.currentTimeMillis() - 3600000 * 2 + 120000,
                    inputPathUri = "content://media/external/video/media/101",
                    outputPathUri = "content://media/external/file/201",
                    outputBaseName = "Kantara Chapter 1 (2024)",
                    progress = 1.0f
                ),
                Job(
                    id = "2",
                    type = JobType.MERGE,
                    status = JobStatus.RUNNING,
                    createdAt = System.currentTimeMillis() - 60000,
                    updatedAt = System.currentTimeMillis(),
                    inputPathUri = "content://media/external/file/201",
                    outputPathUri = "content://media/external/video/media/302",
                    outputBaseName = "Bahubali (2025).merged",
                    progress = 0.45f
                ),
                Job(
                    id = "3",
                    type = JobType.SPLIT,
                    status = JobStatus.FAILED,
                    createdAt = System.currentTimeMillis() - 3600000 * 24,
                    updatedAt = System.currentTimeMillis() - 3600000 * 24 + 10000,
                    inputPathUri = "content://media/external/video/media/105",
                    outputPathUri = "content://media/external/file/201",
                    outputBaseName = "Karuppu (2026)",
                    progress = 0.05f,
                    errorDetails = "Insufficient storage space on destination device."
                )
            )
        }
    }
}
