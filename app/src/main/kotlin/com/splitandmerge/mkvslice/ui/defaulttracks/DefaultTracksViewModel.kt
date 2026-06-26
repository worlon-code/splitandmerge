package com.splitandmerge.mkvslice.ui.defaulttracks

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.data.db.DefaultTrackFileResultDao
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.DefaultTrackFileResultEntity
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.domain.defaulttracks.DefaultTracksEngine
import com.splitandmerge.mkvslice.domain.defaulttracks.TrackAnalyser
import com.splitandmerge.mkvslice.domain.defaulttracks.model.EditSpec
import com.splitandmerge.mkvslice.domain.defaulttracks.model.TrackInfo
import com.splitandmerge.mkvslice.domain.defaulttracks.*
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.platform.io.FileSystem
import com.splitandmerge.mkvslice.service.JobService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.boolean
import java.util.UUID
import javax.inject.Inject

sealed interface DefaultTracksUiState {
    object Idle : DefaultTracksUiState
    object Picking : DefaultTracksUiState
    data class Scanning(val scannedCount: Int) : DefaultTracksUiState
    data class Analyzing(val currentFile: String, val analyzedCount: Int, val totalCount: Int) : DefaultTracksUiState
    data class ReadyList(val editingFileUri: String? = null) : DefaultTracksUiState
    data class Applying(val jobId: String, val progressText: String, val progressPct: Float) : DefaultTracksUiState
    data class Results(val jobId: String) : DefaultTracksUiState
}

data class FileRowState(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val isMkv: Boolean,
    val isChecked: Boolean = true,
    val status: String = "SCAN", // "SCAN", "PENDING", "WILL_CHANGE", "UNCHANGED", etc.
    val reason: String = "",
    val writeStrategy: String = "SKIPPED",
    val audioTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),
    val currentSpec: EditSpec? = null,
    val chosenSpec: EditSpec? = null,
    // Per-dimension choices (null == KeepCurrent); used by applyToSimilar to build Preference
    val audioChoice: AudioChoice? = null,
    val subChoice: SubChoice? = null,
    val note: String? = null,
    val matchState: RowState? = null
)

data class TrackDraft(
    val audioChoice: AudioChoice,
    val subChoice: SubChoice,
    val forcedSubtitle: Boolean
)

