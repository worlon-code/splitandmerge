package com.splitandmerge.mkvslice.data.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.splitandmerge.mkvslice.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateService: UpdateService,
    private val okHttpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    internal var sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }

    private var activeCall: Call? = null
    private var isReceiverRegistered = false

    private val installReceiver = object : BroadcastReceiver() {
        @SuppressLint("NewApi")
        override fun onReceive(ctx: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirmIntent = if (sdkIntProvider() >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                    if (confirmIntent != null) {
                        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(confirmIntent)
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    _state.update { it.copy(phase = Phase.InstallLaunched) }
                    unregisterReceiver()
                }
                else -> {
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed (code $status)"
                    _state.update { it.copy(phase = Phase.Error, errorMessage = message) }
                    unregisterReceiver()
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter("com.splitandmerge.mkvslice.ACTION_INSTALL_STATUS")
        if (sdkIntProvider() >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(installReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(installReceiver, filter)
        }
        isReceiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(installReceiver)
        } catch (e: Exception) {
            // ignore
        }
        isReceiverRegistered = false
    }

    @SuppressLint("NewApi")
    suspend fun checkForUpdate() = withContext(ioDispatcher) {
        _state.update { it.copy(phase = Phase.Checking, errorMessage = null) }
        try {
            val manifest = updateService.fetchManifest()

            // Strict HTTPS Check
            if (!manifest.apkUrl.startsWith("https://")) {
                _state.update { it.copy(phase = Phase.Error, errorMessage = "Update URL is not HTTPS. Aborted.") }
                return@withContext
            }

            val sha256Regex = Regex("^[0-9a-fA-F]{64}$")
            if (!sha256Regex.matches(manifest.sha256)) {
                _state.update { it.copy(phase = Phase.Error, errorMessage = "Invalid update metadata. Hash format incorrect.") }
                return@withContext
            }

            val maxApkSize = 200L * 1024L * 1024L // 200 MB bound
            if (manifest.sizeBytes <= 0 || manifest.sizeBytes > maxApkSize) {
                _state.update { it.copy(phase = Phase.Error, errorMessage = "Invalid update metadata. Size limit exceeded.") }
                return@withContext
            }

            val packageInfo = if (sdkIntProvider() >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val currentVersionCode = packageInfo.versionCode
            val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

            if (manifest.versionCode <= currentVersionCode) {
                _state.update {
                    it.copy(
                        phase = Phase.UpToDate,
                        manifest = manifest,
                        errorMessage = null
                    )
                }
            } else {
                val targetPhase = if (isDebug) Phase.AvailableButDebug else Phase.AvailableReady
                _state.update {
                    it.copy(
                        phase = targetPhase,
                        manifest = manifest,
                        errorMessage = if (isDebug) "Install disabled in debug build" else null
                    )
                }
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    phase = Phase.Error,
                    errorMessage = "Could not check for updates. Try again later."
                )
            }
        }
    }

    suspend fun downloadAndInstall() = withContext(ioDispatcher) {
        val currentState = _state.value
        if (currentState.phase != Phase.AvailableReady) {
            return@withContext
        }
        val manifest = currentState.manifest ?: return@withContext

        _state.update { it.copy(phase = Phase.Downloading, downloadedBytes = 0, totalBytes = manifest.sizeBytes, errorMessage = null) }

        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }
        updatesDir.listFiles()?.forEach { it.delete() }

        val tmpFile = File(updatesDir, "app-update.${System.currentTimeMillis()}.tmp")
        val request = Request.Builder().url(manifest.apkUrl).build()
        val call = okHttpClient.newCall(request)
        activeCall = call

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                throw IOException("Server returned code ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            
            var bytesWritten = 0L
            body.byteStream().use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        output.write(buffer, 0, bytes)
                        bytesWritten += bytes
                        _state.update { it.copy(downloadedBytes = bytesWritten) }
                        bytes = input.read(buffer)
                    }
                }
            }

            if (bytesWritten != manifest.sizeBytes) {
                throw IOException("Downloaded size mismatch. Expected ${manifest.sizeBytes}, got $bytesWritten")
            }

            // Verify hash on .tmp file BEFORE renaming
            _state.update { it.copy(phase = Phase.Verifying) }
            val digest = MessageDigest.getInstance("SHA-256")
            tmpFile.inputStream().use { input ->
                val buffer = ByteArray(256 * 1024)
                var bytes = input.read(buffer)
                while (bytes >= 0) {
                    digest.update(buffer, 0, bytes)
                    bytes = input.read(buffer)
                }
            }
            val hashBytes = digest.digest()
            val computedHash = hashBytes.joinToString("") { "%02x".format(it) }

            if (computedHash != manifest.sha256.lowercase()) {
                tmpFile.delete()
                _state.update { it.copy(phase = Phase.Error, errorMessage = "Update verification failed. Hash mismatch.") }
                return@withContext
            }

            // Rename to app-update.apk
            val apkFile = File(updatesDir, "app-update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }
            if (!tmpFile.renameTo(apkFile)) {
                tmpFile.delete()
                _state.update { it.copy(phase = Phase.Error, errorMessage = "Failed to rename update file.") }
                return@withContext
            }

            _state.update { it.copy(phase = Phase.ReadyToInstall) }

            // Launch PackageInstaller
            registerReceiver()
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = packageInstaller.createSession(params)
            var session: PackageInstaller.Session? = null

            try {
                session = packageInstaller.openSession(sessionId)
                session.openWrite("app_update", 0, -1).use { out ->
                    apkFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }

                val intent = Intent("com.splitandmerge.mkvslice.ACTION_INSTALL_STATUS").apply {
                    `package` = context.packageName
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    if (sdkIntProvider() >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )

                session.commit(pendingIntent.intentSender)
            } catch (e: Exception) {
                unregisterReceiver()
                _state.update { it.copy(phase = Phase.Error, errorMessage = "Installation failed: ${e.message}") }
            } finally {
                session?.close()
            }

        } catch (e: Exception) {
            tmpFile.delete()
            if (call.isCanceled()) {
                _state.update { it.copy(phase = Phase.Idle) }
            } else {
                _state.update { it.copy(phase = Phase.Error, errorMessage = "Download failed: ${e.message}") }
            }
        } finally {
            activeCall = null
        }
    }

    fun cancel() {
        activeCall?.cancel()
        unregisterReceiver()
        _state.update { it.copy(phase = Phase.Idle, downloadedBytes = 0, totalBytes = 0) }
    }
}
