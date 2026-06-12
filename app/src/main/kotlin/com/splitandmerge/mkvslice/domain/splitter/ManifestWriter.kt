package com.splitandmerge.mkvslice.domain.splitter

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.splitandmerge.mkvslice.domain.model.Manifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ManifestWriter @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Serializes the [manifest] to JSON and writes it to `<baseName>.split.json` inside [outputDirUri].
     */
    suspend fun writeManifest(outputDirUri: Uri, baseName: String, manifest: Manifest): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dir = if (outputDirUri.scheme == "file") {
                DocumentFile.fromFile(java.io.File(outputDirUri.path!!))
            } else {
                DocumentFile.fromTreeUri(context, outputDirUri)
            } ?: return@withContext Result.failure(Exception("Cannot access output directory"))
            
            val filename = "$baseName.split.json"
            val jsonString = json.encodeToString(manifest)
            
            if (outputDirUri.scheme == "file") {
                // Direct file operations for file:// URIs
                java.io.File(outputDirUri.path!!, filename).writeText(jsonString, Charsets.UTF_8)
                return@withContext Result.success(Unit)
            }
            
            // Delete if already exists (SAF)
            dir.findFile(filename)?.delete()
            
            val file = dir.createFile("application/json", filename)
                ?: return@withContext Result.failure(Exception("Could not create manifest file $filename"))
                
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
                out.write(jsonString.toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(Exception("Could not open output stream for manifest"))
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
