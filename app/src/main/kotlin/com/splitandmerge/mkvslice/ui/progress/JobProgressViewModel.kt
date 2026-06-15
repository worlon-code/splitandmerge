package com.splitandmerge.mkvslice.ui.progress

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.domain.merger.MergeFormatters
import com.splitandmerge.mkvslice.domain.merger.MergePhase
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.service.JobService
import com.splitandmerge.mkvslice.domain.progress.JobPhaseHint
import com.splitandmerge.mkvslice.domain.progress.JobProgressTracker
import com.splitandmerge.mkvslice.domain.merger.MergePathResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ProgressState(
    val jobId: String = "",
    val fileName: String = "Loading...",
    val jobType: JobType = JobType.SPLIT,
    val status: JobStatus = JobStatus.QUEUED,
    val pct: Int = 0,                       // 0..100 overall
    val phaseLabel: String = "",            // e.g. "Step 1 of 3 · Staging part 2 of 3"
    val speedFormatted: String = "—",
    val etaFormatted: String = "—",
    val phaseHint: JobPhaseHint? = null
)

@HiltViewModel
class JobProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jobDao: JobDao,
    private val jobProgressTracker: JobProgressTracker,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressState())
    val state: StateFlow<ProgressState> = _state.asStateFlow()

    private val jobId: String = checkNotNull(savedStateHandle.get<String>("jobId"))

    init {
        observeJob()
    }

    private var inputsStagedCached: Boolean? = null
    private var outputStagedCached: Boolean? = null

    private fun observeJob() {
        viewModelScope.launch {
            combine(
                jobDao.observeById(jobId),
                jobProgressTracker.phaseHints
            ) { jobEntity, hints ->
                Pair(jobEntity, hints[jobId])
            }.collect { (jobEntity, hint) ->
                if (jobEntity != null) {
                    val pct = jobEntity.progressPct.coerceIn(0, 100)
                    val totalParts = jobEntity.totalParts ?: (jobEntity.requestedParts ?: 1)
                    val isMerge = jobEntity.type == JobType.MERGE

                    val resolvedHint = if (jobEntity.type == JobType.SPLIT) hint else null

                    val statusMessage = when (resolvedHint) {
                        JobPhaseHint.Analyzing -> "Analyzing video structure"
                        JobPhaseHint.Finalizing -> "Finalizing"
                        null -> null
                    }

                    // Compute phase label
                    val phaseLabel = if (statusMessage != null && jobEntity.type == JobType.SPLIT) {
                        statusMessage
                    } else if (isMerge && jobEntity.status == JobStatus.RUNNING) {
                        if (inputsStagedCached == null || outputStagedCached == null) {
                            val parts = jobDao.getPartsForJob(jobId)
                            val partUris = parts.mapNotNull { it.sourceUri }.filter { it.isNotBlank() }
                            val resolvedInputPaths = partUris.map { uriStr ->
                                val path = MergePathResolver.resolveUriToPath(context, uriStr)
                                if (path != null && File(path).exists() && File(path).canRead()) path else null
                            }
                            val allInputsReal = resolvedInputPaths.isNotEmpty() && resolvedInputPaths.all { it != null }
                            inputsStagedCached = !allInputsReal

                            val baseResolvedPath = MergePathResolver.resolveUriToPath(context, jobEntity.outputDirUri)
                            outputStagedCached = if (baseResolvedPath != null) {
                                val baseDirFile = File(baseResolvedPath)
                                val subDirFile = File(baseDirFile, jobEntity.outputBaseName)
                                !(subDirFile.exists() && subDirFile.canWrite() || baseDirFile.exists() && baseDirFile.canWrite())
                            } else {
                                true
                            }
                        }
                        buildMergeLabel(pct, totalParts, inputsStagedCached!!, outputStagedCached!!)
                    } else if (!isMerge && jobEntity.status == JobStatus.RUNNING) {
                        val currentPart = ((pct / 100f) * totalParts).toInt().coerceIn(1, totalParts)
                        "Splitting part $currentPart of $totalParts"
                    } else {
                        when (jobEntity.status) {
                            JobStatus.QUEUED    -> "In Queue…"
                            JobStatus.CANCELLED -> "Job Cancelled"
                            JobStatus.FAILED    -> "Execution Failed"
                            JobStatus.DONE      -> "Processing Complete"
                            else                -> ""
                        }
                    }

                    // Speed: DB stores MB/s; convert to bytes/sec for the formatter.
                    val speedBytesPerSec = ((jobEntity.speedMbs ?: 0.0) * 1024.0 * 1024.0).toLong()
                    val speedFormatted = if (speedBytesPerSec > 0 && jobEntity.status == JobStatus.RUNNING)
                        MergeFormatters.formatSpeed(speedBytesPerSec)
                    else "—"

                    val etaFormatted = if ((jobEntity.etaSeconds ?: 0) > 0 && jobEntity.status == JobStatus.RUNNING)
                        MergeFormatters.formatEta(jobEntity.etaSeconds!!.toLong())
                    else "—"

                    _state.value = ProgressState(
                        jobId = jobEntity.id,
                        fileName = jobEntity.outputBaseName,
                        jobType = jobEntity.type,
                        status = jobEntity.status,
                        pct = pct,
                        phaseLabel = phaseLabel,
                        speedFormatted = speedFormatted,
                        etaFormatted = etaFormatted,
                        phaseHint = resolvedHint
                    )
                }
            }
        }
    }

    private fun buildMergeLabel(pct: Int, totalParts: Int, inputsStaged: Boolean, outputStaged: Boolean): String {
        val totalSteps = (if (inputsStaged) 1 else 0) + 1 + (if (outputStaged) 1 else 0)
        
        val phase: MergePhase
        val localPct: Int
        
        when {
            inputsStaged && outputStaged -> {
                when {
                    pct < 33 -> {
                        phase = MergePhase.STAGING
                        localPct = (pct * 100.0 / 33.0).toInt().coerceIn(0, 100)
                    }
                    pct < 66 -> {
                        phase = MergePhase.CONCAT
                        localPct = ((pct - 33) * 100.0 / 33.0).toInt().coerceIn(0, 100)
                    }
                    else -> {
                        phase = MergePhase.COPYING_TO_OUTPUT
                        localPct = ((pct - 66) * 100.0 / 34.0).toInt().coerceIn(0, 100)
                    }
                }
            }
            inputsStaged && !outputStaged -> {
                when {
                    pct < 50 -> {
                        phase = MergePhase.STAGING
                        localPct = (pct * 100.0 / 50.0).toInt().coerceIn(0, 100)
                    }
                    else -> {
                        phase = MergePhase.CONCAT
                        localPct = ((pct - 50) * 100.0 / 50.0).toInt().coerceIn(0, 100)
                    }
                }
            }
            !inputsStaged && outputStaged -> {
                when {
                    pct < 50 -> {
                        phase = MergePhase.CONCAT
                        localPct = (pct * 100.0 / 50.0).toInt().coerceIn(0, 100)
                    }
                    else -> {
                        phase = MergePhase.COPYING_TO_OUTPUT
                        localPct = ((pct - 50) * 100.0 / 50.0).toInt().coerceIn(0, 100)
                    }
                }
            }
            else -> { // !inputsStaged && !outputStaged
                phase = MergePhase.CONCAT
                localPct = pct
            }
        }
        
        return when (phase) {
            MergePhase.STAGING -> {
                val partIdx = ((localPct / 100.0) * totalParts).toInt().coerceIn(1, totalParts)
                if (totalSteps > 1) {
                    "Step 1 of $totalSteps · Staging part $partIdx of $totalParts"
                } else {
                    "Staging part $partIdx of $totalParts"
                }
            }
            MergePhase.CONCAT -> {
                val stepNum = if (inputsStaged) 2 else 1
                if (totalSteps <= 2) {
                    "Merging"
                } else {
                    "Step $stepNum of $totalSteps · Merging"
                }
            }
            MergePhase.COPYING_TO_OUTPUT -> {
                val stepNum = totalSteps
                "Step $stepNum of $totalSteps · Copying to output"
            }
        }
    }

    /**
     * Sends ACTION_CANCEL to [JobService] for the running job (A1).
     * Does NOT touch the DB — JobService writes the CANCELLED status after
     * cancelAndJoin completes, so the DB observer in the screen drives navigation.
     */
    fun cancelJob() {
        val intent = Intent(context, JobService::class.java).apply {
            action = JobService.ACTION_CANCEL
            putExtra(JobService.EXTRA_JOB_ID, jobId)
        }
        context.startService(intent)
    }
}
