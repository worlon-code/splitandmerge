# Phase 6 Follow-ups — Merge Orchestration

**Status: APPROVED (with amendments A1–A8)**
**Date: 2026-06-13**

---

## Problem

Four distinct issues remain after the stage-then-concat structural fix:

| ID | Issue | Severity |
|----|-------|----------|
| A | 3-file merge (no manifest) reaches ~99% then "Execution Failed" | High |
| B | Cancel button updates DB to CANCELLED but never signals the engine | High |
| C | Swipe-to-kill leaves jobs stuck at `status=RUNNING` in DB forever | High |
| D | Progress UI hard-codes 50% for the entire staging phase | Medium |

---

## Proposed Change

### Fix 1 — Cancel actually cancels

**Root problem**: `cancelJob()` in `JobProgressViewModel` writes DB → `CANCELLED`
then navigates away. Never sends `ACTION_CANCEL` to `JobService`. Engine keeps running.

#### Amendment A1
The Composable (`JobProgressScreen`) observes the job row via DB flow and
navigates when `status` flips to `CANCELLED` or `FAILED`. The ViewModel only
fires the `ACTION_CANCEL` intent. No DB write from the ViewModel. This avoids a
race between the intent send and the DB read.

#### Amendment A2
In `JobProgressScreen`, a `LaunchedEffect` starts after the cancel button is tapped.
Using `withTimeout(5_000)` + polling `state.status`, if status has not flipped to
`CANCELLED` within 5 seconds, show a Snackbar: `"Force-stop requested"`.

#### Amendment A3
`JobService.cancelCurrentJob()` is a `suspend fun` called from a coroutine.
It calls `currentJobCoroutine?.cancelAndJoin()` (not just `cancel()`) before
calling `ffmpegEngine.cancel("all")`. This ensures the `finally{}` cleanup block
in `Merger.runMerge()` fully completes before `nextQueued()` can race in the
processing loop.

#### Amendment A4
`inputStream.copyTo(outputStream)` is not cancellable. Both the staging copy loop
and the final SAF copy-out must use a manual buffer loop with
`currentCoroutineContext().ensureActive()` checked every **8 MB** (128 × 64 KB
iterations). Buffer size: **64 KB**.

**Files touched (Fix 1):**
- `JobProgressScreen.kt` — observe status flip for navigation (A1); 5 s timeout Snackbar (A2)
- `JobProgressViewModel.kt` — fire `ACTION_CANCEL` intent only; remove DB write
- `ProcessFfmpegEngine.kt` — `cancel("all")` iterates and cancels all `activeSessions`
- `JobService.kt` — store per-job `kotlinx.coroutines.Job`; `cancelCurrentJob()` calls `cancelAndJoin()` (A3)
- `Merger.kt` — `ensureActive()` in staging loop (from coroutine context); counted 64 KB loops for both staging copy and SAF copy-out (A4)

---

### Fix 2 — Startup recovery: unstick RUNNING jobs

**Root problem**: Process killed mid-job → DB row stays `RUNNING` → `nextQueued()` ignores it → UI shows stuck forever.

#### Amendment A5
`JobDao` gets a paired query alongside `recoverStuckJobs()`:
```kotlin
@Query("UPDATE parts SET status = 'FAILED' WHERE status = 'RUNNING'")
suspend fun recoverStuckParts()
```
Both are called in sequence in the startup sweep.

#### Amendment A6
`JobService.onStartCommand` awaits a `CompletableDeferred<Unit>` that `App.onCreate()`
completes **after** the recovery sweep finishes. This prevents a queue-tap from
racing the sweep on slow devices. The deferred is provided as a `@Singleton`
via Hilt.

**Files touched (Fix 2):**
- `App.kt` — launch startup recovery; complete the `CompletableDeferred` after sweep
- `JobDao.kt` — add `recoverStuckJobs()` + `recoverStuckParts()` (A5)
- `JobService.kt` — await `CompletableDeferred` before calling `nextQueued()` (A6)
- `di/CoroutineModule.kt` — **[NEW]** provide `@ApplicationScope CoroutineScope` + `CompletableDeferred<Unit>`

---

### Fix 3 — Startup cache cleanup

**Root problem**: Staged files/temp files orphaned on process death; never cleaned.

