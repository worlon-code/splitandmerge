package com.splitandmerge.mkvslice.ui.logs

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    viewModel: LogViewerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 600

    LaunchedEffect(viewModel) {
        viewModel.shareEvents.collect { intent ->
            context.startActivity(intent)
        }
    }

    BackHandler(enabled = !isWide && state.selectedFile != null) {
        viewModel.selectFile(null)
    }

    if (state.showClearConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowClearConfirm(false) },
            title = { Text("Clear all logs?") },
            text = { Text("This will permanently delete all diagnostic logs except today's active log, which will be cleared to 0 bytes.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllLogs() }) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowClearConfirm(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostic logs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!isWide && state.selectedFile != null) {
                                viewModel.selectFile(null)
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { state.selectedFile?.let { viewModel.share(it.name) } },
                        enabled = state.selectedFile != null
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Log")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear all logs") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.setShowClearConfirm(true)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Left pane - File list
                Box(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                ) {
                    FileListSection(
                        state = state,
                        onFileSelected = { viewModel.selectFile(it) }
                    )
                }

                Divider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                )

                // Right pane - Content
                Box(
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight()
                ) {
                    ContentSection(
                        state = state,
                        onLoadFull = { viewModel.loadFull() }
                    )
                }
            }
        } else {
            // Phone screen layout
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (state.selectedFile == null) {
                    FileListSection(
                        state = state,
                        onFileSelected = { viewModel.selectFile(it) }
                    )
                } else {
                    ContentSection(
                        state = state,
                        onLoadFull = { viewModel.loadFull() }
                    )
                }
            }
        }
    }
}

@Composable
fun FileListSection(
    state: LogViewerState,
    onFileSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (state.files.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No diagnostic logs yet. Logs are written when the app encounters errors or runs jobs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(state.files) { file ->
                    val isSelected = state.selectedFile?.name == file.name
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { onFileSelected(file.name) }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = file.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = formatSize(file.sizeBytes),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = getRelativeDateString(file.lastModified),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Divider()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Logs are stored locally for 7 days. They are never sent automatically.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ContentSection(
    state: LogViewerState,
    onLoadFull: () -> Unit
) {
    val selected = state.selectedFile
    if (selected == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Select a log file to view its contents.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val listState = rememberLazyListState()

    // Auto-scroll to the bottom of log file on load
    LaunchedEffect(state.lines) {
        if (state.lines.isNotEmpty()) {
            listState.scrollToItem(state.lines.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (state.truncated) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Showing last 500 KB of log file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Button(
                        onClick = onLoadFull,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Load Full (${formatSize(selected.sizeBytes)})",
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    itemsIndexed(state.lines) { _, line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%4d  ", line.lineNumber),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.width(42.dp)
                            )
                            Text(
                                text = line.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Bottom fade-out gradient
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    return if (bytes < 1024 * 1024) {
        String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun getRelativeDateString(timestamp: Long): String {
    return try {
        val instant = Instant.ofEpochMilli(timestamp)
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        val days = ChronoUnit.DAYS.between(date, today)
        when {
            days == 0L -> "today"
            days == 1L -> "yesterday"
            days > 1L -> "$days days ago"
            else -> date.toString()
        }
    } catch (e: Exception) {
        "unknown"
    }
}
