# 07 — Libraries & Dependencies

The single biggest *external* risk in this project is the FFmpeg integration story.

## 7.1 The FFmpegKit problem

Arthenica's **FFmpegKit** was the de facto Android FFmpeg wrapper for years (drop-in AAR, JNI bindings, callback APIs, multiple LTS branches). In **April 2025** the maintainer announced retirement and archived the repository. The published binaries on Maven Central remain accessible historically, but:

- No new releases.
- No CVE patches in upstream FFmpeg get propagated.
- 16 KB page size compatibility for Android 15+ devices may not be guaranteed in the older artifacts.

**Therefore we must NOT take a hard dependency on `com.arthenica:ffmpeg-kit-*` for a project we expect to maintain through 2026+.**

## 7.2 Options for the FFmpeg engine

Compared from highest to lowest risk:

### Option A — Use FFmpegKit (archived) as-is

| | |
|---|---|
| Effort | Lowest. Add Gradle dependency, call `FFmpegKit.execute(cmd)`. |
| Risk | High. Unmaintained. Pin to last release (`6.0-2`). |
| Licence | LGPL (default builds), GPL with `-gpl` suffix. |
| When OK | Only if you accept "this is a personal-use app, won't be on the Play Store long-term". |

### Option B — Maintained community fork (recommended for v1)

Several active forks exist as of mid-2026 (e.g. `ffmpeg-kit` continuations under the `tanersener-archive` watchers, plus newer projects like `bravobit/Android-FFmpeg`, `WritingMinds/ffmpeg-android-java`, and a handful of independent re-bundles). We will pick **one** based on:

- Latest publish date.
- 16 KB page size support.
- arm64-v8a + armeabi-v7a + x86_64 ABIs.
- Includes `ffprobe` binary, not just `ffmpeg`.
- LGPL build available.

Action item before code generation: confirm one specific fork. (See [11-OPEN-QUESTIONS.md](11-OPEN-QUESTIONS.md).)

### Option C — Vendor pre-built FFmpeg `.so` + custom JNI

We download / build static FFmpeg `.so` files for arm64-v8a (and armeabi-v7a if needed) and write a thin Kotlin/JNI wrapper that exec's `ffmpeg` as a child process via `Runtime.exec()` (or `ProcessBuilder`).

| | |
|---|---|
| Effort | Medium-high. ~2–4 days for someone who knows NDK. |
| Risk | Medium. We control the version. |
| Licence | LGPL build keeps app non-GPL. |
| Pro | Future-proof; we patch CVEs ourselves. |
| Con | We become responsible for builds across NDK versions. |

### Option D — Build FFmpeg from source via gradle plugin

Like Option C but reproducible via a Gradle task that pulls FFmpeg, applies a config, and builds. More effort up front, less rot over time.

### Decision (proposed)

**For v1: Option B (maintained fork).**
**For v1.1+: migrate to Option C/D once we have stress-test data on real devices.**

## 7.3 Other Android libraries

| Purpose | Library | Version (as of mid-2026) | Why |
|---|---|---|---|
| UI | `androidx.compose.bom` | latest stable | Material 3 + adaptive layouts |
| Material 3 | `androidx.compose.material3:material3` | bundled with BOM | M3 components, dynamic colour |
| Navigation | `androidx.navigation:navigation-compose` | latest | Type-safe nav graphs |
| ViewModel | `androidx.lifecycle:lifecycle-viewmodel-compose` | latest | StateFlow integration |
| Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | latest | Async ffmpeg wrappers |
| DI | `com.google.dagger:hilt-android` | latest | Lightweight, Google-supported |
| Persistence | `androidx.room:room-ktx` | latest | Job queue + parts |
| Serialization | `org.jetbrains.kotlinx:kotlinx-serialization-json` | latest | Manifest .json |
| File access | `androidx.documentfile:documentfile` | latest | SAF tree URIs |
| Logging | `com.jakewharton.timber:timber` | latest | Simple, switchable in release |
| Testing | `kotlin.test`, `mockk`, `turbine` | latest | Coroutine + flow testing |
| Update check | (none — direct Retrofit + GitHub API per your other apps) | — | Same pattern as KissKh / HTML Viewer |

## 7.4 Native binaries — APK size budget

| ABI | FFmpeg LGPL build size (approx) |
|---|---|
| `arm64-v8a` | ~22 MB |
| `armeabi-v7a` | ~18 MB |
| `x86_64` | ~24 MB |
| `x86` | ~22 MB |

To stay near a 30–40 MB APK we ship **`arm64-v8a` only by default**, optionally publishing an `arm-v7a` flavour for older devices via Play Store split APKs / build flavours.

Add to `app/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }
}
```

## 7.5 Licensing matrix

| Component | Licence | Notes |
|---|---|---|
| FFmpeg LGPL build | LGPL 2.1+ | Allowed in proprietary apps if dynamically linked. Most "kit" projects bundle as `.so`, dynamically loaded — compliant. |
| FFmpeg GPL build | GPL 2+ | Forces app to be GPL. **Do not ship.** |
| x264 | GPL | Excluded from LGPL build. We don't need encoders anyway (stream copy only). |
| libfdk_aac | non-free | Excluded. AAC isn't re-encoded by us. |
| libass | ISC | Subtitle rendering library; we don't render, only copy. Not needed. |
| Kotlin / AndroidX | Apache 2.0 | Compatible. |
| Hilt / Dagger | Apache 2.0 | Compatible. |
| Room | Apache 2.0 | Compatible. |

**Conclusion:** the app stays under a permissive licence as long as we link a **dynamically-loaded LGPL FFmpeg** and never enable GPL flags. We must include the LGPL notice + a "Used libraries" / "Open source notices" screen in Settings. Standard practice.

## 7.6 What we do NOT depend on

- **WorkManager** for the engine. Long-running video work belongs in a foreground service.
- **ExoPlayer / Media3** for the engine. Their MediaCodec-backed pipeline is for playback, not lossless container manipulation.
- **JCodec** / **MP4Parser** — Java-only MP4 libraries. Don't support MKV well, miss many codecs.
- Cloud SDKs.

## 7.7 Build tooling

- Gradle 8.x with KTS files.
- Kotlin 2.x.
- AGP (Android Gradle Plugin) 8.x stable.
- KSP for Hilt + Room (avoid kapt).
- JDK 17 (toolchain).

## 7.8 Smoke-test before integration

Before integrating any chosen FFmpeg artifact, we verify on a real device:

```kotlin
val out = engine.run(arrayOf("-version"))
val mkvOut = engine.run(arrayOf("-i", testMkv, "-map", "0", "-c", "copy",
                                "-t", "30", outFile))
```

and confirm we see a valid 30-second MKV with all expected streams. If that passes, integration is straightforward.
