package com.splitandmerge.mkvslice.ui.cleanup

import com.splitandmerge.mkvslice.data.db.entity.CleanupPatternEntity
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.HorizontalDivider
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.MoreVert


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupPatternsScreen(
    viewModel: CleanupPatternsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPattern by remember { mutableStateOf<CleanupPatternEntity?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    viewModel.exportBackup(outputStream)
                } else {
                    scope.launch { snackbarHostState.showSnackbar("Failed to open output stream") }
                }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Export failed: ${e.message}") }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    viewModel.importRestore(inputStream)
                } else {
                    scope.launch { snackbarHostState.showSnackbar("Failed to open input stream") }
                }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Restore failed: ${e.message}") }
            }
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbarMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Title Cleanup Patterns", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Actions")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Backup Patterns") },
                                onClick = {
                                    showMenu = false
                                    backupLauncher.launch("mkvslice-cleanup-patterns.json")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Restore Patterns") },
                                onClick = {
                                    showMenu = false
                                    restoreLauncher.launch(arrayOf("application/json"))
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Add Pattern")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Pattern")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            // Live Preview Sandbox Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Live Preview Sandbox",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = state.sampleInput,
                        onValueChange = { viewModel.updateSampleInput(it) },
                        label = { Text("Sample Filename") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Cleaned Output Filename:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (state.sampleOutput.isEmpty()) "[Empty filename]" else state.sampleOutput,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Patterns List
            Text(
                text = "Regex Rules",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                items(state.patterns) { pattern ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pattern.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Regex: ${pattern.regex}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (pattern.replacement.isNotEmpty()) {
                                    Text(
                                        text = "Replace with: \"${pattern.replacement}\"",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (pattern.isBuiltIn) {
                                    Text(
                                        text = "Built-in Rule",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { editingPattern = pattern }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Pattern")
                                }
                                Switch(
                                    checked = pattern.enabled,
                                    onCheckedChange = { viewModel.togglePattern(pattern.id, it) }
                                )
                                if (!pattern.isBuiltIn) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(onClick = { viewModel.deletePattern(pattern.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Pattern", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var labelText by remember { mutableStateOf("") }
        var regexText by remember { mutableStateOf("") }
        var replacementText by remember { mutableStateOf("") }
        var regexError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Custom Pattern") },
            text = {
                Column {
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = { labelText = it },
                        label = { Text("Label Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = regexText,
                        onValueChange = {
                            regexText = it
                            regexError = try {
                                Regex(it)
                                null
                            } catch (e: Exception) {
                                e.message ?: "Invalid regex"
                            }
                        },
                        label = { Text("Regex Pattern") },
                        isError = regexError != null,
                        supportingText = { regexError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = replacementText,
                        onValueChange = { replacementText = it },
                        label = { Text("Replace with (Optional)") },
                        placeholder = { Text("Leave empty to delete matched text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (labelText.length > 0 && regexText.length > 0 && regexError == null) {
                            viewModel.addPattern(regexText, labelText, replacementText)
                            showAddDialog = false
                        }
                    },
                    enabled = labelText.length > 0 && regexText.length > 0 && regexError == null
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val currentEditing = editingPattern
    if (currentEditing != null) {
        var labelText by remember(currentEditing.id) { mutableStateOf<String>(currentEditing.label) }
        var regexText by remember(currentEditing.id) { mutableStateOf<String>(currentEditing.regex) }
        var replacementText by remember(currentEditing.id) { mutableStateOf<String>(currentEditing.replacement) }
        var regexError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingPattern = null },
            title = { Text("Edit Pattern") },
            text = {
                Column {
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = { labelText = it },
                        label = { Text("Label Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = regexText,
                        onValueChange = {
                            regexText = it
                            regexError = try {
                                Regex(it)
                                null
                            } catch (e: Exception) {
                                e.message ?: "Invalid regex"
                            }
                        },
                        label = { Text("Regex Pattern") },
                        isError = regexError != null,
                        supportingText = { regexError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = replacementText,
                        onValueChange = { replacementText = it },
                        label = { Text("Replace with (Optional)") },
                        placeholder = { Text("Leave empty to delete matched text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (labelText.length > 0 && regexText.length > 0 && regexError == null) {
                            viewModel.updatePattern(currentEditing.id, regexText, labelText, replacementText)
                            editingPattern = null
                        }
                    },
                    enabled = labelText.length > 0 && regexText.length > 0 && regexError == null
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPattern = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    state.restoreOutcome?.let { outcome ->
        RestoreOutcomeDialog(
            outcome = outcome,
            onDismiss = { viewModel.clearRestoreOutcome() }
        )
    }
}

@Composable
fun RestoreOutcomeDialog(
    outcome: RestoreOutcome,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore Patterns Summary", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Added patterns: ${outcome.addedCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Ignored duplicates: ${outcome.ignoredPatterns.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                if (outcome.ignoredPatterns.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Ignored Details (Already Exists):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text(
                                text = "Imported Label",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Existing Match",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        outcome.ignoredPatterns.forEach { (backupLabel, matchedLabel) ->
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = backupLabel,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = matchedLabel,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                } else if (outcome.addedCount == 0 && outcome.ignoredPatterns.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No patterns found in the backup file.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
