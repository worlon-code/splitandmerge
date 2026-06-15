package com.splitandmerge.mkvslice.ui.oss

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
data class LicenseNotice(
    val name: String,
    val publisher: String,
    val license: String,
    val licenseTextOrUrl: String
)

data class OssNoticesUiState(
    val isLoading: Boolean = true,
    val notices: List<LicenseNotice> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class OssNoticesViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(OssNoticesUiState())
    val uiState: StateFlow<OssNoticesUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadNotices()
    }

    fun retry() {
        _uiState.update { OssNoticesUiState(isLoading = true) }
        loadNotices()
    }

    private fun loadNotices() {
        viewModelScope.launch {
            try {
                val jsonString = context.assets
                    .open("oss-licenses.json")
                    .bufferedReader()
                    .use { it.readText() }
                val notices = json.decodeFromString<List<LicenseNotice>>(jsonString)
                _uiState.update { OssNoticesUiState(isLoading = false, notices = notices) }
            } catch (e: Exception) {
                _uiState.update {
                    OssNoticesUiState(
                        isLoading = false,
                        error = "Could not load license information."
                    )
                }
            }
        }
    }
}
