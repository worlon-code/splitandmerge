# Phase 3: Compose UI + Navigation + ViewModels with Mock Data

## Problem
In Phase 3, we must construct the entire user interface and navigation flow of the Video Splitter (MKV Slice) app. This includes implementing all 17 screens (and their tablet variants) and 3 modal sheets identified in `AI/DESIGN_INDEX.md` using Jetpack Compose, Material 3, and Hilt. The UI should run on mock data before wiring the backend engine.

## Proposed change
1. Define type-safe string routes in `ui/nav/Routes.kt`.
2. Implement `ui/nav/AppNav.kt` hosting the `NavHost` containing all destination routes.
3. Build the core ViewModel structures under `ui/` with Hilt annotations, exposing mock state through `StateFlow`.
4. Translate each screen from the `Design/` Stitch HTML files to Material 3 Jetpack Compose screens, ensuring visual parity with the design specs.
5. Implement the tablet-specific split-pane variants for S2 (Library), S5 (Split Config), S14 (Jobs History), and S15a (Cleanup Patterns).
6. Implement the 3 sheet dialogs (Cleanup Preview, Folder Collision, Container Promotion) in the `ui/dialogs/` package.
7. Write basic Compose UI tests for all screens to ensure they load and are navigable under the test suite.
8. Wire the Mock screens to `MainActivity.kt` so the app launches directly into the flow and is fully clickable.

## Files touched (paths + intent)
- [NEW] [Routes.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/nav/Routes.kt): Navigation route constants.
- [NEW] [AppNav.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/nav/AppNav.kt): NavHost container wiring.
- [NEW] [LibraryScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/library/LibraryScreen.kt): Home screen.
- [NEW] [LibraryScreenTablet.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/library/LibraryScreenTablet.kt): Dual-pane home screen for tablets.
- [NEW] [LibraryViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/library/LibraryViewModel.kt): ViewModel for Library/Home.
- [NEW] [FileDetailsScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/filedetails/FileDetailsScreen.kt): Media stream probe details screen.
- [NEW] [FileDetailsViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/filedetails/FileDetailsViewModel.kt): ViewModel for file details.
- [NEW] [SplitConfigScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/splitconfig/SplitConfigScreen.kt): Configuration screen.
- [NEW] [SplitConfigScreenTablet.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/splitconfig/SplitConfigScreenTablet.kt): Configuration screen for tablets.
- [NEW] [SplitConfigViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/splitconfig/SplitConfigViewModel.kt): ViewModel for split config.
- [NEW] [SplitConfirmScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/splitconfirm/SplitConfirmScreen.kt): Pre-flight confirmation.
- [NEW] [JobProgressScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/progress/JobProgressScreen.kt): Progress indicators.
- [NEW] [JobProgressViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/progress/JobProgressViewModel.kt): ViewModel for split and merge progress.
- [NEW] [SplitResultScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/result/SplitResultScreen.kt): Results list.
- [NEW] [MergeOrderScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/mergeorder/MergeOrderScreen.kt): Reorderable merge list.
- [NEW] [MergeOrderViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/mergeorder/MergeOrderViewModel.kt): ViewModel for ordering merge parts.
- [NEW] [MergeConfigScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/mergeconfig/MergeConfigScreen.kt): Destination path select.
- [NEW] [MergeResultScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/result/MergeResultScreen.kt): Merge completion screen.
- [NEW] [JobsScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/jobs/JobsScreen.kt): Historical queue screen.
- [NEW] [JobsScreenTablet.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/jobs/JobsScreenTablet.kt): Dual-pane history list/detail screen.
- [NEW] [JobsViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/jobs/JobsViewModel.kt): ViewModel for queue history.
- [NEW] [SettingsScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/settings/SettingsScreen.kt): Settings configuration.
- [NEW] [SettingsViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/settings/SettingsViewModel.kt): ViewModel for settings state.
- [NEW] [CleanupPatternsScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/cleanup/CleanupPatternsScreen.kt): Title cleanup editor.
- [NEW] [CleanupPatternsScreenTablet.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/cleanup/CleanupPatternsScreenTablet.kt): Title cleanup editor for tablets.
- [NEW] [OssNoticesScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/oss/OssNoticesScreen.kt): Open-source license credits.
- [NEW] [OnboardingScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/onboarding/OnboardingScreen.kt): Onboarding screen.
- [NEW] [CleanupPreviewSheet.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/dialogs/CleanupPreviewSheet.kt): Modal bottom sheet for filename previews.
- [NEW] [FolderCollisionSheet.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/dialogs/FolderCollisionSheet.kt): Directory collision selector.
- [NEW] [ContainerPromotionSheet.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/dialogs/ContainerPromotionSheet.kt): Subtitle containment warning popup.
- [MODIFY] [MainActivity.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/MainActivity.kt): Set up `AppNav` content.

## Tests added/updated
- Compose UI loading/navigation tests for each of the screens (under `app/src/androidTest`).

## Migration notes
None.

## Rollback plan
Revert all files under `ui/` folder and restore `MainActivity.kt`.

## Open questions
- None.
