# Video Splitter — Stitch Design Brief

> **Upload this single file to Stitch as the project brief.** It contains the global theme, sample data, and a copy-paste-ready prompt for every screen in the app.

---

## How to use this file in Stitch

1. Create a new Stitch project named **`Video Splitter`**.
2. Paste **Section 1 (Project-level prompt)** below as the project context / system prompt.
3. For each screen in **Section 3**, paste the fenced prompt block one at a time. Ask Stitch to generate **light + dark** variants for every screen, **plus tablet variants** only for the screens marked *(tablet)*.
4. Save each Stitch export into a folder named per **Section 4**, under `…/Kotlin APK/Design/`.

Always prepend each per-screen prompt with this single line so Stitch keeps consistency:

> *Continue the Video Splitter project. Use the project-level theme. Generate light and dark variants. Use Material 3 components. Sample data uses real Indian-cinema 4K filenames as listed.*

---

# 1. Project-level prompt (paste once into Stitch project context)

> **App:** Video Splitter — a native Android utility that splits large 4K videos into parts and merges them back, lossless.
>
> **Brand & tone:** Utilitarian, technical, calm. No marketing copy, no illustrations of people, no decorative gradients. Think "developer tool" meets "Material 3 file manager".
>
> **Design system:** Material 3 (Material You). Use the `expressive` Material 3 component set where available (newer M3 buttons with shape morphing). Typography uses Material 3 type scale defaults. Iconography: Material Symbols Rounded.
>
> **Colour:**
>
> - Light theme: M3 baseline with `primary` from a deep teal (`#006B5F`) seed.
> - Dark theme: M3 dark from same seed; provide AMOLED true-black variant where `surface = #000000` and `surfaceContainer = #0A0A0A`.
> - Status colours: success `#1B873F`, error `#B3261E`, warning `#A8741A`, info `#1F69E0`.
> - Honour Android 12+ dynamic colour: when generating, show one variant with the user's wallpaper colour (use `#7B5DD8` as the dynamic-sample seed).
>
> **Layout:**
>
> - Phone width target 360dp; tablet 800dp+; foldable inner display 712dp.
> - Page horizontal padding: 16dp on phone, 24dp on tablet.
> - List item vertical padding: 12dp. Card corner radius: 16dp.
> - Bottom safe area padding 16dp; FAB bottom-end position `(end:16, bottom:16)` plus FAB size.
>
> **Components used across screens:**
>
> - `CenterAlignedTopAppBar` for primary screens; `MediumTopAppBar` (collapsing) only for File Details.
> - `FilledTonalButton` for secondary actions; `Button` (filled) for primary CTA; `OutlinedButton` for tertiary.
> - `ListItem` (M3 three-line) for all list rows.
> - `AssistChip` / `FilterChip` / `SuggestionChip` per use.
> - `ElevatedCard` for summary blocks.
> - `LinearProgressIndicator` (determinate) for inline progress; `CircularProgressIndicator` (determinate, size 160dp) for big progress.
> - Bottom sheets for dialogs (modal, draggable handle on top).
>
> **Accessibility:**
>
> - All tappable targets ≥ 48dp.
> - Min text size `bodyMedium` for any user-visible text.
> - Long titles wrap to 2 lines with ellipsis on line 2.
> - Status chips include both colour and an icon (so colour-blind users still parse).
>
> **What never appears in this app:**
>
> - Avatars, user profiles, social-share illustrations, "premium" badges, subscription pricing, login screens, signup flows, banners, ads, promo carousels, "rate us" prompts.
> - Pure white backgrounds in dark mode. Pure black backgrounds in light mode.
> - Skeumorphic film reels, clapperboards, "play" triangles. This is a utility, not a media player.
> - Purple-pink gradients. The seed colour is calm teal.
> - Emoji-heavy UI. Status chips use Material Symbols icons, not emoji.

---

# 2. Sample data palette (use these everywhere)

So screens look filled and realistic, use these strings as filler instead of lorem ipsum.

### Movie titles + folder names

