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
}
