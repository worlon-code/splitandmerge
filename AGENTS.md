═══════════════════════════════════════════════════════
AGENT RULES — VIDEO SPLITTER (Android · Kotlin · Jetpack Compose)
Repository: com.splitandmerge.mkvslice
Last updated: 2026-06-12
═══════════════════════════════════════════════════════

These rules apply to every coding agent working on this repository
(Codex, Antigravity, Claude Code, Cursor, GitHub Copilot, etc.).
Read this file fully before any change.

═══════════════════════════════════════════════════════
0. GOLDEN RULES (NEVER VIOLATE)
═══════════════════════════════════════════════════════
- NEVER start implementation without explicit user approval per Section 2.
- NEVER bump version on debug builds.
- NEVER increase the build number (last segment) — only MAJOR.MINOR.PATCH.
- NEVER assume — ask if ANY doubt exists per Section 7.
- NEVER proceed past 10+ errors without ISSUES.md per Section 4.
- NEVER request `MANAGE_EXTERNAL_STORAGE`. Use SAF only.
- NEVER enable any GPL flag in FFmpeg builds. LGPL only.
- NEVER re-encode video or audio. `-c copy` always.
- NEVER touch files under: `analysis/`, `Design/`, `STITCH-BRIEF.md`,
  `STITCH-PROMPT-ROUND2.md`, `ANSWERS.md`. They are read-only specs.
- NEVER force-push, --no-verify, or rewrite published commits.
- ALWAYS use the Gradle wrapper (`./gradlew` / `gradlew.bat`).
- ALWAYS run `./gradlew lint` and `./gradlew test` before every build.
- ALWAYS use ADB at the path in Section 6.
- ALWAYS capture logs under `./logs/` per Section 5.
- ALWAYS install debug APK to a connected device after a successful debug build.
- ALWAYS update `AI/` documentation per Section 8 before any release.

═══════════════════════════════════════════════════════
1. KOTLIN / ANDROID TOOLCHAIN
═══════════════════════════════════════════════════════

REQUIRED VERSIONS
  JDK:                17 (Android Studio Hedgehog+ bundled JDK is fine)
  Kotlin:             2.0.x or later
  AGP:                8.x stable
  Gradle wrapper:     8.x (use the wrapper checked into the repo)
  NDK:                pinned in `app/build.gradle.kts` `ndkVersion = "..."`
  KSP:                used for Hilt + Room (NOT kapt)
  compileSdk:         35 (Android 15)
  targetSdk:          35
  minSdk:             26 (Android 8)
  abiFilters:         ["arm64-v8a"] only (smallest APK; modern devices)

VERIFY BEFORE FIRST RUN
  ./gradlew --version
  java -version       (must report 17)
  & "<ADB path>" devices
If any of the above fails → STOP → tell the user with exact missing item.

NEVER USE
  - `kapt` (use KSP)
  - Global Flutter/Dart commands (this is a pure Android project)
  - Any GPL-licensed FFmpeg flag or codec (x264, libfdk_aac, etc.)
  - `MANAGE_EXTERNAL_STORAGE` permission
  - WorkManager for the FFmpeg engine (use a foreground service)

═══════════════════════════════════════════════════════
2. PLAN / MD FILE APPROVAL GATE
═══════════════════════════════════════════════════════
Create a plan/MD file FIRST for: new features, refactors,
bug fixes spanning >1 file, architecture changes, dependency changes.

  STEP 1 → Create detailed plan/MD inside `AI/plans/`
           (e.g. `AI/plans/2026-06-12-add-resume-job.md`)
  STEP 2 → Present the plan to the user
  STEP 3 → WAIT for explicit approval
  STEP 4 → Implement only after approval
  STEP 5 → If user requests changes → update MD → re-present
Silence is NOT approval. Ask again if no answer.

Plan MD template:
  # <Title>
  ## Problem
  ## Proposed change
  ## Files touched (paths + intent)
  ## Tests added/updated
  ## Migration notes
  ## Rollback plan
  ## Open questions

═══════════════════════════════════════════════════════
3. BUILD SYSTEM
═══════════════════════════════════════════════════════

A — VERSION FORMAT (STRICT)
  versionName format: MAJOR.MINOR.PATCH (string)
  versionCode:        monotonically increasing integer

  Start: versionName = "0.0.1",  versionCode = 1
  After 0.0.99 → ASK USER before bumping to 1.1.0.
  Debug builds: NEVER bump version.