| Cleaned title | Filename to display | Size | Duration |
|---|---|---|---|
| `Baahubali The Epic (2025)` | `Baahubali The Epic 2025 2160p NEWEB-DL DUAL DTS HD MA DDP5.1 H.265.mkv` | 47.2 GB | 2:13:12 |
| `Kantara Chapter 1 (2024)` | `www.5MovieRulz.graphics - Kantara.Chapter.1.2024.4K.WEB-DL.x265.mkv` | 38.6 GB | 2:31:30 |
| `Karuppu (2026)` | `www.5MovieRulz.software - Karuppu.2026.Tamil.4K.WEB-DL.x265.mkv` | 22.9 GB | 2:31:30 |
| `Devara (2024)` | `Devara.2024.2160p.HDR.HEVC.mkv` | 28.4 GB | 2:55:08 |
| `Salaar (2023)` | `Salaar.Part1.Ceasefire.2023.4K.BluRay.HEVC.TrueHD.mkv` | 51.3 GB | 2:55:00 |

### Statuses for jobs list

- Done · 7.6 GB · 2h ago
- Running · part 3 of 7 · 47% · 82 MB/s
- Queued · waiting on previous job
- Failed · insufficient storage
- Cancelled · 12 min ago

### Codec strings (for File Details screen)

```
Video    HEVC (H.265 Main 10)  3840×2160  24fps  HDR10
Audio 1  DTS-HD MA   Telugu    6 ch  48 kHz   1535 kbps
Audio 2  E-AC3       Hindi     6 ch  48 kHz    640 kbps
Audio 3  AAC LC      Malayalam 2 ch  48 kHz    192 kbps
Subtitle 1  ASS  English  default
Subtitle 2  ASS  Telugu
Container   Matroska (.mkv)   Avg bitrate 47.8 Mb/s   Attachments 4 fonts
```

### Style guardrails

- Numeric values right-aligned in any two-column layout (e.g. file sizes, durations).
- Monospace for technical strings (codec names, bitrates, hashes).

---

# 3. Per-screen prompts

Generate each screen separately, in this order.

## S1 — Onboarding

**Generate as:** `onboarding_light/` and `onboarding_dark/`

```text
Generate a single one-time onboarding screen for an Android app called "Video Splitter".

Layout (top to bottom, vertically centred):
- Top safe area padding.
- A small Material Symbols Rounded icon "movie_filter" sized 96dp, in primary color, with a soft tonal background circle behind it (surfaceContainer, 160dp diameter).
- 32dp gap.
- Headline text: "Split big videos. Merge them back." — Material 3 displaySmall, weight 500, two lines max, centred. Color: onSurface.
- 12dp gap.
- Sub-headline: "Lossless. Subtitles preserved. 4K untouched." — Material 3 bodyLarge, color onSurfaceVariant, centred, single line on phone.
- Flexible spacer.
- Card with subtle outline (1dp outlineVariant), radius 16dp, padding 16dp, with a row inside:
    - Material Symbols "folder_open" 24dp leading.
    - Two-line text: line 1 "Pick a folder for your splits" (titleMedium); line 2 "We'll save parts here. You can change this later in Settings." (bodySmall onSurfaceVariant).
- 24dp gap.
- Primary filled button "Pick output folder" — full width minus 16dp horizontal padding, height 56dp, leading icon "folder_open" 18dp.
- 12dp gap.
- Text button "Skip for now" — centred, color primary.
- Bottom safe area padding 24dp.

Do NOT include:
- App logo as decoration.
- Carousel / pager dots.
- "Sign in" or "Sign up" buttons.
- People illustrations.
```

---

## S2 — Library / Home (phone)

**Generate as:** `library_light/` and `library_dark/`

