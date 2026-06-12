# Design Index

> Canonical Phase 2 mapping for the imported Stitch exports. This file is the
> approved replacement for `Design/INDEX.md` because `Design/` is read-only in
> this repository. Use this document together with
> [`SCREENS.md`](./SCREENS.md) and
> [`analysis/09-PROJECT-STRUCTURE.md`](../analysis/09-PROJECT-STRUCTURE.md)
> when scaffolding Compose screens in Phase 3.

## 1. Scope

- `Design/` contains the imported Stitch assets plus raw archive extracts.
- The canonical source of truth for implementation is the normalized
  top-level design folders listed below, not the repeated
  `stitch_video_splitter_design_brief (...)` extracts and not the zip files.
- The shared visual system lives in
  [`Design/kinetic_logic/DESIGN.md`](../Design/kinetic_logic/DESIGN.md).

## 2. Canonical screen map

| ID | Screen / variant | Canonical Stitch folder(s) | Planned Compose target | Notes |
|---|---|---|---|---|
| S1 | Onboarding | `Design/onboarding_light`, `Design/onboarding_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/onboarding/OnboardingScreen.kt` | Single phone layout; no tablet export. |
| S2 | Library / Home (phone) | `Design/library_light`, `Design/library_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/library/LibraryScreen.kt` | Main entry screen. |
| S2 | Library / Home (tablet) | `Design/library_tablet_light`, `Design/library_tablet_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/library/LibraryScreenTablet.kt` | Two-pane / rail treatment. |
| S3 | File Picker (split) | system UI | SAF launcher inside `LibraryScreen` flow | No Stitch asset required. |
| S4 | File Details | `Design/file_details_light`, `Design/file_details_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/filedetails/FileDetailsScreen.kt` | Metadata cards + bottom CTA. |
| S5 | Split Configuration (phone) | `Design/split_configuration_light`, `Design/split_configuration_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/splitconfig/SplitConfigScreen.kt` | Phone single-column layout. |
| S5 | Split Configuration (tablet) | `Design/split_configuration_tablet_light`, `Design/split_configuration_tablet_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/splitconfig/SplitConfigScreenTablet.kt` | Two-column tablet layout. |
| S6 | Split Confirmation | `Design/split_confirmation_light`, `Design/split_confirmation_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/splitconfirm/SplitConfirmScreen.kt` | Pre-flight confirmation. |
| S7 | Split Progress | `Design/split_progress_light`, `Design/split_progress_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/progress/JobProgressScreen.kt` | Shared progress chrome with merge. |
| S8 | Split Complete | `Design/split_complete_light`, `Design/split_complete_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/result/SplitResultScreen.kt` | Result list + merge shortcut. |
| S9 | Merge - Pick first part | system UI | SAF launcher inside merge flow | No Stitch asset required. |
| S10 | Merge - Order parts | `Design/merge_order_parts_light_2`, `Design/merge_order_parts_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/mergeorder/MergeOrderScreen.kt` | `merge_order_parts_light_2` is the canonical light variant because it includes the error card and enabled primary CTA. `merge_order_parts_light_1` is retained as an alternate exploration, not the implementation source of truth. |
| S11 | Merge Configuration | `Design/merge_configuration_light`, `Design/merge_configuration_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/mergeconfig/MergeConfigScreen.kt` | Output destination + summary. |
| S12 | Merge Progress | reuses `Design/split_progress_light`, `Design/split_progress_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/progress/JobProgressScreen.kt` | Same chrome as S7 with merge copy. |
| S13 | Merge Complete | `Design/merge_complete_light`, `Design/merge_complete_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/result/MergeResultScreen.kt` | Merged-file success state. |
| S14 | Jobs / Queue (phone) | `Design/jobs_history_light`, `Design/jobs_history_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/jobs/JobsScreen.kt` | History list with filters. |
| S14 | Jobs / Queue (tablet) | `Design/jobs_history_tablet_light`, `Design/jobs_history_tablet_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/jobs/JobsScreenTablet.kt` | Rail + list/detail treatment. |
| S15 | Settings | `Design/settings_light`, `Design/settings_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/settings/SettingsScreen.kt` | Defaults, updates, about. |
| S15a | Title Cleanup Patterns (phone) | `Design/cleanup_patterns_light`, `Design/cleanup_patterns_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/cleanup/CleanupPatternsScreen.kt` | Phone list + live preview. |
| S15a | Title Cleanup Patterns (tablet) | `Design/cleanup_patterns_tablet_light`, `Design/cleanup_patterns_tablet_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/cleanup/CleanupPatternsScreenTablet.kt` | Includes trace-panel treatment. |
| S16 | OSS Notices | `Design/oss_notices_light`, `Design/oss_notices_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/oss/OssNoticesScreen.kt` | License list screen. |
| D1 | Cleanup preview sheet | `Design/dialogs_sheet_light`, `Design/dialogs_sheet_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/dialogs/CleanupPreviewSheet.kt` | Shared modal export. |
| D2 | Folder collision sheet | `Design/dialogs_sheet_light`, `Design/dialogs_sheet_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/dialogs/FolderCollisionSheet.kt` | Shared modal export. |
| D3 | Container promotion sheet | `Design/dialogs_sheet_light`, `Design/dialogs_sheet_dark` | `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/dialogs/ContainerPromotionSheet.kt` | Shared modal export. |

## 3. Normalization rules

- Implement from the normalized top-level folders in `Design/`.
- Ignore these raw archive extracts during Phase 3 implementation:
  - `Design/stitch_video_splitter_design_brief (1)/`
  - `Design/stitch_video_splitter_design_brief (2)/`
  - `Design/stitch_video_splitter_design_brief (3)/`
- Ignore these imported zip archives during implementation:
  - `Design/stitch_video_splitter_design_brief.zip`
  - `Design/stitch_video_splitter_design_brief (1).zip`
  - `Design/stitch_video_splitter_design_brief (2).zip`
  - `Design/stitch_video_splitter_design_brief (3).zip`

## 4. Open points carried into Phase 3

- The dark `merge_order_parts_dark` screen is stylistically older than the
  chosen light canonical variant. Treat it as theme guidance, but keep the
  Phase 3 behavior model aligned with `merge_order_parts_light_2`.
- S3 and S9 are system SAF flows. They still need route definitions and tests
  in Phase 3 even though they do not have Stitch screens.
- `Design/` remains read-only. Any future mapping or audit updates belong in
  `AI/` unless the rule is explicitly changed.
