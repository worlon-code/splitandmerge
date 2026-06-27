package com.splitandmerge.mkvslice.ui.rename

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.data.db.entity.CleanupPatternEntity
import com.splitandmerge.mkvslice.data.repository.CleanupRepository
import com.splitandmerge.mkvslice.data.repository.RenameRepository
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.domain.cleanup.cleanTitleWith
import com.splitandmerge.mkvslice.domain.rename.RenameInputItem
import com.splitandmerge.mkvslice.domain.rename.RenamePlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

import com.splitandmerge.mkvslice.domain.rename.DocumentRenamer
import com.splitandmerge.mkvslice.domain.rename.RenameOutcome
import com.splitandmerge.mkvslice.domain.rename.RenameStatus
import com.splitandmerge.mkvslice.domain.rename.RenameResult
import com.splitandmerge.mkvslice.domain.rename.RenameBatchResult
import com.splitandmerge.mkvslice.domain.rename.RenameDecision
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException

sealed interface RenameVideosUiState {
    object Idle : RenameVideosUiState
    data class Scanning(val scannedCount: Int) : RenameVideosUiState
    data class ReadyList(val selectedRowId: String? = null) : RenameVideosUiState
    data class Processing(val current: Int, val total: Int) : RenameVideosUiState
    data class Results(val batchResult: RenameBatchResult) : RenameVideosUiState
}

