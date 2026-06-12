package com.splitandmerge.mkvslice.ui.splitconfig

import androidx.lifecycle.ViewModel
import com.splitandmerge.mkvslice.domain.model.SplitMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SplitConfigState(
    val mode: SplitMode = SplitMode.SIZE_CAP_ONLY,
    val partsCount: Int = 3,
    val sizeCapGb: Float = 9.0f,
    val outputFolder: String = "/storage/emulated/0/Movies/Bahubali (2025)",
    val baseName: String = "Bahubali (2025)",
    val predictedPartCount: Int = 3,
    val predictedPartSizeGb: Float = 8.8f
)

@HiltViewModel
class SplitConfigViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(SplitConfigState())
    val state: StateFlow<SplitConfigState> = _state.asStateFlow()

    fun updateMode(mode: SplitMode) {
        _state.value = _state.value.copy(mode = mode)
        recalculatePredictions()
    }

    fun updatePartsCount(parts: Int) {
        _state.value = _state.value.copy(partsCount = parts)
        recalculatePredictions()
    }

    fun updateSizeCap(sizeGb: Float) {
        _state.value = _state.value.copy(sizeCapGb = sizeGb)
        recalculatePredictions()
    }

    fun updateBaseName(name: String) {
        _state.value = _state.value.copy(baseName = name)
    }

    fun updateOutputFolder(folder: String) {
        _state.value = _state.value.copy(outputFolder = folder)
    }

    private fun recalculatePredictions() {
        val currentMode = _state.value.mode
        val parts = _state.value.partsCount
        val cap = _state.value.sizeCapGb
        val totalSizeGb = 26.5f

        val (predCount, predSize) = when (currentMode) {
            SplitMode.EXACT_PARTS -> Pair(parts, totalSizeGb / parts)
            SplitMode.SIZE_CAP_ONLY -> {
                val count = Math.ceil((totalSizeGb / cap).toDouble()).toInt()
                Pair(count, totalSizeGb / count)
            }
            SplitMode.BOTH -> {
                val countFromCap = Math.ceil((totalSizeGb / cap).toDouble()).toInt()
                val count = Math.max(parts, countFromCap)
                Pair(count, totalSizeGb / count)
            }
        }
        _state.value = _state.value.copy(
            predictedPartCount = predCount,
            predictedPartSizeGb = predSize
        )
    }
}
