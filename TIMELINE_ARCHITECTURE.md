# VideoFlow Android Step 2 — Timeline Architecture

## Status

PASS for implemented architecture; physical-device acceptance remains tracked separately.

## Domain and timebase

The editor uses a platform-neutral timeline domain. Timeline/source positions, fades, keyframe positions and project duration are stored as signed 64-bit microseconds (`Long`). UI pixel coordinates are derived from time and zoom and are never persisted as edit truth.

Project frame rate is rational (`FrameRate(numerator, denominator)`) with exact presets for 24, 25, 30000/1001, 30, 60000/1001 and 60 fps.

## Tracks

`TimelineTrack` supports deterministic `orderIndex` and VIDEO, AUDIO and OVERLAY types. Track state includes rename, mute, solo, lock, visibility and dB gain. New projects receive sensible default tracks. Deleting a populated track requires explicit confirmation; deletion and undo restore/remove the complete owned bundle transactionally.

## Clips

`TimelineClip` stores original asset identity, source in/out, timeline start, speed, opacity, gain/fades, transform, crop, rotation and flips. Move, trim, split, duplicate and delete are non-destructive. Split is speed-aware and keyframes are redistributed in clip-local time.

Locked tracks reject clip movement, trim, split, delete and property edits through repository/service guards.

## Overlap policy

Step 2 is a professional cut-only core. Normal video/audio `TimelineClip` instances on the same track may not overlap. `EditorRepository` validates the candidate range and raises `TimelineOverlapException` instead of silently moving or truncating another clip. Parallel content is represented on additional tracks. Overlay entities have independent timed ranges.

## Snapping

Snapping uses timeline-time targets such as clip boundaries/playhead. The visible pixel threshold is converted to microseconds using the current `pixelsPerSecond`; only the returned time is persisted. This keeps edit coordinates independent of zoom and screen density.

## Zoom, scroll and composition cost

The Compose editor uses `LazyColumn`/`LazyRow` for large editor collections plus horizontal timeline scrolling and zoom gestures. Timeline thumbnails and waveform graphics come from bounded caches rather than source-sized media buffers. The timeline model can represent multi-hour projects and tests include a 100-clip, 5-track, >2-hour structural case.

## Step 3 boundary

Step 2 creates deterministic `PreviewPlan` and `RenderPlan` data. It does not encode a final output. Step 3 must consume the same persisted edit model rather than inventing a second timeline representation.
