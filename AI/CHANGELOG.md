# Changelog

> Per-release changelog. Format follows [AGENTS.md §9E](../AGENTS.md). The
> agent appends a new section at release time. Newest at the top.

---

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