B — DEBUG BUILD WORKFLOW
  1. ./gradlew lint            (Section 4)
  2. ./gradlew test            (Section 4)
  3. Fix all errors before building.
  4. ./gradlew assembleDebug
  5. Capture log to ./logs/debug_<YYYYMMDD_HHMMSS>.log (Section 5)
  6. & "<ADB path>" devices    (Section 6)
  7. & "<ADB path>" install -r app/build/outputs/apk/debug/app-debug.apk
  8. Confirm install success. NO version bump.

C — RELEASE BUILD WORKFLOW (Section 8 GATE applies)
  1. AI/ docs update + user approval (GATE 1, Section 8)
  2. Confirm version bump (GATE 2)
  3. ./gradlew lint + ./gradlew test → fix all errors
  4. ./gradlew assembleRelease
  5. Capture log to ./logs/release_<ts>.log
  6. Report APK location + size + SHA-256
  7. Ask user before installing to device

D — POST-BUILD VERIFICATION
  After every feature → debug build → install → list what to verify
  on device → wait for confirmation. Examples:
    "Open Library, tap +Split, pick a 5GB MKV, confirm probe table populates."
    "Run a 9 GB-cap split on a 30 GB file, confirm 4 parts in the auto-folder."

E — APK SIGNING
  Reads `keystore.properties` if present (gitignored) and signs release.
  If absent, falls back to debug signing config — DO NOT block the build.
  No in-app signing UI (Q10 = scaffolded only, hidden from UI).

═══════════════════════════════════════════════════════
4. LINT / TEST GATE — MANDATORY
═══════════════════════════════════════════════════════
Run before every build, after every change, after `./gradlew --refresh-dependencies`.

  ./gradlew lint --warning-mode=summary 2>&1 | Out-File -FilePath "logs/lint_<ts>.log" -Encoding UTF8
  ./gradlew test 2>&1 | Out-File -FilePath "logs/test_<ts>.log" -Encoding UTF8

SEVERITY POLICY (matching your KissKh rules):
  Lint Errors    → COUNT + FIX before any build
  Lint Warnings  → IGNORE unless `errorAsWarning` rule
  Lint Info      → IGNORE
  Test failures  → ALWAYS block. Fix before build.

ERROR THRESHOLD (lint or test):
  0 errors    → proceed immediately
  1–9 errors  → fix all → re-run → build
  10+ errors  → STOP → write `AI/ISSUES.md` → user approval
                → fix in priority order → re-run after each → zero errors → continue

ISSUES.md format (when 10+ errors):
  # Issues — <timestamp>
  ## Summary
  Errors: <X> (lint <a> + test <b>)  | Warnings: <X> (ignored)
  ## Error List
  | # | File | Line | Message | Severity | Priority |
  ## Affected Files / Fix Plan

═══════════════════════════════════════════════════════
5. LOG CAPTURE SYSTEM
═══════════════════════════════════════════════════════
Path:      ./logs/   (create if missing; NOT in .gitignore — user may want them)
Encoding:  UTF-8 without BOM (use `[System.IO.File]::WriteAllText`)

Naming: <type>_<YYYYMMDD_HHMMSS>.log
  Examples:
    debug_20260612_143022.log
    release_20260612_160045.log
    lint_20260612_142800.log
    test_20260612_142950.log
    install_20260612_143140.log
    device_20260612_150500.log

Every build log file header:
  ════════════════════════════════════════
  BUILD LOG — Video Splitter
  Timestamp:    <ISO 8601 UTC>
  Build type:   debug | release
  Gradle:       <version>
  Kotlin:       <version>
  AGP:          <version>
  versionName:  <e.g. 0.0.1>
  versionCode:  <e.g. 1>
  Git branch:   <branch>
  Git commit:   <short SHA>
  ════════════════════════════════════════
  [LINT OUTPUT]
  [TEST OUTPUT]
  [BUILD OUTPUT]
  [INSTALL OUTPUT — debug only]
  [RESULT: Status / APK Path / Size / SHA-256 / Duration]

After build → scan stdout for "error:", "FAILED", "Exception":
  <10 occurrences → fix immediately
  ≥10 occurrences → ISSUES.md gate (Section 4)

═══════════════════════════════════════════════════════
6. ADB CONFIGURATION
═══════════════════════════════════════════════════════
Path: D:\idm\platform-tools-latest-windows\platform-tools\adb.exe

