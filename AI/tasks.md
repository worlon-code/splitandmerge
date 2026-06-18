# tasks.md

> Open task list. Append-only during a phase; the agent moves rows between
> sections as work progresses. One row = one unit of work the agent (or a
> human) can pick up.

Status: `TODO`, `IN-PROGRESS`, `BLOCKED`, `DONE-v<x.y.z>`.

---

## Phase 1 — Scaffold

| ID | Status | Title | Owner |
|---|---|---|---|
| T-001 | DONE-v0.0.1 | User provides target repo path. | user |
| T-002 | DONE-v0.0.1 | Run `git init`, copy specs, create `AI/`, `logs/`, `.gitignore`. | agent |
| T-003 | DONE-v0.0.1 | Create root `README.md` (user-facing). | agent |
| T-004 | DONE-v0.0.1 | Hand-write `app/` Android module per `analysis/09-PROJECT-STRUCTURE.md`. | agent |
| T-005 | DONE-v0.0.1 | Confirm `./gradlew --version` works. | agent |
| T-006 | DONE-v0.0.1 | First commit: `chore: initial scaffold (Phase 1 + 2)`. | agent |
| T-007 | DONE-v0.0.1 | Stop and report. Wait for user approval to proceed. | agent |

## Phase 2 — Stitch import

| ID | Status | Title | Owner |
|---|---|---|---|
| T-010 | DONE-v0.0.1 | Audit the S10 light exports and choose a canonical merge-order light variant. | agent |
| T-011 | DONE-v0.0.1 | Audit the imported Stitch folders in `Design/` and identify the canonical normalized set. | agent |
| T-012 | DONE-v0.0.1 | Publish the final folder map in `AI/DESIGN_INDEX.md` per the approved exception. | agent |
| T-013 | TODO | User confirms the mapping. | user |

## Phase 3 — Compose UI + nav with mock data

| ID | Status | Title | Owner |
|---|---|---|---|
| T-020 | TODO | Theme tokens in `theme/Color.kt` (light, dark, AMOLED). | agent |
| T-021 | TODO | `BottomNavBar` + `NavRail` components. | agent |
| T-022 | TODO | All 17 screen Composables with mock state classes. | agent |
| T-023 | TODO | NavHost wired per `SCREEN_FLOWS.md`. | agent |
| T-024 | TODO | Hilt scaffold + DI modules. | agent |
| T-025 | TODO | Compose UI tests for every screen (mocked state). | agent |
| T-026 | TODO | Debug build installs and navigates through every screen. | agent |

## Phase 4 — Engine: probe + keyframes

| ID | Status | Title | Owner |
|---|---|---|---|
| T-055 | TODO | Storage check + auto-resplit fallback. | agent |
| T-056 | TODO | End-to-end test: split a 1 GB fixture; subs intact. | agent |

## Phase 6 — Engine: merge

| ID | Status | Title | Owner |
|---|---|---|---|
| T-060 | TODO | `MergeValidator` (codec / pix-fmt / track count). | agent |
| T-061 | DONE-v0.0.5 | `Merger` + concat-list writer (updated to Stage-then-concat). | agent |
| T-062 | TODO | Manifest reader + auto-discovery on S9. | agent |
| T-063 | TODO | Round-trip MD5 test: elementary streams identical. | agent |
| T-112 | DONE-v0.0.5 | Fix native concat demuxer JNI saf_close crash on unattached pthread (K-013). | agent |

## Phase 7 — Polish + Settings + Update check

| ID | Status | Title | Owner |
|---|---|---|---|
| T-070 | TODO | `SettingsStore` (DataStore Preferences) per `DATA_MODELS.md` §2.4. | agent |
| T-071 | TODO | `CleanupRepository` + `CleanupEngine` integration on S15a. | agent |
| T-072 | DONE-v0.0.8 | `UpdateService` + Retrofit + SHA-256 verify + `PackageInstaller`. | agent |
| T-073 | TODO | Permission flow (POST_NOTIFICATIONS, SAF, REQUEST_INSTALL_PACKAGES). | agent |
| T-074 | TODO | OSS notices generator (`oss-licenses.json`). | agent |

## Phase 8 — Release v0.0.1

| ID | Status | Title | Owner |
|---|---|---|---|
| T-080 | TODO | AI/ docs gate: full sweep per `AGENTS.md` §8. | agent |
| T-081 | TODO | Version bump to `0.0.1` (versionCode = 1). | agent |
| T-082 | TODO | `assembleRelease`, sign, capture log. | agent |
| T-083 | TODO | `deploy_release.ps1` published to releases repo. | agent |
| T-084 | TODO | GitHub release tag `v0.0.1` + APK attached. | agent |
| T-085 | TODO | In-app update check on a v0.0.1 phone reports "Up to date". | agent |

## Release v0.0.7 — Tight Boundaries, Library Details, Merge Disk Optimization

