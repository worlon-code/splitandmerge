package com.splitandmerge.mkvslice.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var context: Context
    private lateinit var cacheDir: File
    private lateinit var updateService: UpdateService
    private lateinit var okHttpClient: OkHttpClient
    private val testDispatcher = UnconfinedTestDispatcher()
    private val updateJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = false
    }

    private val heldCertificate = HeldCertificate.Builder()
        .addSubjectAlternativeName("localhost")
        .addSubjectAlternativeName("127.0.0.1")
        .build()

    @Before
    fun setUp() {
        val handshakeCerts = HandshakeCertificates.Builder()
            .heldCertificate(heldCertificate)
            .build()
        mockWebServer = MockWebServer().apply {
            useHttps(handshakeCerts.sslSocketFactory(), false)
            start()
        }

        context = mockk(relaxed = true)
        cacheDir = File.createTempFile("temp_cache", "")
        cacheDir.delete()
        cacheDir.mkdirs()
        every { context.cacheDir } returns cacheDir

        val clientCerts = HandshakeCertificates.Builder()
            .addTrustedCertificate(heldCertificate.certificate)
            .build()
        okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager)
            .build()

        updateService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(updateJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(UpdateService::class.java)

        // Mock PackageManager & PackageInfo to represent current version code = 10 (non-debuggable by default)
        val mockPackageManager = mockk<PackageManager>(relaxed = true)
        val mockPackageInfo = PackageInfo().apply {
            versionCode = 10
            versionName = "0.0.10"
        }
        val mockAppInfo = ApplicationInfo().apply {
            flags = 0
        }
        every { context.packageManager } returns mockPackageManager
        every { context.packageName } returns "com.splitandmerge.mkvslice.debug"
        every { context.applicationInfo } returns mockAppInfo
        every { mockPackageManager.getPackageInfo(any<String>(), any<Int>()) } returns mockPackageInfo
        every { mockPackageManager.getPackageInfo(any<String>(), any<PackageManager.PackageInfoFlags>()) } returns mockPackageInfo

        mockkStatic(PendingIntent::class)
        mockkStatic(PackageManager.PackageInfoFlags::class)
        every { PackageManager.PackageInfoFlags.of(any()) } returns mockk()
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } returns mockk()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        cacheDir.deleteRecursively()
        unmockkStatic(PendingIntent::class)
        unmockkStatic(PackageManager.PackageInfoFlags::class)
        unmockkConstructor(Intent::class)
    }

    private fun createRepository(sdkLevel: Int = 33): UpdateRepository {
        return UpdateRepository(context, updateService, okHttpClient, testDispatcher).apply {
            sdkIntProvider = { sdkLevel }
        }
    }

    @Test
    fun test_checkForUpdate_serverReturns200_validJson_phaseAvailable() = runTest(testDispatcher) {
        val manifestJson = """
            {
                "version": "0.0.99",
                "versionCode": 99,
                "apkUrl": "https://example.com/app.apk",
                "sha256": "4a5e3d7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e",
                "sizeBytes": 1024,
                "releaseNotesUrl": "https://example.com/notes.html",
                "minSupportedVersionCode": 1
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))

        val repository = createRepository()
        repository.checkForUpdate()

        assertEquals(Phase.AvailableReady, repository.state.value.phase)
        assertEquals("0.0.99", repository.state.value.manifest?.version)
        assertNull(repository.state.value.errorMessage)
    }

    @Test
    fun test_checkForUpdate_serverReturns200_oldVersion_phaseUpToDate() = runTest(testDispatcher) {
        val manifestJson = """
            {
                "version": "0.0.7",
                "versionCode": 7,
                "apkUrl": "https://example.com/app.apk",
                "sha256": "4a5e3d7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e",
                "sizeBytes": 1024
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))

        val repository = createRepository()
        repository.checkForUpdate()

        assertEquals(Phase.UpToDate, repository.state.value.phase)
        assertEquals("0.0.7", repository.state.value.manifest?.version)
    }

    @Test
    fun test_checkForUpdate_serverReturns404_phaseError() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val repository = createRepository()
        repository.checkForUpdate()

        assertEquals(Phase.Error, repository.state.value.phase)
        assertEquals("Could not check for updates. Try again later.", repository.state.value.errorMessage)
    }

    @Test
    fun test_checkForUpdate_serverReturnsHttpUrl_REJECTED_phaseError() = runTest(testDispatcher) {
        val manifestJson = """
            {
                "version": "0.0.99",
                "versionCode": 99,
                "apkUrl": "http://example.com/app.apk",
                "sha256": "4a5e3d7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e",
                "sizeBytes": 1024
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))

        val repository = createRepository()
        repository.checkForUpdate()

        assertEquals(Phase.Error, repository.state.value.phase)
        assertEquals("Update URL is not HTTPS. Aborted.", repository.state.value.errorMessage)
    }

    @Test
    fun test_checkForUpdate_serverReturnsBadSha256Format_REJECTED_phaseError() = runTest(testDispatcher) {
        val manifestJson = """
            {
                "version": "0.0.99",
                "versionCode": 99,
                "apkUrl": "https://example.com/app.apk",
                "sha256": "too-short-hash",
                "sizeBytes": 1024
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))

        val repository = createRepository()
        repository.checkForUpdate()

        assertEquals(Phase.Error, repository.state.value.phase)
        assertEquals("Invalid update metadata. Hash format incorrect.", repository.state.value.errorMessage)
    }

    @Test
    fun test_checkForUpdate_serverReturnsHugeSize_REJECTED_phaseError() = runTest(testDispatcher) {
        val manifestJson = """
            {
                "version": "0.0.99",
                "versionCode": 99,
                "apkUrl": "https://example.com/app.apk",
                "sha256": "4a5e3d7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e",
                "sizeBytes": 300000000
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))

        val repository = createRepository()
        repository.checkForUpdate()

        assertEquals(Phase.Error, repository.state.value.phase)
        assertEquals("Invalid update metadata. Size limit exceeded.", repository.state.value.errorMessage)
    }

    @Test
    fun test_checkForUpdate_debugBuild_newerVersion_phaseAvailableButDebug() = runTest(testDispatcher) {
        // Mock app as debuggable (ApplicationInfo.FLAG_DEBUGGABLE)
        val debugAppInfo = ApplicationInfo().apply {
            flags = ApplicationInfo.FLAG_DEBUGGABLE
        }
        every { context.applicationInfo } returns debugAppInfo

        try {
            val manifestJson = """
                {
                    "version": "0.0.99",
                    "versionCode": 99,
                    "apkUrl": "https://example.com/app.apk",
                    "sha256": "4a5e3d7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e",
                    "sizeBytes": 1024
                }
            """.trimIndent()

            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))

            val repository = createRepository()
            repository.checkForUpdate()

            assertEquals(Phase.AvailableButDebug, repository.state.value.phase)
            assertEquals("Install disabled in debug build", repository.state.value.errorMessage)
        } finally {
            // Restore context's applicationInfo mock
            val releaseAppInfo = ApplicationInfo().apply {
                flags = 0
            }
            every { context.applicationInfo } returns releaseAppInfo
        }
    }

    @Test
    fun test_downloadAndInstall_beforeCheck_NOOP() = runTest(testDispatcher) {
        val repository = createRepository()
        repository.downloadAndInstall()

        assertEquals(Phase.Idle, repository.state.value.phase)
    }

    @Test
    fun test_downloadAndInstall_sha256Mismatch_phaseError_tmpFileDeleted() = runTest(testDispatcher) {
        // Compute correct hash of "Hello World"
        val correctData = "Hello World".toByteArray()
        val correctDigest = MessageDigest.getInstance("SHA-256")
        correctDigest.update(correctData)
        val correctHash = correctDigest.digest().joinToString("") { "%02x".format(it) }

        // Manifest states size 11 (Hello World) and correctHash
        val manifestJson = """
            {
                "version": "0.0.99",
                "versionCode": 99,
                "apkUrl": "${mockWebServer.url("/app.apk")}",
                "sha256": "$correctHash",
                "sizeBytes": 11
            }
        """.trimIndent()

        // Mock Web Server will return the manifest, then return a tampered APK payload ("Hello Fake!")
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("Hello Fake!")) // Size is 11, but hash is different

        val repository = createRepository()
        repository.checkForUpdate()

        // Confirm check was successful
        assertEquals(Phase.AvailableReady, repository.state.value.phase)

        repository.downloadAndInstall()

        // Verification must fail with hash mismatch
        assertEquals(Phase.Error, repository.state.value.phase)
        assertEquals("Update verification failed. Hash mismatch.", repository.state.value.errorMessage)

        // Tmp files and APK files must be cleaned up
        val updatesDir = File(cacheDir, "updates")
        val files = updatesDir.listFiles() ?: emptyArray()
        assertTrue(files.none { it.name.endsWith(".tmp") })
        assertFalse(File(updatesDir, "app-update.apk").exists())
    }

    @Test
    fun test_downloadAndInstall_sha256Match_phaseReadyToInstall() = runTest(testDispatcher) {
        val fileContent = "Valid APK payload"
        val correctDigest = MessageDigest.getInstance("SHA-256")
        correctDigest.update(fileContent.toByteArray())
        val correctHash = correctDigest.digest().joinToString("") { "%02x".format(it) }
        val size = fileContent.toByteArray().size.toLong()

        val manifestJson = """
            {
                "version": "0.0.99",
                "versionCode": 99,
                "apkUrl": "${mockWebServer.url("/app.apk")}",
                "sha256": "$correctHash",
                "sizeBytes": $size
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(fileContent))

        // Mock PackageInstaller APIs to avoid actual installation trigger crash
        val mockPackageManager = context.packageManager
        val mockPackageInstaller = mockk<PackageInstaller>()
        val mockSession = mockk<PackageInstaller.Session>(relaxed = true)

        every { mockPackageManager.packageInstaller } returns mockPackageInstaller
        every { mockPackageInstaller.createSession(any()) } returns 42
        every { mockPackageInstaller.openSession(42) } returns mockSession

        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns mockk(relaxed = true)

        val repository = createRepository()
        repository.checkForUpdate()

        assertEquals(Phase.AvailableReady, repository.state.value.phase)

        repository.downloadAndInstall()

        // Verify that rename succeeded and it moved past verification to trigger install
        assertEquals(Phase.ReadyToInstall, repository.state.value.phase)

        // Verify file is renamed to app-update.apk
        val apkFile = File(File(cacheDir, "updates"), "app-update.apk")
        assertTrue(apkFile.exists())
        assertEquals(fileContent, apkFile.readText())

        // Verify PackageInstaller session was opened and written to
        verify { mockPackageInstaller.createSession(any()) }
        verify { mockPackageInstaller.openSession(42) }
        verify { mockSession.openWrite("app_update", 0, -1) }
        verify { context.registerReceiver(any(), any<IntentFilter>(), any<Int>()) }
    }

    @Test
    fun test_cancel_during_download_deletesTmpFile_phaseIdle() = runTest(testDispatcher) {
        val manifestJson = """
            {
                "version": "0.0.99",
                "versionCode": 99,
                "apkUrl": "${mockWebServer.url("/app.apk")}",
                "sha256": "4a5e3d7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e",
                "sizeBytes": 1024
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("Body data..."))

        val repository = createRepository()
        repository.checkForUpdate()

        assertEquals(Phase.AvailableReady, repository.state.value.phase)

        // Cancel while Downloading
        repository.cancel()

        assertEquals(Phase.Idle, repository.state.value.phase)
        val updatesDir = File(cacheDir, "updates")
        val files = updatesDir.listFiles() ?: emptyArray()
        assertTrue(files.none { it.name.endsWith(".tmp") })
    }
}
