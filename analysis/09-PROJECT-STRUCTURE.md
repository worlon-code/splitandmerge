# 09 — Project Structure

Proposed Android Studio project layout. Single-module to start; we extract `:engine` and `:domain` modules in v1.1 if test feedback demands it.

## 9.1 Top-level layout

```
KotlinAPK/                              <-- repo root (under AI-LE/Kotlin APK)
├── .gitignore
├── README.md
├── AGENTS.md                          <-- agent rules (your style)
├── AI/                                <-- documentation pack (mirrors your KissKh app)
│   ├── README.md
│   ├── ARCHITECTURE.md
│   ├── SCREENS.md
│   ├── SCREEN_FLOWS.md
│   ├── HLS_NOTUSED.md                 <-- placeholder; we delete
│   ├── KOTLIN_APP.md
│   ├── DATA_MODELS.md
│   ├── STATE_MANAGEMENT.md
│   ├── COMPONENTS.md
│   ├── PERMISSIONS.md
│   ├── KNOWN_ISSUES.md
│   ├── WORK_SUMMARY.md
│   ├── tasks.md
│   └── CHANGELOG.md
├── analysis/                          <-- this very pack (00..13)
├── design/                            <-- Stitch outputs (light/dark variants)
│   ├── home_light/
│   ├── home_dark/
│   ├── split_config_light/
│   ├── split_config_dark/
│   └── ...
├── gradle/
│   └── libs.versions.toml             <-- centralised version catalogue
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── deploy_release.ps1                 <-- per your KissKh release script style
├── update-codebase-graph.ps1          <-- per your KissKh AI/ doc updater style
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── kotlin/com/example/mkvslice/
        │   │   ├── MainActivity.kt
        │   │   ├── App.kt                          <-- @HiltAndroidApp
        │   │   ├── theme/
        │   │   │   ├── Color.kt
        │   │   │   ├── Theme.kt
        │   │   │   └── Type.kt
        │   │   ├── ui/
        │   │   │   ├── library/LibraryScreen.kt
        │   │   │   ├── library/LibraryViewModel.kt
        │   │   │   ├── filedetails/FileDetailsScreen.kt
        │   │   │   ├── filedetails/FileDetailsViewModel.kt
        │   │   │   ├── splitconfig/SplitConfigScreen.kt
        │   │   │   ├── splitconfig/SplitConfigViewModel.kt
        │   │   │   ├── progress/JobProgressScreen.kt
        │   │   │   ├── progress/JobProgressViewModel.kt
        │   │   │   ├── result/ResultScreen.kt
        │   │   │   ├── mergeorder/MergeOrderScreen.kt
        │   │   │   ├── mergeorder/MergeOrderViewModel.kt
        │   │   │   ├── jobs/JobsScreen.kt
        │   │   │   ├── jobs/JobsViewModel.kt
        │   │   │   ├── settings/SettingsScreen.kt
        │   │   │   └── settings/SettingsViewModel.kt
        │   │   ├── nav/
        │   │   │   ├── AppNav.kt
        │   │   │   └── Routes.kt
        │   │   ├── domain/
        │   │   │   ├── model/Job.kt
        │   │   │   ├── model/Part.kt
        │   │   │   ├── model/SplitMode.kt
        │   │   │   ├── model/Manifest.kt
        │   │   │   ├── splitter/CutPlanner.kt
        │   │   │   ├── splitter/Splitter.kt
        │   │   │   ├── merger/Merger.kt
        │   │   │   └── probe/Probe.kt
        │   │   ├── engine/
        │   │   │   ├── FfmpegEngine.kt
        │   │   │   ├── FfprobeEngine.kt
        │   │   │   ├── KeyframeFinder.kt
        │   │   │   ├── ProgressParser.kt
        │   │   │   └── ProcessRunner.kt
        │   │   ├── data/
        │   │   │   ├── db/AppDatabase.kt
        │   │   │   ├── db/JobDao.kt
        │   │   │   ├── db/PartDao.kt
        │   │   │   ├── db/JobEntity.kt
        │   │   │   ├── db/PartEntity.kt
        │   │   │   ├── repo/JobRepository.kt
        │   │   │   └── settings/SettingsStore.kt   <-- DataStore
        │   │   ├── platform/
        │   │   │   ├── saf/SafFile.kt
        │   │   │   ├── saf/PathResolver.kt
        │   │   │   └── saf/UriExt.kt
        │   │   ├── service/
        │   │   │   ├── JobService.kt
        │   │   │   ├── JobNotificationFactory.kt
        │   │   │   └── JobScheduler.kt
        │   │   ├── update/
        │   │   │   ├── UpdateService.kt
        │   │   │   └── ApkInstaller.kt
        │   │   └── di/
        │   │       ├── AppModule.kt
        │   │       └── EngineModule.kt
        │   ├── res/
        │   │   ├── drawable/
        │   │   ├── mipmap-anydpi-v26/   <-- adaptive icon
        │   │   ├── values/strings.xml
        │   │   ├── values/themes.xml
        │   │   ├── values-night/themes.xml
        │   │   └── xml/file_paths.xml   <-- FileProvider config
        │   └── jniLibs/
        │       └── arm64-v8a/
        │           ├── libffmpeg.so
        │           └── libffprobe.so   <-- if our chosen kit splits them
        ├── test/                        <-- unit tests (JVM)
        │   └── kotlin/com/example/mkvslice/
        │       ├── domain/CutPlannerTest.kt
        │       ├── domain/SplitterTest.kt
        │       ├── domain/MergerValidationTest.kt
        │       ├── engine/ProgressParserTest.kt
        │       └── platform/SafFileTest.kt
        └── androidTest/                 <-- instrumented tests
            └── kotlin/com/example/mkvslice/
                ├── EngineSmokeTest.kt    <-- runs on emulator with a small fixture
                └── EndToEndTest.kt
```

