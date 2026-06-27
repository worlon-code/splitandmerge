package com.splitandmerge.mkvslice.ui.rename

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitandmerge.mkvslice.domain.rename.RenameDecision

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameVideosScreen(
    viewModel: RenameVideosViewModel,
    onBack: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    if (isTablet) {
        RenameVideosScreenTablet(
            viewModel = viewModel,
            onBack = onBack
        )
    } else {
        RenameVideosScreenMobile(
            viewModel = viewModel,
            onBack = onBack
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameVideosScreenMobile(
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

    var includeSubfolders by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<RenameFileRowState?>(null) }
    var patternPanelExpanded by remember { mutableStateOf(true) }
    var showInlineCreate by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = remember {
            object : ActivityResultContracts.OpenMultipleDocuments() {
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

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.processPickedFolder(uri, includeSubfolders)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Batch Rename Videos", fontWeight = FontWeight.Bold) },
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            onClick = { filePickerLauncher.launch(arrayOf("video/*", "application/x-matroska")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.VideoFile,
                                    contentDescription = "Select Files",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Select Video Files", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text(
                                        "Select individual MKV or other videos to clean.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        Card(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = "Select Folder",
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("Select Directory", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Text(
                                            "Scan and clean all videos in a folder.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Include Subfolders", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("Scan recursively into nested folders.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = includeSubfolders,
                                        onCheckedChange = { includeSubfolders = it }
                                    )
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
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Scanning video files...", fontWeight = FontWeight.Bold)
                        Text("Scanned count: ${state.scannedCount}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is RenameVideosUiState.ReadyList -> {
                    Column(modifier = Modifier.fillMaxSize()) {

                        // ── Pattern Subset Panel ──────────────────────────────────
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

                        // ── File selection toolbar ────────────────────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { viewModel.selectAll() }) {
                                Text("Select All")
                            }
                            Text(
                                "${filesList.count { it.isChecked }} of ${filesList.size} Selected",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { viewModel.selectNone() }) {
                                Text("Select None")
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            items(filesList, key = { it.id }) { row ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = row.isChecked,
                                            onCheckedChange = { viewModel.toggleCheck(row.id) }
                                        )

                                        Spacer(modifier = Modifier.width(4.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = row.displayName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                textDecoration = TextDecoration.LineThrough
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = row.targetName,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (row.decision == RenameDecision.RENAME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                DecisionBadge(decision = row.decision)
                                                if (row.decision == RenameDecision.SKIP_COLLISION && !row.isPickedFile) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    FilterChip(
                                                        selected = autoSuffixSet.contains(row.id),
                                                        onClick = { viewModel.toggleAutoSuffix(row.id) },
                                                        label = { Text("Auto-suffix", fontSize = 10.sp) },
                                                        leadingIcon = {
                                                            if (autoSuffixSet.contains(row.id)) {
                                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                            // Picked-file advisory: disk collision is resolved
                                            // at apply time via (N) suffix — never at preview.
                                            if (row.isPickedFile && row.decision == RenameDecision.RENAME) {
                                                Text(
                                                    text = "If this name already exists, a number is added — e.g. (1)",
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        IconButton(onClick = { editingRow = row }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit manual name")
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.cancelToIdle() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel")
                                }
                                val toRenameCount = filesList.count { it.isChecked && it.decision == RenameDecision.RENAME }
                                Button(
                                    onClick = { viewModel.executeRename() },
                                    enabled = toRenameCount > 0,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Rename ($toRenameCount)")
                                }
                            }
                        }
                    }
                }
                is RenameVideosUiState.Processing -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Renaming files: ${state.current} of ${state.total}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (state.total > 0) state.current.toFloat() / state.total else 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        OutlinedButton(onClick = { viewModel.cancelToIdle() }) {
                            Text("Abort")
                        }
                    }
                }
                is RenameVideosUiState.Results -> {
                    val batch = state.batchResult
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Renaming Complete", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Succeeded", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${batch.successCount}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Failed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${batch.failedCount}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (batch.failedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                                }
                                if (batch.excludedCount > 0) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Excluded", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${batch.excludedCount}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Renamed Files (Reference for manual revert):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            items(batch.results) { res ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = res.oldName,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                textDecoration = TextDecoration.LineThrough,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                Icons.Default.ArrowForward,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp).padding(horizontal = 4.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = res.newName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            StatusBadge(status = res.status)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.cancelToIdle() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }

    editingRow?.let { row ->
        val currentFile = filesList.firstOrNull { it.id == row.id }
        if (currentFile != null) {
            EditRowDialog(
                row = currentFile,
                viewModel = viewModel,
                onDismiss = { editingRow = null }
            )
        }
    }
}

// ── Pattern Subset Panel ──────────────────────────────────────────────────────

/**
 * Collapsible panel listing all store patterns with checkboxes.
 * All are selected by default. The selection (not the global enabled flag)
 * controls what gets passed to cleanTitleWith.
 * Includes an inline-create form for adding a new pattern.
 */
@Composable
fun PatternSubsetPanel(
    patterns: List<com.splitandmerge.mkvslice.data.db.entity.CleanupPatternEntity>,
    selectedPatternIds: Set<String>,
    inlineCreateState: InlineCreateState,
    expanded: Boolean,
    showInlineCreate: Boolean,
    onToggleExpanded: () -> Unit,
    onTogglePattern: (String) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onShowInlineCreate: () -> Unit,
    onDismissInlineCreate: () -> Unit,
    onRegexChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onSavePattern: () -> Unit
) {
    val selectedCount = patterns.count { it.id in selectedPatternIds }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header row — always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Cleanup Rules",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$selectedCount / ${patterns.size} active",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Expandable body
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider()

                    if (patterns.isEmpty()) {
                        Text(
                            "No cleanup patterns defined. Add one below.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    } else {
                        val allSelected = selectedCount == patterns.size
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleAll(!allSelected) }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { onToggleAll(!allSelected) },
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Select All",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

                        patterns.sortedBy { it.orderIndex }.forEach { pattern ->
                            val isChecked = pattern.id in selectedPatternIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTogglePattern(pattern.id) }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onTogglePattern(pattern.id) },
                                    modifier = Modifier.size(36.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pattern.label.ifEmpty { pattern.regex },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                    Text(
                                        text = if (pattern.replacement.isNotEmpty()) "${pattern.regex} ➔ \"${pattern.replacement}\"" else pattern.regex,
                                        fontSize = 10.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isChecked) 0.8f else 0.4f)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Inline-create toggle / form
                    AnimatedVisibility(
                        visible = !showInlineCreate,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        TextButton(
                            onClick = onShowInlineCreate,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Cleanup Rule", fontSize = 12.sp)
                        }
                    }

                    AnimatedVisibility(
                        visible = showInlineCreate,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        InlineCreatePatternForm(
                            state = inlineCreateState,
                            onRegexChange = onRegexChange,
                            onLabelChange = onLabelChange,
                            onReplacementChange = onReplacementChange,
                            onSave = onSavePattern,
                            onCancel = onDismissInlineCreate
                        )
                    }
                }
            }
        }
    }
}

/**
 * Inline create form: regex + label + replacement.
 * Validates Regex(regex) compiles before the ViewModel persists it.
 */
@Composable
fun InlineCreatePatternForm(
    state: InlineCreateState,
    onRegexChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            "New Cleanup Rule",
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = state.regex,
            onValueChange = onRegexChange,
            label = { Text("Regex pattern", fontSize = 11.sp) },
            placeholder = { Text("e.g. \\s*(1080p|720p).*", fontSize = 11.sp) },
            isError = state.regexError != null,
            supportingText = {
                if (state.regexError != null) {
                    Text(state.regexError, color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                } else if (state.replacement.isEmpty()) {
                    Text("Replacement is empty string — matched text is removed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Standard regular expression matched case-insensitively", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 12.sp
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = state.label,
            onValueChange = onLabelChange,
            label = { Text("Label (optional)", fontSize = 11.sp) },
            placeholder = { Text("e.g. Remove quality tags", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = state.replacement,
            onValueChange = onReplacementChange,
            label = { Text("Replace with (optional)", fontSize = 11.sp) },
            placeholder = { Text("Leave empty to delete matched text", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel", fontSize = 12.sp)
            }
            Button(
                onClick = onSave,
                enabled = state.regex.isNotBlank() && !state.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Text("Add Rule", fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
fun DecisionBadge(decision: RenameDecision) {
    val (text, color) = when (decision) {
        RenameDecision.RENAME -> "WILL RENAME" to MaterialTheme.colorScheme.primaryContainer
        RenameDecision.NO_CHANGE -> "NO CHANGE" to MaterialTheme.colorScheme.surfaceVariant
        RenameDecision.SKIP_COLLISION -> "COLLISION" to MaterialTheme.colorScheme.errorContainer
        RenameDecision.EXCLUDED_INVALID -> "INVALID NAME" to Color(0xFFFFDAD9)
        RenameDecision.EXCLUDED_UNRENAMABLE -> "UNRENAMABLE" to MaterialTheme.colorScheme.surfaceVariant
        RenameDecision.EXCLUDED_UNVERIFIABLE -> "UNVERIFIABLE" to MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        color = color,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = if (decision == RenameDecision.SKIP_COLLISION) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusBadge(status: com.splitandmerge.mkvslice.domain.rename.RenameStatus) {
    val (text, color, textColor) = when (status) {
        is com.splitandmerge.mkvslice.domain.rename.RenameStatus.Success -> 
            Triple("SUCCESS", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        is com.splitandmerge.mkvslice.domain.rename.RenameStatus.Failed -> 
            Triple("FAILED: ${status.reason}", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        is com.splitandmerge.mkvslice.domain.rename.RenameStatus.Skipped -> 
            Triple("SKIPPED: ${status.reason}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        is com.splitandmerge.mkvslice.domain.rename.RenameStatus.Excluded -> 
            Triple("EXCLUDED: ${status.reason}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(
        color = color,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = textColor
        )
    }
}

@Composable
fun EditRowDialog(
    row: RenameFileRowState,
    viewModel: RenameVideosViewModel,
    onDismiss: () -> Unit
) {
    var manualName by remember { mutableStateOf(row.newBaseName) }
    val autoSuffixSet by viewModel.perRowAutoSuffix.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Video Name", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Original Name:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(row.displayName, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))

                OutlinedTextField(
                    value = manualName,
                    onValueChange = {
                        manualName = it
                        viewModel.updateManualName(row.id, it)
                    },
                    label = { Text("New Base Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { viewModel.toggleAutoSuffix(row.id) }
                ) {
                    Checkbox(
                        checked = autoSuffixSet.contains(row.id),
                        onCheckedChange = { viewModel.toggleAutoSuffix(row.id) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Auto-suffix on Collision", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Appends (1), (2), etc., to prevent file overwriting.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text("Target Final Name:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = row.targetName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (row.decision == RenameDecision.RENAME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                DecisionBadge(decision = row.decision)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
