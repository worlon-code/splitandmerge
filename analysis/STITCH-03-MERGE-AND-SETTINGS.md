# Stitch Prompts — Merge, Settings, Dialogs (S10 → S16, dialogs)

Each section is a standalone prompt. Same wrapper as the split flow file: prepend *"Continue the Video Splitter project. Use the project-level theme. Generate light and dark variants."* before pasting any block.

S9 (Pick first part) and S12 (Merge progress) are not designed separately — S9 uses the system SAF picker, S12 reuses the layout from S7 with title "Merging" and a different bottom bar copy.

---

## S10 — Merge — Order parts

**Generate as:** `mergeorder_light/` and `mergeorder_dark/`

```text
Screen where the user orders the parts for a merge job. Reached when no manifest was found.

Layout:
- CenterAlignedTopAppBar: nav icon "arrow_back", title "Order parts", trailing icon "auto_fix_high" (tooltip "Detect order from filenames").
- Top of body, an info banner (filled tonal, 56dp, radius 12dp, primaryContainer / onPrimaryContainer):
    - Leading icon "info" 20dp.
    - Two-line text: line 1 "Add 2 or more parts in playback order." (titleSmall), line 2 "We'll check that they came from the same source." (bodySmall).
- 16dp gap.
- A reorderable LazyColumn list with 7 sample rows. Each row is an ElevatedCard 72dp tall, radius 16dp, padding 12dp:
    - Leading: a "drag_handle" icon 24dp.
    - Index chip 28dp circular tonal showing "1", "2", … with the order number.
    - Middle (vertical):
        - Line 1: filename "Baahubali The Epic (2025).part001.mkv" (titleSmall monospace, 1 line ellipsis).
        - Line 2: small chips inline — "7.62 GB", "HEVC 4K", "ASS subs". 4dp horizontal gap. Use SuggestionChip-small with 28dp height.
    - Trailing: validity icon. For the example, all 7 parts pass validation (success check). For demonstration, show one row (part 6) with a red error icon and a small subtitle line "Codec mismatch — different resolution".
- Below the list, an OutlinedButton "Add another part" with leading icon "add" — full width minus 16dp.
- Below that, a tonal info card (16dp padding, radius 16dp):
    - Title row icon "task_alt" + "All checks passed" (titleSmall) when valid, OR
    - Title row icon "error" (error color) + "1 issue found" (error color) when at least one row failed. Body text describing the first issue and a text-link "Remove invalid parts" tappable.
- Bottom sticky bar (80dp): outlined "Cancel" left, filled primary "Continue" right (disabled when validation fails).
```

---

## S11 — Merge Configuration

**Generate as:** `mergeconfig_light/` and `mergeconfig_dark/`

```text
Configure the merge output before starting.

Layout:
- CenterAlignedTopAppBar: nav icon "arrow_back", title "Configure merge".
- Body, scroll, 16dp horizontal padding, 16dp vertical gaps.

CARD A — "Source"
- Header row icon "playlist_play" + "Source" (titleMedium).
- Body: bodyMedium "7 parts · 53.0 GB total · all checks passed".
- Below: a text-link "Show parts" that expands to show the 7 filenames in monospace bodySmall.

CARD B — "Output"
- Header row icon "save" + "Output" (titleMedium).
- ListItem rows:
    - "Output filename" — OutlinedTextField with value "Baahubali The Epic (2025).merged" and a non-editable trailing suffix ".mkv".
    - "Destination folder" — secondary "/storage/emulated/0/Movies", trailing icon "folder_open".

CARD C — "Summary" (highlighted, primaryContainer / onPrimaryContainer)
- Big number "1" displayMedium label "merged file".
- Stat tiles row: Size 53.0 GB · Duration 2h 13m · ETA ~6 min.
- Body bodySmall: "Lossless. All 8 audio tracks and 2 subtitle tracks preserved.".

- Bottom sticky bar: Cancel outlined + "Start merge" filled primary (icon "play_arrow").
```

---

## S13 — Merge Complete

**Generate as:** `mergeresult_light/` and `mergeresult_dark/`

