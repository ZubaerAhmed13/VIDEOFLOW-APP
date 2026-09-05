# VideoFlow Android UI Step 2 — Direct Manipulation UX

## Principle

The preview is the place where visual edits happen. The contextual panel provides precision and accessibility alternatives.

## Preview content geometry

`PreviewContentGeometry` defines:

- viewport rectangle
- actual project/content rectangle
- project width and height

It provides centralized `screenToProjectNormalized` and `projectNormalizedToScreen` conversion. A unit test verifies round-trip conversion and rejects touches in letterbox space.

## Coordinate contract

Persistent transform/crop values are project-normalized values, never phone pixels.

`screen pointer → project content rectangle → normalized project coordinates → existing domain model`

This keeps edits stable across device sizes, app orientation and project aspect ratios.

## Letterbox safety

The editor calculates visual edits inside the project frame rather than the whole screen. `screenToProjectNormalized` returns no coordinate outside the project rectangle, preventing black letterbox regions from producing invalid project positions.

## Crop interaction

`CropInteractionOverlay` is active only while the Crop tool is active. It:

- dims the region outside the crop;
- draws the crop boundary;
- provides corner handles;
- uses larger invisible hit regions than the visible handle;
- supports left/right/top/bottom edges and all four corners;
- supports moving the current crop region;
- stores only normalized 0..1 bounds.

Ratio presets are applied in normalized source space and stay inside source bounds. Precise edge sliders remain available as a non-gesture alternative.

## Transform interaction

`TransformInteractionOverlay` is active only while Transform is active. Compose `detectTransformGestures` supplies:

- pan → normalized X/Y delta
- pinch → multiplicative scale
- rotation → angle delta

The same persistent transform is used by clip, text and image editing through shared ViewModel/domain paths.

## Selection and guides

Transform mode draws a selection outline and center guides when the object's normalized center approaches 50% horizontally or vertically. Direct manipulation is intentionally hidden in normal preview mode to avoid gesture conflicts.

## Snapping

The direct-manipulation overlay visually indicates center alignment. Existing timeline snapping remains owned by the existing timeline/domain implementation; Step 2 does not replace its precision math.

## Hit testing

Crop handle hit testing uses a larger hit radius than the visible handles. Direct overlay selection/hit-cycle behavior outside an active tool remains an incremental enhancement; Step 2 does not invent an inaccurate screen-pixel selection system.

## Gesture conflicts

- normal preview: playback/selection behavior only;
- Crop mode: crop manipulation only;
- Transform mode: pan/pinch/rotation only.

Opening Crop or Transform pauses playback so pointer gestures cannot simultaneously control media playback and edit geometry.

## History

Live Crop/Transform changes route through coalesced semantic history. High-frequency pointer changes are not a new project schema and are not copied into source media.

## Orientation/aspect safety

Normalized coordinates make the storage independent of app orientation. Physical review still must verify portrait/landscape project and device combinations on the exact Review APK before Step 2 can be declared complete.
