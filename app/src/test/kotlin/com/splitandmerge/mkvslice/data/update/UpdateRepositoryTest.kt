package com.splitandmerge.mkvslice.data.update

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var context: Context
    private lateinit var cacheDir: File
    private val testDispatcher = UnconfinedTestDispatcher()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = false
    }

    private val heldCertificate = HeldCertificate.Builder()
        .addSubjectAlternativeName("localhost")
        .addSubjectAlternativeName("127.0.0.1")
        .build()

    private var defaultSslSocketFactory: javax.net.ssl.SSLSocketFactory? = null
    private var defaultHostnameVerifier: javax.net.ssl.HostnameVerifier? = null

    private lateinit var fakeInstalledMeta: FakeInstalledAppMetaProvider
    private lateinit var fakeApkMeta: FakeApkMetaProvider
    private lateinit var fakeInstaller: FakeSessionInstaller
    private lateinit var fakeSettingsRepo: FakeSettingsRepository
    private lateinit var integrityVerifier: IntegrityVerifier

    private class FakeInstalledAppMetaProvider : InstalledAppMetaProvider {
        @get:JvmName("getFakePackageName")
        var packageName = "com.splitandmerge.mkvslice"
        @get:JvmName("getFakeVersionCode")
        var versionCode = 10
        @get:JvmName("getFakeSignerDigests")
        var signerDigests = setOf("HASH1")
        @get:JvmName("getFakeHasMultipleSigners")
        var hasMultipleSigners = false

        override fun getPackageName() = packageName
        override fun getVersionCode() = versionCode
        override fun getSignerDigests() = signerDigests
        override fun hasMultipleSigners() = hasMultipleSigners
    }

    private class FakeApkMetaProvider : ApkMetaProvider {
        @get:JvmName("getFakeApkPackageName")
        var packageName: String? = "com.splitandmerge.mkvslice"
        @get:JvmName("getFakeApkVersionCode")
        var versionCode: Int? = 12
        @get:JvmName("getFakeApkSignerDigests")
        var signerDigests = setOf("HASH1")
        @get:JvmName("getFakeApkHasMultipleSigners")
        var hasMultipleSigners = false

        override fun getPackageName(apkPath: String) = packageName
        override fun getVersionCode(apkPath: String) = versionCode
        override fun getSignerDigests(apkPath: String) = signerDigests
        override fun hasMultipleSigners(apkPath: String) = hasMultipleSigners
    }

    private class FakeSessionInstaller : SessionInstaller {
        var launchCount = 0
        var canRequest = true
        var settingsLaunched = false
        var shouldFail = false

        override fun canRequestPackageInstalls() = canRequest
        override fun launchInstallSettings() {
            settingsLaunched = true
        }

        override suspend fun install(verified: Verified): Result<Unit> {
            if (shouldFail) return Result.failure(IOException("Session install error"))
            if (verified.file.length() != verified.expectedLength) {
                return Result.failure(IOException("TOCTOU protection triggered"))
            }
            launchCount++
            return Result.success(Unit)
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val _state = MutableStateFlow(com.splitandmerge.mkvslice.data.settings.SettingsState())
        override val settingsFlow = _state.asStateFlow()

        override suspend fun setThemeMode(mode: com.splitandmerge.mkvslice.data.settings.ThemeMode) {}
        override suspend fun setDefaultCapGb(capGb: Double) {}
        override suspend fun setImproveReliability(improve: Boolean) {}
        override suspend fun setKeepScreenOn(keep: Boolean) {}
        override suspend fun setDefaultOutputFolderUri(uri: String) {}
        override suspend fun setLastOfferedVersionCode(versionCode: Int) {
            _state.value = _state.value.copy(lastOfferedVersionCode = versionCode)
        }
    }

    @Before
    fun setUp() {
        val handshakeCerts = HandshakeCertificates.Builder()
            .heldCertificate(heldCertificate)
            .build()
        mockWebServer = MockWebServer().apply {
            useHttps(handshakeCerts.sslSocketFactory(), false)
            start()
        }

        // Configure default SSL parameters for HttpsURLConnection in JVM tests
        val clientCerts = HandshakeCertificates.Builder()
            .addTrustedCertificate(heldCertificate.certificate)
            .build()
        defaultSslSocketFactory = javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory()
        defaultHostnameVerifier = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(clientCerts.sslSocketFactory())
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier { hostname, _ ->
            hostname == "localhost" || hostname == "127.0.0.1"
        }
        io.mockk.mockkStatic(androidx.core.content.ContextCompat::class)
        every { androidx.core.content.ContextCompat.registerReceiver(any(), any(), any(), any()) } returns mockk()

        context = mockk(relaxed = true)
        cacheDir = File.createTempFile("temp_cache", "")
        cacheDir.delete()
        cacheDir.mkdirs()
        every { context.cacheDir } returns cacheDir

        val appInfo = ApplicationInfo().apply {
            flags = 0 // release type
        }
        every { context.applicationInfo } returns appInfo
        every { context.packageName } returns "com.splitandmerge.mkvslice"

        val pm = mockk<PackageManager>()
        every { context.packageManager } returns pm
        val pi = PackageInfo().apply {
            versionCode = 10
            versionName = "0.0.10"
        }
        every { pm.getPackageInfo(any<String>(), any<Int>()) } returns pi
        every { pm.getPackageInfo(any<String>(), any<PackageManager.PackageInfoFlags>()) } returns pi

        fakeInstalledMeta = FakeInstalledAppMetaProvider()
        fakeApkMeta = FakeApkMetaProvider()
        fakeInstaller = FakeSessionInstaller()
        fakeSettingsRepo = FakeSettingsRepository()
        integrityVerifier = IntegrityVerifier(fakeInstalledMeta, fakeApkMeta)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        cacheDir.deleteRecursively()

        // Restore SSL defaults
        defaultSslSocketFactory?.let { javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(it) }
        defaultHostnameVerifier?.let { javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(it) }
        io.mockk.unmockkStatic(androidx.core.content.ContextCompat::class)
    }

    private fun createRepository(sdkLevel: Int = 33): UpdateRepository {
        val mockService = mockk<UpdateService>()
        val repo = UpdateRepository(
            context = context,
            updateService = mockService,
            json = json,
            integrityVerifier = integrityVerifier,
            sessionInstaller = fakeInstaller,
            settingsRepository = fakeSettingsRepo,
            installedAppMetaProvider = fakeInstalledMeta,
            ioDispatcher = testDispatcher
        )
        repo.sdkIntProvider = { sdkLevel }
        repo.allowedHosts = setOf("localhost", "127.0.0.1")
        repo.updateCheckUrl = mockWebServer.url("/").toString()
        return repo
    }

    private fun sha256(content: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(content.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    // T1: versionCompare
    @Test
    fun test_T1_versionCompare() = runTest(testDispatcher) {
        val repo = createRepository()

        // 1. newer version -> Available
        val bodyNewer = """
            {
                "versionName": "0.0.11",
                "versionCode": 11,
                "apkUrl": "${mockWebServer.url("/app.apk")}",
                "sha256": "4a5e3d7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e",
                "sizeBytes": 1024
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(bodyNewer))
        repo.checkForUpdate()
        assertTrue(repo.state.value is UpdateState.Available)

        // 2. equal version -> UpToDate
        val bodyEqual = """
            {
                "versionName": "0.0.10",
                "versionCode": 10,
                "apkUrl": "${mockWebServer.url("/app.apk")}",
                "sha256": "4a5e3d7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e",
                "sizeBytes": 1024
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(bodyEqual))
        repo.checkForUpdate()
        assertTrue(repo.state.value is UpdateState.UpToDate)

        // 3. older version -> UpToDate
        val bodyOlder = """
            {
                "versionName": "0.0.9",
                "versionCode": 9,
                "apkUrl": "${mockWebServer.url("/app.apk")}",
                "sha256": "4a5e3d7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e",
                "sizeBytes": 1024
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(bodyOlder))
        repo.checkForUpdate()
        assertTrue(repo.state.value is UpdateState.UpToDate)

        // 4. Monotonic floor validation
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(bodyNewer))
        fakeSettingsRepo.setLastOfferedVersionCode(11) // floor is 11, manifest is 11
        repo.checkForUpdate()
        assertTrue(repo.state.value is UpdateState.UpToDate)
    }

    // T2: manifestParse
    @Test
    fun test_T2_manifestParse() = runTest(testDispatcher) {
        val repo = createRepository()

        // Valid parsing
        val bodyValid = """
            {
                "versionName": "0.0.12",
                "versionCode": 12,
                "apkUrl": "https://localhost/app.apk",
                "sha256": "4a5e3d7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e",
                "sizeBytes": 2048,
                "changelog": ["Line 1", "Line 2"]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(bodyValid))
        repo.checkForUpdate()
        val state = repo.state.value
        assertTrue(state is UpdateState.Available)
        val available = state as UpdateState.Available
        assertEquals("0.0.12", available.versionName)
        assertEquals(12, available.versionCode)
        assertEquals(2048L, available.size)
        assertEquals(listOf("Line 1", "Line 2"), available.changelog)

        // Malformed json -> Error
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{ malformed json }"))
        repo.checkForUpdate()
        assertTrue(repo.state.value is UpdateState.Error)
        assertEquals(0, fakeInstaller.launchCount)
    }

    // T3: shaVerify
    @Test
    fun test_T3_shaVerify() = runTest(testDispatcher) {
        val repo = createRepository()

        val content = "APK file content payload"
        val expectedSha = sha256(content)
        val manifestJson = """
            {
                "versionName": "0.0.12",
                "versionCode": 12,
                "apkUrl": "${mockWebServer.url("/app.apk")}",
                "sha256": "$expectedSha",
                "sizeBytes": ${content.length}
            }
        """.trimIndent()

        // 1. lowercase sha matches -> ReadyToInstall
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(content))
        repo.checkForUpdate()
        val available = repo.state.value as UpdateState.Available
        repo.downloadUpdate(available)
        assertTrue(repo.state.value is UpdateState.ReadyToInstall)

        // 2. uppercase sha matches -> ReadyToInstall
        val uppercaseSha = expectedSha.uppercase()
        val manifestJsonUpper = """
            {
                "versionName": "0.0.12",
                "versionCode": 12,
                "apkUrl": "${mockWebServer.url("/app.apk")}",
                "sha256": "$uppercaseSha",
                "sizeBytes": ${content.length}
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJsonUpper))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(content))
        repo.checkForUpdate()
        val available2 = repo.state.value as UpdateState.Available
        repo.downloadUpdate(available2)
        assertTrue(repo.state.value is UpdateState.ReadyToInstall)

        // 3. Mismatch (one byte off) -> Error & delete
        val wrongSha = expectedSha.dropLast(1) + "0"
        val manifestJsonWrong = """
            {
                "versionName": "0.0.12",
                "versionCode": 12,
                "apkUrl": "${mockWebServer.url("/app.apk")}",
                "sha256": "$wrongSha",
                "sizeBytes": ${content.length}
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJsonWrong))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(content))
        repo.checkForUpdate()
        val available3 = repo.state.value as UpdateState.Available
        repo.downloadUpdate(available3)
        assertTrue(repo.state.value is UpdateState.Error)
        assertEquals(0, fakeInstaller.launchCount)

        // Verify tmp file is deleted
        val updatesDir = File(cacheDir, "updates")
        val files = updatesDir.listFiles() ?: emptyArray()
        assertTrue(files.none { it.name.endsWith(".tmp") })
    }

    // T4: sizeMismatch / size <=0 / size > cap / stream overrun
    @Test
    fun test_T4_sizeMismatchAndCaps() = runTest(testDispatcher) {
        val repo = createRepository()

        // Size <= 0 -> rejected at check
        val manifestJsonZero = """
            {
                "versionName": "0.0.12",
                "versionCode": 12,
                "apkUrl": "https://localhost/app.apk",
                "sha256": "4a5e3d7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e",
                "sizeBytes": 0
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJsonZero))
        repo.checkForUpdate()
        assertTrue(repo.state.value is UpdateState.Error)
        assertEquals(0, fakeInstaller.launchCount)

        // Size > cap (200MB) -> rejected at check
        val manifestJsonHuge = """
            {
                "versionName": "0.0.12",
                "versionCode": 12,
                "apkUrl": "https://localhost/app.apk",
                "sha256": "4a5e3d7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e",
                "sizeBytes": 300000000
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJsonHuge))
        repo.checkForUpdate()
        assertTrue(repo.state.value is UpdateState.Error)
        assertEquals(0, fakeInstaller.launchCount)

        // Stream overrun during download -> abort & Error
        val content = "Small"
        val expectedSha = sha256(content)
        val manifestOverrun = """
            {
                "versionName": "0.0.12",
                "versionCode": 12,
                "apkUrl": "${mockWebServer.url("/app.apk")}",
                "sha256": "$expectedSha",
                "sizeBytes": 3
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestOverrun))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(content))
        repo.checkForUpdate()
        val available = repo.state.value as UpdateState.Available
        repo.downloadUpdate(available)
        assertTrue(repo.state.value is UpdateState.Error)
        assertEquals(0, fakeInstaller.launchCount)
    }

    // T5: signerMismatch
    @Test
    fun test_T5_signerMismatch() = runTest(testDispatcher) {
        val repo = createRepository()
        val content = "APK payload"
        val expectedSha = sha256(content)
        val manifestJson = """
            {
                "versionName": "0.0.12",
                "versionCode": 12,
                "apkUrl": "${mockWebServer.url("/app.apk")}",
                "sha256": "$expectedSha",
                "sizeBytes": ${content.length}
            }
        """.trimIndent()

        // 1. Signers mismatch -> Error
        fakeApkMeta.signerDigests = setOf("WRONG_HASH")
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(content))
        repo.checkForUpdate()
        val available = repo.state.value as UpdateState.Available
        repo.downloadUpdate(available)
        assertTrue(repo.state.value is UpdateState.Error)
        assertEquals(0, fakeInstaller.launchCount)

        // 2. Empty APK signers -> Error (fail-closed)
        fakeApkMeta.signerDigests = emptySet()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(content))
        repo.checkForUpdate()
        val available2 = repo.state.value as UpdateState.Available
        repo.downloadUpdate(available2)
        assertTrue(repo.state.value is UpdateState.Error)
        assertEquals(0, fakeInstaller.launchCount)
    }

    // T6: httpsOnly
    @Test
    fun test_T6_httpsOnly() = runTest(testDispatcher) {
        val repo = createRepository()

        repo.updateCheckUrl = "http://localhost/"
        repo.checkForUpdate()
        assertTrue(repo.state.value is UpdateState.Error)

        repo.updateCheckUrl = mockWebServer.url("/").toString()
        val manifestHttpApk = """
            {
                "versionName": "0.0.12",
                "versionCode": 12,
                "apkUrl": "http://localhost/app.apk",
                "sha256": "4a5e3d7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e",
                "sizeBytes": 1024
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestHttpApk))
        repo.checkForUpdate()
        val state = repo.state.value
        assertTrue(state is UpdateState.Available)
        val available = state as UpdateState.Available
        repo.downloadUpdate(available)
        assertTrue(repo.state.value is UpdateState.Error)
        assertEquals(0, fakeInstaller.launchCount)
    }

    // T7: errorPaths
    @Test
    fun test_T7_errorPaths() = runTest(testDispatcher) {
        val repo = createRepository()

        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        repo.checkForUpdate()
        assertTrue(repo.state.value is UpdateState.Error)

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(""))
        repo.checkForUpdate()
        assertTrue(repo.state.value is UpdateState.Error)
        assertEquals(0, fakeInstaller.launchCount)
    }

    // T8: TOCTOU
    @Test
    fun test_T8_toctou() = runTest(testDispatcher) {
        val repo = createRepository()
        val content = "APK payload data"
        val expectedSha = sha256(content)
        val manifestJson = """
            {
                "versionName": "0.0.12",
                "versionCode": 12,
                "apkUrl": "${mockWebServer.url("/app.apk")}",
                "sha256": "$expectedSha",
                "sizeBytes": ${content.length}
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(content))
        repo.checkForUpdate()
        val available = repo.state.value as UpdateState.Available
        repo.downloadUpdate(available)
        assertTrue(repo.state.value is UpdateState.ReadyToInstall)

        val apkFile = File(File(context.cacheDir, "updates"), "app-update.apk")
        apkFile.appendText("tampered bytes")

        repo.installUpdate()
        assertTrue(repo.state.value is UpdateState.Error)
        assertEquals(0, fakeInstaller.launchCount)
    }

    // T9: needsPermission
    @Test
    fun test_T9_needsPermission() = runTest(testDispatcher) {
        val repo = createRepository()
        val content = "APK payload data"
        val expectedSha = sha256(content)
        val manifestJson = """
            {
                "versionName": "0.0.12",
                "versionCode": 12,
                "apkUrl": "${mockWebServer.url("/app.apk")}",
                "sha256": "$expectedSha",
                "sizeBytes": ${content.length}
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(content))
        repo.checkForUpdate()

        fakeInstaller.canRequest = false
        val available = repo.state.value as UpdateState.Available
        repo.downloadUpdate(available)

        assertEquals(UpdateState.NeedsInstallPermission, repo.state.value)
        assertEquals(0, fakeInstaller.launchCount)

        fakeInstaller.canRequest = true
        repo.checkInstallPermissionAfterGrant()
        assertEquals(UpdateState.ReadyToInstall, repo.state.value)
    }

    // T10: signerMatches pure test
    @Test
    fun test_T10_signerMatches() {
        val verifier = integrityVerifier

        assertTrue(verifier.signerMatches(setOf("ABC"), setOf("ABC")))
        assertFalse(verifier.signerMatches(setOf("ABC"), setOf("DEF")))
        assertFalse(verifier.signerMatches(emptySet(), setOf("ABC")))
        assertFalse(verifier.signerMatches(setOf("ABC"), emptySet()))
    }

    // Positive Control: all-pass path
    @Test
    fun test_PositiveControl_allPass() = runTest(testDispatcher) {
        val repo = createRepository()
        val content = "APK payload data"
        val expectedSha = sha256(content)
        val manifestJson = """
            {
                "versionName": "0.0.12",
                "versionCode": 12,
                "apkUrl": "${mockWebServer.url("/app.apk")}",
                "sha256": "$expectedSha",
                "sizeBytes": ${content.length}
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(content))
        repo.checkForUpdate()
        val available = repo.state.value as UpdateState.Available
        repo.downloadUpdate(available)
        assertTrue(repo.state.value is UpdateState.ReadyToInstall)

        repo.installUpdate()
        assertEquals(1, fakeInstaller.launchCount)
    }

    @Test
    fun test_defaultAllowedHosts_andUrl() {
        val mockService = mockk<UpdateService>()
        val repo = UpdateRepository(
            context = context,
            updateService = mockService,
            json = json,
            integrityVerifier = integrityVerifier,
            sessionInstaller = fakeInstaller,
            settingsRepository = fakeSettingsRepo,
            installedAppMetaProvider = fakeInstalledMeta,
            ioDispatcher = testDispatcher
        )
        val expectedHosts = setOf(
            "raw.githubusercontent.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com"
        )
        assertEquals(expectedHosts, repo.allowedHosts)
        assertEquals(com.splitandmerge.mkvslice.BuildConfig.UPDATE_CHECK_URL, repo.updateCheckUrl)
    }

    @Test
    fun test_monotonicFloor_raisedOnSuccess() = runTest(testDispatcher) {
        val repo = createRepository()
        val content = "APK payload data"
        val expectedSha = sha256(content)
        val manifestJson = """
            {
                "versionName": "0.0.12",
                "versionCode": 12,
                "apkUrl": "${mockWebServer.url("/app.apk")}",
                "sha256": "$expectedSha",
                "sizeBytes": ${content.length}
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(content))
        repo.checkForUpdate()
        val available = repo.state.value as UpdateState.Available
        repo.downloadUpdate(available)
        
        // Before success, floor is 0
        assertEquals(0, fakeSettingsRepo.settingsFlow.value.lastOfferedVersionCode)

        repo.installUpdate()

        // Manually trigger the broadcast receiver to simulate system success status
        val intent = mockk<android.content.Intent>()
        every { intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, any()) } returns android.content.pm.PackageInstaller.STATUS_SUCCESS
        val receiver = repo.javaClass.getDeclaredField("installReceiver").let {
            it.isAccessible = true
            it.get(repo) as android.content.BroadcastReceiver
        }
        receiver.onReceive(context, intent)

        // After success, floor must be raised to 12
        assertEquals(12, fakeSettingsRepo.settingsFlow.value.lastOfferedVersionCode)
        assertEquals(UpdateState.Installed, repo.state.value)
    }
}
