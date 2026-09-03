# VideoFlow Android Step 2 — Preview Engine

## Deterministic plan

`PlanBuilder.preview()` converts persisted `ProjectSettings` + `TimelineState` + source/proxy maps into an immutable, deterministically sorted `PreviewPlan`. Composables do not use database insertion order as editing truth.

For each clip, a READY proxy is selected when proxy preview is preferred; otherwise the original content URI is selected. `PreviewClip.usingProxy` records that decision explicitly.

## Video preview

The Compose editor resolves the active visible video clip at the playhead and selects the highest ordered active video track. Media3 playback seeks into the clip's original source range and follows timeline playhead time. Crop/position/scale/rotation/flip/opacity are applied to preview presentation, with generic keyframes evaluated in clip-local time.

Multiple video tracks are represented fully in the model. Simultaneous multi-video compositing beyond selection of the top active video layer is **PARTIAL** in Step 2; this is not required to block the cut-oriented Step 2 core.

## Overlays

Timed text and image overlays are evaluated against the playhead and drawn above video. Text supports content, size, weight, italic, color, opacity, alignment, position, scale, rotation and timing. Image overlays support position, scale, rotation, opacity and timing. Text/image keyframes use the same generic evaluator as clip keyframes.

## Audio preview

VIDEO and AUDIO tracks participate in the audio-track policy. Mute and solo are semantic track state, not decoration. Track gain, clip gain, fades and AUDIO_GAIN keyframes are converted to effective preview gain.

## RenderPlan foundation

`PlanBuilder.render()` creates a deterministic future-facing `RenderPlan` containing project dimensions/frame rate, tracks, source-range clips, transforms/crop/speed/opacity, overlays and keyframes. No final encoding is performed in Step 2. Step 3 must resolve original render sources and produce final media from this same edit model.