```text
Result screen after a successful merge. Mirrors S8 but for a single output file.

Layout:
- CenterAlignedTopAppBar: nav icon "arrow_back", title "Merge complete", trailing icon "more_vert" (overflow: Open file, Share, Delete source parts).
- Body:
    - Centred check icon and tonal circle (same as S8).
    - Headline "Merge complete" (headlineSmall).
    - bodyMedium onSurfaceVariant: "Saved · Baahubali The Epic (2025).merged.mkv". Tap to open file.
    - Three stat tiles row: Size 53.0 GB · Duration 2h 13m · Time taken 5 min 42 s.
    - Section "Streams preserved":
        - Two-column rows:
            - Video      HEVC 3840×2160 · HDR10
            - Audio      8 tracks (Telugu DTS-HD MA, Hindi E-AC3, Tamil/Malayalam/Kannada AAC, …)
            - Subtitles  2 tracks (English ASS, Telugu ASS)
            - Chapters   32
            - Fonts      4 attachments
- Bottom sticky bar: outlined "Open file" + filled primary "Delete source parts" (with destructive tonal hint — show a small inline confirmation dialog stub).
```

---

## S14 — Jobs (history)

**Generate as:** `jobs_light/` and `jobs_dark/`

```text
Full job history list with filters.

Layout:
- CenterAlignedTopAppBar: nav icon "arrow_back", title "Jobs", trailing icon "filter_list" (no-op for the design — filters are below).
- Below the app bar, a horizontally scrollable row of FilterChips (single-select):
    - "All" (selected by default), "Running", "Queued", "Done", "Failed", "Cancelled".
- 8dp gap.
- LazyColumn list of jobs. Each row is a swipeable ListItem (show one row in a "swiped left" state to reveal a destructive "Delete" action 80dp wide):
    - Leading: 40dp tonal circular avatar showing job-type icon — "content_cut" for split, "merge_type" for merge.
    - Middle:
        - Line 1: cleaned title (titleMedium, ellipsis).
        - Line 2: status chip + concise meta (bodySmall onSurfaceVariant), e.g. "Done · 7 parts · 53.0 GB · 2h ago".
    - Trailing: small chevron icon.
- Sample rows (mix of split and merge):
    1. Split · Baahubali The Epic (2025) · Running · part 3 of 7 · 47%
    2. Split · Kantara Chapter 1 (2024) · Queued · waiting on previous job
    3. Merge · Devara (2024).merged · Done · 28.4 GB · today 11:15
    4. Split · Karuppu (2026) · Done · 3 parts · 22.9 GB · yesterday
    5. Split · Salaar (2023) · Failed · insufficient storage · 2 days ago
    6. Split · Devara (2024) · Cancelled · 12 min ago
- When the user taps a row, it would open a bottom sheet detail; for the design, just style the row press state.

Empty state variant:
- Title "No jobs yet" with body "Splits and merges will show up here.".
```

---

## S15 — Settings

**Generate as:** `settings_light/` and `settings_dark/`

```text
Settings screen, sectioned ListItems.

Layout:
- CenterAlignedTopAppBar: nav icon "arrow_back", title "Settings".
- Body, 16dp horizontal padding, 24dp gap between sections.

SECTION "Appearance"
- ListItem "Theme" — secondary "Follow system" — trailing icon "chevron_right". On tap shows a bottom sheet with radio options Light / Dark / AMOLED Black / Follow system.
- ListItem "Dynamic colour" — secondary "Use wallpaper colours" — trailing Switch (on by default on A12+).

SECTION "Defaults"
- ListItem "Size cap" — secondary "9 GB (target), 9.5 GB ceiling" — trailing icon "chevron_right".
- ListItem "Output folder" — secondary "/storage/emulated/0/Movies" — trailing icon "chevron_right".
- ListItem "Match input container" — secondary "Auto-promote to MKV when subs need it" — trailing Switch (on).

SECTION "Title cleanup"
- ListItem "Title cleanup patterns" — secondary "12 built-in, 2 custom" — trailing icon "chevron_right" — opens S15a.
- ListItem "Show preview before splitting" — trailing Switch (on).

SECTION "Reliability"
- ListItem "Improve reliability on this device" — secondary "Off · Recommended for Xiaomi, OnePlus, Huawei, Realme" — trailing Switch.
- ListItem "Keep screen on during progress" — trailing Switch (off by default).

SECTION "Updates"
- A small inline card (OutlinedCard, 12dp padding, radius 12dp) showing:
    - Top row: app icon 32dp + "Video Splitter" + "v0.0.1" (monospace).
    - Body row: "Last checked: 2 hours ago" (bodySmall onSurfaceVariant).
    - Buttons row: filled tonal "Check for updates" leading icon "refresh"; outlined "What's new" trailing icon "open_in_new".

SECTION "About"
- ListItem "Open-source notices" — trailing icon "chevron_right" — opens S16.
- ListItem "Privacy" — secondary "No tracking, no telemetry" — trailing icon "chevron_right" — opens a sheet with a single paragraph.
- ListItem "GitHub repo" — secondary "splitandmerge / mkvslice" — trailing icon "open_in_new".
- Footer text centred: "Made with FFmpeg · Apache-2.0 · Lossless by design." (bodySmall onSurfaceVariant), 32dp top padding.

Do NOT include:
- A "Sign in" / "Account" section.
- "Premium" / "Upgrade" buttons.
- A "Rate this app" section.
```

