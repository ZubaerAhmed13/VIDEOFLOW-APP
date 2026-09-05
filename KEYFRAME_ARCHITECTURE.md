# VideoFlow Android Step 2 — Keyframe Architecture

## Generic ownership

Keyframes are generic persisted entities, not one-off opacity fields. Owners are `CLIP`, `TEXT_OVERLAY` or `IMAGE_OVERLAY`. Supported properties are `POSITION_X`, `POSITION_Y`, `SCALE_X`, `SCALE_Y`, `ROTATION`, `OPACITY` and `AUDIO_GAIN` where the owner type supports them.

## Time model

`timeUs` is a 64-bit microsecond position local to the owner. Moving a clip therefore does not rewrite its animation timing. When a clip is split, existing backend split semantics retain/rebase owner-local keyframes rather than converting them to screen/project absolute time.

## Interpolation

Only the genuinely implemented interpolation types are exposed:

- Hold
- Linear

`KeyframeEvaluator` sorts by local time, preserves the base value before the first point, returns exact point values, applies HOLD from the left keyframe, and linearly interpolates otherwise. No fake Ease, Bezier, Bounce or Spring controls are shown.

## UI Step 2 diamond model

The contextual Keyframes panel uses a user-facing diamond metaphor:

- `◇ Add` — no keyframe for that property at the current owner-local playhead time
- `◆ Remove` — a keyframe exists at the current time

Accessibility semantics say what action/property the diamond represents instead of relying on shape alone.

## Property presentation

Backend enum/entity names are not shown. The user sees friendly properties such as Horizontal position, Vertical position, Scale, Rotation, Opacity and Volume. Video, text and image owners reuse the same generic architecture rather than maintaining separate animation tables.

## Add/update behavior

Adding a keyframe at the current playhead creates the point with the current property value. If an exact owner/property/time keyframe already exists, the backend path updates that point rather than silently creating a duplicate.

Editing between keyframes does not enable an implicit Auto-Keyframe mode. Auto-Keyframe remains OFF/not exposed in Step 2 so an ordinary property edit cannot unexpectedly create animation.

## Remove behavior

The active diamond explicitly removes the exact current keyframe. Removal records semantic history and does not delete the entire property's animation.

## Previous/next navigation

The panel provides Previous and Next controls. They search the owner's ordered keyframes and move the project playhead to the selected owner's absolute project time (`ownerStart + keyframe.timeUs`).

## Interpolation UI

Hold and Linear are available both as the intended mode for newly added points and as explicit controls for a point at the current playhead. Changes persist through the existing keyframe repository and history architecture.

## Preview and editing

Clip, text and image keyframes are evaluated at preview time. `AUDIO_GAIN` participates in effective audio gain. Position, scale, rotation and opacity feed the same owner state that is consumed by preview/render planning; UI Step 2 does not add a second animation store.

## Undo/redo

Add, remove and interpolation updates create `KeyframeHistoryEntry` records. Undo/redo therefore restore semantic keyframe sets, not UI-only diamond state.

## Timeline markers

Existing owner keyframe markers in the timeline remain the visualization source. Step 2 improves contextual navigation without making keyframe markers dominate clip content.

## Step 2 limitations

Bezier/easing curves, motion paths, Auto-Keyframe, optical flow and advanced animation graphs are intentionally outside Step 2. They can extend the generic model later only when the backend genuinely supports them.
