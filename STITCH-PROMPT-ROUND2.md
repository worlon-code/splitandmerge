# Stitch — Continuation Prompt (Round 2)

> Paste this entire file into the same Stitch project where you already generated Round 1 (Library, File Details, Onboarding, Split Configuration, Split Confirmation, Split Progress, Split Complete). It generates the **8 remaining screens (20 variants)** in one go.

---

## Project context (already loaded — don't repaste)

The Stitch project already knows:
- Theme `Kinetic Logic` (deep teal `#006B5F` primary, violet `#CDBDFF` secondary).
- M3 expressive components, Material Symbols Rounded, Roboto Flex + JetBrains Mono.
- Sample data: Baahubali The Epic (2025), Kantara Chapter 1 (2024), Karuppu (2026), Devara (2024), Salaar (2023).
- "Utilitarian, technical, calm" tone — no marketing copy, no people, no purple-pink gradients.

---

## NEW global rules (apply to every screen below)

**Adopt these from Round 1 — they were better than my original spec:**

1. **Bottom navigation bar** (4 destinations) on every primary phone screen: `Split` (icon `content_cut`), `Merge` (icon `merge_type`), `Queue` (icon `list_alt`), `Settings` (icon `settings`). Selected destination uses the violet `secondary` pill background per Round 1. Highlight the destination matching the screen.

2. **3-pane tablet layout** for tablet variants: left = navigation rail (40dp wide, icons only) + middle = list (~360dp) + right = detail (flex). Match the layout used in `library_tablet_*` and `split_configuration_tablet_*` from Round 1.

3. **Long release filenames** as input strings (not pre-cleaned ones). Use:
   - `Baahubali The Epic 2025 2160p NEWEB-DL DUAL DTS HD MA DDP5.1 H.265.mkv`
   - `www.5MovieRulz.graphics - Kantara.Chapter.1.2024.4K.WEB-DL.x265.mkv`
   - `www.5MovieRulz.software - Karuppu.2026.Tamil.4K.WEB-DL.x265.mkv`
   This screen pack is *about* cleaning these into folders/parts, so the raw form must be visible.

4. **Cleaned titles** for derived names (folders, part names, output filenames):
   - `Baahubali The Epic (2025)` → folder `Baahubali The Epic (2025)/` → parts `Baahubali The Epic (2025).part001.mkv`
   - `Kantara Chapter 1 (2024)` etc.

5. **Bottom nav is mutually exclusive** with sticky-bottom-bar buttons. When a screen has primary CTAs (Cancel / Continue), they sit *above* the bottom nav, separated by a 1dp `outlineVariant` divider. Bottom nav stays visible.

6. **Tablet variants** are required for **S14 Jobs** and **S15a Cleanup Patterns**. Other tablet variants are not needed for v1.

> **Wrapper for every per-screen prompt below:** *Continue the Video Splitter project. Use the project-level Kinetic Logic theme. Generate light and dark variants. Use Material 3 components. Apply the new global rules: bottom navigation, long release filenames, 3-pane tablet layout where applicable.*

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
- Sticky bottom action bar (above the bottom nav): outlined "Cancel" left, filled primary "Continue" right (disabled when validation fails). 1dp outlineVariant divider between this bar and the bottom nav.
- Bottom navigation: Split / Merge / Queue / Settings. Highlight "Merge".
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

- Sticky bottom action bar: Cancel outlined + "Start merge" filled primary (icon "play_arrow"). Above bottom nav.
- Bottom navigation: Split / Merge / Queue / Settings. Highlight "Merge".
```

---

## S13 — Merge Complete

**Generate as:** `mergeresult_light/` and `mergeresult_dark/`

```text
Result screen after a successful merge. Mirrors the split-complete result style.

