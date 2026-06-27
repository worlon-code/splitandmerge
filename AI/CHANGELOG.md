# Changelog

> Per-release changelog. Format follows [AGENTS.md §9E](../AGENTS.md). The
> agent appends a new section at release time. Newest at the top.

---

## [Unreleased]

## [0.0.14] — 2026-06-28

 NEW FEATURES
- **Batch Rename Videos (Title-Clean)**: New dedicated screen (reachable from Settings) to scan folders or pick multiple files and batch-rename video filenames in-place using the existing cleanup-patterns engine. Supports folder-pick, multi-file-pick, inline preview showing before/after names, per-row checkbox, collision detection, and a results summary sheet.
- **Title Cleanup Patterns — backup & restore**: Export all patterns to a JSON file via SAF, restore from any previously exported file. Validates schema on import; malformed files are rejected with an error toast.
- **Cleanup pattern Replace-with field**: Each pattern can now carry an optional replacement string (default: empty = delete match). Existing patterns without a replacement continue to behave as before.
- **Select-all toggle for cleanup patterns**: A new checkbox in the pattern list header toggles all patterns on/off in a single tap; tapping again deselects all.
- **In-app Help screen**: A new "How to Use / Help" screen (Settings → How to Use / Help) presents step-by-step guides with auto-captured device screenshots for each major feature.
- **AMOLED / dark theme mode**: Settings now exposes a Theme Mode selector (System default / Light / Dark / AMOLED). AMOLED mode forces a true-black background, reducing power draw on OLED panels.
- **Keep-screen-on**: The screen is kept on while an active split, merge, or rename operation is in progress and returns to normal policy once the operation completes or is cancelled.
- **Default size-cap pre-fill**: The Split Config screen now reads the user's saved default size cap from Settings and pre-fills the field, eliminating repetitive manual entry.

 BUG FIXES
- **Rename verify-after-rename false failure**: After `DocumentsContract.renameDocument` succeeds, the document URI changes. The follow-up `queryDisplayName` on the old URI was denied and mapped to a failure. Fixed by trusting the non-null new URI returned by `renameDocument` as proof of success; the display-name query is now best-effort only.
- **Android back on Rename Videos screen**: System back was exiting to Settings instead of returning to the source-picker when a file list was already showing. Fixed by adding `BackHandler` in both phone and tablet variants that intercepts back when state is non-Idle and resets to Idle via `cancelToIdle()`.

 TECHNICAL
- `RenameVideosViewModel.cancelToIdle()` fully resets all state: cancels scan and rename jobs, clears file list, auto-suffix map, manually-edited IDs, inline-create state, and selected pattern IDs.
- Screenshot capture infrastructure: instrumented `ScreenshotTest` using `createComposeRule<ComponentActivity>()` (plain host, no Hilt) with explicit mock stubs for all ViewModel state flows to avoid proxy-cast ClassCastExceptions.
- All screenshots captured at device resolution (1080 × 2264) and committed to `docs/screenshots/`; also registered as `drawable-nodpi` resources for the Help screen.
- `docs/USAGE.md` step-by-step usage guide added; `README.md` updated with screenshot table and link to usage guide.

 VERIFICATION
- Samsung M51 (RZ8N90X627R), Android 12, targetSdk 35.
- `testDebugUnitTest --rerun-tasks` GREEN (all tests pass, 0 failures).
- `lintDebug` GREEN (0 errors).
- `assembleRelease` GREEN.


## [0.0.13] — 2026-06-27

🚀 NEW FEATURES
- **Set Default Tracks**: A new surgical flow that allows users to analyze, modify, and apply default/forced/enabled flags for audio and subtitle tracks on multiple MKV files in-place without re-encoding.
- **In-App Update Checker**: Added a secure update mechanism inside Settings that checks for updates via HTTPS, downloads APK files off-thread to a private cache, verifies SHA-256 + certificate signatures, and installs updates using PackageInstaller.

🔧 BUG FIXES
- **Settings screen crash**: Fixed a runtime crash on minified release builds by adding appropriate ProGuard keep rules for Retrofit, Hilt, and kotlinx.serialization update-related classes.
- **Android 13+ BroadcastReceiver SecurityException**: Replaced manual registration with ContextCompat.registerReceiver using ContextCompat.RECEIVER_NOT_EXPORTED for internal ACTION_INSTALL_STATUS broadcasts.

