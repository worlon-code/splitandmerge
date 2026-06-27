package com.splitandmerge.mkvslice.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.splitandmerge.mkvslice.domain.rename.DocumentRenamer
import com.splitandmerge.mkvslice.domain.rename.RenameOutcome
import com.splitandmerge.mkvslice.domain.rename.VideoScanner
import com.splitandmerge.mkvslice.ui.rename.RenameFileRowState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RenameRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRenamer {

    fun scanFolder(
        treeUri: Uri,
        includeSubfolders: Boolean,
        onProgress: (Int) -> Unit
    ): VideoScanner.ScanResult {
        return VideoScanner.scanFolder(context, treeUri, includeSubfolders, onProgress)
    }

    fun scanPickedFiles(uris: List<Uri>): List<RenameFileRowState> {
        return VideoScanner.scanPickedFiles(context, uris)
    }

    override fun rename(uri: String, newName: String): RenameOutcome {
        return try {
            val parsedUri = Uri.parse(uri)
            val newUri = DocumentsContract.renameDocument(context.contentResolver, parsedUri, newName)
            if (newUri != null) {
                val actualName = queryDisplayName(newUri)
                    ?: run {
                        try {
                            val docId = DocumentsContract.getDocumentId(newUri)
                            if (docId.isNotBlank()) displayNameFromDocId(docId) else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                    ?: newName
                RenameOutcome.Success(actualName)
            } else {
                RenameOutcome.Failure("renameDocument returned null URI")
            }
         } catch (e: SecurityException) {
             RenameOutcome.Failure(e.message ?: "Permission Denied", isPermissionError = true)
         } catch (e: Exception) {
             RenameOutcome.Failure(e.message ?: "Unknown rename error")
         }
    }

    override fun supportsRename(uri: String): Boolean {
         return try {
             val parsedUri = Uri.parse(uri)
             context.contentResolver.query(
                 parsedUri,
                 arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                 null, null, null
             )?.use { cursor ->
                 if (cursor.moveToFirst()) {
                     val flags = cursor.getInt(0)
                     (flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME) != 0
                 } else {
                     false
                 }
             } ?: false
         } catch (e: Exception) {
             false
         }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

fun displayNameFromDocId(docId: String): String {
    return docId.substringAfterLast('/')
}
