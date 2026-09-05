# VideoFlow Android UI Step 2 — Contextual Editing UX

## Scope

UI Step 2 keeps the approved Step 1 editor shell intact: Top Bar → Preview → Transport → Timeline → Bottom Toolbar. Editing operations are no longer opened as unrelated technical forms. Selection determines a compact contextual toolbar and one active contextual tool at a time.

## Active tool architecture

`EditorTool` is the single presentation state for editing operations. Current tool types are Trim, Speed, Crop, Transform, Opacity, Volume, Fade, TextEditor, TextStyle, Timing, Keyframes and More. Passive resource/settings surfaces such as Media, Audio, Canvas, Track Settings and Snapshots remain `EditorPanel` instances.

This separation prevents a matrix of unrelated `showX` booleans and gives Android Back, Cancel and Done one predictable hierarchy.

## Selection model

The contextual workflow is:

`SELECT → EDIT → SEE RESULT`

- no selection: Media, Audio, Text, Overlay, Canvas, More
- video clip: Split, Trim, Speed, Crop, Volume, More
- audio clip: Split, Trim, Volume, Fade, Speed, More
- text overlay: Edit, Style, Transform, Opacity, Keyframe, More
- image overlay: Transform, Opacity, Timing, Keyframe, More

Every visible editing action routes to an existing domain/ViewModel operation. Unsupported backend features are intentionally not exposed.

## Transient and durable state

Preview-then-commit tools keep temporary presentation values locally where appropriate. Durable values remain owned by the existing editor repositories/services and Room model.

High-frequency pointer movement is never written directly to a new UI database schema. Crop/Transform live updates use the existing normalized domain model and coalesced history path; Trim, Speed, Text Style and Timing keep local draft values until Done.

## Commit models

Two interaction models are used deliberately:

1. **Preview then Commit** — Trim, Speed, Text Style, Timing and text creation/editing. Done validates and commits; Cancel discards the draft.
2. **Live Commit with Coalescing** — Crop, Transform, Opacity, Volume and Fade. Preview changes immediately; coalesced history prevents pointer/slider movement from becoming dozens of undo records. Cancel restores the captured pre-tool value for the relevant live-edit tools.

Keyframe add/remove/interpolation are explicit live actions rather than hidden draft changes.

## Cancel, Done and Reset

- Cancel for preview-then-commit tools discards all transient values.
- Cancel for live tools restores the captured pre-tool state.
- Done closes the contextual tool and returns to the selected-item toolbar.
- Reset is available for Trim, Speed, Crop, Transform, Opacity, Volume and Fade.
- Reset never silently removes an animation/keyframe set.

## Back hierarchy

Android Back follows this order:

1. leave/cancel the active contextual tool;
2. close the passive panel;
3. clear the current selection;
4. leave the editor.

A tool therefore cannot cause an unexpected immediate editor exit.

## Undo/redo

All new edits continue through the existing semantic history architecture. Transform and crop use `recordCoalesced` keys so continuous gesture updates merge into the same logical edit while updates continue within the coalescing window. Trim commits one `ClipHistoryEntry`. Text/image duplication and keyframe mutations also create semantic history entries.

## Adaptive presentation

Phone portrait uses a contextual bottom sheet while retaining the fixed preview/timeline editor shell. Existing Step 1 portrait, landscape and expanded-width workspace layouts remain protected. Compact landscape continues to prioritize preview and timeline rather than returning to a long scrolling editor form.

Full tablet visual polish remains a UI Step 3 responsibility; Step 2 does not change project architecture to achieve it.

## Accessibility

Critical functions have non-gesture controls:

- Transform: normalized X/Y, scale and rotation sliders plus direct gestures.
- Crop: ratio presets and four edge sliders plus direct handles.
- Trim: visual range handles plus exact Start/End/Duration readout.
- Speed, Opacity, Volume, Fade and Timing: labeled sliders/values.
- Keyframes: semantic add/remove and previous/next controls.

Toolbar buttons retain at least the approved Step 1 touch-target structure and remain horizontally scrollable under larger font scale.

## Performance and backend authority

The UI does not copy source media, decode source-sized 4K bitmaps for editing controls, add INTERNET/WebView dependencies, or change project format solely for presentation state. Existing media references, proxy/thumbnail/waveform caches, PreviewPlan/RenderPlan domain state and native export remain authoritative.

## Explicitly not added

No fake effects, transitions, AI, easing curves, text stroke/shadow, pitch correction or unsupported font-family storage is exposed. Those require genuine backend support before they can appear in the editor.