---

## S15a — Title Cleanup Patterns

**Generate as:** `cleanuppatterns_light/` and `cleanuppatterns_dark/`

```text
Sub-screen of Settings. The user manages the regex rules used to clean filenames into folder + part names.

Layout:
- CenterAlignedTopAppBar: nav icon "arrow_back", title "Title cleanup patterns", trailing icon "restart_alt" (tooltip "Reset to defaults").
- Sticky preview block at top (ElevatedCard 16dp padding, primaryContainer / onPrimaryContainer):
    - OutlinedTextField labeled "Sample filename" with placeholder "Type or paste a filename to test", value pre-filled "www.5MovieRulz.graphics - Kantara.Chapter.1.2024.4K.WEB-DL.x265.mkv".
    - 8dp gap.
    - Two-column block:
        - Left: label "Cleaned title" (labelMedium onSurfaceVariant), value "Kantara Chapter 1 (2024)" (titleMedium monospace).
        - Right: label "Folder", value "Kantara Chapter 1 (2024)/" (titleSmall monospace).
    - Body bodySmall: "Live preview updates as you reorder or edit rules.".

- Section "Built-in patterns"
- LazyColumn rows, each is a ListItem with 16dp padding:
    - Leading: drag handle icon "drag_indicator" 24dp.
    - Middle (vertical):
        - Line 1: label e.g. "Strip resolution tokens" (titleSmall).
        - Line 2: regex preview e.g. "\\b(2160p|1080p|720p|480p|UHD|4K)\\b" (bodySmall monospace, ellipsis).
    - Trailing: Switch enabled/disabled (built-in rules cannot be deleted, only toggled).
- Sample built-in rules (in order):
    1. Strip leading URL prefix
    2. Strip resolution tokens
    3. Strip codec tokens (HEVC, x265…)
    4. Strip audio tokens (DTS-HD MA, DDP5.1…)
    5. Strip source tokens (WEB-DL, BluRay…)
    6. Strip HDR tokens (HDR10, DV…)
    7. Strip "DUAL", "MULTI" markers
    8. Strip release-group trailing tokens (alphanumeric)
    9. Replace dots with spaces (preserve year parens)
    10. Wrap year in parentheses
    11. Collapse whitespace
    12. Trim trailing punctuation

- Section "Custom patterns" with subtitle "Applied after built-ins. Drag to reorder."
- Sample 2 custom rows:
    - "Strip 5MovieRulz domain variants" — regex "5MovieRulz\\.\\w+" — Switch on
    - "Replace underscores with spaces" — regex "_" — replacement " " — Switch on
- Each custom row has trailing icons: edit "edit" + delete "delete".
- Below: Outlined "Add custom pattern" full width with leading icon "add".

- Bottom sticky bar: outlined "Discard" + filled "Save" (disabled until any change is made).

Add-pattern editor (also in this design as a small modal sheet variant):
- Title "New cleanup pattern".
- OutlinedTextField "Label".
- OutlinedTextField "Regex" (monospace).
- OutlinedTextField "Replacement (optional)" (monospace, defaults to empty).
- Tonal info card "Test against sample" with the same sample input and a live preview of the cleaned result.
- Buttons row: outlined "Cancel" + filled "Add".
```

---

## S16 — OSS Notices