PowerShell pattern (mandatory):
  $adb = "D:\idm\platform-tools-latest-windows\platform-tools\adb.exe"
  & $adb devices
  & $adb install -r "app/build/outputs/apk/debug/app-debug.apk"
  & $adb logcat -s "VideoSplitter" > "logs/device_<ts>.log"

RULES
  - Run `& $adb devices` before every install.
  - If 0 devices connected → report + stop. Never proceed without a device.
  - If >1 devices → ASK the user which serial. Never silently pick one.
  - Never use wireless ADB unless the user explicitly asks.
  - Never `adb uninstall` unless the user explicitly asks (it deletes app data).
  - Use `adb install -r` for normal updates.
  - Use `adb install -r -d` only when intentionally downgrading.

═══════════════════════════════════════════════════════
7. ASKING QUESTIONS — MANDATORY SCENARIOS
═══════════════════════════════════════════════════════
ALWAYS ask the user when:
  - A version bump is implied (release only).
  - You must choose between release vs debug.
  - A change is potentially breaking.
  - Two valid implementation approaches exist.
  - A fix touches code outside the immediate feature scope.
  - Device is not detected, or multiple devices are connected.
  - A rule in this file appears to conflict with the requested task.
  - ANY doubt about user intent.

Ask format:
  ❓ QUESTION: <one-line question>
  Options:
    A. <option A>
    B. <option B>
    C. <option C>  (if applicable)
  Default if no response: <documented assumption>

═══════════════════════════════════════════════════════
8. AI/ DOCUMENTATION GATE (RELEASE ONLY)
═══════════════════════════════════════════════════════

CURRENT AI/ FILES (kept in repo, NOT in .gitignore):
  README.md            Entry point. Lists all AI/ docs.
  ARCHITECTURE.md      Module layout, layer boundaries.
  KOTLIN_APP.md        Kotlin/Compose conventions used in this app.
  SCREENS.md           Each screen's purpose, sample data, status.
  SCREEN_FLOWS.md      Mermaid diagrams of navigation.
  COMPONENTS.md        Reusable Compose composables list.
  DATA_MODELS.md       Room entities + domain models.
  STATE_MANAGEMENT.md  ViewModel / StateFlow / intent patterns.
  ENGINE.md            FFmpeg integration: split, merge, probe.
  SUBTITLES.md         Subtitle handling (SRT/ASS/PGS/VobSub).
  CLEANUP_PATTERNS.md  Title cleanup regex rules.
  PERMISSIONS.md       Manifest permissions + when requested.
  API_USAGE.md         External HTTP calls (only the GitHub Releases JSON).
  KNOWN_ISSUES.md      Open bugs / tech debt.
  WORK_SUMMARY.md      What's been done, ordered by date.
  tasks.md             Open task list (todo / in-progress / done).
  CHANGELOG.md         Per-release changelog (see Section 9E format).
  ISSUES.md            Created on demand when 10+ errors hit (Section 4).
  plans/               Plan/MD files awaiting / received approval.

RELEASE DOC WORKFLOW
  STEP 1 → Run a code-graph snapshot if `update-codebase-graph.ps1` is present.
           If absent, document directory tree + module count manually.
  STEP 2 → git diff <last-tag>..HEAD --name-only → categorise.
  STEP 3 → Update affected files only:
    New screen           → SCREENS.md, SCREEN_FLOWS.md
    Navigation change    → SCREEN_FLOWS.md (Mermaid)
    New Compose comp.    → COMPONENTS.md
    DB / Room change     → DATA_MODELS.md
    New ViewModel        → STATE_MANAGEMENT.md
    New HTTP call        → API_USAGE.md
    New permission       → PERMISSIONS.md (and `ARCHITECTURE.md` § 6.6)
    Engine / FFmpeg      → ENGINE.md
    Subtitle behaviour   → SUBTITLES.md
    Cleanup regex change → CLEANUP_PATTERNS.md
    Bug fix / tech debt  → KNOWN_ISSUES.md
    Completed work       → WORK_SUMMARY.md
    Tasks                → tasks.md
    Any release          → CHANGELOG.md (always)
  STEP 4 → Update CHANGELOG.md (Section 9E format).
  STEP 5 → Update Mermaid diagrams if flows changed.
  STEP 6 → Present summary:
    ┌──────────────────────────────────────────┐
    │ 📄 AI/ DOCUMENTATION UPDATE SUMMARY      │
    │ Files updated:   <list + reason>         │
    │ Files unchanged: <list>                  │
    │ Changes since v<last>: <bullets>         │
    │ Diagrams updated: <yes/no + which>       │
    │ Ready for release review?                │
    └──────────────────────────────────────────┘
  STEP 7 → GATE 1: User approves docs.
           GATE 2: User approves version bump.
           Only after BOTH → proceed to release build.

