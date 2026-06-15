# Screens

> One row per screen in v1. Cross-references: design pack
> [`Design/`](../Design/), brief
> [`STITCH-BRIEF.md`](../STITCH-BRIEF.md), test inventory
> [`TESTING.md`](TESTING.md).

The app uses 17 screens + 3 modal dialogs. Most reuse the same Stitch
"Kinetic Logic" theme.

## 1. Inventory

| ID | Screen | Compose function | ViewModel | Stitch design folders |
|---|---|---|---|---|
| S1 | Onboarding | `OnboardingScreen` | `OnboardingViewModel` | `onboarding_light`, `onboarding_dark` |
| S2 | Library / Home (phone) | `LibraryScreen` | `LibraryViewModel` | `library_light`, `library_dark` |
| S2 | Library tablet | `LibraryScreenTablet` | same | `library_tablet_light`, `library_tablet_dark` |
| S3 | File Picker (SAF) | system | — | n/a |
| S4 | File Details | `FileDetailsScreen` | `FileDetailsViewModel` | `file_details_light`, `file_details_dark` |
| S5 | Split Configuration (phone) | `SplitConfigScreen` | `SplitConfigViewModel` | `split_configuration_light`, `split_configuration_dark` |
| S5 | Split Configuration tablet | `SplitConfigScreenTablet` | same | `split_configuration_tablet_light`, `split_configuration_tablet_dark` |
| S6 | Split Confirmation | `SplitConfirmScreen` | (derived state) | `split_confirmation_light`, `split_confirmation_dark` |
| S7 | Split Progress | `JobProgressScreen` | `JobProgressViewModel` | `split_progress_light`, `split_progress_dark` |
| S8 | Split Complete | `SplitResultScreen` | `SplitResultViewModel` | `split_complete_light`, `split_complete_dark` |
| S9 | Merge — Pick first part (SAF) | system | — | n/a |
| S10 | Merge — Order parts | `MergeOrderScreen` | `MergeOrderViewModel` | `merge_order_parts_light_2` *(canonical)*, `merge_order_parts_dark`; `merge_order_parts_light_1` kept as alternate reference |
| S11 | Merge Configuration | `MergeConfigScreen` | `MergeConfigViewModel` | `merge_configuration_light`, `merge_configuration_dark` |
| S12 | Merge Progress | reuses `JobProgressScreen` with `Title="Merging"` | `JobProgressViewModel` | reuses split progress |
| S13 | Merge Complete | `MergeResultScreen` | `MergeResultViewModel` | `merge_complete_light`, `merge_complete_dark` |
| S14 | Jobs / Queue (phone) | `JobsScreen` | reuses `LibraryViewModel` | `jobs_history_light`, `jobs_history_dark` |
| S14 | Jobs tablet | `JobsScreenTablet` | same | `jobs_history_tablet_light`, `jobs_history_tablet_dark` |
| S15 | Settings | `SettingsScreen` | `SettingsViewModel` | `settings_light`, `settings_dark` |
| S15a | Title Cleanup Patterns (phone) | `CleanupPatternsScreen` | `CleanupPatternsViewModel` | `cleanup_patterns_light`, `cleanup_patterns_dark` |
| S15a | Title Cleanup Patterns tablet | `CleanupPatternsScreenTablet` | same | `cleanup_patterns_tablet_light`, `cleanup_patterns_tablet_dark` |
| S16 | OSS Notices | `OssNoticesScreen` | `OssNoticesViewModel` | `oss_notices_light`, `oss_notices_dark` |
| D1 | Cleanup preview sheet | `CleanupPreviewSheet` | (derived) | inside `dialogs_sheet_*` |
| D2 | Folder collision sheet | `FolderCollisionSheet` | (derived) | inside `dialogs_sheet_*` |
| D3 | Container promotion sheet | `ContainerPromotionSheet` | (derived) | inside `dialogs_sheet_*` |
| D4 | Job details sheet | `JobDetailSheet` | (derived) | inside `dialogs_sheet_*` |

## 2. Bottom navigation

Every primary screen on phone hosts a 4-destination bottom nav:

| Slot | Icon | Route |
|---|---|---|
| Split | `content_cut` | `nav/split` (Library when no current job) |
| Merge | `merge_type` | `nav/merge` |
| Queue | `list_alt` | `nav/queue` (S14) |
| Settings | `settings` | `nav/settings` (S15) |

Tablet variants replace this with a left **navigation rail** (80 dp wide).

## 3. Per-screen details

The numbering matches the Stitch brief and the test inventory. Each row has
the same five fields:

```
Purpose
Inputs (from previous screen / SAF)
Outputs (to next screen)
Composables used
Notes / edge cases
```

