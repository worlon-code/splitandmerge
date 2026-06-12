package com.splitandmerge.mkvslice.ui.cleanup

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject

data class CleanupPattern(
    val id: String,
    val regex: String,
    val replacement: String,
    val enabled: Boolean,
    val isBuiltIn: Boolean,
    val orderIndex: Int,
    val label: String
)

data class CleanupState(
    val patterns: List<CleanupPattern> = emptyList(),
    val sampleInput: String = "www.5movierulz.download - Bahubali (2025) True.mkv",
    val sampleOutput: String = "Bahubali (2025)"
)

@HiltViewModel
class CleanupPatternsViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(CleanupState())
    val state: StateFlow<CleanupState> = _state.asStateFlow()

    init {
        loadMockPatterns()
    }

    private fun loadMockPatterns() {
        _state.value = CleanupState(
            patterns = listOf(
                CleanupPattern("1", "^www\\.[^\\s-]+\\s*[-–]\\s*", "", true, true, 0, "Strip leading URL prefix"),
                CleanupPattern("2", "\\.", " ", true, true, 1, "Replace dots with spaces"),
                CleanupPattern("3", "(?i)\\b(4K|2160p|1080p|720p)\\b", "", true, true, 2, "Strip resolutions"),
                CleanupPattern("4", "(?i)\\b(HDR|HDR10|DV|DolbyVision)\\b", "", true, true, 3, "Strip HDR/DV tokens"),
                CleanupPattern("5", "(?i)\\b(x264|x265|HEVC|H264|H265)\\b", "", true, true, 4, "Strip codec names"),
                CleanupPattern("6", "(?i)\\b(BluRay|BDRip|BRRip|WEBRip|WEB-DL)\\b", "", true, true, 5, "Strip source tags"),
                CleanupPattern("7", "(?i)\\b(True|Real|Repack|Proper|DUAL|MULTI)\\b", "", true, true, 6, "Strip release markers")
            )
        )
        runCleanupPreview()
    }

    fun togglePattern(id: String, enabled: Boolean) {
        val updated = _state.value.patterns.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        _state.value = _state.value.copy(patterns = updated)
        runCleanupPreview()
    }

    fun addPattern(regex: String, label: String) {
        val nextOrder = _state.value.patterns.size
        val newPattern = CleanupPattern(
            id = UUID.randomUUID().toString(),
            regex = regex,
            replacement = "",
            enabled = true,
            isBuiltIn = false,
            orderIndex = nextOrder,
            label = label
        )
        _state.value = _state.value.copy(patterns = _state.value.patterns + newPattern)
        runCleanupPreview()
    }

    fun deletePattern(id: String) {
        _state.value = _state.value.copy(patterns = _state.value.patterns.filter { it.id != id })
        runCleanupPreview()
    }

    fun updateSampleInput(input: String) {
        _state.value = _state.value.copy(sampleInput = input)
        runCleanupPreview()
    }

    private fun runCleanupPreview() {
        var text = _state.value.sampleInput
        val lastDot = text.lastIndexOf('.')
        if (lastDot > 0) {
            text = text.substring(0, lastDot)
        }

        _state.value.patterns.filter { it.enabled }.sortedBy { it.orderIndex }.forEach { rule ->
            try {
                text = text.replace(rule.regex.toRegex(), rule.replacement)
            } catch (e: Exception) {
                // Ignore invalid regexes in preview
            }
        }
        
        text = text.replace("\\s+".toRegex(), " ").trim()
        _state.value = _state.value.copy(sampleOutput = text)
    }
}
