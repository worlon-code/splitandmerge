# Usage Guide for 0.0.14 with Screenshots (Part 1)

## Problem
To support the 0.0.14 punch-list USAGE GUIDE task, we need a reliable way to capture screenshots of each app screen with realistic dummy data.
The Hilt testing harness setup in the instrumentation test environment has legacy ClassCastException issues. We must bypass it by using a plain host `ComponentActivity` under `createAndroidComposeRule<ComponentActivity>()` to render composables directly with hardcoded dummy states and capture their root bounds to a bitmap.

## Proposed change
1. Verify `androidx.compose.ui.test` and `ui-test-manifest` are available in gradle config. (Confirmed available in `app/build.gradle.kts`).
2. Write one instrumented test in a new file `RenameVideosScreenshotTest.kt` under `app/src/androidTest/kotlin/com/splitandmerge/mkvslice/ui/rename/`.
3. The test will launch `ComponentActivity`, render the Rename Videos preview state (using dummy data for `RenameFileRowState` and `CleanupPatternEntity` wrapper inside `VideoSplitterTheme`), capture the root compose node using `captureToImage().asAndroidBitmap()`, and save it to the external files directory.
4. Output directory: `context.getExternalFilesDir("screenshots")` (resolves to `/sdcard/Android/data/com.splitandmerge.mkvslice/files/screenshots/` on the device).
5. Command to pull the screenshot:
   `adb pull /sdcard/Android/data/com.splitandmerge.mkvslice/files/screenshots/rename_preview_dummy.png logs/rename_preview_dummy.png`
   We will verify this pulled PNG is valid and has realistic names (e.g. mix of RENAME, SKIP_COLLISION, NO_CHANGE, etc.) and teal AMOLED aesthetics.

## Files touched (paths + intent)
### [NEW] [RenameVideosScreenshotTest.kt](file:///D:/Repos/splitandmerge/app/src/androidTest/kotlin/com/splitandmerge/mkvslice/ui/rename/RenameVideosScreenshotTest.kt)
- Contains the Compose instrumented test to capture the Rename Videos preview screen state to a PNG file.

## Tests added/updated
- `com.splitandmerge.mkvslice.ui.rename.RenameVideosScreenshotTest`

## Migration notes
None.

## Rollback plan
- Delete the `RenameVideosScreenshotTest.kt` file.

## Open questions
None.