Layout:
- CenterAlignedTopAppBar: nav icon "arrow_back", title "Merge complete", trailing icon "more_vert" (overflow: Open file, Share, Delete source parts).
- Body:
    - Centred Material Symbols "check_circle" 64dp in success color, with a soft circular tonal background 120dp.
    - Headline "Merge complete" (headlineSmall, weight 500).
    - bodyMedium onSurfaceVariant: "Saved · Baahubali The Epic (2025).merged.mkv". Tap to open file.
    - Three stat tiles row, OutlinedCard each: Size 53.0 GB · Duration 2h 13m · Time taken 5 min 42 s.
    - Section "Streams preserved":
        - Two-column rows (label left labelMedium onSurfaceVariant, value right monospace bodyMedium):
            - Video      HEVC 3840×2160 · HDR10
            - Audio      8 tracks (Telugu DTS-HD MA, Hindi E-AC3, Tamil/Malayalam/Kannada AAC, …)
            - Subtitles  2 tracks (English ASS, Telugu ASS)
            - Chapters   32
            - Fonts      4 attachments
- Sticky bottom action bar: outlined "Open file" + filled primary "Delete source parts" (with destructive tonal hint — show a small inline confirmation dialog stub). Above bottom nav.
- Bottom navigation: Split / Merge / Queue / Settings. Highlight "Merge".
```

---

## S14 — Jobs (history) phone

**Generate as:** `jobs_light/` and `jobs_dark/`

```text
Full job history list with filters.

Layout:
- CenterAlignedTopAppBar: title "Queue" (titleLarge). No nav icon — Queue is reached from the bottom nav.
- Below the app bar, a horizontally scrollable row of FilterChips (single-select):
    - "All" (selected by default), "Running", "Queued", "Done", "Failed", "Cancelled".
- 8dp gap.
- LazyColumn list of jobs. Each row is a swipeable ListItem (show one row in a "swiped left" state to reveal a destructive "Delete" action 80dp wide):
    - Leading: 40dp tonal circular avatar showing job-type icon — "content_cut" for split (teal tonal), "merge_type" for merge (violet tonal).
    - Middle:
        - Line 1: cleaned title (titleMedium, ellipsis), e.g. "Baahubali The Epic (2025)".
        - Line 2: status chip + concise meta (bodySmall onSurfaceVariant), e.g. "Done · 7 parts · 53.0 GB · 2h ago".
    - Trailing: small chevron icon.
- Sample rows (mix of split and merge):
    1. Split · Baahubali The Epic (2025) · Running · part 3 of 7 · 47% (with inline determinate progress bar under the row meta)
    2. Split · Kantara Chapter 1 (2024) · Queued · waiting on previous job
    3. Merge · Devara (2024).merged · Done · 28.4 GB · today 11:15
    4. Split · Karuppu (2026) · Done · 3 parts · 22.9 GB · yesterday
    5. Split · Salaar (2023) · Failed · insufficient storage · 2 days ago
    6. Split · Devara (2024) · Cancelled · 12 min ago
- Bottom navigation: Split / Merge / Queue / Settings. Highlight "Queue".

Empty state variant (also generate this in the same export):
- Title "No jobs yet" with body "Splits and merges will show up here.". Bottom nav unchanged.
```

---

## S14 — Jobs (history) tablet *(tablet)*

**Generate as:** `jobs_tablet_light/` and `jobs_tablet_dark/`

```text
Tablet 800dp+ variant of the Jobs screen using the 3-pane layout.

Three panes left to right:
- Left: Navigation rail 80dp wide with destinations Split / Merge / Queue / Settings (icons + small labels). Highlight "Queue".
- Middle: 360dp fixed width. Shows the "Queue" header + filter chips + the same jobs list as phone but no inline progress (move it to the detail pane).
- Right: flex. Shows the *selected* job's detail. Use sample row 1 (Baahubali The Epic (2025), Running, part 3 of 7).
    - Top: a 200dp tall hero card with codec icon + the chips strip "HEVC · 4K · HDR10 · 8 audio tracks".
    - Below: section "About this job" with rows: Source filename (selectable text), Output folder, Mode (e.g. "Size cap 9 GB · ceiling 9.5 GB"), Started at, Duration, Speed, ETA.
    - Section "Parts" with the vertical list of 7 parts (index, name, size, status chip, three icon actions).
    - Sticky bottom-right action bar inside the detail pane: outlined "Cancel job" (destructive) + filled tonal "Pause" (disabled if pause not supported).
```

---

## S15 — Settings

**Generate as:** `settings_light/` and `settings_dark/`

```text
Settings screen, sectioned ListItems.