📦 TECHNICAL
- Added `domain/defaulttracks/` engine package including matcher, normalizer, editor, journal, and verifier components.
- Added `data/update/` package including integrity verifier, metadata lookup, and session installer components.
- Room DB migrated v5 → v6: Added `default_track_file_results` table for Default Tracks tracking.
- Pinned `release-assets.githubusercontent.com` and `objects.githubusercontent.com` to the update download allowed hosts list.

## [0.0.12] — 2026-06-25

🚀 NEW FEATURES
- **Transport byte-exact split**: Per-job toggle on the Split Config screen switches from FFmpeg structural split to a pure-Kotlin byte-chop engine that produces parts byte-identical to a slice of the original — no re-encoding, no keyframe dependency. Parts are named `<base>.part_NN_TT.mkv` (1-based, zero-padded).
- **Transport byte-exact merge**: Automatically detected when parts carry the 64-byte `MKVSLICE` binary header magic. Streams parts in order, verifies per-part SHA-256 and total SHA-256 against the embedded metadata, and produces output that is byte-identical to the original source.
- **SHA-256 verification**: Split computes original-file SHA-256 single-pass during the read. Merge verifies it on the assembled output. Pass confirmation: "byte-identical, SHA-256 verified". Fail: output KEPT, user shown the path and specific mismatch reason.
- **MB / GB unit selector**: The byte-exact size-cap field now has a segmented MB / GB toggle (binary units: 1 MB = 1 048 576 bytes, 1 GB = 1 073 741 824 bytes). Default: MB.
- **Decimal size-cap input**: The byte-exact size-cap field accepts decimal values (e.g. `0.3`, `1.5`). Conversion uses `BigDecimal` with `FLOOR` rounding. Inline error messages for all invalid inputs (empty, non-numeric, scientific notation, signs, floor-to-zero, Long overflow). Start/Continue disabled until valid.
- **Byte Merge Mode chip**: `MergeOrderScreen` displays a "Byte Merge Mode" chip when byte parts are detected. FFprobe is skipped; pre-flight validation results are shown instead.

🔧 BUG FIXES
- K-018 (superseded): Round-trip byte identity is now achievable via Transport mode. FFmpeg structural merge non-identity is a known limitation of the FFmpeg path; byte-exact users are unaffected.

📦 TECHNICAL
- New: `domain/transport/FrameCodec.kt` — 64-byte big-endian binary frame header (magic, version, partIndex, totalParts, payloadOffset, payloadSize, originalTotalSize).
- New: `domain/transport/TransportSplitter.kt` — 512 KB streaming SAF read, single-pass whole-file + per-part SHA-256, MKVSLICE frame header prefix, cancel cleanup.
- New: `domain/merger/PartModeDetector.kt` — sniffs first 8 bytes for `MKVSLICE` magic.
- New: `domain/merger/PreFlightEvaluator.kt` — validates session identity, contiguity, duplicates, missing parts, truncation, version.
- New: `domain/merger/TransportMerger.kt` — streaming SAF concat, running SHA-256, fail-closed / verify-keep policy.
- Modified: `Merger.kt` — routes to `TransportMerger` when `PartModeDetector` returns MKVSLICE; structural FFmpeg path unchanged.
- Modified: `MergeOrderViewModel` / `MergeOrderScreen` — `isByteMerge` state, skip ffprobe for byte parts, show pre-flight results.
- Room DB migrated v4 → v5: `jobs.splitFormat TEXT` ("BYTE" | null), `parts.payloadOffset INTEGER`, `parts.partSha256 TEXT`. New `Migration_4_5` + instrumented test.
- `SplitConfigViewModel` / `SplitConfigScreen` / `SplitConfigScreenTablet`: `isByteSplit` toggle, `SizeUnit` enum (MB/GB), `byteSizeCapInput: String`, `parseTargetCapBytes()` via `BigDecimal`.
- Unit test count: 190+ (adds `ByteSplitEngineTest`, `FrameCodecTest`, `PartModeDetectorTest`, `TransportMergerTest`, expanded `SplitConfigViewModelTest` matrix).

