package com.splitandmerge.mkvslice.ui.filedetails

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class MediaStream(
    val index: Int,
    val type: String, // Video, Audio, Subtitle
    val codec: String,
    val details: String,
    val language: String? = null
)

data class FileDetails(
    val name: String,
    val sizeBytes: Long,
    val durationSec: Double,
    val resolution: String,
    val container: String,
    val streams: List<MediaStream>
)

@HiltViewModel
class FileDetailsViewModel @Inject constructor() : ViewModel() {
    private val _fileDetails = MutableStateFlow<FileDetails?>(null)
    val fileDetails: StateFlow<FileDetails?> = _fileDetails.asStateFlow()

    init {
        loadMockDetails()
    }

    private fun loadMockDetails() {
        _fileDetails.value = FileDetails(
            name = "Baahubali The Epic 2025 2160p NEWEB-DL DUAL DTS HD MA DDP5.1 H.265.mkv",
            sizeBytes = 28485939200, // 26.5 GB
            durationSec = 9642.0, // 2h 40m 42s
            resolution = "3840x2160 (4K UHD)",
            container = "Matroska (MKV)",
            streams = listOf(
                MediaStream(0, "Video", "hevc (Main 10)", "3840x2160, 23.976 fps, HDR10"),
                MediaStream(1, "Audio", "dts", "DTS-HD Master Audio, 6 channels, 48kHz", "Telugu"),
                MediaStream(2, "Audio", "eac3", "Dolby Digital Plus, 6 channels, 48kHz", "Hindi"),
                MediaStream(3, "Subtitle", "ass", "Advanced SubStation Alpha", "English"),
                MediaStream(4, "Subtitle", "ass", "Advanced SubStation Alpha", "Telugu")
            )
        )
    }
}
