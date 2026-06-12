---
name: Kinetic Logic
colors:
  surface: '#101413'
  surface-dim: '#101413'
  surface-bright: '#363a39'
  surface-container-lowest: '#0b0f0e'
  surface-container-low: '#181c1b'
  surface-container: '#1c201f'
  surface-container-high: '#272b2a'
  surface-container-highest: '#313634'
  on-surface: '#e0e3e1'
  on-surface-variant: '#bec9c5'
  inverse-surface: '#e0e3e1'
  inverse-on-surface: '#2d3130'
  outline: '#889390'
  outline-variant: '#3e4946'
  surface-tint: '#83d5c6'
  primary: '#83d5c6'
  on-primary: '#003731'
  primary-container: '#006b5f'
  on-primary-container: '#95e8d9'
  inverse-primary: '#006b5f'
  secondary: '#cdbdff'
  on-secondary: '#370492'
  secondary-container: '#4e2ca9'
  on-secondary-container: '#bda8ff'
  tertiary: '#ffb59e'
  on-tertiary: '#55200d'
  tertiary-container: '#8f4c36'
  on-tertiary-container: '#ffcebf'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#9ff2e2'
  primary-fixed-dim: '#83d5c6'
  on-primary-fixed: '#00201c'
  on-primary-fixed-variant: '#005047'
  secondary-fixed: '#e8deff'
  secondary-fixed-dim: '#cdbdff'
  on-secondary-fixed: '#20005f'
  on-secondary-fixed-variant: '#4e2ca9'
  tertiary-fixed: '#ffdbd0'
  tertiary-fixed-dim: '#ffb59e'
  on-tertiary-fixed: '#390b00'
  on-tertiary-fixed-variant: '#723521'
  background: '#101413'
  on-background: '#e0e3e1'
  surface-variant: '#313634'
typography:
  display-lg:
    fontFamily: Roboto Flex
    fontSize: 57px
    fontWeight: '400'
    lineHeight: 64px
    letterSpacing: -0.25px
  headline-lg:
    fontFamily: Roboto Flex
    fontSize: 32px
    fontWeight: '400'
    lineHeight: 40px
  headline-md:
    fontFamily: Roboto Flex
    fontSize: 28px
    fontWeight: '400'
    lineHeight: 36px
  title-lg:
    fontFamily: Roboto Flex
    fontSize: 22px
    fontWeight: '500'
    lineHeight: 28px
  body-lg:
    fontFamily: Roboto Flex
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0.5px
  body-md:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.25px
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 16px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  phone_margin: 16dp
  tablet_margin: 24dp
  list_padding: 12dp
  gutter: 8dp
  touch_target_min: 48dp
---

## Brand & Style

This design system is built for high-performance video processing, blending the robust utility of a developer tool with the refined structure of Material 3. The personality is **utilitarian, technical, and calm**, prioritizing data density and functional clarity over decorative flair.

The design style is **Corporate / Modern**, specifically adhering to the **Material 3 (Material You)** specification. It leverages a systematic approach to depth and color to guide the user through complex 4K video operations—splitting, merging, and transcoding—without visual fatigue. The aesthetic is "engineering-first," utilizing precision-aligned grids and monospaced technical readouts to instill confidence in the lossless nature of the utility.

## Colors