Layout:
- CenterAlignedTopAppBar: title "Settings". No nav icon — Settings is reached from bottom nav.
- Body, 16dp horizontal padding, 24dp gap between sections.

SECTION "Appearance"
- ListItem "Theme" — secondary "Follow system" — trailing icon "chevron_right". On tap shows a bottom sheet with radio options Light / Dark / AMOLED Black / Follow system.
- ListItem "Dynamic colour" — secondary "Use wallpaper colours" — trailing Switch (on by default on A12+).

SECTION "Defaults"
- ListItem "Size cap" — secondary "9 GB target · 9.5 GB ceiling" — trailing icon "chevron_right".
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

- Bottom navigation: Split / Merge / Queue / Settings. Highlight "Settings".

Do NOT include:
- A "Sign in" / "Account" section.
- "Premium" / "Upgrade" buttons.
- A "Rate this app" section.
```

---

## S15a — Title Cleanup Patterns phone

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

- Sticky bottom action bar: outlined "Discard" + filled "Save" (disabled until any change is made). Above bottom nav.
- Bottom navigation: Split / Merge / Queue / Settings. Highlight "Settings".

Add-pattern editor (also include this design as a small modal sheet variant within the same export):
- Title "New cleanup pattern".
- OutlinedTextField "Label".
- OutlinedTextField "Regex" (monospace).
- OutlinedTextField "Replacement (optional)" (monospace, defaults to empty).
- Tonal info card "Test against sample" with the same sample input and a live preview of the cleaned result.
- Buttons row: outlined "Cancel" + filled "Add".
```

---

## S15a — Title Cleanup Patterns tablet *(tablet)*

**Generate as:** `cleanuppatterns_tablet_light/` and `cleanuppatterns_tablet_dark/`

```text
Tablet 800dp+ variant. 3-pane layout.

Three panes left to right:
- Left: Navigation rail 80dp. Highlight "Settings".
- Middle: 380dp fixed width. The patterns list (built-in section + custom section + "Add custom pattern" outlined button).
- Right: flex. The live preview pane with:
    - Large OutlinedTextField "Sample filename" pre-filled with the long Kantara filename.
    - Below: a card showing the cleaned title and folder names in monospace.
    - Below: a "Trace" panel showing each rule as a row: rule label, regex, before snippet, after snippet, in execution order. This makes it visually obvious which rule transforms the input at which step.

- Sticky bottom-right action bar inside the right pane: outlined "Discard" + filled "Save".
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
- Bottom navigation: Split / Merge / Queue / Settings. Highlight "Settings".
```

---

## Dialogs (D1, D2, D3) — combined sheet

**Generate as:** `dialogs_light/` and `dialogs_dark/`

```text
Three modal bottom sheets for the Video Splitter app. Generate them as a single design page showing all three stacked vertically for reference. Each sheet is a Material 3 ModalBottomSheet with a top drag handle 32dp wide.

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

Do NOT include a bottom navigation in this export — bottom sheets overlay the parent screen and don't replace its nav.
```

---

## Save these into the existing Design folder

Use these export folder names:

| Screen | Light folder | Dark folder |
|---|---|---|
| S10 Merge Order | `mergeorder_light/` | `mergeorder_dark/` |
| S11 Merge Config | `mergeconfig_light/` | `mergeconfig_dark/` |
| S13 Merge Complete | `mergeresult_light/` | `mergeresult_dark/` |
| S14 Jobs (phone) | `jobs_light/` | `jobs_dark/` |
| S14 Jobs (tablet) | `jobs_tablet_light/` | `jobs_tablet_dark/` |
| S15 Settings | `settings_light/` | `settings_dark/` |
| S15a Cleanup Patterns (phone) | `cleanuppatterns_light/` | `cleanuppatterns_dark/` |
| S15a Cleanup Patterns (tablet) | `cleanuppatterns_tablet_light/` | `cleanuppatterns_tablet_dark/` |
| S16 OSS Notices | `ossnotices_light/` | `ossnotices_dark/` |
| Dialogs combined | `dialogs_light/` | `dialogs_dark/` |

When all 20 variants are generated, the design pack is complete and we can move to Phase 1 (`AGENTS.md` + master prompt + AI/ docs skeleton).