```text
Home screen for "Video Splitter". This is the everyday entry point.

Layout:
- CenterAlignedTopAppBar:
    - Title: "Video Splitter" (titleLarge).
    - Trailing icons: "search" then a three-dot "more_vert" overflow.
- Content scroll area (LazyColumn) with 16dp horizontal padding.
- A two-button row at the very top of content:
    - Two equal-width tonal buttons side-by-side, gap 12dp:
        - Left: "Split a video" with leading icon "content_cut" 18dp.
        - Right: "Merge parts" with leading icon "merge_type" 18dp.
    - Both buttons height 56dp, radius 16dp, color secondaryContainer / onSecondaryContainer.
- 24dp gap.
- Section header row: text "Recent jobs" (titleMedium, weight 500) on the left; on the right a small text-link "See all" (labelLarge, color primary) tappable.
- 8dp gap.
- A list of 5 job rows. Each row is an ElevatedCard, radius 16dp, padding 12dp, gap between cards 8dp:
    - Leading: a 56dp rounded square thumbnail placeholder showing the codec ID badge "HEVC" centred on a tonal background.
    - Middle (vertical, takes available width):
        - Line 1: cleaned title (titleMedium, max 1 line, ellipsis), e.g. "Baahubali The Epic (2025)".
        - Line 2: status chip (filterChip-style small) + size + relative time (bodySmall onSurfaceVariant), e.g. "Done · 47.2 GB · 2h ago".
        - Status chip colors:
            - Done = success tonal
            - Running = primary tonal with a tiny inline determinate progress bar (16dp height, full row width below the title)
            - Queued = neutral tonal
            - Failed = error tonal
            - Cancelled = neutral outlined
    - Trailing: a small "more_vert" icon button (40dp).
- Sample rows (in this order, top to bottom):
    1. "Baahubali The Epic (2025)" — Running · part 3 of 7 · 47% · 82 MB/s (show inline progress bar)
    2. "Kantara Chapter 1 (2024)" — Queued · waiting on previous job
    3. "Karuppu (2026)" — Done · 22.9 GB · yesterday
    4. "Devara (2024)" — Failed · insufficient storage
    5. "Salaar (2023)" — Cancelled · 12 min ago
- Floating Action Button bottom-end: extended FAB "New job" with leading icon "add". On tap, it would open a small bottom-sheet with two options Split / Merge — but for this screen, just show the FAB.

Empty state (also generate this variant in the same file):
- When zero jobs exist:
    - The two top tonal buttons stay.
    - Below them, an ElevatedCard centred with: icon "video_file" 48dp, headline "No jobs yet", body "Pick a video to split, or pick parts to merge.", bodyMedium centred.
```

---

## S2 — Library tablet two-pane *(tablet)*

**Generate as:** `library_tablet_light/` and `library_tablet_dark/`

```text
Tablet (800dp+) variant of the Library screen.

Two-pane layout:
- Left pane 360dp fixed width, vertical divider (outlineVariant) on right.
    - Same content as phone Library: top-app-bar (title only, no centred), two tonal action buttons, jobs list.
    - The job list rows shrink: no progress bar inline (move to detail pane); no codec thumbnail (just a leading codec badge chip).
- Right pane (flex) shows the selected job's detail view:
    - Top: large thumbnail / codec card 240dp tall, with codec icon centred and a "HEVC · 4K · HDR10" chip strip below.
    - Below: section "About this job", with rows: Source filename (selectable text), Output folder, Mode (e.g. "Size cap 9 GB"), Started at, Duration, Speed, ETA.
    - Section "Parts" with a vertical list of 7 parts, each row showing index, name, size, status chip, and three icon actions (open / share / more).
    - For a Running job, a sticky bottom bar appears with a Cancel destructive outlined button and a paused-state secondary "Pause" button.

Use sample row 1 (Baahubali The Epic (2025), Running, part 3 of 7) as the selected job.
```

---

## S4 — File Details

**Generate as:** `filedetails_light/` and `filedetails_dark/`

