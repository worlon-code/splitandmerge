package com.splitandmerge.mkvslice.data.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException

interface SessionInstaller {
    fun canRequestPackageInstalls(): Boolean
    fun launchInstallSettings()
    suspend fun install(verified: Verified): Result<Unit>
}

class RealSessionInstaller(private val context: Context) : SessionInstaller {
    override fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    override fun launchInstallSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override suspend fun install(verified: Verified): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // S9 TOCTOU check: Re-confirm the file length immediately before commit
            val currentLength = verified.file.length()
            if (currentLength != verified.expectedLength) {
                return@withContext Result.failure(IOException("TOCTOU protection triggered: file size changed from ${verified.expectedLength} to $currentLength"))
            }

            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            val sessionId = packageInstaller.createSession(params)
            var session: PackageInstaller.Session? = null

            try {
                session = packageInstaller.openSession(sessionId)
                session.openWrite("app_update", 0, currentLength).use { outputStream ->
                    FileInputStream(verified.file).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // S9 TOCTOU check: re-verify file size didn't change right before commit
                val postLength = verified.file.length()
                if (postLength != verified.expectedLength) {
                    throw IOException("TOCTOU protection triggered during streaming: file size changed")
                }

                val intent = Intent("com.splitandmerge.mkvslice.ACTION_INSTALL_STATUS").apply {
                    `package` = context.packageName
                    putExtra("SESSION_ID", sessionId)
                }
                
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    flags
                )

                session.commit(pendingIntent.intentSender)
                Result.success(Unit)
            } catch (e: Exception) {
                try {
                    session?.abandon()
                } catch (ignore: Exception) {}
                Result.failure(e)
            } finally {
                try {
                    session?.close()
                } catch (ignore: Exception) {}
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
