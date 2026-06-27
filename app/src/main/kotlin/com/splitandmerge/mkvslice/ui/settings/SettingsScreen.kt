package com.splitandmerge.mkvslice.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import com.splitandmerge.mkvslice.data.settings.ThemeMode
import com.splitandmerge.mkvslice.data.update.UpdateState
import androidx.compose.material3.LinearProgressIndicator
import com.splitandmerge.mkvslice.ui.components.FolderValidationDialog
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToCleanupPatterns: () -> Unit,
    onNavigateToOssNotices: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToRenameVideos: () -> Unit,
    onNavigateToHelp: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val scrollState = rememberScrollState()
    var dropdownExpanded by remember { mutableStateOf(false) }

    val folderPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.updateOutputFolder(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            // Theme Section
            Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = when (state.themeMode) {
                        ThemeMode.SYSTEM -> "Follow System"
                        ThemeMode.LIGHT -> "Light Theme"
                        ThemeMode.DARK -> "Dark Theme (AMOLED)"
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Theme Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    ThemeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "Follow System"
                                        ThemeMode.LIGHT -> "Light Theme"
                                        ThemeMode.DARK -> "Dark Theme (AMOLED)"
                                    }
                                )
                            },
                            onClick = {
                                viewModel.updateTheme(mode)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Split/Merge Parameters Section
            Text("Defaults", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.defaultCapGb.toString(),
                onValueChange = {
                    it.toFloatOrNull()?.let { cap -> viewModel.updateCapGb(cap) }
                },
                label = { Text("Default Size Threshold per Part (GB)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Default Output Folder", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = state.defaultOutputFolder.ifEmpty { "Not Set" }, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Default.Folder, contentDescription = "Change Folder")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Switches
            Text("App Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Safe Mode Copying", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Slightly slower, but increases metadata compatibility.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.improveReliability,
                    onCheckedChange = { viewModel.toggleReliability(it) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keep Screen On during Jobs", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Prevents system sleep during long operations.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.keepScreenOn,
                    onCheckedChange = { viewModel.toggleKeepScreenOn(it) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val context = LocalContext.current
            val isIgnoringBatteryOptimizations by viewModel.isIgnoringBatteryOptimizations.collectAsState()
            val lifecycleOwner = LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.checkBatteryOptimizations()
                        viewModel.checkInstallPermission()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ignore Battery Optimizations", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        text = if (isIgnoringBatteryOptimizations) {
                            "Already exempted. Tap to manage in system settings."
                        } else {
                            "Prevents system throttling split and merge tasks."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isIgnoringBatteryOptimizations,
                    onCheckedChange = { ignore ->
                        val intent = if (ignore) {
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:" + context.packageName)
                            }
                        } else {
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Timber.tag("Settings").w(e, "Battery optimization intent failed")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Entries
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsNavigationItem(
                        icon = { Icon(Icons.Default.CleaningServices, contentDescription = "Cleanup Patterns") },
                        title = "Title Cleanup Patterns",
                        subtitle = "Configure regular expressions to clean up filenames",
                        onClick = onNavigateToCleanupPatterns
                    )
                    Divider()
                    SettingsNavigationItem(
                        icon = { Icon(Icons.Default.BugReport, contentDescription = "Diagnostic Logs") },
                        title = "View diagnostic logs",
                        subtitle = "Stored locally for 7 days; never sent automatically",
                        onClick = onNavigateToLogs
                    )
                    Divider()
                    SettingsNavigationItem(
                        icon = { Icon(Icons.Default.Info, contentDescription = "Licenses") },
                        title = "Open Source Notices",
                        subtitle = "Review licenses for external libraries",
                        onClick = onNavigateToOssNotices
                    )
                    Divider()
                    SettingsNavigationItem(
                        icon = { Icon(Icons.Default.Help, contentDescription = "Help") },
                        title = "How to Use / Help",
                        subtitle = "Step-by-step usage guide with visual previews",
                        onClick = onNavigateToHelp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Beta section
            Text("Beta", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsNavigationItem(
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Rename Videos") },
                    title = "Rename Videos",
                    subtitle = "Scan folders and clean video titles in batch",
                    onClick = onNavigateToRenameVideos
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Update Section
            Text("Update Checker", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Current Version: v${state.currentVersion}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = state.updateMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    when (val currentUpdate = updateState) {
                        is UpdateState.Available -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("New Version: v${currentUpdate.versionName}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            val sizeMb = currentUpdate.size.toDouble() / (1024.0 * 1024.0)
                            Text("Size: %.2f MB".format(sizeMb), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            if (currentUpdate.changelog.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Changelog:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                currentUpdate.changelog.forEach { bullet ->
                                    Text("• $bullet", fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.downloadUpdate(currentUpdate) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Download")
                            }
                        }
                        is UpdateState.Downloading -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { currentUpdate.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is UpdateState.Verifying -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        is UpdateState.NeedsInstallPermission -> {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.launchInstallSettings() },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant Install Permission")
                            }
                        }
                        is UpdateState.ReadyToInstall -> {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.installUpdate() },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Install Now")
                            }
                        }
                        is UpdateState.Installing -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        else -> {
                            // Idle, Checking, UpToDate, Installed, Error
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val isBusy = updateState is UpdateState.Checking ||
                            updateState is UpdateState.Downloading ||
                            updateState is UpdateState.Verifying ||
                            updateState is UpdateState.Installing

                    Button(
                        onClick = { viewModel.checkForUpdates() },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (updateState is UpdateState.Checking) {
                            CircularProgressIndicator(modifier = Modifier.width(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Check for updates")
                    }
                }
            }

            FolderValidationDialog(
                validation = validationResult,
                onPickAgain = {
                    viewModel.onPickFolderAgain()
                    folderPicker.launch(null)
                },
                onDismiss = {
                    viewModel.dismissValidation()
                }
            )
        }
    }
}

@Composable
fun SettingsNavigationItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Navigate")
    }
}
