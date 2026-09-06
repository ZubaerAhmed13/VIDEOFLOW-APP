# VideoFlow Accessibility — UI Step 3

## Scope

This document covers product-level accessibility for Onboarding, Home, New Project, Editor integration, Export and Settings. It supplements the existing editor semantics from UI Step 1/2 and does not claim a physical TalkBack certification that has not been performed.

## Implemented Product Semantics

- Project cards expose a combined spoken description with project name and available user-facing metadata.
- Settings and navigation actions use labeled Material controls.
- New Project aspect choices use real selected-state `FilterChip` semantics.
- Export progress exposes `ProgressBarRangeInfo` backed by the persisted real export percentage.
- Export actions use text labels (`Export Video`, `Cancel Export`, `Open Video`, `Share`, `Done`) rather than icon-only controls for critical actions.
- Source recovery uses explicit `Locate Original` / `Review Source` actions.
- Destructive project/proxy/export cancellation actions require text confirmation; colour is not the only cue.

## Font Scaling

Product screens rely on scrollable `LazyColumn` content and vertically growing Material components rather than fixed text heights. Horizontal option groups can scroll. This is intended to preserve reachability at 150% and 200% font scale.

Automated compilation does not prove physical 150%/200% usability. Those checks remain part of the physical-device review checklist.

## Touch Targets

Major actions use Material 3 `Button`, `OutlinedButton`, `TextButton`, `IconButton`, `FilterChip`, `ListItem` and `Card` components, retaining platform touch-target behavior.

## Contrast and Non-Colour Cues

Material colour roles provide light/dark contrast behavior. Warnings, errors, success and destructive actions always include text. Selected chips expose selected state in semantics rather than relying only on colour.

## Progress Announcements

The product UI exposes a single real progress value and friendly export stage. It does not synthesize fake 1% timer updates or ETA text. A future TalkBack device pass should verify announcement throttling in the rendered service/job flow.

## Keyboard / IME

The New Project name, Rename and Export filename use standard editable Material text fields. The existing editor text workflow remains protected. Physical hardware-keyboard/IME edge cases require device/emulator interaction and are not pre-certified by this document.

## Motion

No decorative export timer is used. VideoFlow follows Android animation-scale behavior. Step 3 does not add timeline animation purely for decoration.

## Required Physical Checks Before Final Approval

- TalkBack traversal: Onboarding, Home, project card, New Project, Editor toolbar, Export, Settings.
- 150% font scale.
- 200% font scale.
- enlarged display size.
- portrait and landscape.
- minimum practical phone width around 360dp.
- tablet/large-window interaction.
- keyboard/IME reachability for project name and export filename.

Until these are run on a real device, physical accessibility remains **NOT VERIFIED** rather than PASS.
