# VideoFlow Precise Trim Architecture

## Goal

Precise Trim adds professional manual From/To entry without removing the existing visual Trim workflow. The editor continues to use microseconds as timing authority while users can work with readable timecode.

## User Interface

When a visual clip is selected, the final-quality editor exposes `Precise Trim`. The dialog provides:

- a visual range slider;
- `From` exact-time field;
- `To` exact-time field;
- live duration display;
- validation/error text;
- Done/Cancel actions.

Changing the slider updates the fields. Valid field values represent the same trim boundaries applied to the persisted timeline clip.

## Accepted Input

`TrimTimecode.parseToUs` accepts:

- seconds: `5`, `5.5`;
- `MM:SS`: `00:05`, `01:20`;
- `HH:MM:SS.mmm`: `00:01:20.500`.

The normalized display format is always `HH:MM:SS.mmm`.

## Internal Timing Model

The persisted trim model remains integer microseconds (`Long`). Parsing uses decimal/big-number arithmetic and converts only at the microsecond boundary. Negative values, malformed segments, seconds >= 60 in colon notation, invalid ordering, source overruns, and too-short ranges are rejected with an explanatory message.

The default minimum accepted duration is 100 ms.

## CFR Sources

For constant-frame-rate calculations, `TrimTimecode.snapCfrToNearestFrame` uses the exact rational `FrameRate` numerator/denominator. 29.97 remains 30000/1001 and 59.94 remains 60000/1001; it is not rounded to 30/60.

## VFR and Real Sample Timing

The production precise-trim commit path does not infer VFR boundaries from nominal FPS. `PreciseTrimViewModel` opens the selected source with `MediaExtractor` and normalizes requested video boundaries to the nearest real video sample timestamp. This is the timing authority for variable-frame-rate media.

The source duration endpoint remains valid as the final boundary. Audio-only sources retain requested microsecond timing.

## Persistence and Undo

The feature reuses the existing `EditorRepository.trimClipStart` / `trimClipEnd` operations and timeline model. The mutation order is chosen so expanding or narrowing an existing trim does not temporarily create an invalid range. A completed change is recorded as one `ClipHistoryEntry` named `Precise Trim`, preserving Undo/Redo.

## Smart Copy Interaction

Precise Trim and Smart Copy have separate technical rules:

- Match Source/rendered export can honor an exact edit boundary through decoding/rendering.
- Smart Copy can only use a trimmed start when the exact requested/normalized start is a video sync-sample boundary and all encoded track descriptions remain compatible.
- If the exact trim is not Smart-Copy compatible, VideoFlow reports Smart Copy unavailable. It must not silently move the cut to a different keyframe or silently start a rendered fallback while still calling it Smart Copy.

## Tests

Automated tests cover accepted input formats, invalid input, start/end validation, rational CFR snapping, and fractional frame rates. The API-35 product certification also compiles and launches the final-quality editor route; physical exact-entry interaction remains part of the required same-phone review.

## Physical Certification

**NOT VERIFIED.**

Required check on the exact Review APK:

- enter From `00:00:03.000`;
- enter To `00:00:15.000`;
- verify visible range/handles agree;
- verify preview reflects the selected section;
- export and verify output duration/boundary behavior;
- record any sample normalization shown by the source.