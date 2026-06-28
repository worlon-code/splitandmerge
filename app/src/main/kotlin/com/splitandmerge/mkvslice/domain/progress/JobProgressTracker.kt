package com.splitandmerge.mkvslice.domain.progress

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

sealed class JobPhaseHint {
    data object Analyzing : JobPhaseHint()
    data object Finalizing : JobPhaseHint()
}

@Singleton
class JobProgressTracker @Inject constructor() {
    private val _phaseHints = MutableStateFlow<Map<String, JobPhaseHint>>(emptyMap())
    val phaseHints: StateFlow<Map<String, JobPhaseHint>> = _phaseHints.asStateFlow()

    fun setPhaseHint(jobId: String, hint: JobPhaseHint?) {
        _phaseHints.update { current ->
            if (hint == null) {
                current - jobId
            } else {
                current + (jobId to hint)
            }
        }
    }

    // Per-file progress (0..100) for batch jobs (e.g. Set Default Tracks), keyed by the
    // file's content URI.
    private val _fileProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val fileProgress: StateFlow<Map<String, Int>> = _fileProgress.asStateFlow()

    fun setFileProgress(uri: String, pct: Int) {
        _fileProgress.update { it + (uri to pct.coerceIn(0, 100)) }
    }

    fun clearFileProgress() {
        _fileProgress.value = emptyMap()
    }
}
