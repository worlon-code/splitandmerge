# Phase 6 Follow-ups — Cancel UI, Merge Result, Multi-phase Progress

**Status: AWAITING APPROVAL**
**Date: 2026-06-13**

---

## Problem

Three follow-up items after the first successful 3-part merge (25.68 GB, 3:44:10):

| ID | Title |
|----|-------|
| A | Cancel UI navigation not driving from DB status flip |
| B | `MergeResultScreen` (S13) shows fully hardcoded Stitch dummy data |
| C | Progress screen shows a fixed 50% during staging and during FFmpeg concat |

Duration drift (3 s over 2 cut points) is ACCEPTED-v0.0.6 — see K-014 in KNOWN_ISSUES.md.

---

## Preliminary Finding — Fix A

`JobProgressScreen.kt` already has the required `LaunchedEffect(state.status)` block
from the previous round (A1 implementation). However the **navigation in `AppNav.kt`**
routes `DONE` to either `mergeResult` or `splitResult` based on a hardcoded `id == "job-2"`
sentinel. This is incorrect for real job IDs.

**Fix A is therefore two parts:**
1. Read the `JobType` from the DB in `JobProgressViewModel` and expose it in `ProgressState`.
2. `AppNav.kt` routes based on `state.jobType == JobType.MERGE` (not a hardcoded ID string).

The `LaunchedEffect` body already calls `onNavigateToResult(jobId)` correctly; only the
caller-side routing in `AppNav` is wrong.

---

## Preliminary Finding — Fix B

`MergeResultScreen.kt` has no ViewModel at all. All values are hardcoded literals:

```kotlin
val outputFileName = "Bahubali (2025)_merged.mkv"
val outputSize = 26.50 * 1024 * 1024 * 1024
val outputDuration = "2h 45m 12s"
```

A new `MergeResultViewModel` must be created from scratch.

---

## Preliminary Finding — Fix C

`JobProgressViewModel.ProgressState` stores a flat `progress: Float` and `speedMbs: Float`.
The screen has no concept of phases. The entire multi-phase model needs to be added to
`Merger.kt`, threaded through the DAO, and surfaced in the VM and screen.

---

## Proposed Changes

---

### Fix A — Cancel UI sync / post-DONE routing

#### [MODIFY] [JobProgressViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/progress/JobProgressViewModel.kt)
- Add `val jobType: JobType = JobType.SPLIT` to `ProgressState`.
- Read `jobEntity.type` in `observeJob()` and copy it into `_state`.

#### [MODIFY] [AppNav.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/nav/AppNav.kt)
- In the `JOB_PROGRESS` composable, collect `progressViewModel.state` and route
  `onNavigateToResult` based on `state.jobType == JobType.MERGE` (not `id == "job-2"`).
- Pattern:
  ```kotlin
  onNavigateToResult = { id ->
      val jobType = progressViewModel.state.value.jobType
      if (jobType == JobType.MERGE) {
          navController.navigate(Routes.mergeResult(id)) { popUpTo(Routes.LIBRARY) { inclusive = false } }
      } else {
          navController.navigate(Routes.splitResult(id)) { popUpTo(Routes.LIBRARY) { inclusive = false } }
      }
  }
  ```

**No changes to `JobProgressScreen.kt`** — the `LaunchedEffect` is already correct.

---

### Fix B — Merge Result screen (S13) wiring

#### Current state of `MergeResultScreen.kt`

```kotlin
// Hardcoded — no ViewModel injected
val outputFileName = "Bahubali (2025)_merged.mkv"
val outputSize = 26.50 * 1024 * 1024 * 1024
val outputDuration = "2h 45m 12s"
```

The screen composable receives only `jobId: String` and `onNavigateHome: () -> Unit`.
No ViewModel is wired. `MergeResultViewModel` does not exist.

#### [NEW] [MergeResultViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/result/MergeResultViewModel.kt)

```kotlin
data class MergeResultUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val cleanedTitle: String = "",          // job.outputBaseName
    val outputFilename: String = "",        // outputBaseName + outputContainer
    val outputSizeBytes: Long = 0L,         // probed via SAF document
    val durationSeconds: Double = 0.0,      // probed from merged file
    val audioTracks: Int = 0,
    val subtitleTracks: Int = 0,
    val chapterCount: Int = 0,
    val attachmentCount: Int = 0,
    val timeTakenMs: Long = 0L,             // job.updatedAt - job.createdAt
)
```

