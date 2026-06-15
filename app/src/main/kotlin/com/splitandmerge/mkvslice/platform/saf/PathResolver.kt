package com.splitandmerge.mkvslice.platform.saf

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import timber.log.Timber

object PathResolver {
    fun resolveTreeUriToRealPath(context: Context, uri: Uri): String? {
        try {
            if (uri.scheme == "file") {
                return uri.path
            }
            
            var docId: String? = null
            if (DocumentsContract.isDocumentUri(context, uri)) {
                docId = DocumentsContract.getDocumentId(uri)
            } else if (isTreeUri(uri)) {
                docId = DocumentsContract.getTreeDocumentId(uri)
            }
            
            if (docId != null) {
                val split = docId.split(":")
                if (split.isNotEmpty()) {
                    val type = split[0]
                    val path = if (split.size > 1) split[1] else ""

                    if ("primary".equals(type, ignoreCase = true)) {
                        return "${Environment.getExternalStorageDirectory()}/$path"
                    } else {
                        // Attempt to locate removable storage
                        val storageDirs = context.getExternalFilesDirs(null)
                        for (dir in storageDirs) {
                            if (dir != null) {
                                val rootPath = dir.absolutePath.substringBefore("/Android/")
                                if (rootPath.contains(type, ignoreCase = true)) {
                                    return "$rootPath/$path"
                                }
                            }
                        }
                        // Fallback common pattern for SD cards
                        return "/storage/$type/$path"
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to resolve URI to real path: $uri")
        }
        return null
    }

    private fun isTreeUri(uri: Uri): Boolean {
        val paths = uri.pathSegments
        return paths.size >= 2 && "tree" == paths[0]
    }
}