### S1 — Onboarding

- **Purpose:** First-launch only. Brand the app + take SAF folder grant.
- **Inputs:** none.
- **Outputs:** `defaultOutputDirUri` saved to `SettingsStore`. Navigates to S2.
- **Composables:** centred icon halo, headline, sub-headline, outlined info
  card, primary filled button, text "Skip for now".
- **Notes:** Show only when `onboardingDone == false`. After skip, the user
  is prompted again at the first split.

### S2 — Library / Home

- **Purpose:** Daily entry; recent jobs + Split / Merge entry buttons.
- **Inputs:** `JobsRepository.observeAll()`.
- **Outputs:** taps a row → S7/S8 (running/complete) or S14 detail; +Split →
  S3 SAF picker; +Merge → S9 SAF picker.
- **Composables:** `CenterAlignedTopAppBar`, two tonal buttons row, section
  header "Recent jobs", `LazyColumn` of `JobRow`, extended FAB "New job".
- **Notes:** Empty state shows the two buttons + a card "No jobs yet".

### S4 — File Details

- **Purpose:** Show probed metadata + button to configure split.
- **Inputs:** SAF `content://` URI.
- **Outputs:** Navigates to S5 with the same URI.
- **Composables:** `MediumTopAppBar` (collapsing) with hero, four
  `ElevatedCard`s (Video / Audio / Subtitles / Container), sticky bottom bar.
- **Notes:** All cards collapsible. The hero shows codec icon + chips strip.
  Bottom bar's left text is the **original** filename, ellipsised.

### S5 — Split Configuration

- **Purpose:** Configure title, mode, parts, cap, output folder.
- **Inputs:** SAF URI from S4.
- **Outputs:** `SplitJob` to S6.
- **Composables:** `CenterAlignedTopAppBar`, four cards (Title / Mode /
  Output / Summary), sticky bottom bar with Cancel + Continue.
- **Notes:** Live recompute on every change. Container locked to `.mkv` when
  bitmap subs present. "Edit cleanup rules" navigates to S15a.

### S6 — Split Confirmation

- **Purpose:** Last sanity check before kicking off the engine.
- **Inputs:** `SplitJob`.
- **Outputs:** Enqueues the job, navigates to S7.
- **Composables:** `CenterAlignedTopAppBar` with `close`, single
  `ElevatedCard` with check icon, headline, body, two-row table, Back +
  Start row.
- **Notes:** Triggers D1 (cleanup preview) and D2 (folder collision) before
  entering this screen if the user hasn't disabled them.

### S7 — Split Progress

- **Purpose:** Live progress bound to the foreground service.
- **Inputs:** `jobId`.
- **Outputs:** when status flips to DONE → S8; FAILED → error sheet.
- **Composables:** big circular progress with percent, three stat tiles
  (Speed / ETA / Written), parts list, sticky Cancel.
- **Notes:** Reused by S12 with title "Merging".

### S8 — Split Complete

- **Purpose:** Result view + actions.
- **Inputs:** `jobId` (DONE).
- **Outputs:** "Make merge job from these" → S10 with parts pre-populated.
- **Composables:** centered check halo, headline, three stat tiles, parts
  list, sticky Open folder + Make merge job.
- **Notes:** Each row has open / share / more actions.

### S10 — Merge — Order parts

- **Purpose:** User orders parts (when no manifest present).
- **Inputs:** SAF URIs of parts (from S9 picker, or pre-loaded from S8).
- **Outputs:** ordered `List<PartRef>` to S11.
- **Composables:** info banner, reorderable `LazyColumn` with drag handles
  + validity icons, "Add another part" outlined button, summary card,
  sticky Cancel + Continue.
- **Notes:** When a sibling `*.split.json` exists, skip this screen and go
  to S11 with all parts pre-ordered.

### S11 — Merge Configuration

- **Purpose:** Output filename + folder.
- **Inputs:** ordered parts from S10 or manifest.
- **Outputs:** `MergeJob` to S12.
- **Composables:** three cards (Source / Output / Summary), sticky Cancel +
  Start merge.

### S13 — Merge Complete

- **Purpose:** Show the merged file + streams preserved.
- **Inputs:** `jobId` (DONE).
- **Outputs:** Open file, Share, Delete source parts.
- **Composables:** centered check halo, headline, three stat tiles, two-
  column "Streams preserved" table, sticky Open + Delete.

### S14 — Jobs / Queue

- **Purpose:** Full history with status filter.
- **Inputs:** `JobsRepository.observeAll()`.
- **Outputs:** tap → detail bottom sheet.
- **Composables:** `CenterAlignedTopAppBar`, filter chips row, swipeable
  `ListItem` rows.
