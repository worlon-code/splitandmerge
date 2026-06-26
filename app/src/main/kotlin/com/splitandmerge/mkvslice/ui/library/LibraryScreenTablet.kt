package com.splitandmerge.mkvslice.ui.library

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.FloatingActionButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import androidx.compose.ui.platform.LocalContext
import com.splitandmerge.mkvslice.domain.model.Job
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.runtime.LaunchedEffect
import com.splitandmerge.mkvslice.ui.components.JobDetailSheet
import com.splitandmerge.mkvslice.domain.model.JobStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreenTablet(
    viewModel: LibraryViewModel,
    onNavigateToSettings: () -> Unit,
    onStartSplitFlow: (uri: String, filename: String) -> Unit,
    onStartMergeFlow: () -> Unit,
    onStartDefaultTracksFlow: () -> Unit,
    onNavigateToJobDetail: (String) -> Unit,
    onNavigateToSplitResult: (String) -> Unit,
    onNavigateToMergeResult: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val jobs = state.jobs
    var selectedJob by remember { mutableStateOf<Job?>(null) }
    var detailSheetJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { command ->
            when (command) {
                is LibraryViewModel.NavCommand.ToProgress -> onNavigateToJobDetail(command.jobId)
                is LibraryViewModel.NavCommand.ToSplitResult -> onNavigateToSplitResult(command.jobId)
                is LibraryViewModel.NavCommand.ToMergeResult -> onNavigateToMergeResult(command.jobId)
                is LibraryViewModel.NavCommand.ToDetailSheet -> {
                    detailSheetJob = jobs.find { it.id == command.jobId }
                }
                LibraryViewModel.NavCommand.DoNothing -> {}
            }
        }
    }

    val splitFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            var filename = "Unknown"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    filename = cursor.getString(nameIndex)
                }
            }
            onStartSplitFlow(uri.toString(), filename)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Spacer(modifier = Modifier.weight(1f))
            NavigationRailItem(
                selected = true,
                onClick = {},
                icon = { Icon(Icons.Default.CallSplit, contentDescription = "Home") },
                label = { Text("Home") }
            )
            NavigationRailItem(
                selected = false,
                onClick = onNavigateToSettings,
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = { Text("Settings") }
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("MKV Slice (Tablet)", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(
                        onClick = onStartDefaultTracksFlow,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Flag, contentDescription = "Set Default Tracks")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Set defaults")
                        }
                    }
                    FloatingActionButton(
                        onClick = onStartMergeFlow,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CallMerge, contentDescription = "New Merge")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Merge")
                        }
                    }
                    FloatingActionButton(
                        onClick = { splitFilePicker.launch(arrayOf("video/x-matroska", "video/mp4", "video/*")) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CallSplit, contentDescription = "New Split")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Split")
                        }
                    }
                }
            }
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
            ) {
                // Left Pane: Job List
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Jobs",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onStartDefaultTracksFlow) {
                            Icon(Icons.Default.Flag, contentDescription = "Set Default Tracks")
                        }
                        IconButton(onClick = { splitFilePicker.launch(arrayOf("video/x-matroska", "video/mp4", "video/*")) }) {
                            Icon(Icons.Default.CallSplit, contentDescription = "Split")
                        }
                        IconButton(onClick = onStartMergeFlow) {
                            Icon(Icons.Default.CallMerge, contentDescription = "Merge")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (state.isInitialLoad) {
                            items(3) {
                                ShimmerJobRow()
                            }
                        } else {
                            items(jobs) { job ->
                                JobItemRow(
                                    job = job,
                                    onClick = {
                                        selectedJob = job
                                        viewModel.handleIntent(LibraryIntent.RowTapped(job.id))
                                    }
                                )
                            }
                        }
                    }
                }

                // Right Pane: Detail Pane
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selectedJob == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Select a job from the list to view details",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val job = selectedJob!!
                        Text(
                            text = "Job Details",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(
                                    text = job.outputBaseName,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Type: ${job.type.name}")
                                Text("Status: ${job.status.name}")
                                Text("Created At: ${formatDate(job.createdAt)}")
                                if (job.errorDetails != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Error: ${job.errorDetails}",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { viewModel.handleIntent(LibraryIntent.RowTapped(job.id)) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = when (job.status) {
                                            JobStatus.CANCELLED, JobStatus.FAILED -> "Show details"
                                            else -> "Open Progress / Result"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (detailSheetJob != null) {
        JobDetailSheet(
            job = detailSheetJob!!,
            onRetry = { viewModel.handleIntent(LibraryIntent.RetryJob(detailSheetJob!!.id)) },
            onDelete = { viewModel.handleIntent(LibraryIntent.DeleteJob(detailSheetJob!!.id)) },
            onDismiss = { detailSheetJob = null }
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