```text
Screen shown after the user picks a video file. The app has just probed the file. We display its metadata and offer to configure a split.

Layout:
- MediumTopAppBar (collapsing) with:
    - Navigation icon: "arrow_back".
    - Title (collapsed): cleaned title (e.g. "Baahubali The Epic (2025)").
    - Trailing: icon "info" (opens a small sheet with raw filename + container info).
- When expanded, show a 200dp tall header card area:
    - A subtle linear gradient using primaryContainer → surface.
    - Centered Material Symbols "movie" 64dp.
    - Below: cleaned title (headlineSmall) and a one-line metadata strip "47.2 GB · 2h 13m · MKV".
    - At the bottom of the header, a horizontal scroll row of small AssistChips:
        - "HEVC", "4K", "HDR10", "DTS-HD MA", "Multi-language", "Subs: ASS"
- Content scroll area with 16dp horizontal padding, 16dp vertical gap between cards.

Cards (each is an ElevatedCard, radius 16dp, padding 16dp; each card is collapsible with a trailing chevron in the header row):

CARD 1 — "Video"
- Header row: Material Symbols "videocam" 20dp + label "Video" (titleMedium) + chevron.
- Body (table-style two-column rows, label left labelMedium onSurfaceVariant, value right monospace bodyMedium onSurface):
    - Codec      HEVC (H.265 Main 10)
    - Resolution 3840 × 2160
    - Frame rate 24.000 fps
    - Bit depth  10-bit
    - HDR        HDR10
    - Bitrate    47.8 Mb/s

CARD 2 — "Audio (3 tracks)"
- Header row icon "graphic_eq".
- Body: 3 sub-rows each rendered as a mini ListItem with:
    - Leading 32dp circular language tag (text "TE", "HI", "ML") on tonal background.
    - Two-line text: line 1 "Track 1 · Telugu" (titleSmall); line 2 "DTS-HD MA · 5.1 · 48 kHz · 1535 kbps" (bodySmall monospace onSurfaceVariant).
    - Trailing: AssistChip "default" only on the first row.

CARD 3 — "Subtitles (2 tracks)"
- Header icon "subtitles".
- Body: 2 sub-rows similar style with language codes "EN", "TE" and codec strings "ASS · default" / "ASS".

CARD 4 — "Container"
- Header icon "inventory_2".
- Body two-column rows:
    - Container   Matroska (.mkv)
    - Avg bitrate 47.8 Mb/s
    - Attachments 4 fonts (.ttf)
    - Chapters    32

- Bottom sticky bar (elevated tonal surface, 80dp tall, 16dp padding):
    - Left side: small "Source: <original filename>" text (bodySmall, max 1 line ellipsis, takes available width).
    - Right: primary filled button "Configure split" (height 48dp, trailing icon "arrow_forward").

Do NOT include:
- A play / preview button. (We are not a player.)
- Any "share file" action here.
- Posters or thumbnails of the actual movie content.
```

---

## S5 — Split Configuration (phone)

**Generate as:** `splitconfig_light/` and `splitconfig_dark/`

```text
Configuration screen for a new split job. The user has just left File Details.

Layout:
- CenterAlignedTopAppBar:
    - Nav icon "arrow_back"; title "Configure split"; trailing icon "help_outline" (opens an info sheet about lossless splitting).
- Content scroll area, 16dp horizontal padding, 16dp vertical gap between cards.

CARD A — "Title"
- Header row icon "edit" + "Title" (titleMedium).
- Inside:
    - OutlinedTextField labeled "Cleaned title", value "Baahubali The Epic (2025)", supportingText "From: Baahubali The Epic 2025 2160p NEWEB-DL DUAL DTS HD MA DDP5.1 H.265.mkv" (bodySmall onSurfaceVariant, max 2 lines with ellipsis).
    - 8dp gap.
    - Tonal text-button "Edit cleanup rules" with leading icon "rule" 18dp, color secondary. Tappable; navigates to S15a.
    - Tonal info chip (full width, 40dp tall, leading icon "info"): "Subfolder will be created: <chosen folder>/Baahubali The Epic (2025)/".

CARD B — "Mode"
- Header row icon "tune" + "Mode" (titleMedium).
- Segmented button group, 3 segments full width: "Exact parts" / "Size cap" / "Both".
- Selected default: "Both".
- Below the segmented control:
    - When "Exact parts" selected: a numeric stepper labeled "Parts", value 3, range 2..50, with - and + 40dp icon buttons and a centred numeric field.
    - When "Size cap" selected: an OutlinedTextField numeric "Size cap", value "9", with a trailing dropdown menu "GB ▾" (also offers MB). Below it bodySmall text "Will allow up to 9.5 GB if a keyframe lands there. Never exceeds 9.5 GB.".
    - When "Both" selected: BOTH controls visible stacked.

CARD C — "Output"
- Header row icon "drive_file_move" + "Output" (titleMedium).
- ListItem rows:
    - "Destination folder" — secondary "/storage/emulated/0/Movies", trailing icon "folder_open" tappable to re-pick.
    - "Output container" — secondary "Match input (.mkv)" with a trailing dropdown menu (.mkv / .mp4 — but greyed-out and locked because input has bitmap subs; show a tonal chip "Locked: subtitles require MKV").

CARD D — "Summary" (highlighted, primaryContainer / onPrimaryContainer)
- Big number "7" (displayMedium) and small label "parts" beside it.
- Row of three small stat tiles:
    - Avg size  ~7.6 GB
    - Total     ~53 GB
    - ETA       ~12 min
- Below: bodySmall text "Lossless. Subtitles preserved. 4K untouched.".

- Bottom sticky bar (elevated, 80dp): Cancel outlined button on left ("Cancel"), Continue filled primary button on right ("Continue", trailing icon "arrow_forward").
```

