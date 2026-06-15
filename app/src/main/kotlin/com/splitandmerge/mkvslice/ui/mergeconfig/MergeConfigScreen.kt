package com.splitandmerge.mkvslice.ui.mergeconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.splitandmerge.mkvslice.ui.components.FolderValidationDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeConfigScreen(
    viewModel: MergeConfigViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()

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
                title = { Text("Merge Configuration", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = state.outputBaseName,
                    onValueChange = { viewModel.updateBaseName(it) },
                    label = { Text("Output Base Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Output Folder", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = "Folder")
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = state.outputFolder.ifEmpty { "Select Output Folder" },
                            fontSize = 14.sp,
                            color = if (state.outputFolder.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { folderPicker.launch(null) }) {
                            Icon(Icons.Default.Folder, contentDescription = "Change")
                        }
                    }
                }
            }

            Button(
                onClick = { 
                    viewModel.submitMergeJob { jobId ->
                        onConfirm(jobId)
                    }
                },
                enabled = state.outputFolder.isNotEmpty() && state.outputBaseName.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Start Merge", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
