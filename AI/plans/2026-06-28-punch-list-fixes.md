# 2026-06-28-punch-list-fixes.md

## Problem
A list of polish items (0.0.14 Punch-List) needs to be addressed:
1. PART A — Back navigation on the Rename Videos screen exits to Settings instead of returning to the Idle state.
2. PART B — Theme Mode is currently a dummy selector. It must be persisted and applied correctly.
3. PART C — Settings Audit. Verify and list which options take effect. Fix "Keep screen on during job".
4. PART D — Verify updater sanity.

## Proposed change

### PART A — Back Navigation
- Add Compose `BackHandler` inside `RenameVideosScreen` and `RenameVideosScreenTablet` so that if state is not Idle, back button cancels to Idle instead of exiting the screen.
- Reset the inline-create form state in `cancelToIdle()` in the view model.

### PART B — Theme Mode
- Persist a theme enum `{ SYSTEM, LIGHT, DARK }` in Settings DataStore.
- Wire theme mode dropdown.
- Observe and apply the selected theme in the app root:
  - `SYSTEM` -> `isSystemInDarkTheme()`
  - `LIGHT` -> light scheme
  - `DARK` -> AMOLED dark scheme.
- **GATE**: Since no Kinetic Logic teal/AMOLED-black color schemes exist in the Kotlin codebase (it only has the standard template purple colors), we stop for user feedback before inventing or changing color schemas.

### PART C — Settings Audit
- Report on all Settings options:
  - **Theme Mode**: Persists, doesn't take effect (fixing in Part B).
  - **Default size threshold**: Persists, doesn't take effect (no-op in SplitConfigViewModel).
  - **Default output folder**: Persists, takes effect.
  - **Safe-mode copying**: Persists, takes effect.
  - **Keep screen on**: Persists, doesn't take effect.
  - **Ignore battery optimizations**: Checked dynamically, takes effect.
  - **Title Cleanup Patterns nav**: Navigates, takes effect.
  - **View logs nav**: Navigates, takes effect.
  - **Open-source notices nav**: Navigates, takes effect.
  - **Update checker**: Persists/Exempts, takes effect.
- **Fix "Keep screen on during job"**:
  - In `MainActivity.kt`, observe running jobs count from `JobDao` and if `keepScreenOn` setting is active, apply/clear `FLAG_KEEP_SCREEN_ON` on the window.
  - In `RenameVideosScreen` / `RenameVideosScreenTablet`, apply the window keep screen on flag when `keepScreenOn` is enabled and `uiState` is `Processing`.

### PART D — Updater Sanity
- Verify that `data/update/**` is untouched vs `v0.0.13` and compiles perfectly.

## Files touched
- `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/rename/RenameVideosScreen.kt`
- `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/rename/RenameVideosScreenTablet.kt`
- `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/rename/RenameVideosViewModel.kt`
- `app/src/main/kotlin/com/splitandmerge/mkvslice/MainActivity.kt`
- `app/src/main/kotlin/com/splitandmerge/mkvslice/theme/Theme.kt`
- `app/src/main/kotlin/com/splitandmerge/mkvslice/theme/Color.kt`
- `app/src/main/kotlin/com/splitandmerge/mkvslice/data/settings/SettingsState.kt`
- `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/settings/SettingsScreen.kt`

## Tests added/updated
- Unit tests to cover the theme mapping and settings state changes.

## Migration notes
- Settings DataStore enum mapping values.

## Rollback plan
- Revert changes via git.

## Open questions
- Confirmation on Teal / AMOLED-black color scheme values to insert into `Color.kt` and `Theme.kt`, since the repository currently only contains standard purple template schemes.
