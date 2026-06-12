package com.splitandmerge.mkvslice.ui.mergeorder

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class MergePart(
    val id: String,
    val index: Int,
    val name: String,
    val sizeBytes: Long,
    val durationSec: Double,
    val errorDetails: String? = null
)

data class MergeOrderState(
    val parts: List<MergePart> = emptyList(),
    val isCompatible: Boolean = true
)

@HiltViewModel
class MergeOrderViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(MergeOrderState())
    val state: StateFlow<MergeOrderState> = _state.asStateFlow()

    init {
        // Start empty
    }

    fun removePart(partId: String) {
        val updatedList = _state.value.parts.filter { it.id != partId }
        val compatible = updatedList.none { it.errorDetails != null }
        _state.value = _state.value.copy(parts = updatedList, isCompatible = compatible)
    }

    fun reorderParts(fromIndex: Int, toIndex: Int) {
        val list = _state.value.parts.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _state.value = _state.value.copy(parts = list)
        }
    }

    fun addParts(uris: List<String>) {
        val newParts = uris.mapIndexed { index, uri ->
            // Just a basic representation. Real app would probe the files to get real sizes
            MergePart(
                id = java.util.UUID.randomUUID().toString(),
                index = _state.value.parts.size + index,
                name = uri, // Use URI as name temporarily
                sizeBytes = 0L,
                durationSec = 0.0
            )
        }
        val updatedList = _state.value.parts + newParts
        _state.value = _state.value.copy(
            parts = updatedList,
            isCompatible = true // Assume compatible for now since we skip actual probing
        )
    }

    fun getPartsUris(): String {
        return _state.value.parts.joinToString(",") { it.name } // Name stores URI for now
    }
}