- **Notes:** Tablet variant uses 3-pane (rail + list + detail).

### S15 — Settings

- **Purpose:** Theme, defaults, cleanup link, reliability, updates, about.
- **Inputs:** `SettingsStore`, `UpdateService`.
- **Outputs:** various sheets / sub-screens.
- **Composables:** sectioned list, inline update card.
- **Notes:** "Improve reliability on this device" calls
  `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

### S15a — Title Cleanup Patterns

- **Purpose:** Manage regex rules. Live preview against a sample.
- **Inputs:** `CleanupRepository`, `CleanupEngine`.
- **Outputs:** persisted to `cleanup_patterns` table.
- **Composables:** sticky preview card, "Built-in patterns" section,
  "Custom patterns" section, "Add custom pattern" outlined button, sticky
  Discard + Save.
- **Notes:** Tablet variant adds a **trace panel** showing each rule's
  before/after.

### S16 — OSS Notices

- **Purpose:** Bundled licence list.
- **Inputs:** `app/src/main/assets/oss-licenses.json` (generated at build).
- **Outputs:** none.
- **Composables:** grouped list with expandable rows.

### D1 — Cleanup preview

- Triggered between S5 and S6.
- Title: "Title cleanup preview".
- Body: 4 rows (Original / Cleaned / Subfolder / Part name).
- Buttons: Edit title / Cancel / Use as-is.
- Hidden when `show_cleanup_preview = false` in settings.

### D2 — Folder collision

- Triggered when subfolder already exists in the chosen output dir.
- Title: "Folder already exists".
- Body: path of the colliding folder.
- Three tappable rows: Use existing / Add a suffix / Cancel.

### D3 — Container promotion

- Triggered when input is MP4/AVI/MOV/TS and has bitmap subs.
- Title: "Output will be MKV".
- Body: explanation.
- Buttons: Continue with original (drops bitmap subs) / OK.
- Suppressible via `match_input_container = true` (default).

### D4 — Job details sheet

- Triggered when tapping a CANCELLED or FAILED job row on S2 Library.
- Title: original/cleaned filename + status badge.
- Body: cancellation or failure timestamp, and failure error logs inside a tonal expandable container.
- Buttons: Retry (spawns new job, replicating merge parts) / Delete row.
- Constraints: width capped at 600dp on tablets.

## 4. Per-screen sample data

All screens use the sample data from
[STITCH-BRIEF.md §2](../STITCH-BRIEF.md). The five canonical movies are:

```
Baahubali The Epic (2025)         47.2 GB   2:13:12
Kantara Chapter 1 (2024)          38.6 GB   2:31:30
Karuppu (2026)                    22.9 GB   2:31:30
Devara (2024)                     28.4 GB   2:55:08
Salaar (2023)                     51.3 GB   2:55:00
```

These are **not** real files in the repo — they are display strings only.
Real test fixtures are 60-second 720p HEVC clips
([TESTING.md §6](TESTING.md)).

## 5. Status of design pack

| ID | Light | Dark | Tablet L | Tablet D |
|---|---|---|---|---|
| S1 | ✅ | ✅ | n/a | n/a |
| S2 | ✅ | ✅ | ✅ | ✅ |
| S4 | ✅ | ✅ | n/a | n/a |
| S5 | ✅ | ✅ | ✅ | ✅ |
| S6 | ✅ | ✅ | n/a | n/a |
| S7 | ✅ | ✅ | n/a | n/a |
| S8 | ✅ | ✅ | n/a | n/a |
| S10 | ✅ (`_2` canonical; `_1` alternate) | ✅ | n/a | n/a |
| S11 | ✅ | ✅ | n/a | n/a |
| S13 | ✅ | ✅ | n/a | n/a |
| S14 | ✅ | ✅ | ✅ | ✅ |
| S15 | ✅ | ✅ | n/a | n/a |
| S15a | ✅ | ✅ | ✅ | ✅ |
| S16 | ✅ | ✅ | n/a | n/a |
| Dialogs | ✅ | ✅ | n/a | n/a |

S10 now has two light exports. Use `merge_order_parts_light_2` as the
canonical implementation reference and keep `_1` only as an alternate design
exploration.

## 6. Translation rule for the agent

The Compose code is **your translation** of the Stitch designs. You may NOT
copy raw HTML. The Stitch outputs are **visual reference only**. Source of
truth for layout intent is the prompt block in
[STITCH-BRIEF.md §3](../STITCH-BRIEF.md) — when in doubt, follow the prompt
block, not the rendered design.
