# Step 8 — Update Checker Plan

## Problem
Expose an update flow where the user can tap "Check for updates" in Settings. If a newer release exists, download and install it safely with security validation.

## Proposed change
1. Add `UpdateManifest` serializable data class.
2. Add `UpdateService` Retrofit service.
3. Add `UpdateState` and `Phase` definitions.
4. Add `UpdateRepository` singleton to download (chunked 256 KB stream), compute SHA-256 on `.tmp` file, verify matching hash, rename to `.apk`, and commit installation using `PackageInstaller` sessions and `FLAG_IMMUTABLE` PendingIntents.
5. Integrate with `SettingsViewModel` and `SettingsScreen` UI.

## Files touched (paths + intent)
- [NetworkModule.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/di/NetworkModule.kt): Provide `UpdateService` using Retrofit with custom non-coercing Json configurations.
- [SettingsViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/settings/SettingsViewModel.kt): Collect repo update flow state and expose trigger functions.
- [SettingsScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/settings/SettingsScreen.kt): Display progress bars and "Install now" button.

## Tests added/updated
- `UpdateRepositoryTest.kt`: Thorough test suite targeting response parsing, http url rejection, hash formatting, size checking, sha256 mismatch failure, cancellation cleanup, and mock installer execution.

## Migration notes
None.

## Rollback plan
Revert all file additions and edits using git checkout.

## Open questions
None.
