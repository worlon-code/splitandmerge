package com.splitandmerge.mkvslice.ui.library

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.domain.model.Job
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.PartStatus
import com.splitandmerge.mkvslice.service.JobService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface LibraryIntent {
    data class RowTapped(val jobId: String) : LibraryIntent
    data class RetryJob(val jobId: String) : LibraryIntent
    data class DeleteJob(val jobId: String) : LibraryIntent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val jobDao: JobDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    sealed class NavCommand {
        object DoNothing : NavCommand()
        data class ToProgress(val jobId: String) : NavCommand()
        data class ToSplitResult(val jobId: String) : NavCommand()
        data class ToMergeResult(val jobId: String) : NavCommand()
        data class ToDetailSheet(val jobId: String, val status: JobStatus) : NavCommand()
    }

    private val _navigationEvents = Channel<NavCommand>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

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

    fun handleIntent(intent: LibraryIntent) {
        viewModelScope.launch {
            when (intent) {
                is LibraryIntent.RowTapped -> {
                    val job = jobDao.getById(intent.jobId) ?: return@launch
                    val command = when (job.status) {
                        JobStatus.QUEUED -> NavCommand.DoNothing
                        JobStatus.RUNNING -> NavCommand.ToProgress(job.id)
                        JobStatus.DONE -> {
                            if (job.type == JobType.MERGE) {
                                NavCommand.ToMergeResult(job.id)
                            } else {
                                NavCommand.ToSplitResult(job.id)
                            }
                        }
                        JobStatus.CANCELLED, JobStatus.FAILED -> NavCommand.ToDetailSheet(job.id, job.status)
                    }
                    _navigationEvents.send(command)
                }
                is LibraryIntent.RetryJob -> {
                    val oldJob = jobDao.getById(intent.jobId) ?: return@launch
                    val newJobId = UUID.randomUUID().toString()
                    val newJob = oldJob.copy(
                        id = newJobId,
                        status = JobStatus.QUEUED,
                        progressPct = 0,
                        errorMessage = null,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        speedMbs = null,
                        etaSeconds = null
                    )
                    
                    jobDao.upsert(newJob)

                    // A1: Retry part duplication for MERGE jobs
                    if (oldJob.type == JobType.MERGE) {
                        val oldParts = jobDao.getPartsForJob(oldJob.id)
                        oldParts.forEach { oldPart ->
                            val newPart = oldPart.copy(
                                id = UUID.randomUUID().toString(),
                                jobId = newJobId,
                                status = PartStatus.PENDING
                            )
                            jobDao.upsertPart(newPart)
                        }
                    }
                    
                    // Trigger JobService queue pick-up
                    val serviceIntent = Intent(context, JobService::class.java)
                    try {
                        context.startForegroundService(serviceIntent)
                    } catch (e: Exception) {
                        context.startService(serviceIntent)
                    }
                }
                is LibraryIntent.DeleteJob -> {
                    jobDao.deleteById(intent.jobId)
                }
            }
        }
    }
}