The palette is rooted in a **Deep Teal (#006B5F)** primary seed, providing a professional and focused environment. 

### Color Modes
- **Light Theme:** Standard M3 baseline tokens for surfaces and roles.
- **Dark Theme:** High-efficiency AMOLED true-black variant. The primary `surface` is set to `#000000`, while `surfaceContainer` roles use a slightly elevated `#0A0A0A` to maintain subtle separation.
- **Dynamic Color:** When dynamic seeding is active (example seed `#7B5DD8`), the system mapping must maintain the tonal contrast ratios required by the M3 specification.

### Semantic Roles
Success, error, warning, and info colors are strictly defined to ensure critical system states (like "Process Complete" or "Low Disk Space") are instantly recognizable. Technical values and progress indicators should use the primary teal to signify active system health.

## Typography

The typography scale follows the **Material 3** defaults using **Roboto Flex** for its adaptability. For technical metadata (codecs, timestamps, file sizes), **JetBrains Mono** is employed to provide a distinct "developer tool" feel and ensure numerical alignment in lists.

### Implementation Rules
- **Filenames:** Use `body-lg` for 4K video filenames (e.g., `RRR_2022_UHD_HEVC.mkv`).
- **Technical Readouts:** Use `label-md` or `label-sm` in monospaced font for bitrates and frame counts.
- **Numeric Values:** All numeric data in tables or lists must be **right-aligned** to allow for easy comparison.
- **Accessibility:** Never use a size smaller than `body-md` (14px) for interactive text.

## Layout & Spacing

This design system uses a **Fluid Grid** that adapts to specific Android form factors:
- **Phone (360dp+):** 4 columns, 16dp outer margins.
- **Foldable (712dp+):** 8 columns, 24dp margins.
- **Tablet (800dp+):** 12 columns, 24dp margins, utilizing a navigation rail or permanent drawer.

### Spacing Rhythm
- **List Items:** Vertical padding is set to 12dp to increase data density without sacrificing tap accuracy.
- **Touch Targets:** All interactive elements must maintain a minimum hit area of **48dp x 48dp**.
- **Alignment:** Technical strings and file extensions should be treated as distinct blocks with consistent gutters to maintain the "grid" feel of a file manager.

## Elevation & Depth

Hierarchy is established through **Tonal Layers**, following the M3 surface-elevation system. 

### Elevation Levels
- **Level 0 (Surface):** The background (#000000 in Dark/AMOLED).
- **Level 1 (Surface Container):** Used for cards and app bars (#0A0A0A).
- **Level 2+:** Used for floating action buttons and menus, using tonal overlays rather than heavy shadows.

In the AMOLED variant, separation is achieved primarily through these subtle tonal shifts and high-contrast borders (0.5dp or 1dp) where necessary to define boundaries. Shadows should be minimal, crisp, and neutral to keep the UI feeling "light" and technical.

## Shapes

The shape language is defined as **Rounded**, aligning with the "Material Symbols Rounded" iconography.

- **Standard Cards:** 16dp corner radius (`rounded-lg`).
- **Buttons & Chips:** 8dp to 12dp radius depending on size.
- **Progress Bars:** Fully rounded (pill) ends to indicate fluid movement.
- **Bottom Sheets:** 28dp top-corner radius for a soft, modern enclosure.

## Components

### Navigation & Headers
- **CenterAlignedTopAppBar:** Used for primary tool views (e.g., "Video Splitter").
- **MediumTopAppBar:** Used for library views where titles might be longer or require sub-text.

### Buttons & Interaction
- **Filled Button:** Used for the primary "Process" or "Export" actions.
- **FilledTonalButton:** Used for secondary actions like "Add File" or "Clear Queue."
- **OutlinedButton:** Used for tertiary or destructive actions that require confirmation.
- **AssistChip:** Used for technical metadata tags (e.g., "4K", "HEVC", "60FPS").

### Data Representation
- **ListItem (M3):** Standard container for file queues. Should include a leading icon (video file) and trailing monospaced metadata.
- **ElevatedCard:** Used for grouping "Output Settings" or "Splitting Logic" parameters.
- **LinearProgressIndicator:** Positioned at the top of the list or fixed at the bottom for background splitting tasks.
- **CircularProgressIndicator:** Used for indeterminate states during file indexing or metadata fetching.

### Iconography
- Use **Material Symbols Rounded**. 
- Key Icons: `content_cut` (Split), `merge` (Merge), `settings_ethernet` (Lossless), `folder_open` (Browse).