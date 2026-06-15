package com.splitandmerge.mkvslice.ui.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.data.db.entity.CleanupPatternEntity
import com.splitandmerge.mkvslice.data.repository.CleanupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CleanupState(
    val patterns: List<CleanupPatternEntity> = emptyList(),
    val sampleInput: String = "www.5movierulz.download - Bahubali (2025) True.mkv",
    val sampleOutput: String = "Bahubali (2025)"
)

@HiltViewModel
class CleanupPatternsViewModel @Inject constructor(
    private val cleanupRepository: CleanupRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CleanupState())
    val state: StateFlow<CleanupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            cleanupRepository.observePatterns().collectLatest { patterns ->
                _state.value = _state.value.copy(patterns = patterns)
                runCleanupPreview()
            }
        }
    }

    fun togglePattern(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val pattern = _state.value.patterns.find { it.id == id }
            if (pattern != null) {
                cleanupRepository.upsert(pattern.copy(enabled = enabled))
            }
        }
    }

    fun addPattern(regex: String, label: String) {
        viewModelScope.launch {
            try {
                Regex(regex)
            } catch (e: Exception) {
                return@launch // Reject invalid regex
            }
            
            val maxOrder = _state.value.patterns.maxOfOrNull { it.orderIndex } ?: 0
            val nextOrder = maxOrder + 1
            val newPattern = CleanupPatternEntity(
                id = UUID.randomUUID().toString(),
                regex = regex,
                replacement = "",
                enabled = true,
                isBuiltIn = false,
                orderIndex = nextOrder,
                label = label,
                createdAt = System.currentTimeMillis()
            )
            cleanupRepository.upsert(newPattern)
        }
    }

    fun deletePattern(id: String) {
        val pattern = _state.value.patterns.find { it.id == id }
        if (pattern?.isBuiltIn == true) return // Protection: built-in rules cannot be deleted

        viewModelScope.launch {
            cleanupRepository.delete(id)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            cleanupRepository.resetToDefaults()
        }
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

        val currentPatterns = _state.value.patterns
        for (rule in currentPatterns.filter { rule -> rule.enabled }.sortedBy { rule -> rule.orderIndex }) {
            try {
                text = text.replace(Regex(rule.regex, RegexOption.IGNORE_CASE), rule.replacement)
            } catch (e: Exception) {
                // Ignore invalid regexes in preview
            }
        }
        
        text = text.trim()
        _state.value = _state.value.copy(sampleOutput = text)
    }
}
