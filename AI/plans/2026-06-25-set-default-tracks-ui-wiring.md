# Set Default Tracks — Phase A1 UI & Pipeline Wiring Implementation Plan

This plan details the implementation of the UI flow and pipeline wiring for the "Set Default Tracks" feature. Phase A0 (the headless EBML default-flag engine) is fully completed and signed off. A1 builds only the UI screens, ViewModel, and background service integration. No new byte-editing logic or language matching will be added.

## User Review Required

> [!IMPORTANT]
> **Step 0 Compile Fixes:** To fix the compile breakage in `androidTest`, we will add missing constructor arguments to `Merger` and `MergeOrderViewModel` instantiations across three test files: `SafLifecycleTest.kt`, `MergerSequentialCleanupTest.kt`, and `ScreenshotTest.kt`.
> 
> **No-Cross-Apply & Manual Batching:** As per A1 requirements, each file carries its own `EditSpec` keyed strictly by its content URI in `DefaultTracksViewModel` and persisted in the `default_track_file_results` table before the worker starts. No language matching or cross-applying is implemented (disabled "Apply to all similar" button).
> 
> **Orphan Journal Recovery:** Surfaced on app launch via a dialog in the Library screen, allowing the user to roll back any interrupted file modifications globally.

## Open Questions

None at this time. All requirements are fully specified.

---

## Proposed Changes

### Step 0 Test Fixes

#### [MODIFY] [SafLifecycleTest.kt](file:///d:/Repos/splitandmerge/app/src/androidTest/kotlin/com/splitandmerge/mkvslice/SafLifecycleTest.kt)
* Add imports for `PartModeDetector` and `TransportMerger`.
* Pass relaxed mockk instances of `PartModeDetector` and `TransportMerger` when instantiating `Merger`.

#### [MODIFY] [MergerSequentialCleanupTest.kt](file:///d:/Repos/splitandmerge/app/src/androidTest/kotlin/com/splitandmerge/mkvslice/domain/merger/MergerSequentialCleanupTest.kt)
* Inject `PartModeDetector` and `TransportMerger` via Hilt `@Inject lateinit var`.
* Pass them to the `Merger` constructor.

#### [MODIFY] [ScreenshotTest.kt](file:///d:/Repos/splitandmerge/app/src/androidTest/kotlin/com/splitandmerge/mkvslice/ui/ScreenshotTest.kt)
* Add imports for `PartModeDetector` and `PreFlightEvaluator`.
* Declare and instantiate relaxed mockk instances of `PartModeDetector` and `PreFlightEvaluator`.
* Pass them when instantiating `MergeOrderViewModel`.

---

### Home Screen Integration & Route Setup

#### [MODIFY] [Routes.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/nav/Routes.kt)
* Add route constant `Routes.DEFAULT_TRACKS_FLOW = "default_tracks_flow"`.

#### [MODIFY] [AppNav.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/nav/AppNav.kt)
* Wire `Routes.DEFAULT_TRACKS_FLOW` to `DefaultTracksFlowScreen`.

#### [MODIFY] [LibraryScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/library/LibraryScreen.kt)
* Add a third `FloatingActionButton` tile labeled "Set defaults" with a flag icon in the FAB column next to Split and Merge.
* Wire onClick to navigate to `Routes.DEFAULT_TRACKS_FLOW`.
* Add orphan journal recovery dialog popup when the ViewModel reports orphan journals.

#### [MODIFY] [LibraryScreenTablet.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/library/LibraryScreenTablet.kt)
* Add the third `FloatingActionButton` in the FAB column.
* Add a third `IconButton` with a flag icon in the header next to Split and Merge.
* Wire onClick to navigate to `Routes.DEFAULT_TRACKS_FLOW`.

#### [MODIFY] [LibraryViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/library/LibraryViewModel.kt)
* Inject `defaultTrackFileResultDao` and `FileSystem`.
* On startup, scan the app cache folder for orphan journal files (`defaulttracks_*.journal`).
* Resolve their display name and original URI using `defaultTrackFileResultDao`.
* Expose `orphanJournals` state flow and helper functions to confirm rollback (`performRollback(orphan)`) or dismiss (`dismissOrphanDialog(orphan)`).
* Rollback calls A0's `journal.rollback(fd)` with a read-write file descriptor.

