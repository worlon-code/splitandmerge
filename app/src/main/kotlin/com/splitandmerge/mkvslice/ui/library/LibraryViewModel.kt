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
import com.splitandmerge.mkvslice.data.db.DefaultTrackFileResultDao
import com.splitandmerge.mkvslice.domain.defaulttracks.FlagJournal
import com.splitandmerge.mkvslice.domain.defaulttracks.RollbackResult
import com.splitandmerge.mkvslice.platform.io.FileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
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

data class LibraryState(
    val jobs: List<Job> = emptyList(),
    val isInitialLoad: Boolean = true
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val jobDao: JobDao,
    private val defaultTrackFileResultDao: DefaultTrackFileResultDao,
    private val fileSystem: FileSystem,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class OrphanJournal(
        val cacheFile: java.io.File,
        val jobId: String,
        val fileIndex: Int,
        val displayName: String,
        val uri: String
    )

    private val _orphanJournals = MutableStateFlow<List<OrphanJournal>>(emptyList())
    val orphanJournals: StateFlow<List<OrphanJournal>> = _orphanJournals

    private val dismissedJournals = mutableSetOf<String>()

    fun checkForOrphanJournals() {
        viewModelScope.launch {
            val hasRunningJob = state.value.jobs.any { it.status == JobStatus.RUNNING }
            if (hasRunningJob) {
                _orphanJournals.value = emptyList()
                return@launch
            }

            val cacheDir = context.cacheDir
            val files = cacheDir.listFiles { _, name ->
                name.startsWith("defaulttracks_") && name.endsWith(".journal")
            } ?: emptyArray()

            val list = mutableListOf<OrphanJournal>()
            for (file in files) {
                if (dismissedJournals.contains(file.name)) continue
                val parts = file.name.removePrefix("defaulttracks_").removeSuffix(".journal").split("_")
                if (parts.size == 2) {
                    val jobId = parts[0]
                    val fileIndex = parts[1].toIntOrNull() ?: 0
                    val results = defaultTrackFileResultDao.getResultsForJob(jobId)
                    val matchedRow = results.getOrNull(fileIndex)
                    if (matchedRow != null) {
                        list.add(OrphanJournal(file, jobId, fileIndex, matchedRow.displayName, matchedRow.uri))
                    } else {
                        list.add(OrphanJournal(file, jobId, fileIndex, "File #$fileIndex (Job $jobId)", ""))
                    }
                }
            }
            _orphanJournals.value = list
        }
    }

    fun performRollback(orphan: OrphanJournal) {
        viewModelScope.launch {
            val journal = FlagJournal(context.cacheDir, orphan.jobId, orphan.fileIndex)
            val fd = fileSystem.openFileDescriptor(orphan.uri, "rw")
            if (fd != null) {
                val result = withContext(Dispatchers.IO) {
                    fd.use {
                        journal.rollback(fd)
                    }
                }
                if (result is RollbackResult.SUCCESS) {
                    timber.log.Timber.d("Rollback successful for ${orphan.displayName}")
                } else {
                    timber.log.Timber.e("Rollback failed/refused for ${orphan.displayName}")
                }
            } else {
                timber.log.Timber.e("Failed to open file descriptor for rollback")
            }
            dismissedJournals.add(orphan.cacheFile.name)
            checkForOrphanJournals()
        }
    }

    fun dismissOrphanDialog(orphan: OrphanJournal) {
        dismissedJournals.add(orphan.cacheFile.name)
        checkForOrphanJournals()
    }

    sealed class NavCommand {
        object DoNothing : NavCommand()
        data class ToProgress(val jobId: String) : NavCommand()
        data class ToSplitResult(val jobId: String) : NavCommand()
        data class ToMergeResult(val jobId: String) : NavCommand()
        data class ToDetailSheet(val jobId: String, val status: JobStatus) : NavCommand()
    }

    private val _navigationEvents = Channel<NavCommand>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    val state: StateFlow<LibraryState> = jobDao.observeAll()
        .map { entities ->
            val jobList = entities.map { entity ->
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
            LibraryState(jobs = jobList, isInitialLoad = false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryState(jobs = emptyList(), isInitialLoad = true)
        )

    val jobs: StateFlow<List<Job>> = state
        .map { it.jobs }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        checkForOrphanJournals()
    }

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
