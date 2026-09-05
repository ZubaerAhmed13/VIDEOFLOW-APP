# VideoFlow Android UI Step 2 — Trim UI Architecture

## Goal

Trim is a visual media operation, not a raw microsecond property form. The Step 2 trim panel reuses existing cached media previews, previews the active trim boundary on the main player, and keeps the final mutation in the established editor domain.

## Visual source

### Video

The panel displays a compact strip from the existing cached thumbnail source. The UI does not decode a full 4K source bitmap for each handle movement.

### Audio

Pure-audio clips display the existing cached waveform instead of video thumbnails.

## Handles and values

A two-ended Material `RangeSlider` provides Trim Start and Trim End manipulation. The visual strip/waveform gives media context and the panel displays:

- Start as `HH:MM:SS.mmm`
- End as `HH:MM:SS.mmm`
- resulting timeline Duration

Raw `sourceStartUs` and `sourceEndUs` are never shown to the user.

## Source mapping

The transient slider range is normalized against the source media duration. On Done it is converted back to 64-bit microsecond source boundaries and submitted to the existing editor trim operations.

The domain remains the final validator. Bounds must satisfy:

- start >= 0
- end <= source duration
- end > start
- minimum valid duration

## Speed-aware duration

The panel calculates displayed timeline duration from the selected source span divided by the clip speed. It does not reduce time precision to whole seconds and does not replace the existing speed/source mapping rules.

## Boundary preview seeking

While a trim handle moves, the panel identifies the active boundary and maps its source-media position into the selected clip's timeline space. It sends that boundary to the existing editor playhead so the fixed main preview becomes the visual verification surface.

Seeking is throttled to approximately one request every 50 ms while dragging, with a final exact seek when the gesture finishes. This avoids issuing an unbounded decoder seek for every pointer delta while still satisfying the Step 2 trim-preview requirement.

The preview callback changes the player/playhead position only; it does not persist a trim or create history entries.

## Commit policy

Trim uses **Preview then Commit**:

- moving handles changes local trim-tool state and may seek the main preview;
- Cancel leaves the clip boundaries unchanged;
- Reset expands the selected clip to the maximum source range represented by the tool;
- Done applies the source boundaries and records one semantic trim history operation.

This prevents a drag from producing a large series of Room writes or Undo records.

## Playback policy

Opening Trim pauses playback. Handle movement uses cached thumbnails/waveforms for continuous context and throttled main-player boundary seeking for visual verification. The final handle position is sought when the drag ends.

## Accessibility

The Trim range has a semantic description and exact Start/End/Duration values remain visible. The underlying Material range control supplies accessible adjustable actions; trim is not gesture-only.

## Large-media safety

Trim does not copy the original source and does not load the complete media into RAM. Existing content-URI references, thumbnail/waveform caches, player seeking, and source metadata remain the source of truth.

## Known UI Step 2 limitation

Timeline-edge auto-scroll while dragging a trim boundary is not independently physically verified in this Step 2 candidate. The brief marks trim auto-scroll as strongly recommended rather than a hard blocker. Physical usability is recorded separately in `UI_STEP_2_PHYSICAL_DEVICE_REVIEW.md`.
