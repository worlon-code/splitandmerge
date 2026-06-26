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
import androidx.core.content.ContextCompat
import com.splitandmerge.mkvslice.BuildConfig
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

private const val MAX_REDIRECTS = 5
private const val MAX_APK_BYTES = 200L * 1024L * 1024L // 200 MB

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateService: UpdateService,
    private val json: Json,
    private val integrityVerifier: IntegrityVerifier,
    private val sessionInstaller: SessionInstaller,
    private val settingsRepository: SettingsRepository,
    private val installedAppMetaProvider: InstalledAppMetaProvider,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    internal var sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }

    private var isReceiverRegistered = false
    private var savedVerified: Verified? = null
    private var pendingVersionCode: Int = 0

    internal var allowedHosts = setOf(
        "raw.githubusercontent.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )

    internal var updateCheckUrl = BuildConfig.UPDATE_CHECK_URL

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
                    _state.value = UpdateState.Installed
                    unregisterReceiver()

                    val targetVersion = pendingVersionCode
                    if (targetVersion > 0) {
                        CoroutineScope(ioDispatcher).launch {
                            settingsRepository.setLastOfferedVersionCode(targetVersion)
                        }
                    }

                    // S10: Delete the APK only after session terminal status
                    purgeStaleFiles()
                    savedVerified = null
                }
                else -> {
                    _state.value = UpdateState.Error("Installation failed (code $status)")
                    unregisterReceiver()

                    // S10: Delete the APK only after session terminal status
                    purgeStaleFiles()
                    savedVerified = null
                }
            }
        }
    }

    private fun registerReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter("com.splitandmerge.mkvslice.ACTION_INSTALL_STATUS")
        ContextCompat.registerReceiver(
            context,
            installReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
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

    private fun purgeStaleFiles() {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (updatesDir.exists()) {
                updatesDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun performGetRequest(initialUrl: String): HttpsURLConnection {
        var urlStr = initialUrl
        var redirectCount = 0

        while (redirectCount < MAX_REDIRECTS) {
            val uri = URI(urlStr)
            val scheme = uri.scheme ?: throw SecurityException("Missing scheme")
            if (!scheme.equals("https", ignoreCase = true)) {
                throw SecurityException("HTTPS required")
            }
            val host = uri.host ?: throw SecurityException("Missing host")
            if (host !in allowedHosts) {
                throw SecurityException("Untrusted host")
            }

            val url = uri.toURL()
            val connection = url.openConnection() as? HttpsURLConnection
                ?: throw SecurityException("Not an HttpsURLConnection")

            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("User-Agent", "MKVSlice-Updater")

            val responseCode = connection.responseCode
            if (responseCode == HttpsURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpsURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpsURLConnection.HTTP_SEE_OTHER ||
                responseCode == 307 || responseCode == 308) {

                val location = connection.getHeaderField("Location")
                    ?: throw IOException("Redirect missing Location header")
                connection.disconnect()
                urlStr = location
                redirectCount++
            } else if (responseCode == HttpsURLConnection.HTTP_OK) {
                return connection
            } else {
                connection.disconnect()
                throw IOException("HTTP response code: $responseCode")
            }
        }
        throw IOException("Too many redirects")
    }

    suspend fun checkForUpdate() = withContext(ioDispatcher) {
        // S10: At the start of every check, purge any stale *.tmp/APK files left in the cache dir
        purgeStaleFiles()
        savedVerified = null
        pendingVersionCode = 0

        // S5: Hard-disable the update feature on a debug build
        val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebug) {
            _state.value = UpdateState.Error("Updates disabled on debug builds")
            return@withContext
        }

        _state.value = UpdateState.Checking

        var connection: HttpsURLConnection? = null
        try {
            val manifestUrl = updateCheckUrl + "videosplitter-version.json"
            connection = performGetRequest(manifestUrl)

            val manifestText = connection.inputStream.bufferedReader().use { it.readText() }
            val manifest = json.decodeFromString<UpdateManifest>(manifestText)

            // S4: Validate manifest size limits before downloading
            if (manifest.sizeBytes <= 0 || manifest.sizeBytes > MAX_APK_BYTES) {
                _state.value = UpdateState.Error("Invalid update size")
                return@withContext
            }

            // S5: Validate sha256 hex format
            val sha256Regex = Regex("^[0-9a-fA-F]{64}$")
            if (!sha256Regex.matches(manifest.sha256)) {
                _state.value = UpdateState.Error("Invalid update signature hash")
                return@withContext
            }

            val currentVersionCode = installedAppMetaProvider.getVersionCode()

            val isNewer = integrityVerifier.versionCompare(currentVersionCode, manifest.versionCode)
            if (!isNewer) {
                _state.value = UpdateState.UpToDate
                return@withContext
            }

            // S2: Monotonic Floor Protection
            val settings = settingsRepository.settingsFlow.first()
            val monotonicFloor = settings.lastOfferedVersionCode
            if (manifest.versionCode <= monotonicFloor) {
                _state.value = UpdateState.UpToDate
                return@withContext
            }

            pendingVersionCode = manifest.versionCode
            _state.value = UpdateState.Available(
                versionCode = manifest.versionCode,
                versionName = manifest.versionName,
                changelog = manifest.changelog,
                url = manifest.apkUrl,
                sha256 = manifest.sha256,
                size = manifest.sizeBytes
            )
        } catch (e: Exception) {
            _state.value = UpdateState.Error("Update check failed")
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun downloadUpdate(available: UpdateState.Available) = withContext(ioDispatcher) {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }

        val tmpFile = File(updatesDir, "app-update.${System.currentTimeMillis()}.tmp")
        _state.value = UpdateState.Downloading(0f)

        var connection: HttpsURLConnection? = null
        try {
            connection = performGetRequest(available.url)
            val expectedSize = available.size

            val inputStream = connection.inputStream
            tmpFile.outputStream().use { outputStream ->
                val buffer = ByteArray(256 * 1024)
                var bytesRead = 0
                var totalBytesWritten = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesWritten += bytesRead

                    // S4: Abort the instant bytes-written exceed manifest size
                    if (totalBytesWritten > expectedSize) {
                        throw IOException("Downloaded size overrun")
                    }

                    val progress = if (expectedSize > 0) {
                        totalBytesWritten.toFloat() / expectedSize.toFloat()
                    } else 0f
                    _state.value = UpdateState.Downloading(progress)
                }

                // S4 size validation
                if (totalBytesWritten != expectedSize) {
                    throw IOException("Download incomplete")
                }
            }

            _state.value = UpdateState.Verifying

            val manifest = UpdateManifest(
                versionName = available.versionName,
                versionCode = available.versionCode,
                apkUrl = available.url,
                sha256 = available.sha256,
                sizeBytes = available.size,
                changelog = available.changelog
            )

            val apkFile = File(updatesDir, "app-update.apk")

            // S5, S9 & S10: Pure verification producing a sealed Verified token and final file rename inside verifier
            val verified = integrityVerifier.verifyAndFinalize(tmpFile, apkFile, manifest)
            if (verified == null) {
                tmpFile.delete()
                if (apkFile.exists()) {
                    apkFile.delete()
                }
                _state.value = UpdateState.Error("Verification failed")
                return@withContext
            }

            savedVerified = verified

            // S7: Check install permissions
            if (!sessionInstaller.canRequestPackageInstalls()) {
                _state.value = UpdateState.NeedsInstallPermission
            } else {
                _state.value = UpdateState.ReadyToInstall
            }
        } catch (e: Exception) {
            tmpFile.delete()
            _state.value = UpdateState.Error("Download failed")
        } finally {
            connection?.disconnect()
        }
    }

    fun checkInstallPermissionAfterGrant() {
        val verified = savedVerified
        if (verified != null && _state.value is UpdateState.NeedsInstallPermission) {
            if (sessionInstaller.canRequestPackageInstalls()) {
                _state.value = UpdateState.ReadyToInstall
            }
        }
    }

    fun launchInstallSettings() {
        if (_state.value is UpdateState.NeedsInstallPermission) {
            sessionInstaller.launchInstallSettings()
        }
    }

    suspend fun installUpdate() = withContext(ioDispatcher) {
        val verified = savedVerified
        if (verified == null) {
            _state.value = UpdateState.Error("No verified update found")
            return@withContext
        }

        if (!sessionInstaller.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsInstallPermission
            return@withContext
        }

        _state.value = UpdateState.Installing
        registerReceiver()

        val result = sessionInstaller.install(verified)
        if (result.isFailure) {
            unregisterReceiver()
            purgeStaleFiles()
            savedVerified = null
            _state.value = UpdateState.Error("Installation failed")
        }
    }

    fun cancel() {
        savedVerified = null
        pendingVersionCode = 0
        purgeStaleFiles()
        unregisterReceiver()
        _state.value = UpdateState.Idle
    }
}
