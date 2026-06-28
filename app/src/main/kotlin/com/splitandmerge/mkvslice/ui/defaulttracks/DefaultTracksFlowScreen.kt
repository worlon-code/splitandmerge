package com.splitandmerge.mkvslice.ui.defaulttracks

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitandmerge.mkvslice.domain.defaulttracks.model.TrackInfo
import com.splitandmerge.mkvslice.domain.defaulttracks.AudioChoice
import com.splitandmerge.mkvslice.domain.defaulttracks.SubChoice
import com.splitandmerge.mkvslice.domain.defaulttracks.RowState
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.service.JobService
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultTracksFlowScreen(
    viewModel: DefaultTracksViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val filesList by viewModel.filesList.collectAsState()
    val drafts by viewModel.drafts.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.processPickedFile(uri)
            } catch (e: Exception) {
                // permission persist failed
                android.widget.Toast.makeText(context, "Permission grant failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                viewModel.cancelToPicker()
            }
        } else {
            viewModel.cancelToPicker()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.processPickedFolder(uri)
            } catch (e: Exception) {
                // permission persist failed
                android.widget.Toast.makeText(context, "Permission grant failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                viewModel.cancelToPicker()
            }
        } else {
            viewModel.cancelToPicker()
        }
    }

    Scaffold(
        topBar = {
            val titleText = when (uiState) {
                is DefaultTracksUiState.Idle -> "Set Default Tracks"
                is DefaultTracksUiState.Picking -> "Picking Content"
                is DefaultTracksUiState.Scanning -> "Scanning Folder"
                is DefaultTracksUiState.Analyzing -> "Analyzing Files"
                is DefaultTracksUiState.ReadyList -> "Configure Tracks"
                is DefaultTracksUiState.Applying -> "Applying Changes"
                is DefaultTracksUiState.Results -> "Changes Complete"
            }
            TopAppBar(
                title = { Text(titleText, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    val showBack = when (uiState) {
                        is DefaultTracksUiState.Idle -> true
                        is DefaultTracksUiState.ReadyList -> {
                            val editingUri = (uiState as DefaultTracksUiState.ReadyList).editingFileUri
                            editingUri == null || !isTablet
                        }
                        is DefaultTracksUiState.Results -> true
                        else -> false
                    }
                    if (showBack) {
                        IconButton(onClick = {
                            if (uiState is DefaultTracksUiState.ReadyList && (uiState as DefaultTracksUiState.ReadyList).editingFileUri != null) {
                                viewModel.closeEditor()
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
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
            when (val state = uiState) {
                is DefaultTracksUiState.Idle -> {
                    StartScreenContent(
                        onPickFile = { filePicker.launch(arrayOf("video/x-matroska", "video/*")) },
                        onPickFolder = { folderPicker.launch(null) }
                    )
                }
                is DefaultTracksUiState.Picking -> {
                    // Placeholder while picking
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is DefaultTracksUiState.Scanning -> {
                    ScanningContent(
                        scannedCount = state.scannedCount,
                        onCancel = { viewModel.cancelToPicker() }
                    )
                }
                is DefaultTracksUiState.Analyzing -> {
                    AnalyzingContent(
                        currentFile = state.currentFile,
                        analyzedCount = state.analyzedCount,
                        totalCount = state.totalCount,
                        onCancel = { viewModel.cancelToPicker() }
                    )
                }
                is DefaultTracksUiState.ReadyList -> {
                    val openFileUri = state.editingFileUri
                    if (isTablet) {
                        // Two pane tablet layout
                        TabletBatchEditor(
                            viewModel = viewModel,
                            filesList = filesList,
                            editingFileUri = openFileUri,
                            drafts = drafts,
                            onBack = onBack
                        )
                    } else {
                        // Phone layout
                        if (openFileUri != null) {
                            val fileState = filesList.find { it.uri == openFileUri }
                            if (fileState != null) {
                                val draft = drafts[openFileUri]
                                val initialAudio = draft?.audioChoice
                                    ?: fileState.audioChoice
                                    ?: fileState.chosenSpec?.let { AudioChoice.Track(it.defaultAudioTrackNumber) }
                                    ?: AudioChoice.KeepCurrent
                                val initialSub = draft?.subChoice
                                    ?: fileState.subChoice
                                    ?: fileState.chosenSpec?.let { spec ->
                                        if (spec.defaultSubtitleTrackNumber == null) SubChoice.NoneSub else SubChoice.Track(spec.defaultSubtitleTrackNumber)
                                    }
                                    ?: SubChoice.KeepCurrent
                                val initialForced = draft?.forcedSubtitle
                                    ?: fileState.chosenSpec?.forcedSubtitle
                                    ?: false

                                TrackEditorScreen(
                                    fileState = fileState,
                                    initialAudioChoice = initialAudio,
                                    initialSubChoice = initialSub,
                                    initialForcedSubtitle = initialForced,
                                    onConfirm = { audioChoice, subChoice, forced ->
                                        viewModel.confirmEditor(openFileUri, audioChoice, subChoice, forced)
                                    },
                                    onCancel = {
                                        viewModel.discardDraft(openFileUri)
                                        viewModel.closeEditor()
                                    },
                                    onSaveDraft = { audio, sub, forced ->
                                        viewModel.saveDraft(openFileUri, audio, sub, forced)
                                    }
                                )
                            }
                        } else {
                            FileListBatchScreen(
                                viewModel = viewModel,
                                filesList = filesList,
                                onBack = onBack
                            )
                        }
                    }
                }
                is DefaultTracksUiState.Applying -> {
                    ApplyingContent(
                        progressText = state.progressText,
                        progressPct = state.progressPct,
                        files = filesList,
                        onCancel = {
                            // Cancel sends cancel intent to service
                            val cancelIntent = Intent(context, JobService::class.java).apply {
                                action = JobService.ACTION_CANCEL
                                putExtra(JobService.EXTRA_JOB_ID, state.jobId)
                            }
                            context.startService(cancelIntent)
                        }
                    )
                }
                is DefaultTracksUiState.Results -> {
                    ResultsContent(
                        jobId = state.jobId,
                        viewModel = viewModel,
                        onDone = onBack
                    )
                }
            }
        }
    }
}

@Composable
fun StartScreenContent(
    onPickFile: () -> Unit,
    onPickFolder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Flag,
            contentDescription = "Flag defaults",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Set Default Video Tracks",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Set default audio/subtitle tracks and forced subtitle settings across MKV files in place. Select a single file or a folder to start.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onPickFile,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Movie, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select MKV File", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        ElevatedButton(
            onClick = onPickFolder,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Folder / Tree", fontSize = 16.sp)
        }
    }
}

@Composable
fun ScanningContent(
    scannedCount: Int,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Scanning Folder...",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$scannedCount file(s) found so far",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

@Composable
fun AnalyzingContent(
    currentFile: String,
    analyzedCount: Int,
    totalCount: Int,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val pct = if (totalCount > 0) analyzedCount.toFloat() / totalCount else 0f
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Analyzing Matroska Headers...",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Analyzing $analyzedCount of $totalCount files",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = currentFile,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

@Composable
fun FileListBatchScreen(
    viewModel: DefaultTracksViewModel,
    filesList: List<FileRowState>,
    onBack: () -> Unit
) {
    val checkedCount = filesList.count { it.isChecked && it.isMkv }
    val lastEditedUri by viewModel.lastEditedUri.collectAsState()
    val applyHint by viewModel.applyHint.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        if (viewModel.isScanCapped) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Scan stopped at 1000 files / depth 5 — some files not included.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { viewModel.selectAll() }) { Text("Select All") }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = { viewModel.selectNone() }) { Text("Select None") }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${filesList.count { it.isMkv }} files",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            items(filesList) { file ->
                FileRowItem(
                    file = file,
                    onToggle = { viewModel.toggleCheck(file.uri) },
                    onClick = {
                        if (file.isMkv) {
                            viewModel.openEditor(file.uri)
                        }
                    }
                )
            }
        }

        Divider()

        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = { lastEditedUri?.let { viewModel.applyToSimilar(it) } },
                enabled = lastEditedUri != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.LibraryAddCheck, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Apply to all similar files")
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Gentle hint when no dimension is active (both KeepCurrent)
            if (applyHint != null) {
                Text(
                    text = applyHint!!,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = "Apply to all similar: matches by language, then by track position (same track number as seed). A region-tagged seed (e.g. en-US) won't match plain-tagged files. Verify the '→ AUD/SUB' readout on each row before applying.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.applyChanges() },
                enabled = checkedCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Apply changes ($checkedCount)", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun FileRowItem(
    file: FileRowState,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = file.isMkv, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = file.isChecked,
                onCheckedChange = { onToggle() },
                enabled = file.isMkv
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val spec = file.chosenSpec
                    val audioText = if (spec != null) {
                        val audioTrack = file.audioTracks.find { it.trackNumber == spec.defaultAudioTrackNumber }
                        if (audioTrack != null) {
                            "AUD: Track ${audioTrack.trackNumber} (${audioTrack.language.uppercase()})"
                        } else if (file.audioTracks.isNotEmpty()) {
                            "AUD: Track ${spec.defaultAudioTrackNumber}"
                        } else {
                            null
                        }
                    } else {
                        val currentDefaultAudio = file.audioTracks.find { it.flagDefault == 1 }
                            ?: file.audioTracks.firstOrNull()
                        if (currentDefaultAudio != null) {
                            "AUD: Track ${currentDefaultAudio.trackNumber} (${currentDefaultAudio.language.uppercase()})"
                        } else {
                            null
                        }
                    }

                    val subText = if (spec != null) {
                        val subTrackNum = spec.defaultSubtitleTrackNumber
                        if (subTrackNum == null) {
                            "SUB: None"
                        } else {
                            val subTrack = file.subtitleTracks.find { it.trackNumber == subTrackNum }
                            val forcedText = if (spec.forcedSubtitle) " [F]" else ""
                            if (subTrack != null) {
                                "SUB: Track ${subTrack.trackNumber} (${subTrack.language.uppercase()})$forcedText"
                            } else {
                                "SUB: Track $subTrackNum$forcedText"
                            }
                        }
                    } else {
                        val currentDefaultSub = file.subtitleTracks.find { it.flagDefault == 1 }
                            ?: file.subtitleTracks.firstOrNull()
                        if (currentDefaultSub != null) {
                            val forcedText = if (currentDefaultSub.flagForced == 1) " [F]" else ""
                            "SUB: Track ${currentDefaultSub.trackNumber} (${currentDefaultSub.language.uppercase()})$forcedText"
                        } else {
                            null
                        }
                    }

                    if (audioText != null) {
                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                text = audioText,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (subText != null) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                text = subText,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                    }
                }
                if (!file.reason.isNullOrBlank() && file.reason != "not-mkv") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = file.reason,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!file.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = file.note,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(status = file.status, reason = file.reason, matchState = file.matchState)
        }
    }
}

@Composable
fun StatusPill(status: String, reason: String, matchState: RowState?) {
    val (containerColor, contentColor, labelText) = when {
        matchState == RowState.PARTIAL_NEEDS_REVIEW -> {
            Triple(
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
                "Needs Review"
            )
        }
        status == "SCAN" -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Scanning")
        status == "PENDING" -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Ready")
        status == "UNCHANGED" -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "No Change")
        status == "WILL_CHANGE" -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "Will Modify")
        status == "DONE" -> Triple(Color(0xFFE2F0D9), Color(0xFF385723), "Done")
        status == "SKIPPED" -> {
            val label = if (reason == "not-mkv") "Non-MKV" else "Skipped"
            Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, label)
        }
        status == "FAILED" -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "Failed")
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, status)
    }
    
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = labelText,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TrackEditorScreen(
    fileState: FileRowState,
    initialAudioChoice: AudioChoice,
    initialSubChoice: SubChoice,
    initialForcedSubtitle: Boolean,
    onConfirm: (audioChoice: AudioChoice, subChoice: SubChoice, forced: Boolean) -> Unit,
    onCancel: () -> Unit,
    onSaveDraft: (audioChoice: AudioChoice, subChoice: SubChoice, forced: Boolean) -> Unit
) {
    var selectedAudioChoice by remember(fileState.uri, initialAudioChoice) {
        mutableStateOf(initialAudioChoice)
    }
    var selectedSubChoice by remember(fileState.uri, initialSubChoice) {
        mutableStateOf(initialSubChoice)
    }
    var forcedSubtitle by remember(fileState.uri, initialForcedSubtitle) {
        mutableStateOf(initialForcedSubtitle)
    }

    androidx.activity.compose.BackHandler {
        onSaveDraft(selectedAudioChoice, selectedSubChoice, forcedSubtitle)
    }

    val audioTracks = fileState.audioTracks
    val subtitleTracks = fileState.subtitleTracks

    // Determine the file's current default audio for the KeepCurrent label
    val currentDefaultAudio = audioTracks.find { it.flagDefault == 1 } ?: audioTracks.firstOrNull()
    val keepCurrentAudioLabel = if (currentDefaultAudio != null) {
        "Keep current (Track ${currentDefaultAudio.trackNumber} — ${currentDefaultAudio.language.uppercase()})"
    } else {
        "Keep current"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Track Editor: ${fileState.displayName}",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            // Audio Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Audio Tracks", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // Keep current option (default)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedAudioChoice is AudioChoice.KeepCurrent,
                                    onClick = { selectedAudioChoice = AudioChoice.KeepCurrent }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAudioChoice is AudioChoice.KeepCurrent,
                                onClick = { selectedAudioChoice = AudioChoice.KeepCurrent }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(keepCurrentAudioLabel, fontWeight = FontWeight.SemiBold)
                        }

                        audioTracks.forEach { track ->
                            val isSelected = selectedAudioChoice is AudioChoice.Track &&
                                (selectedAudioChoice as AudioChoice.Track).trackNumber == track.trackNumber
                            val isDefault = track.flagDefault == 1
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = isSelected,
                                        onClick = { selectedAudioChoice = AudioChoice.Track(track.trackNumber) }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedAudioChoice = AudioChoice.Track(track.trackNumber) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Track ${track.trackNumber} (${track.language.uppercase()})",
                                        fontWeight = FontWeight.SemiBold)
                                    Text(track.codec, fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isDefault) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("DEFAULT", fontSize = 8.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Subtitle Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Subtitles, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Subtitle Tracks", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // Keep current option (default)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedSubChoice is SubChoice.KeepCurrent,
                                    onClick = { selectedSubChoice = SubChoice.KeepCurrent }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSubChoice is SubChoice.KeepCurrent,
                                onClick = { selectedSubChoice = SubChoice.KeepCurrent }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Keep current", fontWeight = FontWeight.SemiBold)
                        }

                        // Option "None"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedSubChoice is SubChoice.NoneSub,
                                    onClick = { selectedSubChoice = SubChoice.NoneSub }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSubChoice is SubChoice.NoneSub,
                                onClick = { selectedSubChoice = SubChoice.NoneSub }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("None", fontWeight = FontWeight.SemiBold)
                        }

                        subtitleTracks.forEach { track ->
                            val isSelected = selectedSubChoice is SubChoice.Track &&
                                (selectedSubChoice as SubChoice.Track).trackNumber == track.trackNumber
                            val isDefault = track.flagDefault == 1
                            val isForced = track.flagForced == 1
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = isSelected,
                                        onClick = { selectedSubChoice = SubChoice.Track(track.trackNumber) }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedSubChoice = SubChoice.Track(track.trackNumber) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Track ${track.trackNumber} (${track.language.uppercase()})",
                                        fontWeight = FontWeight.SemiBold)
                                    Text(track.codec, fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isDefault) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("DEFAULT", fontSize = 8.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(2.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                if (isForced) {
                                    Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                                        Text("FORCED", fontSize = 8.sp,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Forced Switch card — enabled ONLY when a specific subtitle Track is selected
            item {
                val subIsActiveTrack = selectedSubChoice is SubChoice.Track
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = subIsActiveTrack) {
                                forcedSubtitle = !forcedSubtitle
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Forced Subtitle", fontWeight = FontWeight.Bold)
                            Text("Force the chosen subtitle track in media players.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = if (subIsActiveTrack) forcedSubtitle else false,
                            onCheckedChange = { if (subIsActiveTrack) forcedSubtitle = it },
                            enabled = subIsActiveTrack
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val subForced = if (selectedSubChoice is SubChoice.Track) forcedSubtitle else false
                    onConfirm(selectedAudioChoice, selectedSubChoice, subForced)
                },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirm")
            }
        }
    }
}

@Composable
fun TabletBatchEditor(
    viewModel: DefaultTracksViewModel,
    filesList: List<FileRowState>,
    editingFileUri: String?,
    drafts: Map<String, TrackDraft>,
    onBack: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left Column: List
        Box(modifier = Modifier.weight(0.5f)) {
            FileListBatchScreen(
                viewModel = viewModel,
                filesList = filesList,
                onBack = onBack
            )
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight())

        // Right Column: Editor
        Box(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (editingFileUri != null) {
                val fileState = filesList.find { it.uri == editingFileUri }
                if (fileState != null) {
                    val draft = drafts[editingFileUri]
                    val initialAudio = draft?.audioChoice
                        ?: fileState.audioChoice
                        ?: fileState.chosenSpec?.let { AudioChoice.Track(it.defaultAudioTrackNumber) }
                        ?: AudioChoice.KeepCurrent
                    val initialSub = draft?.subChoice
                        ?: fileState.subChoice
                        ?: fileState.chosenSpec?.let { spec ->
                            if (spec.defaultSubtitleTrackNumber == null) SubChoice.NoneSub else SubChoice.Track(spec.defaultSubtitleTrackNumber)
                        }
                        ?: SubChoice.KeepCurrent
                    val initialForced = draft?.forcedSubtitle
                        ?: fileState.chosenSpec?.forcedSubtitle
                        ?: false

                    TrackEditorScreen(
                        fileState = fileState,
                        initialAudioChoice = initialAudio,
                        initialSubChoice = initialSub,
                        initialForcedSubtitle = initialForced,
                        onConfirm = { audioChoice, subChoice, forced ->
                            viewModel.confirmEditor(editingFileUri, audioChoice, subChoice, forced)
                        },
                        onCancel = {
                            viewModel.discardDraft(editingFileUri)
                            viewModel.closeEditor()
                        },
                        onSaveDraft = { audio, sub, forced ->
                            viewModel.saveDraft(editingFileUri, audio, sub, forced)
                        }
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select an MKV file from the left to edit its default flags.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ApplyingContent(
    progressText: String,
    progressPct: Float,
    files: List<FileRowState>,
    onCancel: () -> Unit
) {
    val batch = files.filter { it.isMkv && it.isChecked }
    val processingUri = batch.firstOrNull { it.status == "PENDING" }?.uri

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val pctValue = (progressPct * 100).toInt()
        Spacer(modifier = Modifier.height(8.dp))
        CircularProgressIndicator(
            progress = { progressPct },
            modifier = Modifier.size(72.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Applying Flag Changes...",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$progressText ($pctValue%)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (batch.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(batch, key = { it.uri }) { file ->
                    ApplyingFileRow(
                        file = file,
                        isProcessing = file.uri == processingUri
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Cancel")
        }
    }
}

@Composable

private fun ApplyingFileRow(
    file: FileRowState,
    isProcessing: Boolean
) {
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val track = MaterialTheme.colorScheme.surfaceVariant

    val label: String
    val tint: Color
    val fraction: Float?
    when (file.status) {
        "PENDING" -> {
            if (isProcessing) { label = "Processing…"; tint = primary; fraction = file.applyProgress.coerceIn(0, 100) / 100f }
            else { label = "Waiting"; tint = onVariant; fraction = 0f }
        }
        "DONE" -> { label = "Updated"; tint = primary; fraction = 1f }
        "UNCHANGED" -> { label = "No change"; tint = onVariant; fraction = 1f }
        "SKIPPED" -> { label = "Skipped"; tint = onVariant; fraction = 1f }
        "FAILED" -> { label = "Failed"; tint = error; fraction = 1f }
        "PARTIAL" -> { label = "Partial"; tint = error; fraction = 1f }
        "NEEDS_MANUAL_REVIEW" -> { label = "Needs review"; tint = error; fraction = 1f }
        else -> { label = file.status; tint = onVariant; fraction = 0f }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = file.displayName,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, color = tint, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (fraction == null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp),
                    color = primary,
                    trackColor = track
                )
            } else {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp),
                    color = if (fraction >= 1f) tint else onVariant,
                    trackColor = track
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (fraction == null) "…" else "${(fraction * 100).toInt()}%",
                color = onVariant,
                fontSize = 12.sp,
                modifier = Modifier.widthIn(min = 36.dp)
            )
        }
    }
}


@Composable
fun ResultsContent(
    jobId: String,
    viewModel: DefaultTracksViewModel,
    onDone: () -> Unit
) {
    val resultsState = viewModel.filesList.collectAsState().value
    
    val totalCount = resultsState.size
    val nonMkvCount = resultsState.count { it.status == "SKIPPED" && it.reason == "not-mkv" }
    
    // Read the terminal result count directly from results list
    val updatedCount = resultsState.count { it.status == "DONE" }
    val unchangedCount = resultsState.count { it.status == "UNCHANGED" }
    val skippedCount = resultsState.count { it.status == "SKIPPED" && it.reason != "not-mkv" }
    val failedCount = resultsState.count { it.status == "FAILED" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Processing Results Summary",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            ResultCountRow(label = "Updated (DONE)", count = updatedCount, color = Color(0xFF385723))
            ResultCountRow(label = "Unchanged", count = unchangedCount, color = MaterialTheme.colorScheme.onSurface)
            ResultCountRow(label = "Skipped (excluding non-MKV)", count = skippedCount, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ResultCountRow(label = "Failed", count = failedCount, color = MaterialTheme.colorScheme.error)
            ResultCountRow(label = "Non-MKV skipped count", count = nonMkvCount, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (viewModel.isScanCapped) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = "Folder scan was stopped at 1000 files / depth 5. Some files were not evaluated.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }
        }

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Done")
        }
    }
}

@Composable
fun ResultCountRow(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.Medium)
        Text(text = count.toString(), color = color, fontWeight = FontWeight.Bold)
    }
}
