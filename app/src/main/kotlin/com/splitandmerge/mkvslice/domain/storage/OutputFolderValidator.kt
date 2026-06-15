package com.splitandmerge.mkvslice.domain.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutputFolderValidator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun validate(
        uriString: String,
        requiredBytes: Long,
        assumePermissionPersisted: Boolean = true
    ): OutputFolderValidation {
        if (uriString.isBlank()) {
            return OutputFolderValidation.NotReachable
        }

        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            return OutputFolderValidation.NotReachable
        }

        // a) NotReachable
        val documentFile = DocumentFile.fromTreeUri(context, uri)
        if (documentFile == null || !documentFile.exists() || !documentFile.isDirectory) {
            return OutputFolderValidation.NotReachable
        }

        val queryUri = try {
            documentFile.uri
        } catch (e: Exception) {
            uri
        }

        try {
            context.contentResolver.query(queryUri, null, null, null, null)?.close()
        } catch (e: Exception) {
            return OutputFolderValidation.NotReachable
        }

        // b) PermissionRevoked
        if (assumePermissionPersisted) {
            val hasPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && (it.isReadPermission && it.isWritePermission)
            }
            if (!hasPermission) {
                return OutputFolderValidation.PermissionRevoked
            }
        }

        // c+d) NotWritable + InsufficientSpace using a SINGLE probe
        val probeName = "${UUID.randomUUID()}.probe"
        var probeFile: DocumentFile? = null
        var usableBytes = -1L
        try {
            probeFile = documentFile.createFile("application/octet-stream", probeName)
                ?: return OutputFolderValidation.NotWritable("Could not create probe file")
            if (!probeFile.canWrite()) {
                return OutputFolderValidation.NotWritable("Probe file is not writable")
            }
            try {
                context.contentResolver.openFileDescriptor(probeFile.uri, "w")?.use { pfd ->
                    val stats = android.system.Os.fstatvfs(pfd.fileDescriptor)
                    usableBytes = stats.f_bavail * stats.f_bsize
                }
            } catch (e: Exception) {
                // fstatvfs unsupported on some SAF backends; treat as unknown rather than fail
                Timber.tag("Validator").d(e, "fstatvfs unavailable; skipping space check")
            }
        } catch (e: Exception) {
            return OutputFolderValidation.NotWritable(e.message ?: "Exception creating probe file")
        } finally {
            try { probeFile?.delete() } catch (_: Exception) {}
        }

        if (usableBytes in 0..<requiredBytes) {
            return OutputFolderValidation.InsufficientSpace(requiredBytes, usableBytes)
        }

        return OutputFolderValidation.Ok
    }
}