IF AI/ MISSING → inform user → approval → generate the skeleton.
DEBUG BUILDS → no doc update required.

FULL RELEASE SEQUENCE
  1. User requests release.
  2. Run code-graph snapshot.
  3. git diff → categorise changes.
  4. Update AI/ markdown files.
  5. Update AI/CHANGELOG.md.
  6. Update Mermaid diagrams if needed.
  7. Present summary → GATE 1 (docs approval).
  8. Confirm version → GATE 2 (version approval).
  9. ./gradlew lint + test → fix errors.
  10. ./gradlew assembleRelease.
  11. Capture log + APK SHA-256.
  12. Report APK path + size.
  13. Ask before installing.

═══════════════════════════════════════════════════════
9. DEPLOY — RELEASE DEPLOYMENT SYSTEM
═══════════════════════════════════════════════════════
Releases repository:    user-supplied GitHub repo (e.g. splitandmerge/mkvslice-releases)
Update-info JSON:       videosplitter-version.json
Max versions kept:      3 (older auto-deleted by deploy script when added)

DEPLOY ONLY AFTER:
  ✓ Code graph updated
  ✓ APK built
  ✓ AI/ docs approved (GATE 1)
  ✓ Version bumped (GATE 2)

A — DEPLOY SEQUENCE
  1. Draft a structured changelog from AI/CHANGELOG.md.
  2. Present changelog to user → wait for approval.
  3. Run `deploy_release.ps1` (creates the ZIP/APK + JSON; cleans old versions).
  4. Commit + push releases repo (Section 9B).
  5. Commit + push main project repo (Section 9C).
  6. Show deploy summary (Section 9D).

B — RELEASES REPO COMMIT
  Set-Location <releases-repo-path>
  git add .
  git commit -m "Release Video Splitter v<version> — <YYYY-MM-DD>"
  git push origin main

C — MAIN PROJECT REPO COMMIT
  Set-Location <main-repo-path>
  git add .
  git commit -m "chore: Release v<version> — update AI docs + changelog"
  git tag v<version>
  git push origin main --tags

D — DEPLOY REPORT
  ┌─────────────────────────────────────────────────┐
  │ 🚀 DEPLOY COMPLETE — Video Splitter v<ver>     │
  │ APK:         v<ver>/app-release.apk    ✓        │
  │ JSON:        videosplitter-version.json ✓       │
  │ SHA-256:     <hash>                             │
  │ Size:        <MB>                               │
  │ Old vers:    Cleaned (kept 3 max)      ✓        │
  │ Releases repo: pushed to main          ✓        │
  │ Project repo:  pushed to main + tag    ✓        │
  │ URL: https://github.com/<org>/<rel-repo>/raw/   │
  │      main/v<ver>/app-release.apk                │
  └─────────────────────────────────────────────────┘

E — CHANGELOG FORMAT IN AI/CHANGELOG.md (STRICT)
  ## [0.0.1] — 2026-06-12
  🚀 NEW FEATURES
  - Feature description (one clear sentence each)

  🔧 BUG FIXES
  - Bug fix description

  ⚡ IMPROVEMENTS
  - Improvement description

  📦 TECHNICAL
  - Internal change description

  🗑️ REMOVED
  - What was removed

  Rules:
  - ALWAYS use emoji section headers.
  - NEVER write "See commits for details".
  - NEVER leave the entry empty or generic.
  - Only include sections that have items for that release.
  - This produces clean \n-separated JSON output for the in-app updater.

F — DEPLOY ERROR HANDLING
  APK not found:
    → ASK USER: "Should I build release first?"
    → WAIT — do NOT auto-build.
  Git push fails:
    → Report exact error → do NOT retry silently.
    → Suggest: check credentials / network / branch.
  Changelog extraction fails:
    → Do NOT fall back to a generic message.
    → ASK USER to provide content manually.
  Version mismatch (gradle versionName vs CHANGELOG.md):
    → STOP → report → confirm before proceeding.

