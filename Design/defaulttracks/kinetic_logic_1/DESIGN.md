---
name: Kinetic Logic
colors:
  surface: '#141315'
  surface-dim: '#141315'
  surface-bright: '#3a393b'
  surface-container-lowest: '#0f0e10'
  surface-container-low: '#1c1b1d'
  surface-container: '#2b292c'
  surface-container-high: '#2b292c'
  surface-container-highest: '#363436'
  on-surface: '#e6e1e4'
  on-surface-variant: '#bec9c5'
  inverse-surface: '#e6e1e4'
  inverse-on-surface: '#313032'
  outline: '#889390'
  outline-variant: '#49454f'
  surface-tint: '#83d5c6'
  primary: '#9ff2e2'
  on-primary: '#003731'
  primary-container: '#006b5f'
  on-primary-container: '#005d52'
  inverse-primary: '#016b5f'
  secondary: '#cdbdff'
  on-secondary: '#34275e'
  secondary-container: '#4e2ca9'
  on-secondary-container: '#bfaff0'
  tertiary: '#ffdbc9'
  on-tertiary: '#502405'
  tertiary-container: '#ffb68c'
  on-tertiary-container: '#7a4524'
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
  on-secondary-fixed: '#1f1048'
  on-secondary-fixed-variant: '#4b3e76'
  tertiary-fixed: '#ffdbc9'
  tertiary-fixed-dim: '#ffb68c'
  on-tertiary-fixed: '#321200'
  on-tertiary-fixed-variant: '#6c3a19'
  background: '#141315'
  on-background: '#e6e1e4'
  surface-variant: '#363436'
typography:
  headline-lg:
    fontFamily: Roboto Flex
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Roboto Flex
    fontSize: 24px
    fontWeight: '500'
    lineHeight: 32px
  headline-sm:
    fontFamily: Roboto Flex
    fontSize: 20px
    fontWeight: '500'
    lineHeight: 28px
  body-lg:
    fontFamily: Roboto Flex
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
  code-sm:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '400'
    lineHeight: 14px
  headline-lg-mobile:
    fontFamily: Roboto Flex
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  gutter: 16px
  margin-mobile: 16px
  margin-desktop: 24px
---

## Brand & Style

The design system is engineered for technical precision and operational clarity. It targets developers, data engineers, and system architects who require high-density information without cognitive fatigue. The visual style is **Corporate / Modern** with a distinct technical edge, leveraging the structural integrity of Material Design 3 principles. 

The aesthetic is defined by a crisp "industrial-digital" feel—utilizing a sophisticated palette of deep teals and vibrant purples to differentiate logical flows. It emphasizes functionality through high-contrast interactions, structured grids, and a systematic approach to hierarchy that favors clarity over decoration.

## Colors

The palette is optimized for a dark-mode-first technical environment. 

- **Primary Teal** (#83d5c6) is the signature action color, representing "Flow" and "Logic." It is supported by a deep teal container for grouped actions.
- **Secondary Purple** (#cdbdff) is used for "Data" and "Configuration" elements, providing a clear visual distinction from primary logical paths.
- **Surface Strategy**: The background uses a near-black neutral (#1C1B1D). Structural elements use `surface-container` to create depth, delineated by `outline-variant` borders rather than heavy shadows.
- **Feedback**: The Error tokens follow a high-visibility high-contrast red scale for critical system failures or validation errors.

## Typography

Typography is split between human-centric UI and machine-centric data.

- **Roboto Flex** is the primary typeface for all UI elements, headings, and body copy. Its variable axes allow for fine-tuned weight adjustments to maintain legibility on dark backgrounds.
- **JetBrains Mono** is reserved for technical readouts, code snippets, metadata labels, and status indicators. This ensures that alphanumeric strings (like IDs or hex codes) are easily scannable.
- **Scale**: Headlines use a tight tracking (-0.02em) to appear more robust, while technical labels use standard monospaced spacing for clarity.

## Layout & Spacing

This design system employs a **12-column fluid grid** for desktop and a **4-column grid** for mobile. 

- **The 4px Rule**: All spacing increments are multiples of 4px.
- **Layout Rhythm**: Use 16px (md) for standard gutters and internal card padding. Use 24px (lg) for vertical section spacing and page margins on larger screens.
- **Reflow**: On mobile devices, side-by-side card layouts should stack vertically, and horizontal navigation should collapse into a bottom sheet or a standard hamburger menu.

## Elevation & Depth

Depth is achieved through **Tonal Layers** rather than traditional shadows. 

- **Level 0**: Background (`#1C1B1D`).
- **Level 1**: Cards and containers (`surface-container`). 
- **Borders**: All containers must feature a 1px solid border using the `outline-variant` token. This provides a "blueprint" feel that suits the technical nature of the brand.
- **Interactions**: On hover or active states, elevation is communicated by increasing the opacity of the surface overlay or brightening the primary/secondary color, never by adding a drop shadow.

## Shapes

The shape language is structured and "Rounded-Large" to balance the technical sharpness of the monospaced fonts.

- **Cards**: Must use `rounded-xl` (12px) to create a distinct containerized feel.
- **Small Components**: Buttons, input fields, and chips use `rounded-md` (8px) for a consistent, professional appearance.
- **Icons**: Utilize the **Material Symbols Outlined** set. Use a 20px optical size for labels and 24px for standalone actions.

## Components

- **Buttons**:
    - **Primary**: `primary_color_hex` background with `on-primary` text. High emphasis.
    - **Secondary**: `secondary-container` background with `secondary_color_hex` text.
- **Cards**: Always use `bg-surface-container`, `rounded-xl` (12px), and a 1px `outline-variant` border. No shadows.
- **Input Fields**: Ghost-style with `outline-variant` borders. Focus state transitions to a 2px `primary_color_hex` border.
- **Chips/Badges**: Use `secondary-container` for informational tags and `primary-container` for active status. Label text must be in `JetBrains Mono`.
- **Lists**: Technical lists should use `JetBrains Mono` for the primary text if displaying data (IDs, paths, tracks), with a 1px bottom divider in `outline-variant`.
- **Icons**: Material Symbols Outlined, consistent weight (standard 400).