## 9.2 Module rationale

- **`app` only for v1.** A single module avoids module-graph overhead while we iterate.
- We push **clean boundaries by package**: `domain` knows nothing about Android, `engine` knows nothing about Compose, `ui` knows nothing about JNI.
- This makes a future split into `:domain`, `:engine`, `:data`, `:app` mechanical (move package → module).

## 9.3 Files we will write a Stitch-design for

(`design/` folder — populated by you from Stitch.) Folder names we'll consume from the agent:

```
design/
  home_light/         home_dark/
  filedetails_light/  filedetails_dark/
  splitconfig_light/  splitconfig_dark/
  progress_light/     progress_dark/
  result_light/       result_dark/
  mergeorder_light/   mergeorder_dark/
  jobs_light/         jobs_dark/
  settings_light/     settings_dark/
  onboarding_light/   onboarding_dark/
  ossnotices_light/   ossnotices_dark/
```

## 9.4 Build flavour & variants

Single product flavour for v1. Two build types:

- `debug`: applicationIdSuffix `.debug`, debuggable, with ProGuard off.
- `release`: minify on, R8 enabled, signing config from Gradle signing-config (env vars).

`abiFilters = ["arm64-v8a"]` for both.

## 9.5 Versioning rule (agent-enforced)

Per your other apps' rules:

- Format: `MAJOR.MINOR.PATCH` (string `versionName`) + integer `versionCode` that monotonically increases.
- Start at `0.0.1` / `versionCode = 1`. After `0.0.99`, ask before bumping to `1.1.0`.
- Debug builds **never** bump version.

## 9.6 Release distribution

Per your KissKh pattern: a separate releases repository under your GitHub org, with versioned APK + a `mkvslice-version.json` for in-app update checks.

```
mkvslice-version.json
{
  "latest": "0.0.1",
  "url": "https://github.com/.../mkvslice-releases/raw/main/v0.0.1/app-release.apk",
  "size": 38765432,
  "sha256": "...",
  "changelog": "..."
}
```

The in-app `Settings → Check for updates` flow does an HTTPS GET, compares versions, downloads, and installs via `PackageInstaller`.
