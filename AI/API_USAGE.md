# API Usage

> Every external network call this app makes. The list is short by design.

## 1. Privacy posture

- **No telemetry, no analytics, no crash reporters** (Q20 = none).
- **All file IO stays on-device.** No upload of user content.
- **One outbound HTTPS endpoint** for the in-app update check.
- **No third-party SDKs** that phone home.

If a future feature requires a new external endpoint, the agent must:

1. Add a row to §2 below explaining purpose, payload, frequency.
2. Add a permission entry to [PERMISSIONS.md](PERMISSIONS.md) if needed.
3. Update `network-policy-test` so the test asserts the new host.
4. Get user approval (Section 7 of [AGENTS.md](../AGENTS.md)) before
   shipping.

## 2. Endpoints

| # | Direction | Host (placeholder) | Path | Method | Purpose | Frequency |
|---|---|---|---|---|---|---|
| E1 | Outbound | `raw.githubusercontent.com` | `/<owner>/<release-repo>/main/videosplitter-version.json` | GET | Update check (latest version, URL, SHA-256, changelog) | On user tap of "Check for updates" + (later) once-per-day if user enabled auto |
| E2 | Outbound | `github.com` (redirect) → `objects.githubusercontent.com` | `/.../v<x.y.z>/app-release.apk` | GET | Download the released APK to local cache | Once per accepted update prompt |

That is the **complete** list for v1.

The exact owner / repo names are configured by the user post-scaffold and
recorded in `app/src/main/res/values/strings.xml`:

```xml
<string name="update_repo_owner" translatable="false">splitandmerge</string>
<string name="update_repo_name"  translatable="false">mkvslice-releases</string>
```

(The repo URL must NOT be hardcoded in code — read from resources for
easy override per build flavour.)

## 3. JSON contract — `videosplitter-version.json`

Located in the releases repo at the repo root.

```json
{
  "latest": "0.0.1",
  "versionCode": 1,
  "url": "https://github.com/splitandmerge/mkvslice-releases/raw/main/v0.0.1/app-release.apk",
  "size": 38765432,
  "sha256": "ab12cd34ef56...",
  "minSdk": 26,
  "publishedAt": "2026-06-12T10:00:00Z",
  "changelog": "## [0.0.1] — 2026-06-12\n🚀 NEW FEATURES\n- Lossless split…\n"
}
```

Parsed via `kotlinx.serialization`:

```kotlin
@Serializable
data class VersionInfo(
    val latest: String,
    val versionCode: Int,
    val url: String,
    val size: Long,
    val sha256: String,
    val minSdk: Int,
    val publishedAt: String,
    val changelog: String,
)
```

## 4. HTTP layer

We use **Retrofit + OkHttp + kotlinx.serialization** (added in Phase 7 of
the roadmap). Module: `update/`.

```kotlin
interface UpdateApi {
    @GET("videosplitter-version.json")
    suspend fun fetchVersion(): VersionInfo
}

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl("https://raw.githubusercontent.com/splitandmerge/mkvslice-releases/main/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton fun provideApi(r: Retrofit): UpdateApi = r.create(UpdateApi::class.java)
}
```

OkHttp logging is `BASIC` in debug, `NONE` in release. Never `BODY` — even
for an internal endpoint we don't want to leak a future signed token to
logs.

## 5. APK download flow

```kotlin
class ApkDownloader @Inject constructor(
    private val client: OkHttpClient,
    private val cacheDir: File,
) {
    suspend fun download(url: String, expectedSha256: String): File {
        val target = File(cacheDir, "update.apk")
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            require(resp.isSuccessful) { "HTTP ${resp.code}" }
            target.outputStream().use { resp.body!!.byteStream().copyTo(it) }
        }
        val actual = sha256(target)
        require(actual.equals(expectedSha256, ignoreCase = true)) {
            target.delete()
            "SHA-256 mismatch (got $actual, expected $expectedSha256)"
        }
        return target
    }
}
```

The downloaded file lives in app-private cache. We **always** verify
SHA-256 against the JSON before invoking `PackageInstaller` — no exceptions.

## 6. Errors

| Error | Cause | UX |
|---|---|---|
| `UnknownHostException` | No internet / DNS down | "No internet" snackbar; offer retry. |
| `SocketTimeoutException` | Slow connection | Same. |
| `HTTP 404` on JSON | Releases repo not configured | "Update channel unavailable" sheet; suggest sideload. |
| `HTTP 4xx/5xx` on APK | Bad URL in JSON | Same; deny install. |
| `SHA-256 mismatch` | Tampered APK / bad upload | Refuse install; show error with copy-button for the SHA. |

All errors logged via Timber with redacted URL (host only).

## 7. Network policy test

`app/src/androidTest/.../NetworkPolicyTest.kt`:

```kotlin
@Test fun onlyAllowedHostsAreContacted() {
    val allowed = setOf(
        "raw.githubusercontent.com",
        "github.com",
        "objects.githubusercontent.com",
    )
    OkHttpDebug.recordedHosts.forEach { host ->
        assertThat(allowed).contains(host)
    }
}
```

Run during the smoke pass; fails if a contributor accidentally adds a new
host. Mirror in CI.

## 8. Domain contract

`update/UpdateService.kt`:

```kotlin
interface UpdateService {
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateState
    suspend fun downloadIfAvailable(state: UpdateState.Available): File
    suspend fun install(apk: File): InstallResult
}

sealed class UpdateState {
    data object UpToDate : UpdateState()
    data class Available(val info: VersionInfo) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

sealed class InstallResult {
    data object Started : InstallResult()      // PackageInstaller intent fired
    data class Failed(val reason: String) : InstallResult()
}
```

`SettingsViewModel` calls `UpdateService.checkForUpdate(BuildConfig.VERSION_CODE)`
when the user taps Check for updates.

## 9. Frequency rules

- v1: manual check only. No background poll.
- v1.x (TBD): one optional background check per day on Wi-Fi via WorkManager
  expedited unique work. Disabled by default (Settings → Updates).

## 10. Versioning of the JSON

The JSON itself doesn't carry a schema version. To extend it without
breakage:

- New fields are optional (have defaults at the parser).
- Removing a field is a breaking change — bump app's minimum supported
  schema in `UpdateService` and gate gracefully.

## 11. Why not Play Store?

Q9 = GitHub Releases only. Reasons recorded in
[`analysis/12-ROADMAP.md`](../analysis/12-ROADMAP.md). Switching to Play
Store later means: remove the in-app update flow, remove
`REQUEST_INSTALL_PACKAGES`, ship via Play Console. The rest of the app is
unaffected.

## 12. What an agent must NOT do here

- ❌ Add a new host without the §1 approval flow.
- ❌ Use plain `HttpURLConnection` — Retrofit + OkHttp only, for one shared
  client.
- ❌ Log full URLs (use `redactHost(url)` helper).
- ❌ Skip the SHA-256 check before `PackageInstaller`.
- ❌ Send any user identifier — there isn't one. The app has no accounts.
- ❌ Use cleartext HTTP. `usesCleartextTraffic="false"` in manifest.
