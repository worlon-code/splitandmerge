package com.splitandmerge.mkvslice.domain.rename

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.splitandmerge.mkvslice.ui.rename.RenameFileRowState

object VideoScanner {

    private data class TempVideoFile(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val flags: Int
    )

    class ScanResult(
        val files: List<RenameFileRowState>,
        val isCapped: Boolean
    )

    fun scanFolder(
        context: Context,
        treeUri: Uri,
        includeSubfolders: Boolean,
        onProgress: (Int) -> Unit
    ): ScanResult {
        var scanCount = 0
        var isScanCapped = false
        val results = mutableListOf<RenameFileRowState>()

        fun recurse(dirDocId: String, depth: Int) {
            if (depth > 5 || scanCount >= 1000) {
                isScanCapped = true
                return
            }

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_FLAGS
            )

            val allChildNames = mutableListOf<String>()
            val videoFiles = mutableListOf<TempVideoFile>()
            val subFolders = mutableListOf<String>()

            try {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val flagsCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)

                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idCol)
                        val displayName = cursor.getString(nameCol)
                        val mimeType = cursor.getString(mimeCol)
                        val size = cursor.getLong(sizeCol)
                        val flags = if (flagsCol >= 0) cursor.getInt(flagsCol) else 0

                        allChildNames.add(displayName)

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (includeSubfolders) {
                                subFolders.add(docId)
                            }
                        } else {
                            val dotIdx = displayName.lastIndexOf('.')
                            val extLower = if (dotIdx >= 0) displayName.substring(dotIdx + 1).lowercase() else ""
                            if (VIDEO_EXTENSIONS.contains(extLower)) {
                                videoFiles.add(
                                    TempVideoFile(
                                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                                        displayName = displayName,
                                        sizeBytes = size,
                                        flags = flags
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore query failures gracefully
            }

            // Map and add video files of the current folder
            for (vf in videoFiles) {
                if (scanCount >= 1000) {
                    isScanCapped = true
                    break
                }
                scanCount++
                onProgress(scanCount)

                val dotIdx = vf.displayName.lastIndexOf('.')
                val base = if (dotIdx >= 0) vf.displayName.substring(0, dotIdx) else vf.displayName
                val ext = if (dotIdx >= 0) vf.displayName.substring(dotIdx) else ""

                results.add(
                    RenameFileRowState(
                        id = java.util.UUID.randomUUID().toString(),
                        uri = vf.uri.toString(),
                        displayName = vf.displayName,
                        sizeBytes = vf.sizeBytes,
                        parentKey = dirDocId,
                        parentKnown = true,
                        supportsRename = (vf.flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME) != 0,
                        existingNamesInParent = allChildNames,
                        originalBaseName = base,
                        extension = ext,
                        newBaseName = base
                    )
                )
            }

            // Recurse into directories
            if (includeSubfolders && !isScanCapped) {
                for (subFolderId in subFolders) {
                    if (scanCount >= 1000) {
                        isScanCapped = true
                        break
                    }
                    recurse(subFolderId, depth + 1)
                }
            }
        }

        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            recurse(rootDocId, 1)
        } catch (e: Exception) {
            // Ignore tree ID extraction failure
        }

        return ScanResult(results, isScanCapped)
    }

    fun scanPickedFiles(context: Context, uris: List<Uri>): List<RenameFileRowState> {
        return uris.mapNotNull { uri ->
            var displayName = "Unknown"
            var size = 0L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameCol >= 0) displayName = cursor.getString(nameCol)
                        if (sizeCol >= 0) size = cursor.getLong(sizeCol)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }

            // Hard filter by VIDEO_EXTENSIONS
            val dotIdx = displayName.lastIndexOf('.')
            val extLower = if (dotIdx >= 0) displayName.substring(dotIdx + 1).lowercase() else ""
            if (!VIDEO_EXTENSIONS.contains(extLower)) {
                return@mapNotNull null
            }

            var flags = 0
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val flagsCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                        if (flagsCol >= 0) {
                            flags = cursor.getInt(flagsCol)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }

            val base = if (dotIdx >= 0) displayName.substring(0, dotIdx) else displayName
            val ext = if (dotIdx >= 0) displayName.substring(dotIdx) else ""

            RenameFileRowState(
                id = java.util.UUID.randomUUID().toString(),
                uri = uri.toString(),
                displayName = displayName,
                sizeBytes = size,
                // parentKnown = true: planner can assign RENAME; collision against existing
                // disk files is deferred to STEP-9 apply (try-then-retry-(N) on live disk).
                parentKey = "picked-session",
                parentKnown = true,
                supportsRename = (flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME) != 0,
                existingNamesInParent = emptyList(),
                originalBaseName = base,
                extension = ext,
                newBaseName = base,
                isPickedFile = true
            )
        }
    }
}