---

## S5 — Split Configuration tablet *(tablet)*

**Generate as:** `splitconfig_tablet_light/` and `splitconfig_tablet_dark/`

```text
Tablet 800dp+ variant of the Split Configuration screen.

Two-column layout, 24dp page padding, 24dp inter-column gap:
- Left column (flex 1): cards A "Title" and B "Mode" stacked, with 16dp vertical gap.
- Right column (flex 1): cards C "Output" and D "Summary" stacked.

The bottom sticky bar spans full width with the same Cancel / Continue buttons. All other styling identical to the phone version.
```

---

## S6 — Split Confirmation

**Generate as:** `splitconfirm_light/` and `splitconfirm_dark/`

```text
A short confirmation screen between Configure and Progress. Sometimes shown as a full-screen sheet.

Layout (full-bleed centred card):
- CenterAlignedTopAppBar with nav icon "close" and title "Confirm split".
- Content area, 24dp horizontal padding, vertically centred:
    - ElevatedCard with radius 24dp, padding 24dp:
        - Centred Material Symbols "task_alt" 48dp in primary color, with tonal circular background 96dp.
        - 16dp gap.
        - Headline "Ready to split" (headlineSmall, weight 500, centred).
        - 8dp gap.
        - Body bodyLarge centred onSurfaceVariant: "We'll create 7 parts of Baahubali The Epic (2025), about 7.6 GB each. Estimated time around 12 min. You can cancel any time.".
        - 24dp gap.
        - A two-row table inside the card, both rows with label left, value right monospace:
            - Source size      47.2 GB
            - Free space here  243 GB
        - 24dp gap.
        - Buttons row with two equal-width buttons:
            - Outlined "Back" on left.
            - Filled primary "Start split" on right with leading icon "play_arrow".
        - 12dp gap.
        - Subtle bodySmall text centred onSurfaceVariant: "Tip: keep your phone plugged in for jobs over 30 min."

Do NOT include:
- Any progress indicator yet (this is pre-start).
- Marketing copy.
```

---

## S7 — Split Progress

**Generate as:** `progress_light/` and `progress_dark/`

```text
Live progress screen for a running split job. This screen is bound to the foreground service; the user can leave and return at any time. Survives rotation.

Layout:
- CenterAlignedTopAppBar:
    - Nav icon "arrow_back" (does NOT cancel; just returns to home).
    - Title "Splitting" (titleLarge).
    - Trailing icon "more_vert" (overflow with: "Pause" disabled-grey if not supported, "Cancel job", "Open output folder").
- Body:
    - 24dp top gap.
    - Centred CircularProgressIndicator determinate, size 200dp, stroke 12dp:
        - In the centre: large numeric "47%" (displayMedium, monospace) and below it bodyMedium "Part 3 of 7".
    - 24dp gap.
    - Centred title text: "Baahubali The Epic (2025)" (titleMedium, max 1 line ellipsis, 16dp horizontal padding).
    - 4dp gap.
    - Centred bodySmall onSurfaceVariant: "Splitting at 9 GB cap · output .mkv".
    - 24dp gap.
    - Three stat tiles row, gap 12dp, each tile is an OutlinedCard 100dp tall:
        - Speed       82 MB/s   icon "speed"
        - ETA         ~8 min    icon "schedule"
        - Written     22.1 GB   icon "save"
    - 24dp gap.
    - "Parts" section header (titleMedium) with a small "(3 of 7)" chip.
    - 8dp gap.
    - List of 7 part rows (LazyColumn), each row 56dp tall:
        - Leading: index circle "1"…"7" 32dp tonal.
        - Middle: file name e.g. "Baahubali The Epic (2025).part001.mkv" (bodyMedium monospace, ellipsis).
        - Trailing: status icon
            - Done: checkmark in success color
            - Running: small circular indeterminate or determinate (use 47% on row 3)
            - Pending: empty circle outlineVariant
    - Bottom safe area padding 96dp (to clear the bottom bar).
- Bottom sticky bar (80dp, elevated):
    - Outlined destructive Cancel button "Cancel job" (full row width minus 32dp horizontal padding) — on tap shows a small bottom sheet "Cancel job? Already-finished parts are kept." with confirm/dismiss.

For the dark variant, ensure the circular progress ring has high contrast against surface (use primary at 100% opacity for the determinate arc, primaryContainer for the track).

This same layout is reused for the merge progress screen with title "Merging" instead of "Splitting".
```

