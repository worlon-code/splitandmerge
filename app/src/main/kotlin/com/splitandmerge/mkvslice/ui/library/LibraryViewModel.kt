package com.splitandmerge.mkvslice.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.domain.model.Job
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val jobDao: JobDao
) : ViewModel() {

    val jobs: StateFlow<List<Job>> = jobDao.observeAll()
        .map { entities ->
            entities.map { entity ->
                Job(
                    id = entity.id,
                    type = entity.type,
                    status = entity.status,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    inputPathUri = entity.sourceUri,
                    outputPathUri = entity.outputDirUri,
                    outputBaseName = entity.outputBaseName,
                    mode = entity.mode,
                    requestedParts = entity.requestedParts,
                    maxPartBytes = entity.targetCapBytes,
                    manifestPath = entity.manifestPath,
                    errorDetails = entity.errorMessage,
                    progress = entity.progressPct / 100f
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
