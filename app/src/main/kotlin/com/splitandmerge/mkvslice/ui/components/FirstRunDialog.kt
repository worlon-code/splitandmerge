package com.splitandmerge.mkvslice.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

/**
 * Non-dismissible modal dialog shown on first launch when no default output
 * folder has been set yet.
 *
 * Design rules (per Step 5c spec):
 * - No "dismiss" / "skip" button — folder is required.
 * - dismissOnClickOutside = false, dismissOnBackPress = false.
 * - Launches ACTION_OPEN_DOCUMENT_TREE; calls [onFolderPicked] with the result.
 * - If the user cancels the system picker (uri == null), the dialog stays open.
 */
@Composable
fun FirstRunDialog(onFolderPicked: (Uri) -> Unit) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            onFolderPicked(uri)
        }
        // uri == null → user pressed Back in the picker; dialog stays visible.
    }

    AlertDialog(
        onDismissRequest = { /* intentionally non-dismissible */ },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false
        ),
        title = {
            Text(
                text = "Pick a default save folder",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "Split and merged files will save here. You can change this anytime in Settings.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = { folderPicker.launch(null) }) {
                Text("Pick folder")
            }
        }
    )
}
