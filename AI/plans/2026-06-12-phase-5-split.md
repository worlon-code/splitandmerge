# Phase 5 — Engine: Split

## Problem
We need to implement the core video splitting functionality (Phase 5). This requires orchestrating the FFmpeg engines created in Phase 4 to evaluate inputs, plan cuts using `CutPlanner`, execute the cuts, verify the outputs, and generate a JSON manifest for the merged results. It also requires running this heavy workload inside a Foreground Service.

The canonical Phase 3 (Room DB scaffolding) appears to have been skipped. To properly satisfy the queue management requirements (resuming jobs after crash/restart), we must scaffold the basic Room database (`AppDatabase` and `JobDao`) before we can build the `JobService`.

## Proposed change
1. **Domain Models**: Align `Job.kt` and `Part.kt` with the canonical specs in `AI/DATA_MODELS.md`, and introduce the `Manifest` serializable models.
2. **Persistence**: Scaffold the Room `AppDatabase` and `JobDao` to store queued, running, and completed jobs/parts.
3. **Core Engine Logic**:
    - Build `Splitter.kt` to coordinate probing, cut planning, looping over parts, executing the FFmpeg command, and handling file staging/SAF renaming.
    - Build `ManifestWriter.kt` to write the `.split.json` file.
4. **Foreground Service**:
    - Build `JobService.kt` to observe the queue, execute the split job via `Splitter`, post live progress notifications, and handle cancellation.
5. **Testing**:
    - Add JVM unit tests for `SplitterTest.kt`.
    - Add an instrumented `SplitSmokeTest.kt` to execute a real split on a sample 720p file on-device.

## Files touched (paths + intent)
- `[MODIFY] app/src/main/kotlin/com/splitandmerge/mkvslice/domain/model/Job.kt` (align with JobEntity/SplitJob)
- `[MODIFY] app/src/main/kotlin/com/splitandmerge/mkvslice/domain/model/Part.kt` (align with PartEntity/PartRef)
- `[NEW] app/src/main/kotlin/com/splitandmerge/mkvslice/domain/model/Manifest.kt` (JSON serializable models)
- `[NEW] app/src/main/kotlin/com/splitandmerge/mkvslice/data/db/AppDatabase.kt` (Room database)
- `[NEW] app/src/main/kotlin/com/splitandmerge/mkvslice/data/db/JobDao.kt` (Room DAO for queue)
- `[NEW] app/src/main/kotlin/com/splitandmerge/mkvslice/domain/splitter/Splitter.kt` (Core orchestrator)
- `[NEW] app/src/main/kotlin/com/splitandmerge/mkvslice/domain/splitter/ManifestWriter.kt` (JSON writer)
- `[NEW] app/src/main/kotlin/com/splitandmerge/mkvslice/service/JobService.kt` (Foreground Service)

## Tests added/updated
- `[NEW] app/src/test/kotlin/com/splitandmerge/mkvslice/domain/splitter/SplitterTest.kt`
- `[NEW] app/src/androidTest/kotlin/com/splitandmerge/mkvslice/engine/SplitSmokeTest.kt`

## Migration notes
N/A (Scaffolding the initial Room Database, version 1).

## Rollback plan
Revert the commit and drop the new domain/data files.

## Open questions
- Since Phase 3 (Room Database and basic UI navigation scaffold) was partially skipped, I am proposing adding the `AppDatabase` and `JobDao` in this phase to satisfy the Foreground Service queue requirements. Is this acceptable?
