# AI/TESTING.md — Test Plan & Coverage

> Companion to [TESTING-AGENT.md](../TESTING-AGENT.md) (the rules + scripts).
> This file is the **AI/** docs perspective: what gets tested, how it maps to
> requirements, and how the testing agent fits the overall workflow.

---

## 1. Test pyramid for v1

```
       ▲
       │   E2E / Monkey   (5 %)   ← run-tests.ps1 -Monkey 5000
       │   Cross-app (UI Aut.)    ← SAF picker flows, install update
       │
       │   Instrumented Compose   (25 %)
       │   ViewModel + UI on a real device or emulator
       │
       │   JVM unit               (70 %)
       │   Pure logic: cleanup engine, cut planner, manifest parsing
       ▼
```

The **engine layer** (FFmpeg invocation, real `.so` binary calls) is tested
twice: a JVM-level fake under `src/test/` for cut planning + progress parsing,
and a real instrumented smoke test under `src/androidTest/engine/` that runs a
real `-version` + a 5-second `-c copy` against a 10 MB fixture.

## 2. Required test coverage by package

| Package | Min coverage | Why |
|---|---|---|
| `domain/` (CleanupEngine, CutPlanner, Manifest, ProbeResult) | ≥ 80 % line | Pure logic; cheap to test; high blast radius. |
| `engine/ProgressParser` | ≥ 90 % line | A bug here breaks every progress UI. |
| `data/db/` (Room DAOs) | ≥ 70 % line | Schema + queries; Room test helpers. |
| `ui/*ViewModel` | ≥ 70 % line | StateFlow correctness; covered with Turbine. |
| `service/JobService` | smoke only | Hard to unit-test foreground services; covered via instrumented + manual. |
| `engine/FfmpegEngine` (real `.so`) | smoke only | Single `-version` + 5-sec copy test on emulator + real device. |
| `platform/saf` | smoke only | URI permission flow tested via UI Automator. |

The 80 % rule mirrors the org-wide rule in your default instructions.

## 3. Mapping to the requirements docs

| Requirement (from `analysis/01`) | Test that covers it |
|---|---|
| H1 — Split into N parts | `CutPlannerTest#exactPartsProducesNMinusOneCuts` |
| H2 — 9 GB cap (≤ 9.5 GB ceiling) | `CutPlannerTest#sizeCapNeverExceedsCeiling` + `EngineSmokeTest#capRespectedOnFixture` |
| H3 — Combined modes — cap wins | `CutPlannerTest#bothModeCapWinsWhenLarger` |
| H4 — Lossless (`-c copy`) | `EngineSmokeTest#elementaryStreamMd5MatchesAfterRoundTrip` |
| H5 — All subtitle tracks preserved | `EngineSmokeTest#allSubStreamsPresentInPart1` |
| H6 — Subtitle timing starts at 00:00 | `EngineSmokeTest#subTimestampShiftedToZero` |
| H7 — Lossless merge | `MergerTest#concatProducesIdenticalElementaryStream` |
| H8 — 4K / HDR preserved | Verified by H4's MD5 round-trip + manual check |
| H9 — Fully on-device | `ApiUsageTest#noUnexpectedHttpHosts` (network policy) |
| H10 — Survives long jobs | Manual: 30-min split with screen off, see `KNOWN_ISSUES.md` |

## 4. Cleanup-rule test set (S15a)

Every built-in cleanup pattern has at least one test in
`CleanupEngineTest`:

| Pattern | Sample input | Expected cleaned title |
|---|---|---|
| Strip URL prefix | `www.5MovieRulz.graphics - Kantara.Chapter.1.2024.4K.WEB-DL.x265.mkv` | `Kantara Chapter 1 (2024)` |
| Strip resolution | `Devara.2024.2160p.HDR.HEVC.mkv` | `Devara (2024)` |
| Strip codec | `Salaar.Part1.Ceasefire.2023.4K.BluRay.HEVC.TrueHD.mkv` | `Salaar Part1 Ceasefire (2023)` |
| Strip audio | `…DTS HD MA DDP5.1 H.265.mkv` | (no DTS/DDP/H.265 tokens) |
| Strip release group | `…Friday4KPopc.mkv` | (group token gone) |
| Year detection | `Karuppu.2026.Tamil.4K.WEB-DL.x265.mkv` | `Karuppu (2026)` |
| Fallback when too short | `abc.mkv` | `abc` |

Custom user patterns are exercised by `CleanupEngineCustomRulesTest`.

## 5. UI test inventory

| Screen | Test class | Mode |
|---|---|---|
| S1 Onboarding | `OnboardingScreenTest` | Compose |
| S2 Library (phone) | `LibraryScreenTest` | Compose |
| S2 Library (tablet) | `LibraryScreenTabletTest` | Compose |
| S4 File Details | `FileDetailsScreenTest` | Compose |
| S5 Split Config | `SplitConfigScreenTest` | Compose |
| S6 Confirm | `SplitConfirmScreenTest` | Compose |
| S7 Progress | `SplitProgressScreenTest` | Compose |
| S8 Split Complete | `SplitResultScreenTest` | Compose |
| S10 Merge Order | `MergeOrderScreenTest` | Compose |
| S11 Merge Config | `MergeConfigScreenTest` | Compose |
| S13 Merge Complete | `MergeResultScreenTest` | Compose |
| S14 Jobs | `JobsScreenTest` | Compose |
| S15 Settings | `SettingsScreenTest` | Compose |
| S15a Cleanup Patterns | `CleanupPatternsScreenTest` | Compose |
| S16 OSS Notices | `OssNoticesScreenTest` | Compose |
| Dialogs (D1/D2/D3) | `ModalSheetsTest` | Compose |
| **SAF picker flow** | `SafPickerFlowTest` | UI Automator |
| **Install-update flow** | `UpdateInstallTest` | UI Automator (Android 12+ `PackageInstaller`) |

## 6. Test fixture inventory

| File | Purpose | Size |
|---|---|---|
| `app/src/androidTest/assets/fixture.mkv` | 60 s 720p HEVC + ASS English + 2 audio (eng E-AC3, tel AAC). Used by EngineSmokeTest. | ~10 MB |
| `app/src/androidTest/assets/fixture.mp4` | Same content in MP4 to test container-promote dialog (D3). | ~10 MB |
| `app/src/test/resources/probe-bahubali.json` | Probe JSON used by JVM unit tests. | <5 KB |
| `app/src/test/resources/probe-kantara.json` | Same, second movie. | <5 KB |
| `app/src/test/resources/keyframes-7200s.txt` | Pre-computed keyframe list for cut planner unit tests. | <50 KB |

Fixtures are **synthetic**; they are generated once with FFmpeg locally and
checked in. Never commit a real movie file.

## 7. How a contributor adds a new test

1. Find the package nearest the code under test.
2. Open the existing `*Test.kt` in that package; add a method.
3. Only create a new test class when the package has none.
4. Run `.\run-tests.ps1` locally — must be green before push.
5. CI re-runs the same suite on Linux + an emulator. Treat the CI report
   as authoritative when local results disagree (often timezone / SAF-package).

## 8. Test-only dependencies

If you add a new test-only library, add it BOTH to:

- `app/build.gradle.kts` (under `testImplementation` / `androidTestImplementation`).
- [TESTING-AGENT.md §1](../TESTING-AGENT.md) (the canonical version list).

Failing to update the agent file means the next agent run will revert your
change because the spec said "use only what's in TESTING-AGENT.md §1".

## 9. Coverage report

```powershell
.\gradlew.bat :app:createDebugCoverageReport
Start-Process app\build\reports\coverage\androidTest\debug\connected\index.html
```

Coverage <80 % on a changed file blocks merge. (CI rule TBD; manual for now.)

## 10. Known testing limitations

- Compose UI test cannot drive the system **SAF picker** — that's why we
  have UI Automator under `SafPickerFlowTest`.
- The `PackageInstaller` install screen is a system UI; UI Automator handles it.
- Hardware-decoder thumbnail tests are skipped on emulators (no HEVC decoder
  on x86_64 Android emulators below API 33).
- DRM / encrypted content is **out of scope** in v1; no tests required.
- Real 4K HEVC files are too large for CI; the engine smoke uses a 720p fixture.

## 11. Where to read more

- [TESTING-AGENT.md](../TESTING-AGENT.md) — full Gradle config, sample tests, run commands, CI workflow.
- [AGENTS.md](../AGENTS.md) §4 — lint/test gate rules.
- [run-tests.ps1](../run-tests.ps1) — automation script source.
- [.github/workflows/android-test.yml](../.github/workflows/android-test.yml) — CI workflow.