Constructor: `@HiltViewModel`, `SavedStateHandle` (reads `"jobId"`), `JobDao`,
`FfprobeEngine`.

`init` block:
1. `jobDao.getById(jobId)` — gets the completed `JobEntity`.
2. Build the output SAF URI from `job.outputDirUri + job.outputBaseName + job.outputContainer`.
3. Call `ffprobeEngine.probe(outputFileUri)` — reads duration, streams.
4. Read size from `DocumentFile.fromSingleUri(context, uri).length()`.
5. Populate `MergeResultUiState` with real values.

No caching — each visit probes fresh (per user rule: "do NOT cache the final probe").

#### [MODIFY] [MergeResultScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/result/MergeResultScreen.kt)
- Accept `viewModel: MergeResultViewModel` as a parameter.
- Collect `viewModel.uiState`.
- Replace all hardcoded values with `uiState.*`.
- Show a `CircularProgressIndicator` while `isLoading == true`.
- Show an error card if `uiState.error != null`.
- Add stat rows: Audio tracks, Subtitle tracks, Chapters, Attachments, Time taken.
- Keep the existing Play Video / Share File / Back to Library button layout.
- Duration formatted as "Xh Ym Zs" (using a shared `formatDuration(Double)` helper).
- Size formatted as the existing `formatSize(Long)` local function.
- Time taken formatted as minutes + seconds.

#### [MODIFY] [AppNav.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/nav/AppNav.kt)
- In the `MERGE_RESULT` composable: inject `MergeResultViewModel` via `hiltViewModel()`;
  pass it to `MergeResultScreen`.

---

### Fix C — Multi-phase progress

#### [MODIFY] [Merger.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/Merger.kt)

Add to `Merger.kt` (top-level, in `domain.merger` package):

```kotlin
enum class MergePhase { STAGING, CONCAT, COPYING_TO_OUTPUT }

data class MergeProgress(
    val phase: MergePhase,
    val partIndex: Int?,          // only for STAGING; null otherwise
    val totalParts: Int,
    val phaseBytesCopied: Long,
    val phaseBytesTotal: Long,
    val overallPct: Int,          // 0..100 across all phases
    val speedBytesPerSec: Long,
    val etaSeconds: Long,
)
```

Overall percent bands:
- `STAGING`:            0 % → 33 %  — linear on bytes copied / total input bytes
- `CONCAT`:            33 % → 66 %  — linear on `time_ms / total_duration_ms`
- `COPYING_TO_OUTPUT`: 66 % → 100 % — linear on bytes written / `merge_tmp` size

Speed: exponential moving average (alpha = 0.2) over 1-second wall-clock windows.
ETA: `(phaseBytesTotal - phaseBytesCopied) / speedBytesPerSec`.

Emit on two triggers, whichever comes first:
1. Every 1 second (wall-clock).
2. Every 256 MB written in the current phase.

Replace the current `jobDao.updateProgress(...)` calls with `writeProgress(MergeProgress)`:
```kotlin
private suspend fun writeProgress(p: MergeProgress) {
    jobDao.updateProgress(
        id = jobId,
        status = JobStatus.RUNNING,
        pct = p.overallPct,
        speed = p.speedBytesPerSec.toDouble() / (1024 * 1024),  // → MB/s in DB
        eta = p.etaSeconds.toInt(),
        parts = p.totalParts,
        now = System.currentTimeMillis()
    )
}
```

> The `MergeProgress` object is NOT stored in the DB — it's used only to drive DB writes.
> The DB schema does not change.

#### [NEW] [domain/merger/MergeFormatters.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/MergeFormatters.kt)

Pure Kotlin (no Android dependencies), JVM-testable:

```kotlin
object MergeFormatters {
    fun formatSpeed(bytesPerSec: Long): String  // "245.3 MB/s", "1.2 GB/s", etc.
    fun formatEta(seconds: Long): String         // "45s", "2m 3s", "1h 5m"
}
```

Speed tiers: B/s, KB/s, MB/s, GB/s — one decimal place, threshold at 1024.

#### [MODIFY] [JobProgressViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/progress/JobProgressViewModel.kt)

Replace `ProgressState` with a version that exposes phase-aware fields:

