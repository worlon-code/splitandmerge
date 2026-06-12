# Stitch Prompts — Split Flow (S1 → S8)

Each section below is a **standalone prompt**. Copy the entire fenced block into Stitch *after* pasting `STITCH-01-GLOBAL.md`'s project-level prompt as the project context.

> Prefix every prompt with: *"Continue the Video Splitter project. Use the project-level theme. Generate light and dark variants."*

---

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
- A list of 5 job rows (use the sample data from the project context). Each row is an ElevatedCard, radius 16dp, padding 12dp, gap between cards 8dp:
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

## S2 — Library tablet two-pane

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

Tablet variant (800dp+): two columns. Left column = cards A and B. Right column = cards C and D. Same sticky bottom bar.
```

---

## S5 — Split Configuration tablet

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

