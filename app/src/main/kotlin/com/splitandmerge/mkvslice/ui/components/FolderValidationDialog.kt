package com.splitandmerge.mkvslice.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.splitandmerge.mkvslice.R
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidation

@Composable
fun FolderValidationDialog(
    validation: OutputFolderValidation?,
    onPickAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    if (validation == null || validation is OutputFolderValidation.Ok) return

    val title = stringResource(id = R.string.folder_validation_title)
    
    val text = when (validation) {
        is OutputFolderValidation.NotReachable -> {
            stringResource(id = R.string.folder_validation_not_reachable)
        }
        is OutputFolderValidation.PermissionRevoked -> {
            stringResource(id = R.string.folder_validation_permission_revoked)
        }
        is OutputFolderValidation.NotWritable -> {
            stringResource(id = R.string.folder_validation_not_writable, validation.reason)
        }
        is OutputFolderValidation.InsufficientSpace -> {
            val neededGb = validation.needed.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val haveGb = validation.have.toDouble() / (1024.0 * 1024.0 * 1024.0)
            stringResource(id = R.string.folder_validation_insufficient_space, neededGb, haveGb)
        }
        else -> ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = text) },
        confirmButton = {
            TextButton(onClick = onPickAgain) {
                Text(text = stringResource(id = R.string.folder_validation_pick_again))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.folder_validation_cancel))
            }
        }
    )
}