```kotlin
data class ProgressState(
    val jobId: String = "",
    val fileName: String = "Loading...",
    val jobType: JobType = JobType.SPLIT,
    val status: JobStatus = JobStatus.QUEUED,
    val pct: Int = 0,               // 0..100 overall
    val phaseLabel: String = "",    // e.g. "Step 1 of 3 · Staging part 2 of 3"
    val speedFormatted: String = "—",
    val etaFormatted: String = "—",
)
```

`phaseLabel` construction (in ViewModel, derived from DB fields):

The DB stores `progressPct` (0..100). Phase is inferred:
- 0–32 → STAGING, `partIndex` estimated as `(pct / 33.0 * totalParts).toInt() + 1`
- 33–65 → CONCAT
- 66–100 → COPYING_TO_OUTPUT

```kotlin
val phase = when {
    pct < 33 -> MergePhase.STAGING
    pct < 66 -> MergePhase.CONCAT
    else     -> MergePhase.COPYING_TO_OUTPUT
}
val phaseLabel = when (phase) {
    MergePhase.STAGING ->
        "Step 1 of 3 · Staging part $partIndex of ${totalParts}"
    MergePhase.CONCAT ->
        "Step 2 of 3 · Merging"
    MergePhase.COPYING_TO_OUTPUT ->
        "Step 3 of 3 · Copying to output"
}
```

`speedFormatted` = `MergeFormatters.formatSpeed(speedBytesPerSec)` from `speedMbs * 1024 * 1024`.
`etaFormatted`   = `MergeFormatters.formatEta(etaSeconds)`.

For SPLIT jobs the phaseLabel is replaced with `"Splitting part $currentPart of $totalParts"`.

#### [MODIFY] [JobProgressScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/progress/JobProgressScreen.kt)

- Add a `Text(state.phaseLabel)` row **above** the circular progress indicator.
- Replace `"${state.speedMbs} MB/s"` with `state.speedFormatted`.
- Replace `formatEta(state.etaSeconds)` with `state.etaFormatted`.
- Remove the local `formatEta()` private function (superseded by `MergeFormatters`).
- Remove the `statusText` `when` block — replaced by `state.phaseLabel` for RUNNING/QUEUED,
  and kept for terminal states (DONE, FAILED, CANCELLED).

---

## Files Touched Summary

| File | Fix | New? |
|------|-----|------|
| `JobProgressViewModel.kt` | A, C | No |
| `AppNav.kt` | A, B | No |
| `MergeResultViewModel.kt` | B | **YES** |
| `MergeResultScreen.kt` | B | No |
| `Merger.kt` | C | No |
| `MergeFormatters.kt` | C | **YES** |
| `JobProgressScreen.kt` | C | No |

---

## Tests Added / Updated

| Test | Fix | Type |
|------|-----|------|
| `MergerProgressTest` | C | JVM — feed fake phase events; assert `overallPct` monotonically increases across phase boundaries |
| `MergeFormattersTest` | C | JVM — speed formatter: B/s, KB/s, MB/s, GB/s tiers; ETA formatter: seconds/minutes/hours |

---

## Migration Notes

- No DB schema changes.
- `speedMbs` field in DB remains MB/s (double). The VM converts back to bytes/s
  for `MergeFormatters.formatSpeed`.
- `JobProgressViewModel` previously exposed `progress: Float` (0..1). Replaced
  with `pct: Int` (0..100) to match `MergeProgress.overallPct` directly. The
  screen multiplies by `/ 100f` where needed for `CircularProgressIndicator`.

---

## Rollback Plan

- Fix A: Revert `AppNav.kt` routing guard; remove `jobType` from `ProgressState`.
- Fix B: Delete `MergeResultViewModel.kt`; revert `MergeResultScreen.kt` to hardcoded values.
- Fix C: Delete `MergeFormatters.kt`; revert `Merger.kt` to old flat progress emit;
  revert `JobProgressViewModel.ProgressState` to previous shape.

---

## Open Questions

None.

---

## Status

```
PHASE: 6
STATUS: blocked-pending-approval
NEXT: user reviews AI/plans/2026-06-13-merge-followups.md and
      AI/plans/2026-06-13-merge-disk-optimization.md (DRAFT)
NEEDS APPROVAL: yes — implementation start for this plan only
```
