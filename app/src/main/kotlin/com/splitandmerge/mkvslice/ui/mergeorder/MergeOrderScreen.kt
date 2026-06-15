package com.splitandmerge.mkvslice.ui.mergeorder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeOrderScreen(
    viewModel: MergeOrderViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merge Order", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        floatingActionButton = {
            val context = androidx.compose.ui.platform.LocalContext.current
            val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
            ) { uris ->
                if (uris.isNotEmpty()) {
                    for (uri in uris) {
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (e: SecurityException) {
                            // ignore
                        }
                    }
                    viewModel.addParts(uris.map { it.toString() })
                }
            }
            FloatingActionButton(
                onClick = { filePicker.launch(arrayOf("video/x-matroska", "video/mp4", "video/*", "application/json")) },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Add Part")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Part")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (!state.isCompatible) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Codecs / resolution mismatch detected. Remove incompatible items (marked in red) to proceed.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                if (state.parts.isEmpty()) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = "No files selected. Add some video parts to merge them.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(state.parts) { index, part ->
                            val hasError = part.errorDetails != null
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hasError) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Movie,
                                            contentDescription = "Part",
                                            tint = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${index + 1}. ${part.name}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                                            )
                                            Text(
                                                text = "Size: ${formatSize(part.sizeBytes)} • Duration: ${formatDuration(part.durationSec)}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row {
                                            IconButton(
                                                onClick = { viewModel.reorderParts(index, index - 1) },
                                                enabled = index > 0
                                            ) {
                                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
                                            }
                                            IconButton(
                                                onClick = { viewModel.reorderParts(index, index + 1) },
                                                enabled = index < state.parts.lastIndex
                                            ) {
                                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
                                            }
                                            IconButton(onClick = { viewModel.removePart(part.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }

                                    if (hasError) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = part.errorDetails ?: "",
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onContinue,
                enabled = state.isCompatible && state.parts.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Continue to Merge Config", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
        String.format("%.2f GB", mb / 1024)
    } else {
        String.format("%.2f MB", mb)
    }
}

private fun formatDuration(seconds: Double): String {
    val mins = (seconds / 60).toInt()
    val secs = (seconds % 60).toInt()
    return String.format("%dm %ds", mins, secs)
}
