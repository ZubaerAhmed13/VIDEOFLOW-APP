# VideoFlow Android UI/UX — Step 2 Physical Device Review

## Status

**NOT VERIFIED — HARD GATE OPEN**

This checklist must be completed on a real Android phone using the exact `VideoFlow_Android_UI_Step2_Review.apk` produced by the final successful branch-head certification workflow. Emulator success is not a substitute for this report.

## Build identity to record before testing

- Branch: `ui-step2-contextual-tools`
- Certified commit SHA: ________________________________
- CI run ID: ________________________________
- Review APK SHA-256: ________________________________
- Device model: ________________________________
- Android version/API: ________________________________
- App version shown: ________________________________

## Install/update

- [ ] Fresh Review APK install succeeds.
- [ ] Cold launch succeeds.
- [ ] In-place update from the previously installed stable Review build succeeds.
- [ ] Existing project data remains available after update.

## Base flow

1. Open an existing real project.
2. Enter Editor.
3. Select a real video clip.
4. Confirm preview, transport, timeline and bottom toolbar remain visible and usable.

Status: **NOT VERIFIED**

## Video tools

### Split

- [ ] Place playhead inside clip and Split.
- [ ] Two valid clips result.
- [ ] Invalid start/end split is rejected with friendly feedback.
- [ ] Undo restores one original clip.

Status: **NOT VERIFIED**

### Trim

- [ ] Video thumbnail strip is visible.
- [ ] Start/end handles are easy to grab.
- [ ] Dragging either boundary updates the fixed main preview to that boundary.
- [ ] Start/End/Duration values update correctly.
- [ ] Reset behaves predictably.
- [ ] Cancel leaves original trim unchanged.
- [ ] Done commits once.
- [ ] Undo restores previous trim.

Status: **NOT VERIFIED**

### Speed

- [ ] 0.5×, 1×, 1.5× and 2× work.
- [ ] Custom slider works across supported range.
- [ ] Resulting duration preview is correct.
- [ ] Reset returns to 1×.

Status: **NOT VERIFIED**

### Crop

- [ ] Outside region dims correctly.
- [ ] Four corners are easy to grab.
- [ ] Four edges are easy to grab.
- [ ] Entire crop region can be repositioned.
- [ ] Free Crop works without forced aspect locking.
- [ ] Original, 16:9, 9:16, 4:3, 3:2, 1:1 and 4:5 presets stay inside source.
- [ ] Precise edge sliders work.
- [ ] Preview follows finger movement continuously.
- [ ] Cancel restores exact original crop without a visible compensating edit.
- [ ] Reset returns full frame.
- [ ] One continuous crop gesture becomes one logical Undo action.

Status: **NOT VERIFIED**

### Transform

- [ ] One-finger/pan movement is natural.
- [ ] Pinch scale is stable.
- [ ] Rotation gesture is stable and non-noisy.
- [ ] X/Y/Scale/Rotation precision controls match preview.
- [ ] 0/90/180/270 controls work.
- [ ] Flip H and Flip V work for video clip.
- [ ] Center guides/snapping appear appropriately.
- [ ] Cancel restores prior state without a visible compensating write.
- [ ] One continuous gesture becomes one logical Undo action.

Status: **NOT VERIFIED**

### Opacity

- [ ] 0%, 50% and 100% preview correctly while the control is moving.
- [ ] Reset returns 100%.
- [ ] Cancel restores prior opacity.
- [ ] Done creates the expected single logical edit.

Status: **NOT VERIFIED**

## Timeline direct editing

- [ ] Dragging the selected clip body moves the clip rather than trimming it.
- [ ] Left trim-edge zone trims only the left boundary.
- [ ] Right trim-edge zone trims only the right boundary.
- [ ] Selected trim handles remain visually understandable.
- [ ] Moving a clip near the left/right visible timeline edge auto-scrolls naturally.
- [ ] Trimming near the left/right visible timeline edge auto-scrolls naturally.
- [ ] Auto-scroll does not cause uncontrolled jumps.

Status: **NOT VERIFIED**

## Audio tools

- [ ] Pure-audio Trim uses waveform, not thumbnails.
- [ ] Clip Volume is visibly separate from Track Volume.
- [ ] Volume adjustment previews audibly and remains stable.
- [ ] Cancel restores the previous clip volume.
- [ ] Mute-equivalent minimum gain is effectively silent.
- [ ] Fade In works and is audible.
- [ ] Fade Out works and is audible.
- [ ] Fade values never exceed clip duration.
- [ ] Cancel restores previous fade values.

Status: **NOT VERIFIED**