⚠️ KNOWN ISSUES
- K-025: Byte-split parts carry an `.mkv` extension but are NOT individually playable. Players will show garbage or errors. User is warned on the Split Config screen.
- K-021: Merged output ~571 KB smaller than sum of parts (FFmpeg structural merge path only; Transport merge is byte-identical).

🔍 VERIFICATION
- Samsung M51 (RZ8N90X627R), Android 14, targetSdk 35.
- :app:assembleDebug GREEN
- :app:lintDebug GREEN
- :app:testReleaseUnitTest GREEN (190+ tests)
- :app:assembleRelease GREEN
- On-device smoke: byte-exact split → merge → SHA-256 verified byte-identical.

## [0.0.11] — 2026-06-24

![🚀](https://fonts.gstatic.com/s/e/notoemoji/17.0/1f680/72.png) NEW FEATURES
- (none this release — internal testability/refactor release)

![🔧](https://fonts.gstatic.com/s/e/notoemoji/17.0/1f527/72.png) BUG FIXES
- K-024: Fixed androidTest constructor compile break introduced when v0.0.10 added SettingsRepository to the Merger constructor. androidTest now compiles (compileDebugAndroidTestKotlin GREEN).

![📦](https://fonts.gstatic.com/s/e/notoemoji/17.0/1f4e6/72.png) TECHNICAL
- K-019: FileSystem-seam refactor — Merger disk I/O is now mockable. New FileSystem interface (8 File-typed methods: cacheDir, exists, canRead, length, openInput, openOutput, createNewFile, delete) + RealFileSystem delegate (java.io.File) + Hilt @Binds. Merger constructor takes FileSystem as the 7th param; in-scope disk I/O routed through it. DocumentFile/SAF/PFD reads unchanged.
- Unit tests migrated off TemporaryFolder-hack + mockkConstructor to a mocked FileSystem (real TemporaryFolder.root backs cacheDir): MergerFastPathTest (4 cases), MergerArgvTest, MergerCollisionTest; RealFileSystemTest added.

![⚠️](https://fonts.gstatic.com/s/e/notoemoji/17.0/26a0_fe0f/72.png) KNOWN ISSUES
- K-018: round-trip GOP drift (v0.0.12 audio-snap) — OPEN.
- K-021: ~571KB merged-vs-sum-of-parts header-dedup size delta — OPEN.
- K-022: switch verification sampling from df to du — OPEN.

![🔍](https://fonts.gstatic.com/s/e/notoemoji/17.0/1f50d/72.png) VERIFICATION
- :app:assembleDebug (GREEN)
- :app:lintDebug (GREEN)
- :app:testDebugUnitTest (GREEN)
- :app:assembleRelease (GREEN)
- compileDebugAndroidTestKotlin (GREEN)

## [0.0.10.1] — 2026-06-18

🔧 HOTFIX
- Release-build crash in SAF file picker. R8 was stripping FFmpegKit native binding classes, causing UnsatisfiedLinkError on JNI_OnLoad when the picker was opened from Split or Merge.

📦 TECHNICAL
- app/proguard-rules.pro: keep com.arthenica.** and com.antonkarpenko.** classes; keep all native-method classes globally. No source-code changes.
- versionCode 10 → 11, versionName 0.0.10 → 0.0.10.1.

🔍 VERIFICATION
- Re-installed release APK on Samsung M51 (RZ8N90X627R).
- Split → Merge round-trip on a 120s test MKV completed without crash; SAF permission prompt presents correctly.

⚠️ AFFECTS
- Supersedes v0.0.10 release build. Users on v0.0.10 should upgrade.
- K-018 envelope clarified: round-trip drift is sub-1 % on real long sources; can be larger as a percentage on short or synthetic sources due to per-cut GOP overhead. Same root cause; planned audio-snap fix in v0.0.12.

## [0.0.10] — 2026-06-18

🚀 NEW FEATURES
- **Disk fast-path option for Merger**: Adds an opt-in fast-path merge optimization (via Settings → toggle "Improve reliability" OFF). When direct file access is possible, Merger skips copying split parts to internal cache and merging to cache, writing the final output directly to the destination folder.
- **Pre-flight readability and writability probes**: Guard the fast-path gate with a 1-byte read test on inputs and a create/delete test on the output directory. On any probe failure (such as SAF-restricted folders), Merger gracefully and transparently degrades to standard, safe staging behavior instead of crashing.

🔧 BUG FIXES
- n/a (this release adds an opt-in feature; staged behavior is unchanged.)

📦 TECHNICAL
- `Merger` constructor now takes `SettingsRepository`. Existing tests updated; new `MergerFastPathTest` covers the four gate branches.

⚠️ KNOWN ISSUES
- K-018: ~4 s round-trip drift on long sources (deferred to v0.0.12).
- K-019: Merger has filesystem side-effects that complicate unit testing; planned FileSystem-seam refactor in v0.0.11.
- K-021: Merged MKV is ~571 KB smaller than sum-of-parts; likely benign header dedup, to be investigated.
- K-022: Device verification used partition-wide `df`; switch to `du` on the app cache dir for future runs.

🔍 VERIFICATION
- Samsung M51 (RZ8N90X627R), Android 14, targetSdk 35.
- Source: Perfect Crown S01E04 (4393 s, 0.96 GB), 3-part split.
- Staged and fast-path runs produced byte-identical media streams (post-4KB SHA-256 match); container header differs by 75 bytes due to ffmpeg's per-mux random SegmentUID.
- Fast-path probes correctly detected scoped-storage block on SAF-picked Download files and fell back to staged path.

## [0.0.9] — 2026-06-16

🚀 NEW FEATURES
- **Adaptive launcher icon + splash screen**: Foreground/background layer icons with AMOLED-safe background; splash screen held until first-run check resolves, preventing library flash on cold start.
- **Loading primitives**: New reusable Compose components — `LoadingArc` (indeterminate spinner), `PulseDot` (animated status dot), `ShimmerSkeleton` (shimmer placeholder rows).
- **Library shimmer + PulseDot**: Library screen shows 3 shimmer card placeholders on cold start (before Room emits first value); RUNNING jobs show a `PulseDot` alongside their status chip.
- **Merge "Verifying parts…" arc** (K-017): `MergeOrderViewModel.addParts()` now sets `verifying = true` while ffprobe runs on selected parts; `MergeOrderScreen` shows `LoadingArc` + "Verifying parts…" text, with action buttons disabled until probing completes.
- **File Details loading/error state**: `FileDetailsScreen` replaces the static "Loading file metadata…" text with `LoadingArc`; probe failures are surfaced as an error card with a retry button instead of silently swallowing the exception.
- **OSS Notices screen**: Real open-source licence data served from the packaged `oss_notices.json` asset; shimmer shown while loading; each entry taps through to its licence URL.
- **Cleanup Patterns (Room)**: 12 default regex cleanup rules persisted to Room (DB v4); user can view, enable/disable, reorder, add, and delete patterns in `CleanupPatternsScreen`. Rules applied in-order to split/merge output filenames.

🔧 BUG FIXES
- K-017: Merge "Pick parts" flow no longer sits idle for 5-30 s when probing large MKV files; `LoadingArc` + disabled buttons give clear feedback during the wait.
- K-006 (mitigated): The default cleanup pattern set no longer includes the TRUE / REAL tokens that could strip words from legitimate titles ("True Lies", "Real Steel"). K-006 remains OPEN because user-added custom patterns can still hit the same edge case; the built-in defaults no longer trigger it.
- Fixed silent exception swallow in `FileDetailsViewModel`: probe errors now propagate to a visible error card in `FileDetailsScreen` instead of leaving the screen stuck on a loading state.
- Fixed `CleanupPattern` regex Pattern #7 (`markers`): removed `TRUE` and `REAL` tokens that would have stripped words from legitimate titles such as "True Lies" and "Real Steel".
- Fixed `CleanupPattern` regex Pattern #8 (`release_group`): changed from `[-.\s][A-Za-z0-9]{3,}$` (which ate trailing title words like "Endgame") to `-([A-Z][A-Za-z0-9]{2,15})$`, matching only dash-prefixed, capitalised release group tags.

📦 TECHNICAL
- Room schema migrated from v3 to v4 (`Migration_3_4`): adds `cleanup_patterns` table; seeded with 12 default patterns on first install.
- `MigrationTestHelper` test (`Migration_3_4Test`) validates schema upgrade from exported v3 JSON.
- `CleanupPatternsViewModel` and `CleanupPatternsViewModelTest` added (enable/disable, reorder, CRUD operations).
- `LibraryViewModel` gains `isInitialLoad: Boolean` flag (flipped false on first DAO emission); unit tests cover loading to loaded transition.
- `FileDetailsViewModel` refactored to sealed `UiState` (Loading / Success / Error); `FileDetailsViewModelTest` covers all three states.
- `MergeOrderState` gains `verifying: Boolean`; `try/finally` guarantees the flag resets on exception paths.
- ShimmerSkeleton implemented as `Modifier.shimmer()` extension using `rememberInfiniteTransition` + `animateFloat`; gradient sweeps left-to-right across the rendered surface (200px band over 1400px range).

---

## [0.0.8] — 2026-06-15

🚀 NEW FEATURES
- **First-run setup**: New install now prompts for a default save folder
  on launch (non-dismissible until set). Folder selection is unified —
  Settings, Split, and Merge all read from / write to the same source.
- **Diagnostic logs**: All app activity is now captured to local
  rotating daily log files in app cache (`logs/app-<date>.log`). 7-day
  auto-purge. SAF URIs are redacted (8-char hash) for privacy. Logs
  are accessible via Settings → "View diagnostic logs" with read,
  share (via FileProvider), and safe clear-all (active file truncated,
  not deleted — writer survives).
- **In-app update check**: Settings → "Check for updates" fetches a
  signed manifest over HTTPS, validates SHA-256, and installs via
  `PackageInstaller` session. HTTPS-only, debug builds disabled,
  malformed manifests rejected.
- **Battery optimization toggle**: Settings exposes a real
  `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` toggle with state
  reflected via `PowerManager`.
- **Split "Analyzing" UI**: The 20-30s keyframe scan at split start
  no longer appears as stuck-at-0%. Shows an indeterminate spinner
  with "Analyzing video structure" until the first real progress.

🔧 BUG FIXES
- **Merge folder collision** (5d): When the user's chosen output
  folder already contains a sub-folder with the merge's base name,
  SAF correctly auto-creates `<name> (1)`, but the app was writing
  to `<name>/file.mkv` instead of `<name> (1)/<name> (1).mkv`. Fixed
  in Merger and Splitter; MergeResultScreen no longer crashes on
  collision.
- K-016: Fix SplitResultScreen showing fake "Bahubali" outputs on
  every split. Now reads real part filenames and sizes from the Room
  database via new SplitResultViewModel. Also removed dead Phase 3
  Jobs scaffolding (JobsScreen/JobsScreenTablet/JobsViewModel +
  Routes.JOBS) that was imported in AppNav but never routed.

📦 TECHNICAL
- Migrated SettingsViewModel from SharedPreferences to androidx
  Preferences DataStore via new `SettingsRepository`. Legacy values
  auto-migrate on first launch.
- New `OutputFolderValidator` rejects unreachable / unwritable / low-space
  / permission-revoked folders with a unified `FolderValidationDialog`
  shown across all three pickers (11 unit tests cover every branch).
- Added: androidx.datastore-preferences 1.1.1, retrofit 2.11.0,
  okhttp 4.12.0, okhttp-tls 4.12.0 (test only).
- Round-trip lossless floor confirmed at 13450.895s for the 3:44:07
  source (matches v0.0.7 baseline).
- All v0.0.7 functionality unchanged.

---

## [0.0.7] — 2026-06-14

🚀 NEW FEATURES
- Tap on cancelled or failed rows in S2 Library now opens a `JobDetailSheet` bottom sheet showing execution details.
- Added `JobDetailSheet` action buttons: "Retry" (spawns a new queued job, replicating parts for merge) and "Delete row".

🔧 BUG FIXES
- Fixed the native crash (SIGSEGV in libffmpegkit.so saf_close) by staging files to cache and running concat demuxer on standard filesystem paths (Fixes K-013).
- Resolved video duration drift and GOP overlap in split-then-merge round-trips by positioning `-ss` before `-i` in the Splitter for fast seek and using identical cut timestamps verbatim (Fixes K-014).

⚡ IMPROVEMENTS
- Skipped staging and temporary copying in the Merger when input and output files are on readable/writable physical paths.
- Added sequential cache cleanup of staged parts with a 5-second safety margin, freeing disk space during concat progression.
- Dynamically hides the "Step X of Y" step count prefix in progress phase labels when total steps <= 2.
- Added a 600dp max width cap for `JobDetailSheet` on tablets to prevent stretching.

📦 TECHNICAL
- Version bump to 0.0.7, versionCode 7.
- Added comprehensive instrumented tests for sequential cleanup (`MergerSequentialCleanupTest`), direct path merge (`MergerNoStagingTest`), and round-trip duration verification (`SplitMergeRoundTripTest`).

---

## [0.0.5] — 2026-06-12

🚀 NEW FEATURES
- Real-time engine progress reporting including ETA and MB/s metrics during splitting.
- Implemented Phase 6: Merge engine using FFmpeg `concat` demuxer and connected it to the MergeConfig UI.
- Integrated automatic file subfolder grouping logic based on title prefixes.

🔧 BUG FIXES
- Corrected the `totalParts` miscalculation bug when using Size Cap only mode.
- Fixed merge failures on Android work profiles / dual apps by using `saf:` protocol descriptors directly in the FFmpeg concat demuxer.

⚡ IMPROVEMENTS
- Added powerful regex-based filename Title Cleaner to automatically normalize messy release names (e.g. stripping codec, resolution, and release group markers).

📦 TECHNICAL
- Version bump to 0.0.5, versionCode 5.
- Deployed `deploy_release.ps1` fallback to avoid failing when GitHub CLI (`gh`) is not installed.
- Database Schema migrated from version 1 to 2 to support real-time metrics storage on JobEntity.

## [0.0.4] — 2026-06-12

🚀 NEW FEATURES
- Integrated com.antonkarpenko:ffmpeg-kit-min:2.1.0 (LGPL 3.0 only) for native FFmpeg command execution.
- Implemented core engine wrappers for metadata extraction (FFprobe) and video manipulation (FFmpeg).
- Created a pure Kotlin `CutPlanner` to calculate split points based on keyframes and target caps.

📦 TECHNICAL
- Version bump to 0.0.4, versionCode 4.
- Added dependency injection bindings for Engine components.
- Added comprehensive unit and instrumented smoke tests for the Engine implementations.

---

## [0.0.3] — 2026-06-12

🚀 NEW FEATURES
- Release build for version 0.0.3 (APK ready).

🔧 BUG FIXES
- (none)

⚡ IMPROVEMENTS
- (none)

📦 TECHNICAL
- Version bump to 0.0.3, versionCode 3.
- Updated changelog.

---

## [0.0.2] — 2026-06-12

🚀 NEW FEATURES
- (none yet — Phase 1 scaffold pending)

🔧 BUG FIXES
- (none)

⚡ IMPROVEMENTS
- (none)

📦 TECHNICAL
- Initial documentation pack:
  - `AGENTS.md` — operating manual for every coding agent.
  - `TESTING-AGENT.md` + `run-tests.ps1` + CI workflow.
  - `MASTER-PROMPT.md` for Codex / Antigravity.
  - `AI/` skeleton (16 documents + `plans/`).
  - `analysis/` deep dive (00–13).
  - `STITCH-BRIEF.md` + `STITCH-PROMPT-ROUND2.md` design briefs.
  - `Design/` folder placeholder for Stitch outputs.

🗑️ REMOVED
- (none)

---

## How to add a new release

When a release ships, replace `[Unreleased]` with `[X.Y.Z] — YYYY-MM-DD`,
then add a fresh `[Unreleased]` block below the new heading. Always:

1. Use **emoji section headers** exactly as above.
2. Only include sections that have items for that release.
3. Never write "See commits for details".
4. Keep entries to one clear sentence each.
5. Reference K-NNN from `KNOWN_ISSUES.md` when fixing a known issue.

This format produces clean `\n`-separated text for the `changelog` field
of `videosplitter-version.json`.

## Versioning

- `0.0.1` … `0.0.99` — patch series.
- After `0.0.99`, ASK USER before bumping to `1.1.0` (per [AGENTS.md §3A](../AGENTS.md)).
- Debug builds NEVER bump version.
