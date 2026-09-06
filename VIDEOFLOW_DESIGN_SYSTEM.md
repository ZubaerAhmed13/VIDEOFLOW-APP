# VideoFlow Design System

## Purpose

VideoFlow uses a restrained native Android product system: clear information hierarchy for Home and Settings, a dark professional editing workspace, and progressive disclosure for expert controls.

## Core Principles

1. **Simple by default.** The common action is visually dominant.
2. **Advanced when requested.** Detailed codec, bitrate and colour controls live behind Advanced Settings.
3. **Editor continuity.** The fixed Preview → Transport → Timeline → Contextual Toolbar architecture remains visually stable.
4. **One restrained accent.** VideoFlow does not use competing brand colours for primary actions.
5. **No technical leakage.** Normal product surfaces use user language rather than database, URI, enum, renderer or codec implementation names.

## Colour Tokens

The implemented Compose theme exposes a compact core in `VideoFlowDesignTokens`:

| Token | Use |
|---|---|
| `BrandPrimary` | Primary action / selection in light surfaces |
| `BrandPrimaryDark` | Primary action / selection on dark surfaces |
| `Warning` | Warning semantics |
| `Success` | Completion semantics |
| `Error` | Destructive/error semantics |

Material 3 colour roles provide Background, Surface, SurfaceContainer, on-colours, divider/outline and disabled-state treatment. The editor keeps its established dark surfaces and timeline colours from UI Step 1/2 rather than being globally repainted by Step 3.

## Surface Hierarchy

- **Background:** application/page canvas.
- **Surface:** normal product cards and sheets.
- **SurfaceContainer:** grouped settings/export controls.
- **Editor background / editor surface:** inherited from protected UI Step 1/2 editor.
- **Timeline:** inherited from existing video/audio/overlay clip presentation.

## Spacing

Primary spacing follows a controlled 4dp grid:

- 4dp — micro separation
- 8dp — adjacent controls/chips
- 12dp — compact grouping
- 16dp — normal card/page spacing
- 24dp — strong section/panel spacing
- 32dp — expanded/tablet page margin when appropriate

No unique spacing value should be introduced merely for decoration.

## Corner / Container Policy

Material 3 component shapes are used consistently for cards, dialogs, bottom sheets, chips and buttons. Step 3 intentionally does not create a separate custom radius for every component.

## Typography

Material 3 typography roles are used semantically:

- `headlineMedium` / `headlineSmall` — primary onboarding/result titles
- `titleLarge` — page/card-group emphasis
- `titleMedium` — card title / section title
- `titleSmall` — control-group label
- `bodyLarge` / `bodyMedium` — primary explanatory text
- `bodySmall` — metadata and supporting copy
- `labelMedium` / `labelLarge` — compact product metadata/action context

Project names are single-line where necessary and ellipsize rather than colliding with toolbar actions.

## Button Hierarchy

- **Filled Button:** primary action (`New Project`, `Export Video`, positive completion action).
- **Outlined Button:** secondary action (`Start from Media`, `Advanced Settings`, location selection, Share where appropriate).
- **Text Button:** tertiary/navigation action (`Skip`, `Done`, dialog secondary actions).
- **Destructive:** explicit destructive wording plus confirmation; colour is never the only cue.

## Icons

Step 3 uses a coherent Material icon strategy. Emoji and random Unicode glyphs are not used as functional icons. Icons that convey state are accompanied by text or accessible descriptions when needed.

## Motion

Step 3 relies on platform/Material transitions and does not add decorative timeline animation. No artificial splash delay or fake export progress animation is permitted. Android animation-scale preferences remain authoritative.

## Responsive Behaviour

- 360dp is the compact phone target.
- 390–430dp is the standard phone range.
- landscape content remains scrollable/reachable.
- 600dp+ product shell uses increased page margins.
- the protected editor uses its adaptive Step 1/2 layout and remains the authority for preview/timeline composition.

## Accessibility Design Rules

- Major actions use native Material controls with appropriate touch targets.
- Selection uses selected state plus label, not colour only.
- Warning/error state includes text.
- export progress exposes progress semantics.
- large-font layouts grow/scroll instead of relying on fixed text heights.
- project cards provide a combined spoken description.

## Product Vocabulary

Preferred user terms include:

`Media`, `Audio`, `Text`, `Overlay`, `Canvas`, `Split`, `Trim`, `Speed`, `Crop`, `Volume`, `Transform`, `Opacity`, `Fade`, `Keyframes`, `Export Video`, `Advanced Settings`, `Match Project`, `High Quality`, `Original file needed`, `Permission needed`.

Normal UI must not display internal source-status enum names, raw content URIs, Java exception classes, database IDs, raw bits-per-second numbers or renderer class names.
