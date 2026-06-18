# Changelog

> Per-release changelog. Format follows [AGENTS.md §9E](../AGENTS.md). The
> agent appends a new section at release time. Newest at the top.

---

## [Unreleased]

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
