package com.splitandmerge.mkvslice.ui.mergeorder

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class MergePart(
    val id: String,
    val index: Int,
    val name: String,
    val uriString: String,
    val sizeBytes: Long,
    val durationSec: Double,
    val errorDetails: String? = null
)

data class MergeOrderState(
    val parts: List<MergePart> = emptyList(),
    val isCompatible: Boolean = true,
    val compatibilityError: String? = null,
    val isLoading: Boolean = false,
    val verifying: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MergeOrderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ffprobeEngine: FfprobeEngine,
    private val mergeValidator: com.splitandmerge.mkvslice.domain.merger.MergeValidator
) : ViewModel() {
    private val _state = MutableStateFlow(MergeOrderState())
    val state: StateFlow<MergeOrderState> = _state.asStateFlow()

    fun removePart(partId: String) {
        val updatedList = _state.value.parts.filter { it.id != partId }
        validateList(updatedList)
    }

    fun reorderParts(fromIndex: Int, toIndex: Int) {
        val list = _state.value.parts.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            validateList(list)
        }
    }

    fun addParts(uris: List<String>) {
        _state.value = _state.value.copy(verifying = true, error = null)
        viewModelScope.launch {
            try {
                val newParts = uris.mapIndexed { index, uriStr ->
                    val uri = Uri.parse(uriStr)
                    val docFile = DocumentFile.fromSingleUri(context, uri)
                    val name = docFile?.name ?: "Unknown Part"
                    val size = docFile?.length() ?: 0L
                    val duration = ffprobeEngine.probe(uriStr).format.durationSeconds
                    
                    MergePart(
                        id = java.util.UUID.randomUUID().toString(),
                        index = _state.value.parts.size + index,
                        name = name,
                        uriString = uriStr,
                        sizeBytes = size,
                        durationSec = duration
                    )
                }
                
                val updatedList = _state.value.parts + newParts
                validateList(updatedList)
            } catch (e: Exception) {
                timber.log.Timber.tag("MergeOrder").w(e, "addParts failed")
                _state.value = _state.value.copy(
                    error = e.message ?: "Could not verify parts"
                )
            } finally {
                _state.value = _state.value.copy(verifying = false)
            }
        }
    }

    private fun validateList(list: List<MergePart>) {
        if (list.isEmpty()) {
            _state.value = _state.value.copy(
                parts = list,
                isCompatible = true,
                compatibilityError = null,
                isLoading = false
            )
            return
        }
        
        viewModelScope.launch {
            try {
                mergeValidator.validate(list.map { it.uriString })
                _state.value = _state.value.copy(
                    parts = list,
                    isCompatible = true,
                    compatibilityError = null,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    parts = list,
                    isCompatible = false,
                    compatibilityError = e.message,
                    isLoading = false
                )
            }
        }
    }

    fun getPartsUris(): String {
        return _state.value.parts.joinToString(",") { it.uriString }
    }
}
