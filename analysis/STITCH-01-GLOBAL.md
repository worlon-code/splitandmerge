# Stitch — Global Style + Usage Guide

Read this once before you generate any screen. Pin the **Project-level prompt** below in Stitch so every screen inherits the same theme.

## How to use Stitch effectively for this app

1. **Create one Stitch project** named `Video Splitter`.
2. Paste the **Project-level prompt** (below) as the project context / system prompt.
3. Generate **one screen at a time** using the per-screen prompts in the other two files (`STITCH-02-CORE-FLOWS.md`, `STITCH-03-MERGE-AND-SETTINGS.md`).
4. For each screen, generate **light + dark** variants. AMOLED-true-black is a swatch override on dark, not a separate generation.
5. **Phone first.** Generate tablet variant only for S2 and S5 (the two that have two-pane layouts in v1).
6. Save outputs into `Kotlin APK\Design\<screenname>_light\` and `\<screenname>_dark\` so the agent can find them.

## Naming convention for Stitch exports

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

S3, S9 are system SAF pickers (no design needed). S12 reuses S7. S13 reuses S8 layout.

---

## Project-level prompt (paste once into Stitch project context)

> **App:** Video Splitter — a native Android utility that splits large 4K videos into parts and merges them back, lossless.
>
> **Brand & tone:** Utilitarian, technical, calm. No marketing copy, no illustrations of people, no decorative gradients. Think "developer tool" meets "Material 3 file manager".
>
> **Design system:** Material 3 (Material You). Use the `expressive` Material 3 component set where available (newer M3 buttons with shape morphing). Typography uses Material 3 type scale defaults. Iconography: Material Symbols Rounded.
>
> **Colour:**
> - Light theme: M3 baseline with `primary` from a deep teal (`#006B5F`) seed.
> - Dark theme: M3 dark from same seed; provide AMOLED true-black variant where `surface = #000000` and `surfaceContainer = #0A0A0A`.
> - Status colours: success `#1B873F`, error `#B3261E`, warning `#A8741A`, info `#1F69E0`.
> - Honour Android 12+ dynamic colour: when generating, show one variant with the user's wallpaper colour (use `#7B5DD8` as the dynamic-sample seed).
>
> **Layout:**
> - Phone width target 360dp; tablet 800dp+; foldable inner display 712dp.
> - Page horizontal padding: 16dp on phone, 24dp on tablet.
> - List item vertical padding: 12dp. Card corner radius: 16dp.
> - Bottom safe area padding 16dp; FAB bottom-end position `(end:16, bottom:16)` plus FAB size.
>
> **Components used across screens:**
> - `CenterAlignedTopAppBar` for primary screens; `MediumTopAppBar` (collapsing) only for File Details.
> - `FilledTonalButton` for secondary actions; `Button` (filled) for primary CTA; `OutlinedButton` for tertiary.
> - `ListItem` (M3 three-line) for all list rows.
> - `AssistChip` / `FilterChip` / `SuggestionChip` per use.
> - `ElevatedCard` for summary blocks.
> - `LinearProgressIndicator` (determinate) for inline progress; `CircularProgressIndicator` (determinate, size 160dp) for big progress.
> - Bottom sheets for dialogs (modal, draggable handle on top).
>
> **Accessibility:**
> - All tappable targets ≥ 48dp.
> - Min text size `bodyMedium` for any user-visible text.
> - Long titles wrap to 2 lines with ellipsis on line 2.
> - Status chips include both colour and an icon (so colour-blind users still parse).
>
> **What never appears in this app:**
> - Avatars, user profiles, social-share illustrations, "premium" badges, subscription pricing, login screens, signup flows, banners, ads, promo carousels, "rate us" prompts.
> - Pure white backgrounds in dark mode. Pure black backgrounds in light mode.

---

## Generic per-screen wrapper

When you paste an individual screen prompt, prepend this single line so Stitch keeps consistency:

> *Continue the Video Splitter project. Use the project-level theme. Generate light and dark variants. Use Material 3 components. Sample data uses real Indian-cinema 4K filenames as listed.*

---

## Sample data palette (use these everywhere)

So screens look filled and realistic, use these strings as filler instead of lorem ipsum.

**Movie titles + folder names**

| Cleaned title | Filename to display | Size | Duration |
|---|---|---|---|
| `Baahubali The Epic (2025)` | `Baahubali The Epic 2025 2160p NEWEB-DL DUAL DTS HD MA DDP5.1 H.265.mkv` | 47.2 GB | 2:13:12 |
| `Kantara Chapter 1 (2024)` | `www.5MovieRulz.graphics - Kantara.Chapter.1.2024.4K.WEB-DL.x265.mkv` | 38.6 GB | 2:31:30 |
| `Karuppu (2026)` | `www.5MovieRulz.software - Karuppu.2026.Tamil.4K.WEB-DL.x265.mkv` | 22.9 GB | 2:31:30 |
| `Devara (2024)` | `Devara.2024.2160p.HDR.HEVC.mkv` | 28.4 GB | 2:55:08 |
| `Salaar (2023)` | `Salaar.Part1.Ceasefire.2023.4K.BluRay.HEVC.TrueHD.mkv` | 51.3 GB | 2:55:00 |

**Statuses for jobs list**

- 🟢 Done · 7.6 GB · 2h ago
- 🔵 Running · part 3 of 7 · 47% · 82 MB/s
- ⚪ Queued · waiting on previous job
- 🔴 Failed · insufficient storage
- ⚫ Cancelled · 12 min ago

**Codec strings (for File Details screen)**

```
Video    HEVC (H.265 Main 10)  3840×2160  24fps  HDR10
Audio 1  DTS-HD MA   Telugu    6 ch  48 kHz   1535 kbps
Audio 2  E-AC3       Hindi     6 ch  48 kHz    640 kbps
Audio 3  AAC LC      Malayalam 2 ch  48 kHz    192 kbps
Subtitle 1  ASS  English  default
Subtitle 2  ASS  Telugu
Container   Matroska (.mkv)   Avg bitrate 47.8 Mb/s   Attachments 4 fonts
```

---

## What to do after Stitch generates

1. Download each screen's HTML/CSS export.
2. Save to `Kotlin APK\Design\<screen>_<variant>\` per the table above.
3. Don't edit Stitch outputs by hand — if you want changes, re-prompt.
4. The agent (Phase 3) reads these as visual reference for Compose translation. It will not copy raw HTML; it produces equivalent Compose layouts.

## Style guardrails specific to this app

- **No skeumorphic film reels, no clapperboards, no "play" triangles.** This is a utility, not a media player.
- **No purple-pink gradients.** The seed colour above gives a calm teal.
- **No emoji-heavy UI.** Status chips use Material Symbols icons, not emoji.
- **Numeric values right-aligned** in any two-column layout (e.g. file sizes, durations).
- **Monospace** for technical strings (codec names, bitrates, hashes).
