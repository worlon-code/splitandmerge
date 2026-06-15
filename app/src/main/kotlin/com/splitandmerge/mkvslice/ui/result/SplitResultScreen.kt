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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
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
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitResultScreen(
    jobId: String,
    onMergePartsShortcut: () -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: SplitResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split Result", fontWeight = FontWeight.Bold) },
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
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.error != null -> {
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
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            // Success banner
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
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
                                            text = "Split Completed Successfully!",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "${uiState.partCount} part${if (uiState.partCount != 1) "s" else ""} generated from source video.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "Generated Files",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            LazyColumn(
                                modifier = Modifier.weight(1f)
                            ) {
                                itemsIndexed(uiState.outputs) { index, outputFile ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Movie,
                                                contentDescription = "Video file",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = outputFile.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (outputFile.sizeBytes > 0L) {
                                                        "Size: ${formatSizeResult(outputFile.sizeBytes)}"
                                                    } else {
                                                        "Size: —"
                                                    },
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = {}) {
                                                Icon(
                                                    Icons.Default.Share,
                                                    contentDescription = "Share",
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onMergePartsShortcut,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                Icon(Icons.Default.CallMerge, contentDescription = "Merge parts")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Merge these parts back",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = onNavigateHome,
                                shape = RoundedCornerShape(12.dp),
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
}

private fun formatSizeResult(bytes: Long): String {
    val gb = bytes.toDouble() / (1024 * 1024 * 1024)
    return String.format("%.2f GB", gb)
}
