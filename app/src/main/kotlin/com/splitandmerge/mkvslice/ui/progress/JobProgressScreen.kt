package com.splitandmerge.mkvslice.ui.progress

import com.splitandmerge.mkvslice.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.splitandmerge.mkvslice.domain.progress.JobPhaseHint
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitandmerge.mkvslice.domain.model.JobStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobProgressScreen(
    viewModel: JobProgressViewModel,
    jobId: String,
    onNavigateToResult: (String) -> Unit,
    onCancelOrBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val isAnalyzing = state.phaseHint == JobPhaseHint.Analyzing
    val animatedProgress by animateFloatAsState(targetValue = state.pct / 100f, label = "ProgressAnim")
    val snackbarHostState = remember { SnackbarHostState() }

    // A1: Navigation is driven by the DB observer in the ViewModel, NOT by the cancel button click.
    LaunchedEffect(state.status) {
        when (state.status) {
            JobStatus.DONE -> onNavigateToResult(jobId)
            JobStatus.CANCELLED, JobStatus.FAILED -> onCancelOrBack()
            else -> Unit
        }
    }

    // A2: Cancel timeout — if status hasn't flipped within 5 s, show a snackbar.
    var cancelPending by remember { mutableStateOf(false) }
    LaunchedEffect(cancelPending) {
        if (!cancelPending) return@LaunchedEffect
        try {
            withTimeout(5_000L) {
                // Poll until status leaves RUNNING (or QUEUED).
                while (state.status == JobStatus.RUNNING || state.status == JobStatus.QUEUED) {
                    delay(200)
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            snackbarHostState.showSnackbar("Force-stop requested")
        } finally {
            cancelPending = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Processing Job", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancelOrBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.fileName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Phase label — shown for RUNNING/QUEUED; terminal labels for other states.
                val displayLabel = if (isAnalyzing) {
                    stringResource(R.string.split_analyzing_title)
                } else if (state.phaseLabel.isNotEmpty()) {
                    state.phaseLabel
                } else when (state.status) {
                    JobStatus.CANCELLED -> "Job Cancelled"
                    JobStatus.FAILED    -> "Execution Failed"
                    JobStatus.DONE      -> "Processing Complete"
                    else               -> "In Queue…"
                }

                Text(
                    text = displayLabel,
                    fontSize = 13.sp,
                    color = if (state.status == JobStatus.FAILED)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                if (isAnalyzing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.split_analyzing_subtitle),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(200.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    )
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(170.dp),
                            strokeWidth = 12.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "...",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Analyzing",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(170.dp),
                            strokeWidth = 12.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${state.pct}%",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Progress",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = "Speed",
                                tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Speed", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = state.speedFormatted,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.HourglassEmpty, contentDescription = "ETA",
                                tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Remaining", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = state.etaFormatted,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            if (state.status == JobStatus.RUNNING || state.status == JobStatus.QUEUED) {
                Button(
                    onClick = {
                        // A1: ViewModel fires the intent. Navigation is driven by DB observer above.
                        // A2: arm the 5-second cancel-timeout watcher.
                        viewModel.cancelJob()
                        cancelPending = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Cancel Job", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onCancelOrBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Go Back", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

