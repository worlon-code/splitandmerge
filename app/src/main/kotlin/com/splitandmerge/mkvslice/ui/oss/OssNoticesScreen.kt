package com.splitandmerge.mkvslice.ui.oss

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LicenseNotice(
    val name: String,
    val publisher: String,
    val licenseName: String,
    val licenseText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OssNoticesScreen(
    onBack: () -> Unit
) {
    val licenses = listOf(
        LicenseNotice(
            name = "FFmpeg Kit (LGPL Version)",
            publisher = "Taner Sener / Anton Karpenko",
            licenseName = "GNU Lesser General Public License v3.0",
            licenseText = "This software uses libraries from the FFmpeg project under the LGPLv3.0. We do not re-encode any audio/video stream and do not enable any GPL flag, complying strictly with LGPL requirements. Full license code can be obtained at https://www.gnu.org/licenses/lgpl-3.0.txt."
        ),
        LicenseNotice(
            name = "Dagger Hilt Dependency Injection",
            publisher = "Google LLC",
            licenseName = "Apache License 2.0",
            licenseText = "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0"
        ),
        LicenseNotice(
            name = "Jetpack Compose, Room Database & AndroidX Libraries",
            publisher = "Android Open Source Project",
            licenseName = "Apache License 2.0",
            licenseText = "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0"
        ),
        LicenseNotice(
            name = "Kotlin Coroutines & Serialization",
            publisher = "JetBrains s.r.o.",
            licenseName = "Apache License 2.0",
            licenseText = "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0"
        ),
        LicenseNotice(
            name = "Timber Logging Library",
            publisher = "Jake Wharton",
            licenseName = "Apache License 2.0",
            licenseText = "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open Source Notices", fontWeight = FontWeight.Bold) },
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
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                item {
                    Text(
                        text = "Licenses & Credits",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "The following open-source dependencies make MKV Slice possible. We thank the maintainers for their work.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                items(licenses) { notice ->
                    LicenseNoticeRowItem(notice)
                }
            }
        }
    }
}

@Composable
fun LicenseNoticeRowItem(notice: LicenseNotice) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = notice.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = "Publisher: ${notice.publisher}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "License: ${notice.licenseName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = "Expand details"
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = notice.licenseText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
