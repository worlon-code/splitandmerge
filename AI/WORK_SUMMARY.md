# Work Summary

> Reverse-chronological log of completed work. The agent appends one row
> per meaningful unit of work as it finishes. Released items also appear
> in `CHANGELOG.md`.

Format: `YYYY-MM-DD · phase · short title · ref(s)`.

---

## 2026-06-15 — Release v0.0.8 (Settings, Logs, Update check, Folder collision fix)

- 2026-06-15 · 10 steps shipped · Migrated Settings to DataStore, added OutputFolderValidator + unified picker validation, first-run dialog, legacy folder migration, split "Analyzing" UI, fixed SAF folder collision in merger/splitter, added rotating file logger with redaction + log viewer + safe clear, added secure update check (Retrofit + SHA-256 + PackageInstaller). Refs: AI/plans/2026-06-14-v0-0-8.md and step 5/5b/5c/5d/7/8 prompts. Step 5e fixed SplitResultScreen mock data with real PartEntity wiring and removed dead Jobs* scaffolding.

## 2026-06-14 — Release v0.0.7

- 2026-06-14 · 7 · Implemented precise split boundaries, Library retry duplication with new IDs/same URIs verbatim, tablet cap on JobDetailSheet, merge path resolution and disk optimization, and dynamic phase labels. · ref: `AI/plans/2026-06-14-v0-0-7.md`.

## 2026-06-13 — Phase 6 Follow-ups

- 2026-06-13 · 6 · Fixed native JNI saf_close SIGSEGV crash in libffmpegkit.so by staging merge inputs to cache before concat; implemented pre-flight storage check and staged file lifecycle cleanup. · ref: `AI/plans/2026-06-13-merge-stage-then-concat.md`.

## 2026-06-12 — Pre-scaffold (specs)

- 2026-06-12 · 4 · Implemented FFmpeg/FFprobe engine wrappers with LGPL ffmpeg-kit-min, and pure Kotlin CutPlanner. Verified with smoke tests. · ref: `app/src/main/kotlin/com/splitandmerge/mkvslice/engine/`, `AI/ENGINE.md`.
- 2026-06-12 · 3 · Release build v0.0.3 successfully compiled, signed, and GitHub release package prepared. · ref: `app/build.gradle.kts`, `AI/CHANGELOG.md`, `logs/release_20260612_194100.log`.
- 2026-06-12 · 2 · Existing lint blockers fixed; `run-tests.ps1` hardened for local execution; debug APK built, tested, and installed on device. · ref: `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, `run-tests.ps1`, `logs/`.
- 2026-06-12 · 2 · Phase 2 Stitch audit completed; canonical screen mapping published in `AI/DESIGN_INDEX.md` and S10 light export reconciled. · ref: `AI/DESIGN_INDEX.md`, `AI/SCREENS.md`, `AI/tasks.md`.

- 2026-06-12 · 0 · Documentation pack complete (analysis 00–13). · ref: `analysis/`.
- 2026-06-12 · 0 · `ANSWERS.md` finalised (24 Q + 4 clarifications). · ref: `ANSWERS.md`.
- 2026-06-12 · 0 · `STITCH-BRIEF.md` produced (single-file design brief). · ref: `STITCH-BRIEF.md`.
- 2026-06-12 · 0 · Stitch Round 1 generated 17 design files. · ref: `Design/` and the two attached Stitch folders.
- 2026-06-12 · 0 · `STITCH-PROMPT-ROUND2.md` produced for the missing 8 screens. · ref: `STITCH-PROMPT-ROUND2.md`.
- 2026-06-12 · 0 · Stitch Round 2 delivered 19 of 20 variants (mergeorder_light pending). · ref: K-007.
- 2026-06-12 · 0 · `AGENTS.md` written (Android-adapted house rules). · ref: `AGENTS.md`.
- 2026-06-12 · 0 · `TESTING-AGENT.md` + `run-tests.ps1` + `.github/workflows/android-test.yml` written. · ref: `TESTING-AGENT.md`.
- 2026-06-12 · 0 · `MASTER-PROMPT.md` written. · ref: `MASTER-PROMPT.md`.
- 2026-06-12 · 0 · `AI/` skeleton populated (16 docs + plans/). · ref: `AI/`.

---

## How to add a row

```
- YYYY-MM-DD · <phase number> · <one-line title> · ref: <T-IDs / file paths / commit shas>
```

Group rows by date (descending). Newest at the top. Don't fold rows; one
event per line.

## What does NOT go here

- Trivial CI fixes.
- Routine `./gradlew --refresh-dependencies` runs.
- Lint/test green confirmations (those live in `logs/test-reports/`).
- Anything not visible to a future reader trying to understand the
  project's state.
