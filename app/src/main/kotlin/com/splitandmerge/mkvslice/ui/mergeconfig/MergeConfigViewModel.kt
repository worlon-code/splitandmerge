package com.splitandmerge.mkvslice.ui.mergeconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.service.JobService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext

data class MergeConfigState(
    val outputBaseName: String = "Merged_Output",
    val outputFolder: String = "",
    val partsUris: String = "" // comma separated
)

@HiltViewModel
class MergeConfigViewModel @Inject constructor(
    private val jobDao: JobDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(MergeConfigState())
    val state: StateFlow<MergeConfigState> = _state.asStateFlow()

    // For now, mock some URIs if none provided
    fun initMock(uris: String) {
        _state.value = _state.value.copy(partsUris = uris)
    }

    fun updateBaseName(name: String) {
        _state.value = _state.value.copy(outputBaseName = name)
    }

    fun updateOutputFolder(uri: String) {
        _state.value = _state.value.copy(outputFolder = uri)
    }

    fun submitMergeJob(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val currentState = _state.value
            val jobId = UUID.randomUUID().toString()

            val jobEntity = JobEntity(
                id = jobId,
                type = JobType.MERGE,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                status = JobStatus.QUEUED,
                progressPct = 0,
                sourceUri = "content://mock", // Merge uses manifestPath
                outputDirUri = currentState.outputFolder.ifEmpty { "content://unknown" },
                outputBaseName = currentState.outputBaseName,
                outputContainer = ".mkv",
                manifestPath = currentState.partsUris // Store parts list here
            )

            jobDao.upsert(jobEntity)
            
            val intent = Intent(context, JobService::class.java)
            context.startService(intent)

            onSuccess(jobId)
        }
    }
}
