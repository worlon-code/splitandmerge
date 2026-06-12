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
| T-040 | TODO | Choose FFmpeg artefact (community fork survey). Document in `ENGINE.md` §1. | agent |
| T-041 | TODO | Pin in `gradle/libs.versions.toml`. | agent |
| T-042 | TODO | `FfmpegEngine` + `FfprobeEngine` interfaces + `ProcessFfmpegEngine` impl. | agent |
| T-043 | TODO | `ProgressParser` with full unit tests. | agent |
| T-044 | TODO | Engine smoke test on emulator + real device. | agent |
| T-045 | TODO | Wire S4 to real `ffprobe`. | agent |

## Phase 5 — Engine: split

| ID | Status | Title | Owner |
|---|---|---|---|
| T-050 | TODO | `CutPlanner` with full unit tests (every branch in `analysis/03`). | agent |
| T-051 | TODO | `Splitter` orchestrator. | agent |
| T-052 | TODO | `JobService` foreground service + notification. | agent |
| T-053 | TODO | `JobsRepository` with progress bus. | agent |
| T-054 | TODO | Manifest writer (`Manifest.write`). | agent |
| T-055 | TODO | Storage check + auto-resplit fallback. | agent |
| T-056 | TODO | End-to-end test: split a 1 GB fixture; subs intact. | agent |

## Phase 6 — Engine: merge

| ID | Status | Title | Owner |
|---|---|---|---|
| T-060 | TODO | `MergeValidator` (codec / pix-fmt / track count). | agent |
| T-061 | TODO | `Merger` + concat-list writer. | agent |
| T-062 | TODO | Manifest reader + auto-discovery on S9. | agent |
| T-063 | TODO | Round-trip MD5 test: elementary streams identical. | agent |

## Phase 7 — Polish + Settings + Update check

| ID | Status | Title | Owner |
|---|---|---|---|
| T-070 | TODO | `SettingsStore` (DataStore Preferences) per `DATA_MODELS.md` §2.4. | agent |
| T-071 | TODO | `CleanupRepository` + `CleanupEngine` integration on S15a. | agent |
| T-072 | TODO | `UpdateService` + Retrofit + SHA-256 verify + `PackageInstaller`. | agent |
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

---

## How to use

- Add `T-NNN` rows in numeric order (no gaps; reuse retired numbers if rare).
- Move rows between sections as phases end.
- Mark `DONE-v<x.y.z>` when the change ships.
- Cross-reference K-NNN from `KNOWN_ISSUES.md` when the task fixes a known issue.
