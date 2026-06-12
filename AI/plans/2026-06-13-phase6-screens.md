# Phase 6: Screen Implementation (UI)

## Problem
The core domain logic, database, and FFmpeg split engine (Phase 5) are complete and fully verified by end-to-end instrumented tests. We now need to build the actual user interface to allow the user to select MKV files, configure split options, and monitor progress.

## Proposed change
Implement the core UI screens defined in `STITCH-BRIEF.md` and `SCREENS.md`, utilizing the scaffolding built in Phases 3 and 4. We will focus on:

1. **S2: Library / Home Screen (`LibraryScreen`)**
   - Display a list of past/current split jobs from the Room DB (`JobDao`).
   - Floating Action Button (FAB) to initiate a new split.

2. **S4: Probe / Analysis Screen (`ProbeScreen`)**
   - File picker to select an MKV file via SAF (`ACTION_OPEN_DOCUMENT`).
   - Trigger `FfprobeEngine` to parse metadata (duration, resolution, audio/subtitle tracks).
   - Display the results in a bottom sheet / info card.

3. **S5: Config Screen (`ConfigScreen`)**
   - Present split mode options (Size Cap vs. Exact Parts).
   - Allow user to pick an output directory via SAF (`ACTION_OPEN_DOCUMENT_TREE`).
   - "Start Slice" button.

4. **S6: Progress Screen (`ProgressScreen`)**
   - Bind to the `JobDao` flow to show real-time percentage progress of the active split.
   - Show part-by-part completion status.

## Files touched (paths + intent)
- **[NEW] `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/screens/library/LibraryScreen.kt`** - Home screen showing job history.
- **[NEW] `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/screens/library/LibraryViewModel.kt`** - Handles fetching history from DB.
- **[NEW] `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/screens/probe/ProbeScreen.kt`** - Handles file selection and FFprobe metadata display.
- **[NEW] `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/screens/probe/ProbeViewModel.kt`** - Orchestrates probing.
- **[NEW] `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/screens/config/ConfigScreen.kt`** - The split settings UI.
- **[NEW] `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/screens/config/ConfigViewModel.kt`** - Saves job to DB and triggers the `JobService`.
- **[NEW] `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/screens/progress/ProgressScreen.kt`** - Active job tracker.
- **[NEW] `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/screens/progress/ProgressViewModel.kt`** - Observes DB changes for the active job.

## Tests added/updated
- UI preview functions for all screens.
- Basic unit tests for ViewModel state flows.

## Open questions
- None. We will strictly follow the provided Figma/Stitch layouts for styling.

> [!IMPORTANT]
> Once you approve this plan, I will proceed to implement the UI screens so you can start testing massive MKV files via the actual Android interface!