**Generate as:** `ossnotices_light/` and `ossnotices_dark/`

```text
Open-source licences screen.

Layout:
- CenterAlignedTopAppBar: nav icon "arrow_back", title "Open-source notices".
- Body LazyColumn with grouped sections.
- Sample groups:
    - "Engine"
        - FFmpeg (LGPL 2.1+)  · Used for stream-copy split and concat merge.
        - libass (ISC)        · Subtitle code path metadata.
    - "Android & UI"
        - AndroidX (Apache 2.0)
        - Jetpack Compose (Apache 2.0)
        - Material Components for Android (Apache 2.0)
    - "Kotlin & DI"
        - Kotlin (Apache 2.0)
        - Hilt (Apache 2.0)
        - Coroutines (Apache 2.0)
    - "Data & Networking"
        - Room (Apache 2.0)
        - Retrofit (Apache 2.0)
        - kotlinx.serialization (Apache 2.0)
- Each row is an ExpandableListItem: tapping reveals the full licence text (collapsed by default).
- Footer footnote: "This app is licensed under Apache-2.0. FFmpeg binaries are LGPL; we link them dynamically." centered, bodySmall.
```

---

## Dialogs (D1, D2, D3) — combined sheet

**Generate as:** `dialogs_light/` and `dialogs_dark/`

```text
Three modal bottom sheets for the Video Splitter app. Generate them as a single design page showing all three side by side (or stacked) for reference. Each sheet is a Material 3 ModalBottomSheet with a top drag handle 32dp wide.

D1 — "Cleanup preview"
- Triggered between Configure (S5) and Confirm (S6).
- Title "Title cleanup preview" (titleMedium).
- Body block:
    - Row "Original" — value monospace bodySmall ellipsis 1 line: "www.5MovieRulz.graphics - Kantara.Chapter.1.2024.4K.WEB-DL.x265.mkv".
    - Row "Cleaned title" — value monospace titleMedium: "Kantara Chapter 1 (2024)".
    - Row "Subfolder" — value monospace bodyMedium: "Kantara Chapter 1 (2024)/".
    - Row "Part name" — value monospace bodyMedium: "Kantara Chapter 1 (2024).part001.mkv".
- Buttons row 3 buttons: text "Edit title" left, outlined "Cancel" middle, filled "Use as-is" right.

D2 — "Folder collision"
- Title "Folder already exists" (titleMedium).
- Body bodyMedium: "/storage/emulated/0/Movies/Baahubali The Epic (2025)/ already exists.".
- Three stacked filled tonal action rows (not buttons — full-width tappable cards 64dp each):
    - "Use existing folder" + body small "Existing files with the same name will be overwritten." + leading icon "folder_open".
    - "Add a suffix" + body small "Save to Baahubali The Epic (2025) (1)/ instead." + leading icon "create_new_folder".
    - "Cancel" + body small "Don't start the split." + leading icon "close".

D3 — "Container promotion"
- Title "Output will be MKV" (titleMedium).
- Body bodyMedium: "Your file uses bitmap subtitles (PGS or VobSub). MP4 can't carry these. To keep all subtitles, we'll save the parts as .mkv. The video and audio bytes are unchanged.".
- A small two-row tonal card showing:
    - Original container  .mp4
    - Output container    .mkv  (badge "auto-promoted" tonal primary).
- Buttons row: text "Continue with .mp4 (drops bitmap subs)" left (smaller, secondary color), filled "OK" right.

Common style:
- 24dp horizontal padding.
- 16dp top padding under the drag handle.
- 24dp bottom padding.
- Sheet corner radius 28dp top corners.
- All sheets land at ~50% of the phone screen height.
```

---

## Done — what to do with these prompts

1. Open Stitch, create the project, paste `STITCH-01-GLOBAL.md`'s project-level prompt as project context.
2. Open `STITCH-02-CORE-FLOWS.md` (this folder) and paste each fenced prompt one at a time, generating its light and dark variants.
3. Repeat for `STITCH-03-MERGE-AND-SETTINGS.md` (this file).
4. Save each export under `Kotlin APK/Design/<screenname>_<variant>/` per the table in `STITCH-01-GLOBAL.md`.
5. When all are done, ping me. I'll then generate `AGENTS.md` and the master prompt that consume these designs.
