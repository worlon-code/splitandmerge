package com.splitandmerge.mkvslice.ui.logs

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitandmerge.mkvslice.util.log.FileLoggingTree
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject

data class LogViewerState(
    val files: List<LogFile> = emptyList(),
    val selectedFile: LogFile? = null,
    val lines: List<LogLine> = emptyList(),
    val truncated: Boolean = false,
    val totalSizeBytes: Long = 0L,
    val isLoading: Boolean = false,
    val showClearConfirm: Boolean = false
)

data class LogFile(
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
)

data class LogLine(
    val lineNumber: Int,
    val text: String
)

@HiltViewModel
class LogViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val logsDir = File(context.cacheDir, "logs")

    private val _state = MutableStateFlow(LogViewerState())
    val state: StateFlow<LogViewerState> = _state.asStateFlow()

    private val _shareEvents = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<Intent> = _shareEvents.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(ioDispatcher) {
            _state.update { it.copy(isLoading = true) }
            try {
                FileLoggingTree.flushIfPlanted()
                if (!logsDir.exists()) {
                    logsDir.mkdirs()
                }
                val filesList = logsDir.listFiles()
                    ?.filter { it.isFile && it.name.startsWith("app-") && it.name.endsWith(".log") }
                    ?.map { LogFile(it.name, it.length(), it.lastModified()) }
                    ?.sortedByDescending { it.lastModified }
                    ?: emptyList()
                val totalSize = filesList.sumOf { it.sizeBytes }
                
                _state.update {
                    it.copy(
                        files = filesList,
                        totalSizeBytes = totalSize
                    )
                }

                val currentSelected = _state.value.selectedFile
                if (filesList.isNotEmpty()) {
                    if (currentSelected == null || !filesList.any { it.name == currentSelected.name }) {
                        selectFile(filesList.first().name)
                    } else {
                        // Refresh selected file content
                        val updatedSelected = filesList.first { it.name == currentSelected.name }
                        loadFileContent(updatedSelected)
                    }
                } else {
                    _state.update {
                        it.copy(
                            selectedFile = null,
                            lines = emptyList(),
                            truncated = false,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectFile(name: String?) {
        if (name == null) {
            _state.update {
                it.copy(
                    selectedFile = null,
                    lines = emptyList(),
                    truncated = false
                )
            }
            return
        }
        val targetFile = _state.value.files.find { it.name == name }
            ?: LogFile(name, File(logsDir, name).length(), File(logsDir, name).lastModified())
        loadFileContent(targetFile)
    }

    private fun loadFileContent(logFile: LogFile) {
        viewModelScope.launch(ioDispatcher) {
            _state.update { it.copy(isLoading = true) }
            try {
                FileLoggingTree.flushIfPlanted()
                val file = File(logsDir, logFile.name)
                if (file.exists()) {
                    val size = file.length()
                    val (contentBytes, isTruncated) = if (size > 1024 * 1024) {
                        val lastBytes = ByteArray(500 * 1024)
                        RandomAccessFile(file, "r").use { raf ->
                            raf.seek(size - lastBytes.size)
                            raf.readFully(lastBytes)
                        }
                        Pair(lastBytes, true)
                    } else {
                        Pair(file.readBytes(), false)
                    }
                    val text = String(contentBytes, Charsets.UTF_8)
                    val rawLines = text.split('\n')
                    val logLines = rawLines.mapIndexed { index, line ->
                        LogLine(lineNumber = index + 1, text = line)
                    }
                    _state.update {
                        it.copy(
                            selectedFile = logFile,
                            lines = logLines,
                            truncated = isTruncated,
                            isLoading = false
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            selectedFile = null,
                            lines = emptyList(),
                            truncated = false,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadFull() {
        val selected = _state.value.selectedFile ?: return
        viewModelScope.launch(ioDispatcher) {
            _state.update { it.copy(isLoading = true) }
            try {
                val file = File(logsDir, selected.name)
                if (file.exists()) {
                    val text = file.readText(Charsets.UTF_8)
                    val rawLines = text.split('\n')
                    val logLines = rawLines.mapIndexed { index, line ->
                        LogLine(lineNumber = index + 1, text = line)
                    }
                    _state.update {
                        it.copy(
                            lines = logLines,
                            truncated = false,
                            isLoading = false
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun share(name: String) {
        val file = File(logsDir, name)
        if (!file.exists()) return
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share log file")
            _shareEvents.tryEmit(chooser)
        } catch (e: Exception) {
            // Non-blocking error
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch(ioDispatcher) {
            _state.update { it.copy(isLoading = true, showClearConfirm = false) }
            try {
                FileLoggingTree.flushIfPlanted()
                val activeName = FileLoggingTree.currentFileName()
                logsDir.listFiles()
                    ?.filter { it.isFile && it.name.startsWith("app-") && it.name.endsWith(".log") }
                    ?.forEach { f ->
                        if (f.name == activeName) {
                            RandomAccessFile(f, "rw").use { it.setLength(0) }
                        } else {
                            f.delete()
                        }
                    }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setShowClearConfirm(show: Boolean) {
        _state.update { it.copy(showClearConfirm = show) }
    }
}
