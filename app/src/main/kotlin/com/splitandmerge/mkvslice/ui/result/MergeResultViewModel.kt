package com.splitandmerge.mkvslice.ui.result

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class MergeResultUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val cleanedTitle: String = "",          // job.outputBaseName
    val outputFilename: String = "",        // outputBaseName + outputContainer
    val outputSizeBytes: Long = 0L,         // probed via SAF document
    val durationSeconds: Double = 0.0,      // probed from merged file
    val audioTracks: Int = 0,
    val subtitleTracks: Int = 0,
    val chapterCount: Int = 0,
    val attachmentCount: Int = 0,
    val timeTakenMs: Long = 0L,             // job.updatedAt - job.createdAt
)

@HiltViewModel
class MergeResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jobDao: JobDao,
    private val ffprobeEngine: FfprobeEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MergeResultUiState())
    val uiState: StateFlow<MergeResultUiState> = _uiState.asStateFlow()

    private val jobId: String = checkNotNull(savedStateHandle.get<String>("jobId"))

    init {
        loadResult()
    }

    private fun loadResult() {
        viewModelScope.launch {
            try {
                val job = jobDao.getById(jobId)
                if (job == null) {
                    _uiState.value = MergeResultUiState(isLoading = false, error = "Job not found")
                    return@launch
                }

                val outDirUri = Uri.parse(job.outputDirUri)
                val baseOutDir = if (outDirUri.scheme == "file") {
                    DocumentFile.fromFile(java.io.File(outDirUri.path!!))
                } else {
                    DocumentFile.fromTreeUri(context, outDirUri)
                } ?: throw IllegalStateException("Cannot access output directory")

                val subDir = baseOutDir.listFiles()
                    .filter { it.isDirectory && it.name?.startsWith(job.outputBaseName) == true }
                    .maxByOrNull { it.lastModified() } ?: baseOutDir

                val outputFile = findOutputFile(subDir, job)
                val outputFileName = outputFile.name ?: "${job.outputBaseName}${job.outputContainer}"

                val outputFileUri = outputFile.uri.toString()
                
                // Probe the output file
                val probeResult = ffprobeEngine.probe(outputFileUri)
                
                val audioTracks = probeResult.streams.count { it.codecType == "audio" }
                val subtitleTracks = probeResult.streams.count { it.codecType == "subtitle" }
                val attachmentCount = probeResult.streams.count { it.codecType == "attachment" }
                
                _uiState.value = MergeResultUiState(
                    isLoading = false,
                    error = null,
                    cleanedTitle = job.outputBaseName,
                    outputFilename = outputFileName,
                    outputSizeBytes = outputFile.length(),
                    durationSeconds = probeResult.format.durationSeconds,
                    audioTracks = audioTracks,
                    subtitleTracks = subtitleTracks,
                    chapterCount = 0, // Chapter count is not exposed by ffprobe engine in this version
                    attachmentCount = attachmentCount,
                    timeTakenMs = job.updatedAt - job.createdAt
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load merge result for job $jobId")
                _uiState.value = MergeResultUiState(isLoading = false, error = e.message ?: "Unknown error occurred")
            }
        }
    }

    private fun findOutputFile(
        outDir: DocumentFile,
        job: JobEntity
    ): DocumentFile {
        val expected = "${job.outputBaseName}${job.outputContainer}"

        // Path A: obvious filename — covers 95% of cases (no collision).
        outDir.findFile(expected)?.let { return it }

        // Path B: collision — folder is "<base> (1)". Output file inside
        // it has the post-rename folder's name. Look up via outDir.name.
        val resolved = "${outDir.name ?: job.outputBaseName}${job.outputContainer}"
        outDir.findFile(resolved)?.let { return it }

        // Path C: defensive fallback — pick the largest .mkv in this
        // subdir. The merged output is always the largest single file
        // in a freshly-created merge folder.
        val mkvs = outDir.listFiles()
            .filter { it.name?.endsWith(job.outputContainer, ignoreCase = true) == true }
        if (mkvs.isNotEmpty()) {
            return mkvs.maxBy { it.length() }
        }

        throw IllegalStateException(
            "Output file not found in ${outDir.uri}. " +
            "Expected '$expected' or '$resolved' or any *.mkv."
        )
    }
}
