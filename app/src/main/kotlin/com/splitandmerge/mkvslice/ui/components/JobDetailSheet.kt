package com.splitandmerge.mkvslice.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitandmerge.mkvslice.domain.model.Job
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.ui.library.StatusChip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailSheet(
    job: Job,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var showTechDetails by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 800

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (job.status == JobStatus.CANCELLED) Icons.Default.Cancel else Icons.Default.Error,
                    contentDescription = job.status.name,
                    tint = if (job.status == JobStatus.CANCELLED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.width(32.dp).padding(end = 8.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.outputBaseName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(status = job.status)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (job.type == JobType.SPLIT) "Split Job" else "Merge Job",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle time
            val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(job.updatedAt))
            val dateString = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(job.updatedAt))
            val relativeText = if (job.status == JobStatus.CANCELLED) {
                "Cancelled at $timeString on $dateString"
            } else {
                "Failed at $timeString on $dateString"
            }
            Text(
                text = relativeText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error message card (only for failed)
            if (job.status == JobStatus.FAILED && job.errorDetails != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Error Message",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = job.errorDetails ?: "Unknown error occurred",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Tech details / logs (expandable)
            if (job.errorDetails != null) {
                TextButton(
                    onClick = { showTechDetails = !showTechDetails },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(if (showTechDetails) "Hide details" else "Show details")
                }
                
                AnimatedVisibility(visible = showTechDetails) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = job.errorDetails ?: "",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Actions Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        onRetry()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete row", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
