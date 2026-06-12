package com.splitandmerge.mkvslice.ui.cleanup

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupPatternsScreenTablet(
    viewModel: CleanupPatternsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var newRuleLabel by remember { mutableStateOf("") }
    var newRuleRegex by remember { mutableStateOf("") }
    val rightScrollState = rememberScrollState()

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
                    title = { Text("Title Cleanup (Tablet)", fontWeight = FontWeight.Bold) },
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
                // Left Pane: Patterns List + Add Pattern Form
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Active Rules",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.weight(0.6f)
                    ) {
                        items(state.patterns) { pattern ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pattern.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Regex: ${pattern.regex}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = pattern.enabled,
                                        onCheckedChange = { viewModel.togglePattern(pattern.id, it) }
                                    )
                                    if (!pattern.isBuiltIn) {
                                        IconButton(onClick = { viewModel.deletePattern(pattern.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Add Pattern Form Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Add Custom Regex Rule", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newRuleLabel,
                                onValueChange = { newRuleLabel = it },
                                label = { Text("Label / Description") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newRuleRegex,
                                onValueChange = { newRuleRegex = it },
                                label = { Text("Regular Expression Pattern") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (newRuleLabel.isNotEmpty() && newRuleRegex.isNotEmpty()) {
                                        viewModel.addPattern(newRuleRegex, newRuleLabel)
                                        newRuleLabel = ""
                                        newRuleRegex = ""
                                    }
                                },
                                enabled = newRuleLabel.isNotEmpty() && newRuleRegex.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Custom Rule")
                            }
                        }
                    }
                }

                // Right Pane: Live preview + Step-by-Step trace log
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .verticalScroll(rightScrollState)
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Live Sandbox & Trace",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = state.sampleInput,
                        onValueChange = { viewModel.updateSampleInput(it) },
                        label = { Text("Test Input Filename") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Final Cleaned Filename", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            Text(
                                text = if (state.sampleOutput.isEmpty()) "[Empty filename]" else state.sampleOutput,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Sequential Transform Trace", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Compute step-by-step trace
                    val steps = computeTraceSteps(state.sampleInput, state.patterns)
                    steps.forEachIndexed { index, step ->
                        TraceStepItem(stepIndex = index + 1, stepName = step.first, stepResult = step.second)
                    }
                }
            }
        }
    }
}

@Composable
fun TraceStepItem(stepIndex: Int, stepName: String, stepResult: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Step $stepIndex",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stepName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = stepResult, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun computeTraceSteps(input: String, patterns: List<CleanupPattern>): List<Pair<String, String>> {
    val steps = mutableListOf<Pair<String, String>>()
    var text = input
    steps.add(Pair("Initial Input (with extension)", text))

    val lastDot = text.lastIndexOf('.')
    if (lastDot > 0) {
        text = text.substring(0, lastDot)
        steps.add(Pair("Remove File Extension", text))
    }

    patterns.filter { it.enabled }.sortedBy { it.orderIndex }.forEach { rule ->
        val oldText = text
        try {
            text = text.replace(rule.regex.toRegex(), rule.replacement)
        } catch (e: Exception) {
            // Ignore
        }
        if (text != oldText) {
            steps.add(Pair(rule.label, text))
        }
    }

    val finalClean = text.replace("\\s+".toRegex(), " ").trim()
    if (finalClean != text) {
        steps.add(Pair("Collapse Spaces & Trim", finalClean))
    }

    return steps
}