/** State for the inline-create pattern form inside the Rename screen. */
data class InlineCreateState(
    val regex: String = "",
    val label: String = "",
    val replacement: String = "",
    val regexError: String? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class RenameVideosViewModel @Inject constructor(
    private val renameRepository: RenameRepository,
    private val cleanupRepository: CleanupRepository,
    private val settingsRepository: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow<RenameVideosUiState>(RenameVideosUiState.Idle)
    val uiState: StateFlow<RenameVideosUiState> = _uiState.asStateFlow()

    /** Reflects the user's "Keep screen on during job" preference. */
    val keepScreenOn: StateFlow<Boolean> = settingsRepository.settingsFlow
        .map { it.keepScreenOn }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    private val _filesList = MutableStateFlow<List<RenameFileRowState>>(emptyList())
    val filesList: StateFlow<List<RenameFileRowState>> = _filesList.asStateFlow()

    private val _perRowAutoSuffix = MutableStateFlow<Set<String>>(emptySet())
    val perRowAutoSuffix: StateFlow<Set<String>> = _perRowAutoSuffix.asStateFlow()

    val cleanupPatterns = cleanupRepository.observePatterns()

    /**
     * IDs of patterns the user has checked in the pattern subset panel.
     * Starts as ALL pattern IDs (seeded when the first non-empty pattern list arrives).
     * Never mutates CleanupPatternEntity.enabled — this is a local UI selection only.
     */
    private val _selectedPatternIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPatternIds: StateFlow<Set<String>> = _selectedPatternIds.asStateFlow()

    /** Whether the initial "select all" seed has been applied after first load. */
    private var patternIdsSeeded = false

    /** Inline-create form state for adding a new pattern directly from the Rename screen. */
    private val _inlineCreateState = MutableStateFlow(InlineCreateState())
    val inlineCreateState: StateFlow<InlineCreateState> = _inlineCreateState.asStateFlow()

    private val manuallyEditedIds = mutableSetOf<String>()
    private var cachedPatterns: List<CleanupPatternEntity> = emptyList()
    private var renameJob: Job? = null
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            cleanupRepository.observePatterns().collectLatest { patterns ->
                cachedPatterns = patterns

                // Seed selectedPatternIds to all IDs on first non-empty load only.
                // After that, new patterns added via inline-create are also pre-selected.
                if (!patternIdsSeeded && patterns.isNotEmpty()) {
                    _selectedPatternIds.value = patterns.map { it.id }.toSet()
                    patternIdsSeeded = true
                } else if (patternIdsSeeded) {
                    // Auto-select any newly-added patterns (e.g., from inline-create)
                    val known = _selectedPatternIds.value
                    val incoming = patterns.map { it.id }.toSet()
                    val newIds = incoming - known
                    if (newIds.isNotEmpty()) {
                        _selectedPatternIds.value = known + newIds
                    }
                }

                updateFilesWithPatterns(patterns, _selectedPatternIds.value)
            }
        }
    }

    // ── Pattern subset panel ──────────────────────────────────────────────────

    /**
     * Toggle a single pattern in the subset selection.
     * Does NOT mutate CleanupPatternEntity.enabled.
     */
    fun togglePatternSelection(patternId: String) {
        val current = _selectedPatternIds.value.toMutableSet()
        if (patternId in current) {
            current.remove(patternId)
        } else {
            current.add(patternId)
        }
        _selectedPatternIds.value = current
        updateFilesWithPatterns(cachedPatterns, current)
    }

    /**
     * Select or deselect all patterns in the subset selection.
     */
    fun toggleAllPatternsSelection(select: Boolean) {
        val next = if (select) {
            cachedPatterns.map { it.id }.toSet()
        } else {
            emptySet()
        }
        _selectedPatternIds.value = next
        updateFilesWithPatterns(cachedPatterns, next)
    }

    // ── Inline-create ─────────────────────────────────────────────────────────

    fun updateInlineRegex(regex: String) {
        _inlineCreateState.value = _inlineCreateState.value.copy(regex = regex, regexError = null)
    }

    fun updateInlineLabel(label: String) {
        _inlineCreateState.value = _inlineCreateState.value.copy(label = label)
    }

    fun updateInlineReplacement(replacement: String) {
        _inlineCreateState.value = _inlineCreateState.value.copy(replacement = replacement)
    }

    /**
     * Validate and persist a new pattern.
     * Validates Regex(regex) compiles before writing.
     * Constructs CleanupPatternEntity with fresh UUID, isBuiltIn=false,
     * appended orderIndex (max existing + 1).
     * On success: resets the form; the new pattern appears in the panel pre-selected
     * via the auto-seed logic in init{}.
     */
    fun createInlinePattern() {
        val form = _inlineCreateState.value
        val regex = form.regex.trim()
        val label = form.label.trim().ifEmpty { regex }
        val replacement = form.replacement

        // Validate regex compiles
        try {
            Regex(regex)
        } catch (e: Exception) {
            _inlineCreateState.value = form.copy(regexError = "Invalid regex: ${e.message?.take(80)}")
            return
        }
        if (regex.isEmpty()) {
            _inlineCreateState.value = form.copy(regexError = "Regex cannot be empty")
            return
        }

        _inlineCreateState.value = form.copy(isSaving = true, regexError = null)

        viewModelScope.launch(ioDispatcher) {
            val allExisting = cleanupRepository.getAllPatterns()
            val maxOrder = allExisting.maxOfOrNull { it.orderIndex } ?: -1
            val entity = CleanupPatternEntity(
                id = UUID.randomUUID().toString(),
                regex = regex,
                replacement = replacement,
                enabled = true,
                isBuiltIn = false,
                orderIndex = maxOrder + 1,
                label = label,
                createdAt = System.currentTimeMillis()
            )
            cleanupRepository.upsert(entity)

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _inlineCreateState.value = InlineCreateState() // reset form
            }
        }
    }

    fun resetInlineCreateForm() {
        _inlineCreateState.value = InlineCreateState()
    }

    // ── File picking ──────────────────────────────────────────────────────────

    fun startPicking() {
        _uiState.value = RenameVideosUiState.Idle
    }

    fun cancelToIdle() {
        scanJob?.cancel()
        renameJob?.cancel()
        _filesList.value = emptyList()
        _perRowAutoSuffix.value = emptySet()
        manuallyEditedIds.clear()
        _inlineCreateState.value = InlineCreateState()
        _selectedPatternIds.value = emptySet()
        patternIdsSeeded = false
        _uiState.value = RenameVideosUiState.Idle
    }

    fun processPickedFiles(uris: List<Uri>) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.value = RenameVideosUiState.Scanning(0)
            val scanned = withContext(ioDispatcher) {
                renameRepository.scanPickedFiles(uris)
            }
            _filesList.value = scanned
            // Auto-opt-in every picked-file row for auto-suffix.
            // Policy: picked-file collisions are ALWAYS resolved via (N) suffix — never skip.
            // This makes intra-batch picked duplicates auto-suffix in preview (movie.mkv +
            // movie (1).mkv). Existing-disk collisions are handled at STEP-9 apply time.
            _perRowAutoSuffix.value = scanned.map { it.id }.toSet()
            updateFilesWithPatterns(cachedPatterns, _selectedPatternIds.value)
            _uiState.value = RenameVideosUiState.ReadyList()
        }
    }

    fun processPickedFolder(treeUri: Uri, includeSubfolders: Boolean) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.value = RenameVideosUiState.Scanning(0)
            val result = withContext(ioDispatcher) {
                renameRepository.scanFolder(treeUri, includeSubfolders) { progressCount ->
                    _uiState.value = RenameVideosUiState.Scanning(progressCount)
                }
            }
            _filesList.value = result.files
            updateFilesWithPatterns(cachedPatterns, _selectedPatternIds.value)
            _uiState.value = RenameVideosUiState.ReadyList()
        }
    }

    // ── File list operations ──────────────────────────────────────────────────

    fun toggleCheck(id: String) {
        _filesList.value = _filesList.value.map {
            if (it.id == id) it.copy(isChecked = !it.isChecked) else it
        }
        recalculateRenamePlan()
    }

    fun selectAll() {
        _filesList.value = _filesList.value.map { it.copy(isChecked = true) }
        recalculateRenamePlan()
    }

    fun selectNone() {
        _filesList.value = _filesList.value.map { it.copy(isChecked = false) }
        recalculateRenamePlan()
    }

    fun toggleAutoSuffix(id: String) {
        val current = _perRowAutoSuffix.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _perRowAutoSuffix.value = current
        recalculateRenamePlan()
    }

    fun updateManualName(id: String, newName: String) {
        manuallyEditedIds.add(id)
        _filesList.value = _filesList.value.map {
            if (it.id == id) it.copy(newBaseName = newName) else it
        }
        recalculateRenamePlan()
    }

    fun selectRowForPreview(id: String?) {
        val current = _uiState.value
        if (current is RenameVideosUiState.ReadyList) {
            _uiState.value = RenameVideosUiState.ReadyList(selectedRowId = id)
        }
    }

    fun executeRename(renamer: DocumentRenamer = renameRepository) {
        val targetRows = _filesList.value.filter { it.isChecked }
        if (targetRows.isEmpty()) return

        renameJob?.cancel()
        renameJob = viewModelScope.launch {
            _uiState.value = RenameVideosUiState.Processing(0, targetRows.size)
            val resultsList = mutableListOf<RenameResult>()
            var successCount = 0
            var failedCount = 0
            var skippedCount = 0
            var excludedCount = 0

            val succeededRowIds = mutableSetOf<String>()
            val succeededRowNewNames = mutableMapOf<String, String>()

            try {
                for ((index, item) in targetRows.withIndex()) {
                    if (!isActive) break

                    _uiState.value = RenameVideosUiState.Processing(index, targetRows.size)

                    val oldName = item.displayName
                    val target = item.targetName

                    // Map pre-planned non-RENAME decisions directly
                    when (item.decision) {
                        RenameDecision.SKIP_COLLISION -> {
                            resultsList.add(RenameResult(item.uri, oldName, target, RenameStatus.Skipped("Collision detected in preview")))
                            skippedCount++
                            continue
                        }
                        RenameDecision.EXCLUDED_INVALID -> {
                            resultsList.add(RenameResult(item.uri, oldName, target, RenameStatus.Excluded("Invalid target name")))
                            excludedCount++
                            continue
                        }
                        RenameDecision.EXCLUDED_UNRENAMABLE -> {
                            resultsList.add(RenameResult(item.uri, oldName, target, RenameStatus.Excluded("Document does not support rename")))
                            excludedCount++
                            continue
                        }
                        RenameDecision.EXCLUDED_UNVERIFIABLE -> {
                            resultsList.add(RenameResult(item.uri, oldName, target, RenameStatus.Excluded("Metadata unverifiable")))
                            excludedCount++
                            continue
                        }
                        RenameDecision.NO_CHANGE -> {
                            // NO_CHANGE checked rows are skipped silently without status logging
                            continue
                        }
                        RenameDecision.RENAME -> {
                            // Proceed to rename execution
                        }
                    }

                    // 1. Re-assert FLAG_SUPPORTS_RENAME immediately before rename (TOCTOU check)
                    if (!renamer.supportsRename(item.uri)) {
                        resultsList.add(RenameResult(item.uri, oldName, target, RenameStatus.Excluded("Document does not support rename (TOCTOU)")))
                        excludedCount++
                        continue
                    }

                    if (item.isPickedFile) {
                        // Picked rows retry logic (deferred collision resolution)
                        var finalOutcome: RenameResult? = null
                        var lastError = "Unknown failure"
                        var isPermissionDenied = false

                        // Try first with raw target name
                        when (val outcome = renamer.rename(item.uri, target)) {
                            is RenameOutcome.Success -> {
                                val resultName = outcome.actualName
                                if (resultName == target) {
                                    finalOutcome = RenameResult(item.uri, oldName, target, RenameStatus.Success)
                                } else if (resultName != oldName) {
                                    // Accept provider auto-named/auto-suffixed result
                                    finalOutcome = RenameResult(item.uri, oldName, resultName, RenameStatus.Success)
                                } else {
                                    // Result == oldName means rename silently no-oped; treat as conflict
                                    lastError = "Rename silently no-oped (name unchanged)"
                                }
                            }
                            is RenameOutcome.Failure -> {
                                lastError = outcome.errorMessage
                                if (outcome.isPermissionError) {
                                    isPermissionDenied = true
                                }
                            }
                        }

                        if (finalOutcome == null && !isPermissionDenied) {
                            // Retry with "base (N).ext" for N = 1..50
                            for (n in 1..50) {
                                if (!isActive) break
                                val suffixName = "${item.newBaseName} ($n)${item.extension}"
                                when (val outcome = renamer.rename(item.uri, suffixName)) {
                                    is RenameOutcome.Success -> {
                                        val resultName = outcome.actualName
                                        if (resultName == suffixName) {
                                            finalOutcome = RenameResult(item.uri, oldName, suffixName, RenameStatus.Success)
                                            break
                                        } else if (resultName != oldName) {
                                            finalOutcome = RenameResult(item.uri, oldName, resultName, RenameStatus.Success)
                                            break
                                        } else {
                                            lastError = "Rename silently no-oped for suffix ($n)"
                                        }
                                    }
                                    is RenameOutcome.Failure -> {
                                        lastError = outcome.errorMessage
                                        if (outcome.isPermissionError) {
                                            isPermissionDenied = true
                                            break
                                        }
                                    }
                                }
                            }
                        }

                        if (finalOutcome != null) {
                            resultsList.add(finalOutcome)
                            successCount++
                            succeededRowIds.add(item.id)
                            succeededRowNewNames[item.id] = finalOutcome.newName
                        } else {
                            val failureReason = if (isPermissionDenied) lastError else "Max retries exceeded / $lastError"
                            resultsList.add(RenameResult(item.uri, oldName, target, RenameStatus.Failed(failureReason)))
                            failedCount++
                        }

                    } else {
                        // Folder rows logic (no retries)
                        when (val outcome = renamer.rename(item.uri, target)) {
                            is RenameOutcome.Success -> {
                                val resultName = outcome.actualName
                                if (resultName == target) {
                                    resultsList.add(RenameResult(item.uri, oldName, target, RenameStatus.Success))
                                    successCount++
                                    succeededRowIds.add(item.id)
                                    succeededRowNewNames[item.id] = target
                                } else {
                                    resultsList.add(RenameResult(item.uri, oldName, target, RenameStatus.Failed("Actual name $resultName did not match target $target")))
                                    failedCount++
                                }
                            }
                            is RenameOutcome.Failure -> {
                                resultsList.add(RenameResult(item.uri, oldName, target, RenameStatus.Failed(outcome.errorMessage)))
                                failedCount++
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Caught coroutine cancellation, ensure we still emit progress results
                throw e
            } finally {
                // Ensure in-memory rows and Results UI are updated even if job was cancelled
                withContext(NonCancellable) {
                    _filesList.value = _filesList.value.map { file ->
                        if (file.id in succeededRowIds) {
                            val newName = succeededRowNewNames[file.id] ?: file.targetName
                            file.copy(
                                displayName = newName,
                                originalBaseName = newName.substringBeforeLast(".", newName),
                                extension = if (newName.contains(".")) "." + newName.substringAfterLast(".") else "",
                                newBaseName = newName.substringBeforeLast(".", newName),
                                targetName = newName,
                                isChecked = false,
                                decision = RenameDecision.NO_CHANGE
                            )
                        } else {
                            file
                        }
                    }

                    _uiState.value = RenameVideosUiState.Results(
                        RenameBatchResult(
                            results = resultsList,
                            successCount = successCount,
                            failedCount = failedCount,
                            skippedCount = skippedCount,
                            excludedCount = excludedCount
                        )
                    )
                }
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Recomputes newBaseName for all non-manually-edited rows using only the
     * patterns whose IDs are in [selectedIds] — regardless of their enabled flag.
     * This is the CHECKPOINT rule: checkbox selection drives cleanTitleWith, not enabled.
     */
    private fun updateFilesWithPatterns(
        patterns: List<CleanupPatternEntity>,
        selectedIds: Set<String>
    ) {
        val currentFiles = _filesList.value

        // Build the subset: from all patterns, keep only those whose ID is selected,
        // sorted by orderIndex (cleanTitleWith also sorts but be explicit).
        val subsetPatterns = patterns
            .filter { it.id in selectedIds }
            .sortedBy { it.orderIndex }

        val updated = currentFiles.map { file ->
            if (file.id !in manuallyEditedIds) {
                val cleaned = cleanTitleWith(file.originalBaseName, subsetPatterns)
                file.copy(newBaseName = cleaned)
            } else {
                file
            }
        }
        _filesList.value = updated
        recalculateRenamePlan()
    }

    private fun recalculateRenamePlan() {
        val files = _filesList.value
        if (files.isEmpty()) return

        val inputs = files.map { file ->
            RenameInputItem(
                id = file.id,
                oldDisplayName = file.displayName,
                newBaseName = file.newBaseName,
                supportsRename = file.supportsRename,
                parentKnown = file.parentKnown,
                existingNamesInParent = file.existingNamesInParent,
                parentKey = file.parentKey
            )
        }
        val planResults = RenamePlanner.plan(inputs, _perRowAutoSuffix.value)

        _filesList.value = files.mapIndexed { index, file ->
            val result = planResults[index]
            file.copy(
                decision = result.decision,
                targetName = result.targetName
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        renameJob?.cancel()
    }
}
