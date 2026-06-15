# Step 5: Wire Loading Components

This plan outlines the changes required to wire `LoadingArc`, `PulseDot`, and `ShimmerSkeleton` into the Library and SplitConfig screens.

## Problem
Currently, the `LoadingArc`, `PulseDot`, and `ShimmerSkeleton` components (created in Step 1) are not fully integrated into the remaining screens:
1. The Library screen shows a blank empty state during initial database load, instead of a shimmer placeholder.
2. The Library screen's running jobs do not have an active pulsing dot indicating progress.
3. The SplitConfig screen does not display a loading spinner during initial file duration/keyframe probing.

## Proposed Change

### 1. Library Screen Shimmer & PulseDot
- Add `LibraryState` data class:
  ```kotlin
  data class LibraryState(
      val jobs: List<Job> = emptyList(),
      val isInitialLoad: Boolean = true
  )
  ```
- Expose `state: StateFlow<LibraryState>` in `LibraryViewModel` that collects from `JobDao.observeAll()` and flips `isInitialLoad` to `false` upon the first emission.
- In `LibraryScreen.kt`, check `state.isInitialLoad`. If true, render 3 shimmer placeholder rows mimicking the `JobItemRow` layout using `Modifier.shimmer()`.
- If a job has status `JobStatus.RUNNING`, display `PulseDot` next to the status chip in the row layout.

### 2. SplitConfig Screen LoadingArc
- Add `analyzing: Boolean` and `error: String?` to `SplitConfigState`.
- Inject `FfprobeEngine` in `SplitConfigViewModel` and launch `ffprobeEngine.probe(uri)` on init.
- Set `analyzing = true` on start, and `analyzing = false` once complete (or `error` set on failure).
- In `SplitConfigScreen.kt`, show `LoadingArc` with the label "Analyzing source..." in place of the cap-size slider area when `analyzing == true`.
- Disable the "Continue" button at the bottom while `analyzing` is true.

### 3. MergeConfig Screen
- No changes needed because `MergeConfigViewModel` does not run any additional probe logic.

---

## Files touched
- [LibraryViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/library/LibraryViewModel.kt)
- [LibraryScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/library/LibraryScreen.kt)
- [SplitConfigViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/splitconfig/SplitConfigViewModel.kt)
- [SplitConfigScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/splitconfig/SplitConfigScreen.kt)
- [SplitConfigScreenTablet.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/splitconfig/SplitConfigScreenTablet.kt)
- [LibraryViewModelTest.kt](file:///d:/Repos/splitandmerge/app/src/test/kotlin/com/splitandmerge/mkvslice/ui/LibraryViewModelTest.kt)
- [SplitConfigViewModelTest.kt](file:///d:/Repos/splitandmerge/app/src/test/kotlin/com/splitandmerge/mkvslice/ui/splitconfig/SplitConfigViewModelTest.kt)

## Tests added/updated

### LibraryViewModelTest:
- `test_init_isInitialLoadTrue`
- `test_afterFirstEmit_isInitialLoadFalse`
- `test_emptyList_isInitialLoadFalse_emptyState`

### SplitConfigViewModelTest:
- `test_init_analyzingTrue`
- `test_probeReturns_analyzingFalse`
- `test_probeThrows_analyzingFalse_errorSet`

## Migration notes
None.

## Rollback plan
Revert the additions of `LibraryState`, Hilt dependency parameters, state flow maps, and conditional loading layout logic from UI views.