| ID | Status | Title | Owner |
|---|---|---|---|
| T-113 | DONE-v0.0.7 | Position `-ss` before `-i` in Splitter for fast seek and tight GOP boundaries. | agent |
| T-114 | DONE-v0.0.7 | Omit `-to` on final part in Splitter. | agent |
| T-115 | DONE-v0.0.7 | Implement JobDetailSheet for CANCELLED/FAILED jobs with tablet max width 600dp constraint. | agent |
| T-116 | DONE-v0.0.7 | Implement retry replication logic duplicating part entities with new IDs/same URIs verbatim. | agent |
| T-117 | DONE-v0.0.7 | Skip staging/copying in Merger for writable real paths; sequential cache cleanup with 5s safety margin. | agent |
| T-118 | DONE-v0.0.7 | Update phase label steps to support dynamic step totals and hide step prefix for steps <= 2. | agent |

## Release v0.0.9 — Loading Primitives, Cleanup Patterns, OSS Notices, Adaptive Icon

| ID | Status | Title | Owner |
|---|---|---|---|
| T-119 | DONE-v0.0.9 | Create LoadingArc, PulseDot, ShimmerSkeleton Compose primitives. | agent |
| T-120 | DONE-v0.0.9 | Adaptive launcher icon (foreground/background layers) + splash screen keep-on-screen guard. | agent |
| T-121 | DONE-v0.0.9 | OSS Notices screen from packaged oss_notices.json asset with shimmer + tap-to-URL. | agent |
| T-122 | DONE-v0.0.9 | Room v3 to v4: cleanup_patterns table + seed 12 default patterns (Migration_3_4 + MigrationTestHelper test). | agent |
| T-123 | DONE-v0.0.9 | Fix CleanupPattern regex #7 (remove TRUE/REAL tokens) and #8 (restrict to dash-prefixed group tag). | agent |
| T-124 | DONE-v0.0.9 | CleanupPatternsViewModel + CRUD unit tests. | agent |
| T-125 | DONE-v0.0.9 | Wire LoadingArc into MergeOrderScreen (verifying state, try/finally guard). Fixes K-017. | agent |
| T-126 | DONE-v0.0.9 | LibraryViewModel isInitialLoad flag + ShimmerSkeleton placeholders + PulseDot on RUNNING jobs. | agent |
| T-127 | DONE-v0.0.9 | FileDetailsViewModel UiState refactor (Loading/Success/Error) + LoadingArc + error card in FileDetailsScreen. | agent |
| T-128 | DONE-v0.0.9 | Unit tests: LibraryViewModelTest (isInitialLoad), FileDetailsViewModelTest (all 3 UiState paths). | agent |

## Release v0.0.10 — Merger Fast Path (scoped storage fallback)

| ID | Status | Title | Owner |
|---|---|---|---|
| T-129 | DONE-v0.0.10 | Inject SettingsRepository to read improveReliability in Merger. | agent |
| T-130 | DONE-v0.0.10 | Implement input-readability and output-writability probes in canFastPath gate. | agent |
| T-131 | DONE-v0.0.10 | Add MergerFastPathTest verifying all 4 branches of fast-path gate. | agent |
| T-132 | DONE-v0.0.10 | Verify v0.0.10 Step 3-v2 on-device; confirm graceful fallback in logcat. | agent |

## Release v0.0.10.1 — Hotfix

| ID | Status | Title | Owner |
|---|---|---|---|
| T-133 | DONE-v0.0.10.1 | FFmpegKit ProGuard keep rules to fix release-build crash | agent |

### Carried forward
- **K-018** (ByParts drift / snap fix) -> Carried forward to v0.0.12.
- **K-019** (Merger unit-test filesystem seams) -> Carried forward to v0.0.11.
- **K-021** (MKV concat header dedup size delta) -> Carried forward to v0.0.11+.
- **K-022** (Switch from df to du cache sampling in verification) -> Carried forward to v0.0.11+.
- **Revisit Fast-Path**: Re-evaluate fast-path optimization after FileSystem seam is introduced in v0.0.11 (if no realistic targets exist, consider removing).

---

## Backlog (post-v1)

| ID | Status | Title | Target |
|---|---|---|---|
| T-100 | TODO | "Import to app cache" for cloud-backed inputs (K-002). | v0.0.2 |
| T-101 | TODO | Resume cancelled / interrupted jobs (K-001). | v0.0.3 |
| T-102 | TODO | Per-job "disable cleanup" toggle (K-006). | v0.0.4 |
| T-103 | TODO | Two-pane treatments for S4 / S7 / S8 / S15 (K-003). | v0.0.5+ |
| T-104 | TODO | Strip-metadata toggle. | v0.0.6 |
| T-105 | TODO | Audio / subtitle track filters per job. | v0.0.7 |
| T-106 | TODO | Hardware-decoder thumbnails on File Details (MediaCodec, **not** for split). | v0.0.8 |
| T-107 | TODO | Migrate engine to in-house FFmpeg build via Gradle. | v1.1 |
| T-108 | TODO | Re-encode mode for too-few-keyframes files (K-004). | v1.1+ |
| T-111 | TODO | (Optional) DebugCrashCollector: FileObserver on /data/tombstones | v0.0.6 |

---

## How to use

- Add `T-NNN` rows in numeric order (no gaps; reuse retired numbers if rare).
- Move rows between sections as phases end.
- Mark `DONE-v<x.y.z>` when the change ships.
- Cross-reference K-NNN from `KNOWN_ISSUES.md` when the task fixes a known issue.
