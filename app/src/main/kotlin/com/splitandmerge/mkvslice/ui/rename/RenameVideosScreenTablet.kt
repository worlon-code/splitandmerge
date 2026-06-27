package com.splitandmerge.mkvslice.ui.rename

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitandmerge.mkvslice.data.db.entity.CleanupPatternEntity
import com.splitandmerge.mkvslice.domain.rename.RenameDecision

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameVideosScreenTablet(
    viewModel: RenameVideosViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(enabled = uiState !is RenameVideosUiState.Idle) {
        viewModel.cancelToIdle()
    }

    // Keep the screen on while a rename batch is processing, if the user has opted in.
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val view = LocalView.current
    DisposableEffect(keepScreenOn, uiState is RenameVideosUiState.Processing) {
        val window = (view.context as android.app.Activity).window
        if (keepScreenOn && uiState is RenameVideosUiState.Processing) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    val filesList by viewModel.filesList.collectAsState()
    val autoSuffixSet by viewModel.perRowAutoSuffix.collectAsState()
    val patterns by viewModel.cleanupPatterns.collectAsState(initial = emptyList())
    val selectedPatternIds by viewModel.selectedPatternIds.collectAsState()
    val inlineCreateState by viewModel.inlineCreateState.collectAsState()

    var patternPanelExpanded by remember { mutableStateOf(true) }
    var showInlineCreate by remember { mutableStateOf(false) }

    val selectedRowId = (uiState as? RenameVideosUiState.ReadyList)?.selectedRowId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Batch Rename Videos (Tablet Mode)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState !is RenameVideosUiState.Idle) {
                            viewModel.cancelToIdle()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                is RenameVideosUiState.Idle -> {
                    // Reusing the same Idle card picker as mobile but side-by-side or scaled nicely for tablet
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val filePickerLauncher = rememberLauncherForActivityResult(
                            contract = remember {
                                object : androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments() {
                                    override fun createIntent(context: android.content.Context, input: Array<String>): android.content.Intent =
                                        super.createIntent(context, input).apply {
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                        }
                                }
                            }
                        ) { uris ->
                            if (uris.isNotEmpty()) {
                                viewModel.processPickedFiles(uris)
                            }
                        }

                        var includeSubfolders by remember { mutableStateOf(false) }
                        val folderPickerLauncher = rememberLauncherForActivityResult(
                            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
                        ) { uri ->
                            if (uri != null) {
                                viewModel.processPickedFolder(uri, includeSubfolders)
                            }
                        }

                        Card(
                            onClick = { filePickerLauncher.launch(arrayOf("video/*", "application/x-matroska")) },
                            modifier = Modifier
                                .weight(1f)
                                .height(280.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.VideoFile,
                                    contentDescription = "Select Files",
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Select Video Files", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Pick specific videos directly from storage.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Card(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier
                                .weight(1f)
                                .height(280.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = "Select Folder",
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Select Directory", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Scan and clean videos in a directory.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { includeSubfolders = !includeSubfolders }
                                ) {
                                    Checkbox(
                                        checked = includeSubfolders,
                                        onCheckedChange = { includeSubfolders = it }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Include Subfolders", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                is RenameVideosUiState.Scanning -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Scanning video files...", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Scanned count: ${state.scannedCount}", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is RenameVideosUiState.ReadyList -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Pane: File list with inline edit controls
                        Column(
                            modifier = Modifier
                                .weight(1.3f)
                                .fillMaxHeight()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(onClick = { viewModel.selectAll() }) {
                                    Text("Select All", fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    "${filesList.count { it.isChecked }} of ${filesList.size} Selected",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { viewModel.selectNone() }) {
                                    Text("Select None", fontWeight = FontWeight.Bold)
                                }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                            ) {
                                items(filesList, key = { it.id }) { row ->
                                    val isSelected = row.id == selectedRowId
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .border(
                                                width = if (isSelected) 2.dp else 0.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surface
                                        ),
                                        onClick = { viewModel.selectRowForPreview(row.id) }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Checkbox(
                                                    checked = row.isChecked,
                                                    onCheckedChange = { viewModel.toggleCheck(row.id) }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Original: ${row.displayName}",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                        textDecoration = TextDecoration.LineThrough,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                DecisionBadge(decision = row.decision)
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                OutlinedTextField(
                                                    value = row.newBaseName,
                                                    onValueChange = { viewModel.updateManualName(row.id, it) },
                                                    label = { Text("New Base Name") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )

                                                Spacer(modifier = Modifier.width(12.dp))

                                                // Auto-suffix chip is for folder-pick rows only.
                                                // Picked-file rows are always opted-in (auto) and
                                                // show the advisory caption below instead.
                                                if (!row.isPickedFile) {
                                                    FilterChip(
                                                        selected = autoSuffixSet.contains(row.id),
                                                        onClick = { viewModel.toggleAutoSuffix(row.id) },
                                                        label = { Text("Auto-suffix", fontSize = 11.sp) },
                                                        leadingIcon = {
                                                            if (autoSuffixSet.contains(row.id)) {
                                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                                            }
                                                        }
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Final Target: ${row.targetName}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (row.decision == RenameDecision.RENAME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            // Picked-file advisory caption (STEP 8 — display only)
                                            if (row.isPickedFile && row.decision == RenameDecision.RENAME) {
                                                Text(
                                                    text = "If this name already exists, a number is added — e.g. (1)",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Surface(
                                shadowElevation = 8.dp,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.cancelToIdle() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel", fontSize = 16.sp)
                                    }
                                    val toRenameCount = filesList.count { it.isChecked && it.decision == RenameDecision.RENAME }
                                    Button(
                                        onClick = { viewModel.executeRename() },
                                        enabled = toRenameCount > 0,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Rename Selected ($toRenameCount)", fontSize = 16.sp)
                                    }
                                }
                            }
                        }

                        // Right Pane: Pattern subset panel + Live Reactive Preview
                        Column(
                            modifier = Modifier
                                .weight(0.9f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        ) {
                            // Pattern subset panel (collapsible)
                            PatternSubsetPanel(
                                patterns = patterns,
                                selectedPatternIds = selectedPatternIds,
                                inlineCreateState = inlineCreateState,
                                expanded = patternPanelExpanded,
                                showInlineCreate = showInlineCreate,
                                onToggleExpanded = { patternPanelExpanded = !patternPanelExpanded },
                                onTogglePattern = { viewModel.togglePatternSelection(it) },
                                onToggleAll = { viewModel.toggleAllPatternsSelection(it) },
                                onShowInlineCreate = { showInlineCreate = true },
                                onDismissInlineCreate = {
                                    showInlineCreate = false
                                    viewModel.resetInlineCreateForm()
                                },
                                onRegexChange = { viewModel.updateInlineRegex(it) },
                                onLabelChange = { viewModel.updateInlineLabel(it) },
                                onReplacementChange = { viewModel.updateInlineReplacement(it) },
                                onSavePattern = {
                                    viewModel.createInlinePattern()
                                    showInlineCreate = false
                                }
                            )

                            HorizontalDivider()

                            // Regex pipeline preview
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            ) {
                            Text(
                                "Regex Pipeline Preview",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Shows the sequential replacement chain applied by your cleanup rules.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            if (selectedRowId == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Select a video row on the left to see the interactive rules pipeline preview.",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            } else {
                                val selectedRow = filesList.firstOrNull { it.id == selectedRowId }
                                if (selectedRow != null) {
                                    val steps = remember(selectedRow.originalBaseName, patterns, selectedPatternIds) {
                                        getRegexChainSteps(selectedRow.originalBaseName, patterns, selectedPatternIds)
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("Original Filename:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(selectedRow.displayName, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        steps.forEachIndexed { index, step ->
                                            RuleStepItem(index = index + 1, step = step)
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("Cleaned Name Base:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                                Text(selectedRow.newBaseName, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    }
                                }
                            }
                            } // end regex pipeline Column
                        }
                    }
                }
                is RenameVideosUiState.Processing -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(64.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Renaming files: ${state.current} of ${state.total}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { if (state.total > 0) state.current.toFloat() / state.total else 0f },
                            modifier = Modifier.fillMaxWidth(0.6f)
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        OutlinedButton(onClick = { viewModel.cancelToIdle() }) {
                            Text("Abort Operation", fontSize = 16.sp)
                        }
                    }
                }
                is RenameVideosUiState.Results -> {
                    val batch = state.batchResult
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Renaming Complete", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(0.6f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Succeeded", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${batch.successCount}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Failed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${batch.failedCount}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (batch.failedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                                }
                                if (batch.excludedCount > 0) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Excluded", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${batch.excludedCount}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "Renamed Files (Reference for manual revert):",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(0.8f)
                        ) {
                            items(batch.results) { res ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = res.oldName,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                textDecoration = TextDecoration.LineThrough,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                Icons.Default.ArrowForward,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp).padding(horizontal = 8.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = res.newName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            StatusBadge(status = res.status)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.cancelToIdle() },
                            modifier = Modifier.fillMaxWidth(0.3f)
                        ) {
                            Text("Done", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RuleStepItem(index: Int, step: RegexStep) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (step.didModify) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$index. ${step.label}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (step.didModify) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (step.didModify) "REPLACED" else "NO MATCH",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (step.didModify) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Regex: ${step.regex}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (step.didModify) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Before: ${step.inputBefore}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "After: ${step.outputAfter}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

data class RegexStep(
    val label: String,
    val regex: String,
    val inputBefore: String,
    val outputAfter: String,
    val didModify: Boolean
)

fun getRegexChainSteps(
    originalBase: String,
    patterns: List<CleanupPatternEntity>,
    selectedPatternIds: Set<String> = patterns.filter { it.enabled }.map { it.id }.toSet()
): List<RegexStep> {
    val steps = mutableListOf<RegexStep>()
    var current = originalBase
    // Use the subset selected in the panel, sorted by orderIndex
    val activePatterns = patterns.filter { it.id in selectedPatternIds }.sortedBy { it.orderIndex }
    for (rule in activePatterns) {
        try {
            val next = current.replace(Regex(rule.regex, RegexOption.IGNORE_CASE), rule.replacement)
            val modified = current != next
            steps.add(
                RegexStep(
                    label = rule.label,
                    regex = rule.regex,
                    inputBefore = current,
                    outputAfter = next,
                    didModify = modified
                )
            )
            current = next
        } catch (e: Exception) {
            // Ignore invalid regex
        }
    }
    return steps
}
