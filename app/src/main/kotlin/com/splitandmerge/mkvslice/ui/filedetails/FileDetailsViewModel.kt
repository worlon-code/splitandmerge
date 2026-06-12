package com.splitandmerge.mkvslice.ui.filedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@HiltViewModel
class FileDetailsViewModel @Inject constructor(
    private val ffprobeEngine: FfprobeEngine
) : ViewModel() {
    private val _fileDetails = MutableStateFlow<FileDetails?>(null)
    val fileDetails: StateFlow<FileDetails?> = _fileDetails.asStateFlow()

    fun probeFile(uri: String, filename: String) {
        viewModelScope.launch {
            try {
                _fileDetails.value = null // reset while loading
                val result = ffprobeEngine.probe(uri)
                
                // Determine main resolution from first video stream
                val videoStream = result.streams.firstOrNull { it.codecType == "video" }
                val resolution = if (videoStream != null && videoStream.width != null && videoStream.height != null) {
                    "${videoStream.width}x${videoStream.height}"
                } else {
                    "Unknown"
                }

                _fileDetails.value = FileDetails(
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
            } catch (e: Exception) {
                Timber.e(e, "Failed to probe file: $uri")
                // TODO: handle error state
            }
        }
    }
}
