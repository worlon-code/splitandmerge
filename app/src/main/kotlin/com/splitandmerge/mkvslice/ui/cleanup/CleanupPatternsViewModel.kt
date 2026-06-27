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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
import java.util.UUID
import com.splitandmerge.mkvslice.data.cleanup.backup.CleanupBackupFile
import com.splitandmerge.mkvslice.data.cleanup.backup.CleanupPatternBackup
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class RestoreOutcome(
    val addedCount: Int,
    val ignoredPatterns: List<Pair<String, String>>
)

data class CleanupState(
    val patterns: List<CleanupPatternEntity> = emptyList(),
    val sampleInput: String = "www.5movierulz.download - Bahubali (2025) True.mkv",
    val sampleOutput: String = "Bahubali (2025)",
    val snackbarMessage: String? = null,
    val restoreOutcome: RestoreOutcome? = null
)

@HiltViewModel
class CleanupPatternsViewModel @Inject constructor(
    private val cleanupRepository: CleanupRepository,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher
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

    fun addPattern(regex: String, label: String, replacement: String = "") {
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
                replacement = replacement,
                enabled = true,
                isBuiltIn = false,
                orderIndex = nextOrder,
                label = label,
                createdAt = System.currentTimeMillis()
            )
            cleanupRepository.upsert(newPattern)
        }
    }

    fun updatePattern(id: String, regex: String, label: String, replacement: String) {
        viewModelScope.launch {
            try {
                Regex(regex)
            } catch (e: Exception) {
                return@launch // Reject invalid regex
            }
            val existing = _state.value.patterns.find { it.id == id }
            if (existing != null) {
                val updated = existing.copy(
                    regex = regex,
                    label = label,
                    replacement = replacement
                )
                cleanupRepository.upsert(updated)
            }
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

    fun exportBackup(outputStream: java.io.OutputStream) {
        viewModelScope.launch {
            try {
                val backupPatterns = _state.value.patterns.map {
                    CleanupPatternBackup(
                        regex = it.regex,
                        replacement = it.replacement,
                        enabled = it.enabled,
                        label = it.label
                    )
                }
                val backupFile = CleanupBackupFile(patterns = backupPatterns)
                val jsonString = json.encodeToString(backupFile)
                withContext(ioDispatcher) {
                    outputStream.use { out ->
                        out.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                }
                _state.value = _state.value.copy(snackbarMessage = "Backup exported successfully")
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbarMessage = "Export failed: ${e.message}")
            }
        }
    }

    fun importRestore(inputStream: java.io.InputStream) {
        viewModelScope.launch {
            try {
                val jsonString = withContext(ioDispatcher) {
                    inputStream.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    }
                }
                val backupFile = json.decodeFromString<CleanupBackupFile>(jsonString)
                if (backupFile.version != 1) {
                    _state.value = _state.value.copy(snackbarMessage = "Unsupported backup version: ${backupFile.version}")
                    return@launch
                }

                val currentPatterns = cleanupRepository.getAllPatterns()
                val ignored = mutableListOf<Pair<String, String>>()
                val toAdd = mutableListOf<CleanupPatternEntity>()
                
                var maxOrder = currentPatterns.maxOfOrNull { it.orderIndex } ?: 0

                for (backup in backupFile.patterns) {
                    val match = currentPatterns.find { it.regex == backup.regex && it.replacement == backup.replacement }
                    if (match != null) {
                        ignored.add(backup.label to match.label)
                    } else {
                        maxOrder++
                        toAdd.add(
                            CleanupPatternEntity(
                                id = UUID.randomUUID().toString(),
                                regex = backup.regex,
                                replacement = backup.replacement,
                                enabled = backup.enabled,
                                isBuiltIn = false,
                                orderIndex = maxOrder,
                                label = backup.label,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                if (toAdd.isNotEmpty()) {
                    cleanupRepository.addAll(toAdd)
                }

                _state.value = _state.value.copy(
                    restoreOutcome = RestoreOutcome(
                        addedCount = toAdd.size,
                        ignoredPatterns = ignored
                    ),
                    snackbarMessage = "Restore completed successfully"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbarMessage = "Restore failed: ${e.message}")
            }
        }
    }

    fun clearSnackbarMessage() {
        _state.value = _state.value.copy(snackbarMessage = null)
    }

    fun clearRestoreOutcome() {
        _state.value = _state.value.copy(restoreOutcome = null)
    }
}