---

## S8 — Split Complete

**Generate as:** `splitresult_light/` and `splitresult_dark/`

```text
Result screen after a successful split. This is a delightful but utilitarian moment.

Layout:
- CenterAlignedTopAppBar: nav icon "arrow_back", title "Split complete", trailing icon "more_vert" (overflow: Open folder, Share all, Delete all parts, Make merge job).
- Body:
    - 24dp top gap.
    - Centred Material Symbols "check_circle" 64dp in success color (#1B873F), with a soft circular tonal background 120dp.
    - 12dp gap.
    - Centred headline "All 7 parts saved" (headlineSmall, weight 500).
    - 4dp gap.
    - Centred bodyMedium onSurfaceVariant: "Subfolder · Baahubali The Epic (2025)/". Tappable to open the folder.
    - 24dp gap.
    - Three stat tiles row, OutlinedCard each:
        - Total size  53.0 GB
        - Avg size    7.6 GB
        - Time        11 min 42 s
    - 24dp gap.
    - Section header "Parts" (titleMedium) with small text "tap to share or open".
    - 8dp gap.
    - List of 7 ListItem rows:
        - Leading: index circle.
        - Middle: 2-line text — name (titleSmall monospace) and "7.62 GB · ASS subs · 8 audio tracks" (bodySmall onSurfaceVariant).
        - Trailing: 3 icon buttons inline — "open_in_new", "share", "more_vert".
- Bottom sticky bar (80dp): two equal width buttons gap 12dp:
    - Outlined "Open folder" (icon "folder_open").
    - Filled primary "Make merge job from these" (icon "merge_type").
```

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

Add-pattern editor (also include this design as a small modal sheet variant within the same export):
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

# 4. Export folder naming convention

Save each Stitch export under `Kotlin APK/Design/<folder>/` per the table:

| Screen | Light folder | Dark folder |
|---|---|---|
| S1 Onboarding | `onboarding_light/` | `onboarding_dark/` |
| S2 Library | `library_light/` | `library_dark/` |
| S2 Library tablet | `library_tablet_light/` | `library_tablet_dark/` |
| S4 File Details | `filedetails_light/` | `filedetails_dark/` |
| S5 Split Config | `splitconfig_light/` | `splitconfig_dark/` |
| S5 Split Config tablet | `splitconfig_tablet_light/` | `splitconfig_tablet_dark/` |
| S6 Split Confirm | `splitconfirm_light/` | `splitconfirm_dark/` |
| S7 Progress | `progress_light/` | `progress_dark/` |
| S8 Split Complete | `splitresult_light/` | `splitresult_dark/` |
| S10 Merge Order | `mergeorder_light/` | `mergeorder_dark/` |
| S11 Merge Config | `mergeconfig_light/` | `mergeconfig_dark/` |
| S13 Merge Complete | `mergeresult_light/` | `mergeresult_dark/` |
| S14 Jobs | `jobs_light/` | `jobs_dark/` |
| S15 Settings | `settings_light/` | `settings_dark/` |
| S15a Cleanup Patterns | `cleanuppatterns_light/` | `cleanuppatterns_dark/` |
| S16 OSS Notices | `ossnotices_light/` | `ossnotices_dark/` |
| Dialogs (combined) | `dialogs_light/` | `dialogs_dark/` |

S3 and S9 use the system SAF picker (no design needed). S12 reuses S7's layout with title "Merging".

---

# 5. After all screens are generated

1. Confirm each export landed in the correct folder per the table above.
2. Don't hand-edit Stitch outputs — if you want changes, re-prompt that screen.
3. Reply that the design pack is ready. The Kotlin/Compose agent that builds the app will read these as visual reference and produce equivalent Compose layouts; it will not copy raw HTML.
