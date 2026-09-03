# VideoFlow Android Step 2 — Keyframe Architecture

## Generic ownership

Keyframes are generic persisted entities, not one-off opacity fields. Owners are CLIP, TEXT_OVERLAY or IMAGE_OVERLAY. Supported Step 2 properties are POSITION_X, POSITION_Y, SCALE_X, SCALE_Y, ROTATION, OPACITY and AUDIO_GAIN.

## Time model

`timeUs` is a 64-bit microsecond position local to the owner. Moving a clip therefore does not rewrite its animation timing. When a clip is split, keyframes at/before the split stay with the left clip and keyframes at/after the split are copied to the right owner with local time rebased to zero.

## Interpolation

HOLD and LINEAR are implemented. `KeyframeEvaluator` sorts by local time, preserves the base value before the first point, returns exact point values, applies HOLD from the left keyframe, and linearly interpolates otherwise.

## Preview and editing

The editor exposes property/interpolation controls and evaluates clip, text and image keyframes at preview time. AUDIO_GAIN keyframes participate in effective audio gain. Persistence is through Room and owner/keyframe edits participate in semantic history.

## Step 2 limitations

Bezier/easing curves, motion paths, optical flow and advanced animation graphs are intentionally outside Step 2. They can extend the generic schema later without changing timeline time ownership.