@HiltViewModel
class DefaultTracksViewModel @Inject constructor(
    private val jobDao: JobDao,
    private val defaultTrackFileResultDao: DefaultTrackFileResultDao,
    private val fileSystem: FileSystem,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<DefaultTracksUiState>(DefaultTracksUiState.Idle)
    val uiState: StateFlow<DefaultTracksUiState> = _uiState.asStateFlow()

    private val _filesList = MutableStateFlow<List<FileRowState>>(emptyList())
    val filesList: StateFlow<List<FileRowState>> = _filesList.asStateFlow()

    private val _drafts = MutableStateFlow<Map<String, TrackDraft>>(emptyMap())
    val drafts: StateFlow<Map<String, TrackDraft>> = _drafts.asStateFlow()

    val lastEditedUri: StateFlow<String?> = savedStateHandle.getStateFlow("last_edited_uri", null)

    fun saveDraft(uri: String, audioChoice: AudioChoice, subChoice: SubChoice, forced: Boolean) {
        val current = _drafts.value.toMutableMap()
        current[uri] = TrackDraft(audioChoice, subChoice, forced)
        _drafts.value = current
        closeEditor()
    }

    fun discardDraft(uri: String) {
        val current = _drafts.value.toMutableMap()
        current.remove(uri)
        _drafts.value = current
    }

    // Gentle hint shown near the batch button when both dimensions are KeepCurrent
    private val _applyHint = MutableStateFlow<String?>(null)
    val applyHint: StateFlow<String?> = _applyHint.asStateFlow()

    var isScanCapped = false
        private set

    private var activeJob: CoroutineJob? = null
    private var applyingJobObserver: CoroutineJob? = null

    // Restores spec map from SavedStateHandle for process death safety
    fun getSpecsMap(): Map<String, EditSpec> {
        val map = savedStateHandle.get<Map<String, String>>("edit_specs") ?: emptyMap()
        return map.mapValues { deserializeEditSpec(it.value) }
    }

    private fun putSpec(uri: String, spec: EditSpec) {
        val map = (savedStateHandle.get<Map<String, String>>("edit_specs") ?: emptyMap()).toMutableMap()
        map[uri] = serializeEditSpec(spec)
        savedStateHandle["edit_specs"] = map
    }

    private fun serializeEditSpec(spec: EditSpec): String {
        return buildJsonObject {
            put("defaultAudioTrackNumber", spec.defaultAudioTrackNumber)
            if (spec.defaultSubtitleTrackNumber != null) {
                put("defaultSubtitleTrackNumber", spec.defaultSubtitleTrackNumber)
            } else {
                put("defaultSubtitleTrackNumber", null as Long?)
            }
            put("forcedSubtitle", spec.forcedSubtitle)
        }.toString()
    }

    private fun deserializeEditSpec(jsonString: String): EditSpec {
        val element = Json.parseToJsonElement(jsonString).jsonObject
        val audio = element["defaultAudioTrackNumber"]?.jsonPrimitive?.long ?: 0L
        val sub = element["defaultSubtitleTrackNumber"]?.jsonPrimitive?.longOrNull
        val forced = element["forcedSubtitle"]?.jsonPrimitive?.boolean ?: false
        return EditSpec(audio, sub, forced)
    }

    fun dismissApplyHint() {
        _applyHint.value = null
    }

    fun startPicker() {
        _uiState.value = DefaultTracksUiState.Picking
    }

    fun cancelToPicker() {
        activeJob?.cancel()
        activeJob = null
        _filesList.value = emptyList()
        isScanCapped = false
        _applyHint.value = null
        _uiState.value = DefaultTracksUiState.Idle
    }

    fun processPickedFile(uri: Uri) {
        _applyHint.value = null
        savedStateHandle["last_edited_uri"] = null as String?
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = DefaultTracksUiState.Scanning(0)
            
            // Single file setup
            val candidate = withContext(Dispatchers.IO) {
                var displayName = "Unknown"
                var size = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                        if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
                    }
                }

                // Check .mkv extension case-insensitive and magic bytes
                val isMkvExtension = displayName.endsWith(".mkv", ignoreCase = true)
                var isMkvMagic = false
                if (isMkvExtension) {
                    isMkvMagic = try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val bytes = ByteArray(4)
                            val read = inputStream.read(bytes)
                            read == 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() && bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()
                        } ?: false
                    } catch (e: Exception) {
                        false
                    }
                }

                FileRowState(
                    uri = uri.toString(),
                    displayName = displayName,
                    sizeBytes = size,
                    isMkv = isMkvExtension && isMkvMagic,
                    status = if (isMkvExtension && isMkvMagic) "SCAN" else "SKIPPED",
                    reason = if (isMkvExtension && isMkvMagic) "" else "not-mkv",
                    isChecked = isMkvExtension && isMkvMagic
                )
            }

            _filesList.value = listOf(candidate)
            runAnalysisFlow()
        }
    }

    fun processPickedFolder(treeUri: Uri) {
        _applyHint.value = null
        savedStateHandle["last_edited_uri"] = null as String?
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = DefaultTracksUiState.Scanning(0)
            isScanCapped = false

            val list = mutableListOf<FileRowState>()
            withContext(Dispatchers.IO) {
                var scanCount = 0
                fun recurse(dirDocId: String, depth: Int) {
                    if (depth > 5 || scanCount >= 1000) {
                        isScanCapped = true
                        return
                    }

                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
                    val projection = arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE
                    )

                    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                        while (cursor.moveToNext()) {
                            if (scanCount >= 1000) {
                                isScanCapped = true
                                break
                            }

                            val docId = cursor.getString(idCol)
                            val displayName = cursor.getString(nameCol)
                            val mimeType = cursor.getString(mimeCol)
                            val size = cursor.getLong(sizeCol)
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                            if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                                recurse(docId, depth + 1)
                            } else {
                                scanCount++
                                _uiState.value = DefaultTracksUiState.Scanning(scanCount)

                                val isMkvExtension = displayName.endsWith(".mkv", ignoreCase = true)
                                var isMkvMagic = false
                                if (isMkvExtension) {
                                    isMkvMagic = try {
                                        context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                                            val bytes = ByteArray(4)
                                            val read = inputStream.read(bytes)
                                            read == 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() && bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()
                                        } ?: false
                                    } catch (e: Exception) {
                                        false
                                    }
                                }

                                list.add(
                                    FileRowState(
                                        uri = fileUri.toString(),
                                        displayName = displayName,
                                        sizeBytes = size,
                                        isMkv = isMkvExtension && isMkvMagic,
                                        status = if (isMkvExtension && isMkvMagic) "SCAN" else "SKIPPED",
                                        reason = if (isMkvExtension && isMkvMagic) "" else "not-mkv",
                                        isChecked = isMkvExtension && isMkvMagic
                                    )
                                )
                            }
                        }
                    }
                }

                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                recurse(rootDocId, 1)
            }

            _filesList.value = list
            runAnalysisFlow()
        }
    }

    private suspend fun runAnalysisFlow() {
        val currentList = _filesList.value
        val toAnalyze = currentList.filter { it.isMkv }
        val totalCount = toAnalyze.size

        if (totalCount == 0) {
            _uiState.value = DefaultTracksUiState.ReadyList()
            return
        }

        val analyzer = TrackAnalyser()
        toAnalyze.forEachIndexed { index, candidate ->
            _uiState.value = DefaultTracksUiState.Analyzing(candidate.displayName, index, totalCount)

            val rowState = withContext(Dispatchers.IO) {
                try {
                    fileSystem.openFileDescriptor(candidate.uri, "r")?.use { fd ->
                        val tracks = analyzer.analyse(fd)
                        val audios = tracks.filter { it.trackType == 2 }
                        val subs = tracks.filter { it.trackType == 17 }

                        // Detect initial defaults
                        val initialAudio = audios.find { it.flagDefault == 1 }?.trackNumber
                            ?: audios.firstOrNull()?.trackNumber
                            ?: 0L
                        val initialSub = subs.find { it.flagDefault == 1 }?.trackNumber
                        val isForced = if (initialSub != null) {
                            subs.find { it.trackNumber == initialSub }?.flagForced == 1
                        } else false

                        val spec = EditSpec(initialAudio, initialSub, isForced)

                        // Check if we have a saved spec from SavedStateHandle (process death)
                        val savedSpec = getSpecsMap()[candidate.uri]
                        val finalSpec = savedSpec ?: spec

                        candidate.copy(
                            status = if (savedSpec != null && savedSpec != spec) "WILL_CHANGE" else "UNCHANGED",
                            audioTracks = audios,
                            subtitleTracks = subs,
                            currentSpec = spec,
                            chosenSpec = finalSpec
                        )
                    } ?: candidate.copy(status = "SKIPPED", reason = "read-failed")
                } catch (e: Exception) {
                    candidate.copy(status = "SKIPPED", reason = "analyze-failed: ${e.message}")
                }
            }

            // Mutate in place
            _filesList.value = _filesList.value.map {
                if (it.uri == candidate.uri) rowState else it
            }
        }

        _uiState.value = DefaultTracksUiState.ReadyList()
    }

    fun toggleCheck(uri: String) {
        _filesList.value = _filesList.value.map {
            if (it.uri == uri && it.isMkv) it.copy(isChecked = !it.isChecked) else it
        }
    }

    fun selectAll() {
        _filesList.value = _filesList.value.map {
            if (it.isMkv) it.copy(isChecked = true) else it
        }
    }

    fun selectNone() {
        _filesList.value = _filesList.value.map {
            if (it.isMkv) it.copy(isChecked = false) else it
        }
    }

    fun openEditor(uri: String) {
        _uiState.value = DefaultTracksUiState.ReadyList(editingFileUri = uri)
    }

    fun closeEditor() {
        _uiState.value = DefaultTracksUiState.ReadyList(editingFileUri = null)
    }

    fun confirmEditor(uri: String, audioChoice: AudioChoice, subChoice: SubChoice, forced: Boolean) {
        _applyHint.value = null
        val file = _filesList.value.find { it.uri == uri }

        // Resolve AudioChoice to concrete track number
        val audioTrackNumber: Long = when (audioChoice) {
            is AudioChoice.Track -> audioChoice.trackNumber
            AudioChoice.KeepCurrent -> {
                file?.audioTracks?.find { it.flagDefault == 1 }?.trackNumber
                    ?: file?.audioTracks?.firstOrNull()?.trackNumber
                    ?: file?.currentSpec?.defaultAudioTrackNumber
                    ?: 0L
            }
        }

        // Resolve SubChoice to concrete track number (or null)
        val subtitleTrackNumber: Long? = when (subChoice) {
            is SubChoice.Track -> subChoice.trackNumber
            SubChoice.NoneSub -> null
            SubChoice.KeepCurrent -> {
                file?.subtitleTracks?.find { it.flagDefault == 1 }?.trackNumber
                    ?: file?.currentSpec?.defaultSubtitleTrackNumber
            }
        }

        // Resolve forced state
        val resolvedForced: Boolean = when (subChoice) {
            is SubChoice.Track -> forced
            SubChoice.NoneSub -> false
            SubChoice.KeepCurrent -> {
                val currentDefaultSubNum = file?.subtitleTracks?.find { it.flagDefault == 1 }?.trackNumber
                    ?: file?.currentSpec?.defaultSubtitleTrackNumber
                if (currentDefaultSubNum != null) {
                    file?.subtitleTracks?.find { it.trackNumber == currentDefaultSubNum }?.flagForced == 1
                } else false
            }
        }

        val newSpec = EditSpec(audioTrackNumber, subtitleTrackNumber, resolvedForced)
        putSpec(uri, newSpec)
        savedStateHandle["last_edited_uri"] = uri

        _filesList.value = _filesList.value.map {
            if (it.uri == uri) {
                val status = if (newSpec != it.currentSpec) "WILL_CHANGE" else "UNCHANGED"
                it.copy(
                    chosenSpec = newSpec,
                    status = status,
                    audioChoice = audioChoice,
                    subChoice = subChoice
                )
            } else it
        }
        discardDraft(uri)
        closeEditor()
    }

    fun applyToSimilar(seedUri: String) {
        _applyHint.value = null
        val currentList = _filesList.value
        val seed = currentList.find { it.uri == seedUri } ?: return

        // 1. Derive active dimensions from seed's saved choices (null == KeepCurrent)
        val seedAudioChoice: AudioChoice = seed.audioChoice ?: AudioChoice.KeepCurrent
        val seedSubChoice: SubChoice = seed.subChoice ?: SubChoice.KeepCurrent

        val audioActive = seedAudioChoice is AudioChoice.Track
        val subActive = seedSubChoice is SubChoice.Track
        val subNone = seedSubChoice is SubChoice.NoneSub

        // Both dimensions are KeepCurrent → gentle hint, do nothing
        if (!audioActive && !subActive && !subNone) {
            _applyHint.value = "Pick an audio or subtitle to apply to similar files"
            return
        }

        // 2. Build Preference from active dimensions
        val audioLang: String
        val audioRegion: String?
        if (audioActive) {
            val chosenTrackNum = (seedAudioChoice as AudioChoice.Track).trackNumber
            val seedAudioTrack = seed.audioTracks.find { it.trackNumber == chosenTrackNum }
            val rawLang = seedAudioTrack?.language ?: ""
            audioLang = LanguageNormaliser.normalizeLang(rawLang)
            audioRegion = LanguageNormaliser.extractRegion(rawLang)
        } else {
            audioLang = "und"
            audioRegion = null
        }

        val subLang: String?
        val subRegion: String?
        val forcedSub: Boolean
        if (subActive) {
            val chosenTrackNum = (seedSubChoice as SubChoice.Track).trackNumber
            val seedSubTrack = seed.subtitleTracks.find { it.trackNumber == chosenTrackNum }
            val rawLang = seedSubTrack?.language ?: ""
            subLang = LanguageNormaliser.normalizeLang(rawLang)
            subRegion = LanguageNormaliser.extractRegion(rawLang)
            forcedSub = seed.chosenSpec?.forcedSubtitle ?: false
        } else {
            subLang = null
            subRegion = null
            forcedSub = false
        }

        // Seed's actual track numbers for Rung-0 positional matching
        val seedAudioTrackNumber: Long? = if (audioActive) {
            (seedAudioChoice as AudioChoice.Track).trackNumber
        } else null

        val seedSubTrackNumber: Long? = if (subActive) {
            (seedSubChoice as SubChoice.Track).trackNumber
        } else null

        val pref = Preference(
            audioActive = audioActive,
            defaultAudioLang = audioLang,
            defaultAudioRegion = audioRegion,
            subActive = subActive,
            subNone = subNone,
            defaultSubLang = subLang,
            defaultSubRegion = subRegion,
            forcedSub = forcedSub,
            seedAudioTrackNumber = seedAudioTrackNumber,
            seedSubTrackNumber = seedSubTrackNumber
        )

        // 3. Match
        val matchResults = BatchMatcher.match(currentList, pref, seedUri)

        // 4. Stamp and update state
        _filesList.value = currentList.map { file ->
            val matchRes = matchResults.find { it.uri == file.uri }
            if (matchRes != null) {
                when (matchRes.state) {
                    RowState.MATCHED -> {
                        val spec = matchRes.resolvedSpec ?: file.currentSpec ?: EditSpec(0, null, false)
                        putSpec(file.uri, spec)
                        val status = if (spec != file.currentSpec) "WILL_CHANGE" else "UNCHANGED"
                        file.copy(
                            isChecked = true,
                            chosenSpec = spec,
                            status = status,
                            reason = "",
                            note = matchRes.note,
                            matchState = RowState.MATCHED
                        )
                    }
                    RowState.PARTIAL_NEEDS_REVIEW -> {
                        file.copy(
                            isChecked = false,
                            status = "SKIPPED",
                            reason = matchRes.reason ?: "",
                            chosenSpec = null,
                            note = null,
                            matchState = RowState.PARTIAL_NEEDS_REVIEW
                        )
                    }
                    RowState.UNCHECKED -> {
                        file.copy(
                            isChecked = false,
                            status = "UNCHANGED",
                            reason = matchRes.reason ?: "",
                            note = null,
                            matchState = RowState.UNCHECKED
                        )
                    }
                }
            } else {
                file
            }
        }
    }

    fun applyChanges() {
        val checkedRows = _filesList.value.filter { it.isChecked && it.isMkv }
        if (checkedRows.isEmpty()) return

        viewModelScope.launch {
            val jobId = UUID.randomUUID().toString()
            
            // Insert JobEntity
            val jobEntity = JobEntity(
                id = jobId,
                type = JobType.SET_DEFAULT_TRACKS,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                status = JobStatus.QUEUED,
                progressPct = 0,
                sourceUri = "",
                outputDirUri = "",
                outputBaseName = if (checkedRows.size == 1) checkedRows.first().displayName else "Batch of ${checkedRows.size} files",
                outputContainer = ".mkv"
            )
            jobDao.upsert(jobEntity)

            // Pre-write DefaultTrackFileResultEntity entries as source of truth
            val results = checkedRows.mapIndexed { index, row ->
                val spec = row.chosenSpec ?: row.currentSpec ?: EditSpec(0, null, false)
                DefaultTrackFileResultEntity(
                    id = UUID.randomUUID().toString(),
                    jobId = jobId,
                    uri = row.uri,
                    displayName = row.displayName,
                    status = "PENDING",
                    reason = "",
                    writeStrategy = "SKIPPED",
                    appliedSpecJson = serializeEditSpec(spec),
                    createdAt = System.currentTimeMillis() + index
                )
            }
            defaultTrackFileResultDao.insertAll(results)

            // Launch service
            val serviceIntent = Intent(context, JobService::class.java)
            try {
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                context.startService(serviceIntent)
            }

            // Transition to Applying UI
            _uiState.value = DefaultTracksUiState.Applying(jobId, "Enqueuing...", 0f)
            observeApplyingJob(jobId)
        }
    }

    private fun observeApplyingJob(jobId: String) {
        applyingJobObserver?.cancel()
        applyingJobObserver = viewModelScope.launch {
            // Observe Job progress
            jobDao.observeById(jobId).collectLatest { job ->
                if (job == null) return@collectLatest

                val fileResults = defaultTrackFileResultDao.getResultsForJob(jobId)
                
                // Update filesList with terminal statuses from DB
                _filesList.value = _filesList.value.map { row ->
                    val dbResult = fileResults.find { it.uri == row.uri }
                    if (dbResult != null) {
                        row.copy(
                            status = dbResult.status,
                            reason = dbResult.reason,
                            writeStrategy = dbResult.writeStrategy
                        )
                    } else {
                        row
                    }
                }

                val pendingCount = fileResults.count { it.status == "PENDING" }
                val completedCount = fileResults.size - pendingCount
                
                val currentFileText = fileResults.find { it.status == "PENDING" }?.displayName ?: ""

                val progressText = if (pendingCount > 0) {
                    "Processing file ${completedCount + 1} of ${fileResults.size}: $currentFileText"
                } else {
                    "Completing job..."
                }

                _uiState.value = DefaultTracksUiState.Applying(
                    jobId = jobId,
                    progressText = progressText,
                    progressPct = job.progressPct / 100f
                )

                // Match terminal states
                if (job.status == JobStatus.DONE || job.status == JobStatus.FAILED || job.status == JobStatus.CANCELLED) {
                    _uiState.value = DefaultTracksUiState.Results(jobId)
                    applyingJobObserver?.cancel()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
        applyingJobObserver?.cancel()
    }
}