#### Amendment A7
`MergeCacheSweeper` whitelists exactly these patterns (delete if older than 60 s):
- `staged_part*`
- `merge_tmp*`
- `concat.txt`
- `*.part.tmp`
- `*.mkv.tmp`
- `*.mp4.tmp`

Test fixtures **must** include `mkvslice_db.lck` as a "kept" file to prove Room
database lock files are not deleted.

**Files touched (Fix 3):**
- `App.kt` — call `MergeCacheSweeper.sweep(cacheDir)` in startup coroutine
- `domain/merger/MergeCacheSweeper.kt` — **[NEW]** sweep logic with A7 whitelist; cutoff = 60 s
- `MergeCacheSweeperTest.kt` — **[NEW]** JVM unit test including `mkvslice_db.lck` kept-file fixture

---

### Fix 4 — Diagnostic logging (no logic change)

> **Explicit rule**: Do NOT attempt a logic fix for the 99% failure until log data lands.

#### Amendment A8
Log severity:
- `Timber.i` for phase-boundary milestones (preserved by release `ReleaseTree`)
- `Timber.d` for the 128 MB tick lines (debug-only)

**Logging added in `Merger.runMerge()`** (tag = `"MERGE"`):

| Point | Level | Content |
|-------|-------|---------|
| Before staging | i | `job=X parts=N totalSizeRequired=X cacheDir.usableSpace=X` |
| Before each part copy | i | `staging part N uri=X size=X` |
| After each part copy | i | `staged part N -> path size=X diskFreeAfter=X` |
| Before FFmpeg concat | i | `concat.txt contents: …` |
| Before FFmpeg | i | `tempOutputFile=X diskFree=X` |
| After FFmpeg exit | i | `FFmpeg exit=X tempOutputFile.length=X diskFree=X` |
| Before SAF copy-out | i | `SAF copy-out start: src=X size=X destUri=X` |
| Every 128 MB tick | d | `SAF copy-out tick: bytesCopied=X diskFree=X` |
| After SAF copy-out | i | `SAF copy-out done: bytesCopied=X diskFreeAfter=X` |

**Files touched (Fix 4):**
- `Merger.kt` — add `Timber.tag("MERGE")` at all phase boundaries (A8 severity);
  replace final `copyTo` with counted loop (already covered by A4)

---

## Files Touched Summary

| File | Fix | New? |
|------|-----|------|
| `JobProgressScreen.kt` | 1 (A1, A2) | No |
| `JobProgressViewModel.kt` | 1 | No |
| `ProcessFfmpegEngine.kt` | 1 | No |
| `JobService.kt` | 1 (A3), 2 (A6) | No |
| `Merger.kt` | 1 (A4), 4 (A8) | No |
| `App.kt` | 2, 3 | No |
| `JobDao.kt` | 2 (A5) | No |
| `di/CoroutineModule.kt` | 2 (A6) | YES |
| `domain/merger/MergeCacheSweeper.kt` | 3 (A7) | YES |
| `MergeCacheSweeperTest.kt` | 3 | YES |

---

## Tests Added / Updated

- `MergeCacheSweeperTest` (JVM) — files older than 60 s deleted; newer kept;
  `mkvslice_db.lck` never deleted (A7)
- `ProcessFfmpegEngineTest` — `cancel("all")` drains `activeSessions`

---

## Migration Notes

- No DB schema changes (no new columns).
- `recoverStuckJobs()` + `recoverStuckParts()` are idempotent.
- Cache sweep cutoff: exactly 60 s.

---

## Rollback Plan

Each fix is isolated:
- Fix 1: Revert `JobProgressScreen` intent + navigation, `ProcessFfmpegEngine.cancel`, `JobService` per-job handle, `Merger` `ensureActive` + counted loops.
- Fix 2: Remove startup sweep from `App.kt`; delete `recoverStuckJobs` + `recoverStuckParts` DAO queries; remove `CompletableDeferred` gate.
- Fix 3: Delete `MergeCacheSweeper.kt`; remove call from `App.kt`.
- Fix 4: Remove `Timber.tag("MERGE")` lines from `Merger.kt`.

---

## Open Questions

None. The 99% failure root cause is deliberately deferred; diagnostic data must
land first before any logic fix is attempted.
