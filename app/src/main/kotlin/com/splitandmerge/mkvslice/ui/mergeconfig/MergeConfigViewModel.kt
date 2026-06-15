package com.splitandmerge.mkvslice.ui.mergeconfig

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.domain.cleanup.TitleCleaner
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.PartStatus
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidation
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidator
import com.splitandmerge.mkvslice.service.JobService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class MergeConfigState(
    val outputBaseName: String = "Merged_Output",
    val outputFolder: String = "",
    val partsUris: String = "" // comma separated
)

@HiltViewModel
class MergeConfigViewModel @Inject constructor(
    private val jobDao: JobDao,
    @ApplicationContext private val context: Context,
    private val titleCleaner: TitleCleaner,
    private val settingsRepository: SettingsRepository,
    private val outputFolderValidator: OutputFolderValidator
) : ViewModel() {

    private val _validationResult = MutableStateFlow<OutputFolderValidation?>(null)
    val validationResult: StateFlow<OutputFolderValidation?> = _validationResult.asStateFlow()

    private val _state = MutableStateFlow(MergeConfigState())
    val state: StateFlow<MergeConfigState> = _state.asStateFlow()

    init {
        // Keep outputFolder in sync with the single source of truth (DataStore).
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _state.value = _state.value.copy(outputFolder = settings.defaultOutputFolderUri)
            }
        }
    }

    // For now, mock some URIs if none provided
    fun initMock(uris: String) {
        var baseName = "Merged_Output"
        val firstUriStr = uris.split(",").firstOrNull { it.isNotBlank() }
        if (firstUriStr != null) {
            try {
                val uri = Uri.parse(firstUriStr)
                val docFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                if (docFile != null && docFile.name != null) {
                    baseName = titleCleaner.cleanTitle(docFile.name!!)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        _state.value = _state.value.copy(partsUris = uris, outputBaseName = baseName)
    }

    fun updateBaseName(name: String) {
        _state.value = _state.value.copy(outputBaseName = name)
    }

    fun updateOutputFolder(uri: String) {
        val parts = _state.value.partsUris.split(",").filter { it.isNotBlank() }
        val needed = parts.mapNotNull { uriStr ->
            try {
                Uri.parse(uriStr)
            } catch (e: Exception) {
                null
            }
        }.sumOf { partUri ->
            try {
                context.contentResolver.query(
                    partUri,
                    arrayOf(android.provider.OpenableColumns.SIZE),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (index != -1) cursor.getLong(index) else 0L
                    } else 0L
                } ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

        val result = outputFolderValidator.validate(uri, needed, assumePermissionPersisted = false)
        _validationResult.value = result
        if (result is OutputFolderValidation.Ok) {
            try {
                val parsedUri = Uri.parse(uri)
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(parsedUri, flags)
            } catch (e: Exception) {
                // ignore
            }
            // Update local state immediately for responsive UI; DataStore write
            // will re-emit the same value through the collector above.
            _state.value = _state.value.copy(outputFolder = uri)
            viewModelScope.launch {
                settingsRepository.setDefaultOutputFolderUri(uri)
            }
            _validationResult.value = null
        }
    }

    fun dismissValidation() {
        _validationResult.value = null
    }

    fun onPickFolderAgain() {
        _validationResult.value = null
    }

    fun submitMergeJob(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val currentState = _state.value
            val jobId = UUID.randomUUID().toString()

            val parts = currentState.partsUris.split(",").filter { it.isNotBlank() }

            val jobEntity = JobEntity(
                id = jobId,
                type = JobType.MERGE,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                status = JobStatus.QUEUED,
                progressPct = 0,
                sourceUri = "content://mock", // Merge uses Parts table now
                outputDirUri = currentState.outputFolder.ifEmpty { "content://unknown" },
                outputBaseName = currentState.outputBaseName,
                outputContainer = ".mkv",
                manifestPath = null // Stop abusing manifestPath, we use Parts table
            )

            jobDao.upsert(jobEntity)

            parts.forEachIndexed { index, uriStr ->
                val part = PartEntity(
                    id = UUID.randomUUID().toString(),
                    jobId = jobId,
                    index = index + 1,
                    name = "Part ${index + 1}",
                    sourceUri = uriStr,
                    startSec = 0.0,
                    endSec = 0.0,
                    status = PartStatus.PENDING
                )
                jobDao.upsertPart(part)
            }

            val intent = Intent(context, JobService::class.java)
            context.startService(intent)

            onSuccess(jobId)
        }
    }
}