---

### Foreground Service Dispatch

#### [MODIFY] [JobService.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/service/JobService.kt)
* Inject `DefaultTracksEngine` and `AppDatabase`.
* Add branch for `JobType.SET_DEFAULT_TRACKS` in `startProcessing` loop.
* Implement private method `runDefaultTracksJob(jobId: String)`:
  * Read `default_track_file_results` for the job.
  * For each pending result, call `defaultTracksEngine.processFile(row.uri, spec, jobId, index)`.
  * Update database result with engine status, reason, and write strategy.
  * Update notification text and title dynamically ("Setting Default Tracks") based on the current running job type.
  * Respect cancellation signal: if cancelled, update all remaining pending rows to `SKIPPED(reason="canceled")` inside a `NonCancellable` coroutine context.
  * Idempotency: skip files already terminal (non-`PENDING`).

---

### New Flow UI & State Machine

#### [NEW] [DefaultTracksViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/defaulttracks/DefaultTracksViewModel.kt)
* State machine: `Idle` -> `Picking` -> `Scanning` -> `Analyzing` -> `ReadyList` -> `Applying` -> `Results`.
* Uses `SavedStateHandle` to preserve confirmed `EditSpec` map (`Map<String, EditSpec>`) across process death.
* Scanning phase: Scans folders recursively on `Dispatchers.IO` using a content resolver child document query. Hard guard: depth capped at 5 and total files capped at 1000. Detects EBML magic `1A 45 DF A3` via raw 4-byte read from SAF InputStream. Marks non-MKV files as `SKIPPED(reason="not-mkv")`.
* Analysis phase: Runs `TrackAnalyser().analyse(fd)` off the main thread, updating the UI list with audio and subtitle tracks.
* "Apply" action: Inserts a single `JobEntity` of type `SET_DEFAULT_TRACKS`, pre-writes `DefaultTrackFileResultEntity` rows with `PENDING` status and serialized JSON `appliedSpecJson` representation of specs, and launches `JobService`.

#### [NEW] [DefaultTracksFlowScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/defaulttracks/DefaultTracksFlowScreen.kt)
* Hosts start, scanning, file list, track editor (both phone and tablet pane layouts), applying, and results screens inside a unified Composable switching on UI state.
* `FileListBatchScreen`: Lists files with checkboxes, language chips, status pills, and "Apply (N)" button. Includes disabled "Apply to all similar" button.
* `TrackEditorScreen`: Shows current default audio and subtitle flags vs chosen ones, radio groups for default audio and default subtitle (with "None"), and a "Forced" switch.
* `BatchTrackEditorTabletScreen`: Unified two-pane layout for tablets (list on the left, editor on the right).
* `ChangesCompleteScreen`: Grouped summary counts by status/reason, including non-MKV skip count and truncation banners.

---

## Verification Plan

### Automated Tests
1. Run `./gradlew lintDebug` to verify no lint errors are introduced.
2. Run `./gradlew testDebugUnitTest` to run VM unit tests covering:
   - State-machine transitions, scanner caps, non-MKV checks, and cancellation.
   - Core correctness: `testNoCrossApply` asserts that each file's edits are applied using its own specific `EditSpec` and never cross-applied.
3. Run `./gradlew connectedDebugAndroidTest` to execute Compose UI tests verifying:
   - Reachability of each screen.
   - Editor controls, radio groups, and tablet layout interactions.
   - `Migration_5_6Test` remains green.

### Manual Verification
1. Pick a single MKV file, verify it parses and updates correctly.
2. Pick a folder with nested directories, verify scanning count and magic pre-filters.
3. Perform batch edit on two files with different choices, verify both files are patched independently with their own target default flags.
4. Interrupt middle of apply job, verify canceled files are correctly marked as canceled in the results database.
