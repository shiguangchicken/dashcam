---
name: DroidDash
colors:
  surface: '#10141a'
  surface-dim: '#10141a'
  surface-bright: '#353940'
  surface-container-lowest: '#0a0e14'
  surface-container-low: '#181c22'
  surface-container: '#1c2026'
  surface-container-high: '#262a31'
  surface-container-highest: '#31353c'
  on-surface: '#dfe2eb'
  on-surface-variant: '#e2bfb0'
  inverse-surface: '#dfe2eb'
  inverse-on-surface: '#2d3137'
  outline: '#a98a7d'
  outline-variant: '#5a4136'
  surface-tint: '#ffb693'
  primary: '#ffb693'
  on-primary: '#561f00'
  primary-container: '#ff6b00'
  on-primary-container: '#572000'
  inverse-primary: '#a04100'
  secondary: '#98cbff'
  on-secondary: '#003354'
  secondary-container: '#00a2fd'
  on-secondary-container: '#003558'
  tertiary: '#4ae183'
  on-tertiary: '#003919'
  tertiary-container: '#00b05c'
  on-tertiary-container: '#003a1a'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffdbcc'
  primary-fixed-dim: '#ffb693'
  on-primary-fixed: '#351000'
  on-primary-fixed-variant: '#7a3000'
  secondary-fixed: '#cfe5ff'
  secondary-fixed-dim: '#98cbff'
  on-secondary-fixed: '#001d33'
  on-secondary-fixed-variant: '#004a77'
  tertiary-fixed: '#6bfe9c'
  tertiary-fixed-dim: '#4ae183'
  on-tertiary-fixed: '#00210c'
  on-tertiary-fixed-variant: '#005228'
  background: '#10141a'
  on-background: '#dfe2eb'
  surface-variant: '#31353c'
typography:
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  headline-sm:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  data-lg:
    fontFamily: JetBrains Mono
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 24px
  data-md:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
  label-caps:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '700'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  unit: 4px
  edge-margin: 24px
  gutter: 16px
  touch-target-min: 48px
  container-padding: 12px
---

## Brand & Style

The brand identity is built on **High-Reliability Utility**. The design system prioritizes immediate legibility and technical confidence, catering to drivers who require a "set-and-forget" tool that remains glanceable under diverse lighting conditions.

The visual style is a hybrid of **Corporate Modern** and **Glassmorphism**. It utilizes a dark-mode-first architecture to minimize cabin glare during night driving, paired with high-chroma safety accents that demand attention only when necessary. Surface logic relies on semi-transparent overlays to maintain context with the live camera feed, ensuring the interface feels like an integrated HUD (Heads-Up Display) rather than a separate layer.

## Colors

The palette is optimized for high-contrast visibility and reduced eye strain.

- **Primary (Safety Orange):** Reserved for recording indicators, emergency save buttons, and critical alerts.
- **Secondary (Cyber Blue):** Used for "active" but non-critical states, such as cloud synchronization, Wi-Fi connectivity, and remote view.
- **Success (Emerald Green):** Indicates healthy system status, full battery, and completed uploads.
- **Error/Warning (Crimson Red):** Used for hardware failures, storage errors, or high-heat warnings.
- **Neutrals:** A deep "Obsidian" base (#0D1117) provides the primary canvas, with "Dark Slate" (#161B22) used for secondary containers and cards to create depth without introducing grey-wash haze.

## Typography

This design system employs a dual-font strategy to separate intent:
1. **Inter (Sans-Serif):** Used for all UI controls, navigation, and system messaging. Its high x-height ensures readability at arm's length while the device is mounted.
2. **JetBrains Mono (Monospace):** Used exclusively for telemetry and technical data (GPS coordinates, KM/H, Bitrate, Timecodes). The fixed width prevents "jittering" layouts as numbers change rapidly.

**Mobile Scaling:** For mobile playback, `headline-lg` should scale down to 24px to ensure critical status messages do not obscure the video evidence.

## Elevation & Depth

This design system uses **Tonal Layering** combined with **Backdrop Blurs** to create a focused hierarchy:

1.  **Base Layer:** The camera feed or a deep #0D1117 background.
2.  **Glass Layer:** Controls that sit directly on top of the video use a 20px backdrop blur with a 10% white tint and a 1px border (#FFFFFF15). This ensures text remains readable regardless of the colors in the video feed.
3.  **Floating Elements:** Critical alerts use an "Ambient Shadow" (0px 8px 24px) tinted with the Primary Safety Orange to create a localized glow, signaling urgency without being visually abrasive.

## Shapes

The design system uses a **Soft** shape language (4px - 8px radius) to maintain a professional, technical aesthetic. 
- **Standard UI elements:** 4px (0.25rem) radius for buttons and input fields.
- **Information Cards:** 8px (0.5rem) radius for secondary groupings.
- **Status Indicators:** Perfect circles for "Recording" or "GPS Active" dots to differentiate them from interactive buttons.

Sharp corners are avoided to prevent the UI from feeling too "aggressive," while high-radius pills are avoided to maintain the technical, utility-focused vibe.

## Components

- **Buttons:** Primary buttons use a solid Safety Orange background with black text for maximum contrast. Secondary buttons use an outlined style with Cyber Blue. All buttons must have a minimum height of 56px for in-car accessibility.
- **Status Chips:** Small, high-contrast badges located at the top of the viewport. Use specific icons for:
    - *GPS:* Satellite icon (Blue when locked, Red when searching).
    - *Heat:* Thermometer icon (Turns Red at &gt;70°C).
    - *Storage:* SD Card icon with a percentage bar.
- **Telemetry Overlay:** A monospaced data block positioned in the bottom-right corner. It uses a 60% black background blur to ensure white text pops.
- **Recording Button:** A large, circular floating action button (FAB) with a pulsing Safety Orange outer ring when active. 
- **Input Fields:** Dark Slate backgrounds with a 1px Cyber Blue focus border. Labels are always persistent (not floating) to ensure constant clarity of what a field represents.
- **Event Cards:** List items for "Incidents" use a 4px left-border strip in Crimson Red to denote hard-braking or collision events in the timeline.