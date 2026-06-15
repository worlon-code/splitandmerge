package com.splitandmerge.mkvslice.domain.merger

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

import timber.log.Timber

object MergePathResolver {
    fun resolveUriToPath(context: Context, uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == null || (!uriString.startsWith("content://") && !uriString.contains("://"))) {
                return uriString
            }
            if (uri.scheme == "file") {
                return uri.path
            }
            if (uri.scheme == "content") {
                if (uri.authority == "com.android.externalstorage.documents") {
                    val docId = if (DocumentsContract.isDocumentUri(context, uri)) {
                        DocumentsContract.getDocumentId(uri)
                    } else {
                        // Check if it's a tree URI
                        val paths = uri.pathSegments
                        if (paths.size >= 2 && paths[0] == "tree") {
                            paths[1]
                        } else {
                            return null
                        }
                    }
                    val split = docId.split(":")
                    if (split.size >= 2 && "primary".equals(split[0], ignoreCase = true)) {
                        val path = split[1]
                        val primaryStorage = Environment.getExternalStorageDirectory().absolutePath
                        val file = File(primaryStorage, path)
                        return file.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to resolve URI to path")
        }
        return null
    }
}
