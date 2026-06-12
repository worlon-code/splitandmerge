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
        loadMockParts()
    }

    private fun loadMockParts() {
        _state.value = MergeOrderState(
            parts = listOf(
                MergePart("1", 0, "Bahubali (2025).part001.mkv", 9239850123, 3120.0),
                MergePart("2", 1, "Bahubali (2025).part002.mkv", 9123850123, 3080.0),
                MergePart("3", 2, "Kantara.part001.mkv", 8823850123, 2900.0, "Mismatched codecs: HEVC vs H264"),
                MergePart("4", 3, "Bahubali (2025).part003.mkv", 9212850123, 3150.0)
            ),
            isCompatible = false
        )
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
}
