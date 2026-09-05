# VideoFlow Editor UX Architecture — UI Step 1

## Scope

UI Step 1 is a presentation-layer refactor on top of the existing native Android Step 1–3 implementation. It does not change the project format, Room schema, source identity model, SAF content-URI architecture, proxy engine, timeline domain model, preview semantics, RenderPlan, final renderer, or export engine.

The goal is to replace the former long vertically scrolling engineering interface with a stable mobile editing workspace in which preview, playback, timeline, and editing tools remain in a predictable relationship.

## Previous editor structure

The previous `EditorScreen` was a single large `LazyColumn` that directly owned preview, transport, Media Bin cards, proxy controls, waveform controls, timeline controls, tracks, selected clip inspector, text/image controls, snapshots, and technical notes. This made the timeline secondary to asset and engineering controls and required substantial parent-page scrolling.

## New editor structure

`EditorScreen` is now an orchestration layer around these responsibilities:

- `EditorTopBar` — Back, project identity, transient save state, Undo, Redo, Export.
- `PreviewWorkspace` — persistent project-aspect preview using the existing native playback/overlay semantics.
- `TransportBar` — current time, jump-to-start, play/pause, total duration.
- `TimelineWorkspace` — permanent timeline viewport with ruler, playhead, internal track scrolling, zoom, clip/overlay selection, clip movement, compact track headers, and track settings entry.
- `EditorBottomToolbar` — persistent primary or selection-context toolbar.
- `EditorPanelHost` — Material 3 contextual sheets for media, audio, overlays, canvas, track settings, snapshots, media details/proxy controls, and existing advanced editing operations.
- `EditorWarningBanner` — friendly non-blocking source-status warnings.
- `VideoFlowEditorColors` — editor-specific dark workspace tokens.

The parent editor surface is no longer the mechanism for discovering tools. Timeline content scrolls internally; contextual tools are disclosed through the bottom toolbar and sheets.

## Selection model

Presentation selection is explicit through `EditorSelection`:

- `None`
- `Clip`
- `Track`
- `TextOverlay`
- `ImageOverlay`

Clip selection is synchronized with the existing `EditorViewModel.selectedClipId` so established backend operations continue to operate on the selected clip. Text/image selection stays presentation-local and routes to the existing overlay mutation APIs.

The bottom toolbar changes by selection:

- No selection: Media, Audio, Text, Overlay, Canvas, More.
- Video clip: Split, Trim, Speed, Crop, Volume, More.
- Audio clip: Split, Volume, Fade, Speed, More.
- Text: Edit, Style, Transform, Opacity, Keyframe, More.
- Image: Transform, Opacity, Duration, Keyframe, More.

## Panel model

`EditorPanel` allows one primary contextual surface at a time. Media, audio, overlay, canvas, snapshots, track settings, media details, clip tools, text tools, and image tools all use the same panel host instead of permanently consuming editor height.

Android Back is resolved in this order in the editor workspace:

1. close the active contextual sheet;
2. clear active selection;
3. leave the editor.

Destructive/identity-sensitive flows remain dialogs: track deletion, duplicate source confirmation, and weak relink confirmation.

## Media and SAF architecture

Media import and relink inside the editor reuse `ProjectViewModel` and `ActivityResultContracts.OpenDocument`. There is no raw-path picker and no replacement import system. Imported files remain content-URI references and continue through the existing source validation, duplicate detection, permission, metadata, fingerprint, and relink policy.

Media cards are removed from the permanent editor surface. The Media sheet displays user-relevant identity, resolution/duration, source warnings, Add, and Details. Advanced codec/source/proxy information is progressively disclosed under Media Details.

## Proxy UX

The proxy engine is unchanged. Everyday 540p/720p/1080p engineering buttons are removed from the permanent editor workspace. When a ready proxy is used, preview can show a small `Proxy` badge. Media Details exposes the existing `PERFORMANCE`, `BALANCED`, and `HIGH` proxy qualities, cancellation, and deletion without changing proxy persistence or source-for-export rules.

## Preview strategy

`PreviewWorkspace` preserves the existing native preview behavior:

- ready proxy preferred for editing preview when available;
- original content URI used when source is available and no ready proxy is selected;
- original source remains the render source through the unchanged render/export architecture;
- clip source timing and speed;
- crop, position, scale, rotation, flip, opacity;
- transform/opacity keyframe evaluation;
- text overlays and text keyframes;
- image overlays and image keyframes;
- track/clip gain, fades, mute/solo effective-audio behavior.

The preview surface is sized by available constraints and project aspect ratio rather than a fixed phone-only height.

## Timeline strategy

The timeline remains visible in the editor viewport and owns its own scrolling/zoom behavior. It provides:

- adaptive ruler intervals;
- clear playhead;
- horizontal time scrolling;
- vertical track scrolling for multi-track projects;
- pinch zoom and compact +/- zoom controls;
- compact fixed track header with name, settings, visibility/mute, and lock;
- thumbnails and cached waveforms;
- selected clip/overlay emphasis;
- direct clip tap selection;
- empty-space seek and selection clear;
- existing snapped clip movement.

Empty projects hide technical empty track rows and display a simple `Start your video` state while the primary Media tool remains available at the bottom.

## Adaptive layout

Portrait uses a stable vertical composition of preview, optional warning, transport, and timeline with the bottom toolbar provided by `Scaffold`.

Landscape/expanded width uses a two-column upper workspace: preview on the left and lightweight context/project information on the right; transport and timeline remain full-width below. This prevents simply rotating the former long phone page.

The implementation uses `BoxWithConstraints` for the editor workspace and preview. UI Step 3 can extend this foundation to richer tablet inspectors/window-size-class behavior without changing the Step 1 domain architecture.

## Performance strategy

High-frequency playhead state is kept in the main workspace and passed only to components that require it. Media, proxy, and snapshot collections are contained in contextual sheets rather than the always-visible tree. Existing thumbnail/waveform caches are displayed rather than regenerated by the UI components.

Opening and closing contextual sheets does not intentionally start proxy jobs, thumbnail jobs, imports, or project writes. Durable editing operations continue through existing ViewModels/services. Selection and timeline zoom are presentation/session state and are not continuously written to Room.

## Accessibility strategy

Critical toolbar and transport controls expose content descriptions. Track visibility/mute, lock, and settings actions are labeled with the track name. Timeline ruler/keyframe decoration is excluded from TalkBack. Clip/overlay items expose grouped semantic descriptions and selected state. Bottom-tool items retain larger touch containers than their icon visuals.

## No-sacrifice boundary

The refactor deliberately reuses the existing `EditorViewModel`, `ProjectViewModel`, `OverlayAdvancedViewModel`, `TrackLifecycleViewModel`, native preview components, Room-backed repositories, SAF import/relink, proxy manager, snapshots, history, timeline engine, and existing export route. UI Step 1 does not introduce a second editor/domain implementation.