## Text

- [ ] Add Text opens keyboard and creates visible text.
- [ ] Existing text can be edited without leaving editor.
- [ ] Text changes preview live while editing.
- [ ] Size works.
- [ ] Regular/Medium/Bold work.
- [ ] Italic works.
- [ ] Left/Center/Right alignment works.
- [ ] Quick colors work.
- [ ] Hue control works.
- [ ] Saturation control works.
- [ ] Brightness/Value control works.
- [ ] HSV controls remain synchronized with displayed hex color.
- [ ] Hex color input works and updates HSV consistently.
- [ ] Opacity works.
- [ ] Direct Transform works.
- [ ] Timing works.
- [ ] Duplicate works.
- [ ] Delete is undoable.
- [ ] Cancel restores the text state that existed before entering the tool.

Backend note: font-family persistence is not present in the existing model, so no fake font-family picker is expected.

Status: **NOT VERIFIED**

## Image overlays

- [ ] Transform drag works.
- [ ] Pinch scale works.
- [ ] Rotation works.
- [ ] Original image aspect is not unexpectedly stretched.
- [ ] Opacity works.
- [ ] Timing works.
- [ ] Duplicate works without copying original source media.
- [ ] Delete is undoable.
- [ ] Cancel restores the previous transform/opacity state.

Status: **NOT VERIFIED**

## Keyframes

- [ ] Diamond add behavior is understandable.
- [ ] Active diamond/remove behavior targets only the intended exact point.
- [ ] Editing at an existing keyframe updates that keyframe rather than creating an unintended duplicate.
- [ ] Previous navigates correctly.
- [ ] Next navigates correctly.
- [ ] Hold evaluates correctly.
- [ ] Linear interpolates correctly.
- [ ] Scale animation remains uniform rather than independently distorting X/Y scale.
- [ ] Timeline markers use the correct owner duration for video clips, text and image overlays.
- [ ] Timeline markers remain visible without dominating clips.
- [ ] Scrubbing evaluates animation.
- [ ] Playback animates properties correctly.
- [ ] Undo/redo keyframe operations work.

Status: **NOT VERIFIED**

## Responsive layout

- [ ] Portrait phone layout is usable.
- [ ] Small phone around 360×800dp keeps Trim/Crop/Transform/Text/Keyframes reachable.
- [ ] Around 430dp width remains usable.
- [ ] Contextual tool surfaces do not unnecessarily hide the preview behind a modal scrim.
- [ ] Landscape Crop works without unusably covering preview/timeline.
- [ ] Landscape Transform works.
- [ ] Landscape Text works.

Status: **NOT VERIFIED**

## Touch quality

- [ ] Crop handles are easy to grab.
- [ ] Trim handles are easy to grab.
- [ ] Slider thumbs are comfortable.
- [ ] Toolbar targets are comfortable.
- [ ] No accidental destructive Delete action.
- [ ] No preview gesture conflicts.
- [ ] Direct edits visually follow the finger without obvious persistence-induced lag.

Status: **NOT VERIFIED**

## Accessibility

### 150% system font

- [ ] Contextual tool panels remain reachable.
- [ ] No critical Done/Cancel action is clipped.

### TalkBack

Confirm understandable focus/labels for:

- [ ] Trim
- [ ] Speed
- [ ] Crop presets and precise edges
- [ ] Transform values
- [ ] Opacity
- [ ] Volume
- [ ] Text color HSV/hex controls
- [ ] Keyframes
- [ ] Done
- [ ] Cancel/Close

Status: **NOT VERIFIED**

## Final export parity — critical

Create a short project containing:

- video trim/speed
- crop
- transform/rotation
- text
- image if available
- opacity
- audio volume/fade
- at least one animated keyframe property

Export through the existing Step 3 native renderer. Compare final output to the editor preview.

Expected:

- [ ] same crop
- [ ] same position/scale/rotation
- [ ] same text
- [ ] same image placement
- [ ] same opacity
- [ ] same speed
- [ ] same volume/fades
- [ ] same keyframe animation

Status: **NOT VERIFIED**

## Large-source spot check — strongly recommended

If a multi-GB source is available:

- [ ] open existing project without source copy;
- [ ] Trim;
- [ ] Crop;
- [ ] Transform;
- [ ] observe no abnormal memory behavior or ANR.

Status: **NOT VERIFIED**

## Physical review result

Overall physical result: **NOT VERIFIED**

Do not change this to PASS unless every required hard-gate section above is tested on the exact certified Review APK. A failed required item keeps UI Step 2 incomplete and must be reported before any UI Step 3 work begins.