═══════════════════════════════════════════════════════
10. WORKFLOW SUMMARY (QUICK REFERENCE)
═══════════════════════════════════════════════════════

  NEW FEATURE
  Plan MD → Approval → Implement → Lint → Test →
  Fix → Debug Build → Logs → Install → Verify → Report

  BUG FIX
  Reproduce → Lint → Test → Fix → Lint again → Test again →
  Debug Build → Logs → Install → Verify fix

  RELEASE
  git diff → Update AI/ → Update CHANGELOG → Update diagrams →
  Present summary → GATE 1 (docs) → GATE 2 (version) →
  Lint → Test → Release Build → Logs → Report APK

  DEPLOY (after release)
  Draft changelog → User approves → Run deploy script →
  Push releases repo → Push project repo → Deploy report

  10+ ERRORS
  STOP → AI/ISSUES.md → Approval → Fix in order →
  Re-run lint+test each fix → Zero errors → Continue

═══════════════════════════════════════════════════════
11. CANONICAL SOURCES OF TRUTH
═══════════════════════════════════════════════════════
When in doubt about WHAT to build, consult these in order:

  1. AGENTS.md                        ← THIS FILE (the how)
  2. ANSWERS.md                       ← Phase-0 decisions (Q1..Q24, 4 clarifications)
  3. analysis/00-MASTER-INDEX.md      ← Map of all analysis docs
  4. analysis/01..13                  ← Deep dives (problem, algorithms, risks…)
  5. STITCH-BRIEF.md                  ← Visual design brief (theme + 17 screen prompts)
  6. STITCH-PROMPT-ROUND2.md          ← Round-2 design pack (bottom nav etc.)
  7. Design/                          ← Stitch outputs (visual reference, NOT source)

The Compose implementation is YOUR translation of these documents.
You may NOT copy raw HTML from `Design/`. You produce equivalent Compose layouts.

═══════════════════════════════════════════════════════
12. SECURITY & PRIVACY
═══════════════════════════════════════════════════════
- No telemetry, no analytics, no crash reporters in v1 (Q20 = none).
- No HTTP requests except the GitHub Releases JSON for update checks.
- All file IO via SAF (`ACTION_OPEN_DOCUMENT` / `ACTION_OPEN_DOCUMENT_TREE`).
- Persist URI permissions only when needed; release on app uninstall.
- Do not log full file paths in user-visible UI; truncate to 200 chars.
- Verify update-APK SHA-256 against `videosplitter-version.json` BEFORE
  invoking `PackageInstaller`. Never install an unverified APK.

═══════════════════════════════════════════════════════
13. WHAT THE AGENT MUST DO ON FIRST RUN
═══════════════════════════════════════════════════════
The user provides ONE thing: an empty or near-empty target folder path.

In that folder you must, in this exact order:

  1. Verify the folder exists; if it has files, list them and ASK the user
     whether to proceed (refuse if non-trivial files exist that aren't ours).
  2. `git init` the folder. Set default branch to `main`.
  3. Copy the canonical specs into the repo:
        - AGENTS.md (this file)
        - ANSWERS.md
        - STITCH-BRIEF.md
        - STITCH-PROMPT-ROUND2.md
        - analysis/  (entire folder)
        - Design/    (entire folder, even if partial)
  4. Create `.gitignore` (Android Studio default + the additions in Section 14).
  5. Create `README.md` at repo root with project overview + quickstart.
  6. Create `AI/` folder with the skeleton listed in Section 8.
  7. Create `logs/` folder (empty, with `.gitkeep`).
  8. Create `app/` Android module via Gradle init or by writing the files
     directly per the structure in `analysis/09-PROJECT-STRUCTURE.md`.
  9. Run `./gradlew --version` to validate the wrapper.
  10. Make the first commit:
        git add .
        git commit -m "chore: initial scaffold (Phase 1 + 2)"
  11. STOP and report. Do NOT proceed to Phase 3 (engine wiring) without
      a fresh user approval per Section 2.

═══════════════════════════════════════════════════════
14. .gitignore ADDITIONS (beyond AS default)
═══════════════════════════════════════════════════════
  # Logs
  /logs/*.log
  !/logs/.gitkeep

  # Plans (kept locally; only commit approved plans)
  /AI/plans/*.draft.md

  # Local dev
  /local.properties
  /keystore.properties
  *.jks

  # NEVER commit these:
  /app/release/

═══════════════════════════════════════════════════════
END OF AGENTS.md
═══════════════════════════════════════════════════════
