package com.splitandmerge.mkvslice.ui.splitconfig

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitandmerge.mkvslice.domain.model.SplitMode
import com.splitandmerge.mkvslice.ui.components.FolderValidationDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitConfigScreen(
    viewModel: SplitConfigViewModel,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()
    val scrollState = rememberScrollState()

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
                title = { Text("Split Configuration", fontWeight = FontWeight.Bold) },
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
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                Text("Split Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.mode == SplitMode.EXACT_PARTS,
                        onClick = { viewModel.updateMode(SplitMode.EXACT_PARTS) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) {
                        Text("Parts")
                    }
                    SegmentedButton(
                        selected = state.mode == SplitMode.SIZE_CAP_ONLY,
                        onClick = { viewModel.updateMode(SplitMode.SIZE_CAP_ONLY) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) {
                        Text("Size Cap")
                    }
                    SegmentedButton(
                        selected = state.mode == SplitMode.BOTH,
                        onClick = { viewModel.updateMode(SplitMode.BOTH) },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) {
                        Text("Both")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (state.mode == SplitMode.EXACT_PARTS || state.mode == SplitMode.BOTH) {
                    Text(
                        text = "Number of Parts: ${state.partsCount}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Slider(
                        value = state.partsCount.toFloat(),
                        onValueChange = { viewModel.updatePartsCount(it.toInt()) },
                        valueRange = 2f..50f,
                        steps = 47,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (state.mode == SplitMode.SIZE_CAP_ONLY || state.mode == SplitMode.BOTH) {
                    OutlinedTextField(
                        value = state.sizeCapGb.toString(),
                        onValueChange = {
                            it.toFloatOrNull()?.let { cap -> viewModel.updateSizeCap(cap) }
                        },
                        label = { Text("Size Cap per Part (GB)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = state.baseName,
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

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Split Summary",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This will split the video into ${state.predictedPartCount} parts, with each part averaging approximately ${String.format("%.2f", state.predictedPartSizeGb)} GB.",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
