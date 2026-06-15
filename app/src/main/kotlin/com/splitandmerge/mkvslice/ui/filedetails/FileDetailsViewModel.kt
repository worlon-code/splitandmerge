package com.splitandmerge.mkvslice.ui.filedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class MediaStream(
    val index: Int,
    val type: String, // Video, Audio, Subtitle
    val codec: String,
    val details: String,
    val language: String? = null
)

data class FileDetails(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val durationSec: Double,
    val resolution: String,
    val container: String,
    val streams: List<MediaStream>
)

data class FileDetailsUiState(
    val isLoading: Boolean = true,
    val details: FileDetails? = null,
    val error: String? = null,
    val uri: String = "",
    val filename: String = ""
)

@HiltViewModel
class FileDetailsViewModel @Inject constructor(
    private val ffprobeEngine: FfprobeEngine
) : ViewModel() {
    private val _uiState = MutableStateFlow(FileDetailsUiState())
    val uiState: StateFlow<FileDetailsUiState> = _uiState.asStateFlow()

    val fileDetails: StateFlow<FileDetails?> = uiState
        .map { it.details }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun probeFile(uri: String, filename: String) {
        _uiState.value = FileDetailsUiState(isLoading = true, details = null, error = null, uri = uri, filename = filename)
        viewModelScope.launch {
            try {
                val result = ffprobeEngine.probe(uri)
                
                // Determine main resolution from first video stream
                val videoStream = result.streams.firstOrNull { it.codecType == "video" }
                val resolution = if (videoStream != null && videoStream.width != null && videoStream.height != null) {
                    "${videoStream.width}x${videoStream.height}"
                } else {
                    "Unknown"
                }

                val details = FileDetails(
                    uri = uri,
                    name = filename,
                    sizeBytes = result.format.sizeBytes,
                    durationSec = result.format.durationSeconds,
                    resolution = resolution,
                    container = result.format.formatName,
                    streams = result.streams.map { s ->
                        val type = s.codecType.replaceFirstChar { it.uppercase() }
                        var details = "${s.codecName.uppercase()}"
                        if (s.channels != null) details += ", ${s.channels} channels"
                        if (s.profile != null) details += " (${s.profile})"
                        
                        MediaStream(
                            index = s.index,
                            type = type,
                            codec = s.codecName,
                            details = details,
                            language = s.language
                        )
                    }
                )
                _uiState.value = FileDetailsUiState(isLoading = false, details = details, error = null, uri = uri, filename = filename)
            } catch (e: Exception) {
                Timber.e(e, "Failed to probe file: $uri")
                _uiState.value = FileDetailsUiState(
                    isLoading = false,
                    details = null,
                    error = e.message ?: "Could not analyze file metadata",
                    uri = uri,
                    filename = filename
                )
            }
        }
    }
}
