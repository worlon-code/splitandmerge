# AI/ — Documentation Pack

> Lives inside the repo (NOT in `.gitignore`). Read by every agent at the
> start of a task. Updated by the agent at the end of every release.

This folder mirrors the pattern from your KissKh and HTML Viewer apps,
re-targeted for Android Kotlin/Compose + an FFmpeg engine.

---

## How to use this folder

1. **Before any change** — read `tasks.md` + `KNOWN_ISSUES.md` to see what's
   in flight.
2. **During a feature** — append to `WORK_SUMMARY.md` as you complete steps.
3. **Before a release** — follow [AGENTS.md §8](../AGENTS.md) "AI/
   Documentation Gate". Update affected files only.
4. **After a release** — bump `CHANGELOG.md` per [AGENTS.md §9E](../AGENTS.md)
   format. Tag the version. Push.

---

## Files in this folder

| File | What it covers |
|---|---|
| `README.md` | This file. Map of AI/. |
| `ARCHITECTURE.md` | Modules, layers, threading model. |
| `KOTLIN_APP.md` | Kotlin/Compose conventions used in this codebase. |
| `SCREENS.md` | Each screen's purpose, components, sample data. |
| `SCREEN_FLOWS.md` | Mermaid diagrams of navigation. |
| `COMPONENTS.md` | Reusable Compose composables. |
| `DATA_MODELS.md` | Room entities + domain models. |
| `STATE_MANAGEMENT.md` | ViewModel + StateFlow + intent patterns. |
| `ENGINE.md` | FFmpeg integration (split, merge, probe). |
| `SUBTITLES.md` | Subtitle handling per codec family. |
| `CLEANUP_PATTERNS.md` | Title-cleanup regex rules. |
| `PERMISSIONS.md` | Manifest permissions + when requested. |
| `API_USAGE.md` | External HTTP calls (only the GitHub Releases JSON). |
| `TESTING.md` | Coverage map + per-screen test inventory. |
| `KNOWN_ISSUES.md` | Open bugs / tech debt. |
| `WORK_SUMMARY.md` | Completed work, ordered by date. |
| `tasks.md` | Open task list (todo / in-progress / done). |
| `CHANGELOG.md` | Per-release changelog. |
| `ISSUES.md` | Created on demand when 10+ errors hit (see [AGENTS.md §4](../AGENTS.md)). |
| `plans/` | Plan/MD files awaiting / received approval. |

---

## File ownership

The agent owns every file here. Humans review, but the agent edits in place
during release-doc updates. Never hand-edit `WORK_SUMMARY.md` or `CHANGELOG.md`
during normal development — let the release flow update them.

---

## Pointers to non-AI/ specs

These are the canonical sources of truth. They live in the workspace root,
NOT here:

| File | Purpose |
|---|---|
| [`../AGENTS.md`](../AGENTS.md) | Operating manual for every agent. |
| [`../TESTING-AGENT.md`](../TESTING-AGENT.md) | Test sub-agent rules. |
| [`../MASTER-PROMPT.md`](../MASTER-PROMPT.md) | The prompt used to bootstrap the agent. |
| [`../ANSWERS.md`](../ANSWERS.md) | Phase-0 user decisions. |
| [`../STITCH-BRIEF.md`](../STITCH-BRIEF.md) | Visual design brief. |
| [`../STITCH-PROMPT-ROUND2.md`](../STITCH-PROMPT-ROUND2.md) | Round-2 design. |
| [`../analysis/`](../analysis/) | Deep-dive analysis pack (problem, algorithms, risks). |
| [`../Design/`](../Design/) | Stitch design exports (visual reference only). |

If you want to know **what** to build, read those.
If you want to know **how** to build it (rules, gates, conventions), read
this folder.
