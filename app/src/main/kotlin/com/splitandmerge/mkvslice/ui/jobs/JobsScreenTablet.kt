package com.splitandmerge.mkvslice.ui.jobs

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.splitandmerge.mkvslice.domain.model.Job
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.splitandmerge.mkvslice.ui.splitconfirm.DetailRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreenTablet(
    viewModel: JobsViewModel,
    onBack: () -> Unit,
    onNavigateToJobDetail: (String) -> Unit
) {
    val jobs by viewModel.jobs.collectAsState()
    var filterType by remember { mutableStateOf<JobType?>(null) }
    var selectedJob by remember { mutableStateOf<Job?>(null) }

    val filteredJobs = when (filterType) {
        JobType.SPLIT -> jobs.filter { it.type == JobType.SPLIT }
        JobType.MERGE -> jobs.filter { it.type == JobType.MERGE }
        null -> jobs
    }

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Spacer(modifier = Modifier.weight(1f))
            NavigationRailItem(
                selected = false,
                onClick = onBack,
                icon = { Icon(Icons.Default.ArrowBack, contentDescription = "Back") },
                label = { Text("Back") }
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Job History (Tablet)", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
            ) {
                // Left Pane: List of Jobs
                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = filterType == null,
                            onClick = { filterType = null },
                            label = { Text("All") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = filterType == JobType.SPLIT,
                            onClick = { filterType = JobType.SPLIT },
                            label = { Text("Splits") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = filterType == JobType.MERGE,
                            onClick = { filterType = JobType.MERGE },
                            label = { Text("Merges") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (filteredJobs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No jobs found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredJobs) { job ->
                                JobHistoryRowItem(
                                    job = job,
                                    onClick = { selectedJob = job },
                                    onDelete = { viewModel.deleteJob(job.id) }
                                )
                            }
                        }
                    }
                }

                // Right Pane: Job Detail Pane
                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(24.dp)
                ) {
                    if (selectedJob == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Select a job from the list to view trace logs & status details.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val job = selectedJob!!
                        Text(
                            text = "Job Details & Logs",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = job.outputBaseName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                DetailRow(label = "Job ID", value = job.id)
                                DetailRow(label = "Type", value = job.type.name)
                                DetailRow(label = "Status", value = job.status.name)
                                DetailRow(label = "Created", value = formatDate(job.createdAt))
                                DetailRow(label = "Updated", value = formatDate(job.updatedAt))
                                DetailRow(label = "Input URI", value = job.inputPathUri)
                                DetailRow(label = "Output URI", value = job.outputPathUri)

                                if (job.errorDetails != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Diagnostics & Trace:",
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = job.errorDetails ?: "",
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    if (job.status == JobStatus.RUNNING) {
                                        Button(
                                            onClick = { viewModel.cancelJob(job.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Cancel Job")
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Button(
                                        onClick = { onNavigateToJobDetail(job.id) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Open Full Screen")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
