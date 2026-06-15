package com.splitandmerge.mkvslice.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitandmerge.mkvslice.domain.merger.MergeFormatters
import com.splitandmerge.mkvslice.ui.splitconfirm.DetailRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeResultScreen(
    viewModel: MergeResultViewModel,
    onNavigateHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merge Result", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.width(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Error Loading Result",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.error ?: "Unknown error",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onNavigateHome) {
                        Text("Back to Library")
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Success banner
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.width(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Merging Completed!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "All selected parts merged losslessly.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Complete Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(64.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Merge Complete",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Output File Summary",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                DetailRow(label = "Filename", value = uiState.outputFilename)
                                DetailRow(label = "Total Size", value = formatSize(uiState.outputSizeBytes))
                                DetailRow(label = "Duration", value = MergeFormatters.formatDuration(uiState.durationSeconds))
                                DetailRow(label = "Container", value = if (uiState.outputFilename.endsWith(".mp4", ignoreCase = true)) "MPEG-4 (MP4)" else "Matroska (MKV)")
                                DetailRow(label = "Audio Tracks", value = uiState.audioTracks.toString())
                                DetailRow(label = "Subtitle Tracks", value = uiState.subtitleTracks.toString())
                                if (uiState.chapterCount > 0) {
                                    DetailRow(label = "Chapters", value = uiState.chapterCount.toString())
                                }
                                if (uiState.attachmentCount > 0) {
                                    DetailRow(label = "Attachments", value = uiState.attachmentCount.toString())
                                }
                                val elapsedSec = uiState.timeTakenMs / 1000L
                                val elapsedMin = elapsedSec / 60
                                val elapsedSecRem = elapsedSec % 60
                                val elapsedStr = if (elapsedMin > 0) "${elapsedMin}m ${elapsedSecRem}s" else "${elapsedSecRem}s"
                                DetailRow(label = "Time Taken", value = elapsedStr)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {},
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Video")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play Video", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {},
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share File", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = onNavigateHome,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text("Back to Library", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val gb = bytes.toDouble() / (1024 * 1024 * 1024)
    return if (gb >= 1.0) {
        String.format("%.2f GB", gb)
    } else {
        val mb = bytes.toDouble() / (1024 * 1024)
        String.format("%.2f MB", mb)
    }
}
