package com.splitandmerge.mkvslice.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.splitandmerge.mkvslice.domain.model.Job
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToSettings: () -> Unit,
    onStartSplitFlow: () -> Unit,
    onStartMergeFlow: () -> Unit,
    onNavigateToJobDetail: (String) -> Unit
) {
    val jobs by viewModel.jobs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MKV Slice", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
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
                    onClick = onStartSplitFlow,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            if (jobs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent jobs. Tap Split or Merge to start.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    item {
                        Text(
                            text = "Recent Jobs",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    items(jobs) { job ->
                        JobItemRow(job = job, onClick = { onNavigateToJobDetail(job.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun JobItemRow(job: Job, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (job.type == JobType.SPLIT) Icons.Default.CallSplit else Icons.Default.CallMerge,
                    contentDescription = job.type.name,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.outputBaseName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatDate(job.createdAt),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(status = job.status)
            }
            
            if (job.status == JobStatus.RUNNING) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { job.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun StatusChip(status: JobStatus) {
    val containerColor = when (status) {
        JobStatus.DONE -> MaterialTheme.colorScheme.primaryContainer
        JobStatus.RUNNING -> MaterialTheme.colorScheme.tertiaryContainer
        JobStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        JobStatus.QUEUED -> MaterialTheme.colorScheme.surfaceVariant
        JobStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (status) {
        JobStatus.DONE -> MaterialTheme.colorScheme.onPrimaryContainer
        JobStatus.RUNNING -> MaterialTheme.colorScheme.onTertiaryContainer
        JobStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        JobStatus.QUEUED -> MaterialTheme.colorScheme.onSurfaceVariant
        JobStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.name,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
