# VideoFlow Android Step 2 — Edit History and Snapshots

## Semantic undo/redo

`EditHistoryService` records semantic before/after state rather than screenshots or UI pixels. Entry types cover clips, tracks, populated track bundles, generic keyframes, text overlays and image overlays.

Undo/redo applies changes inside Room transactions and touches project format/version timestamps. Redo is cleared by a new edit after undo. The history is bounded to 200 entries.

## Gesture/property coalescing

Repeated compatible changes with the same coalescing key within a 750 ms window merge into one semantic entry. This prevents drag/slider gestures from consuming dozens of undo slots while preserving the first "before" state and final "after" state.

## Safe track deletion

Deleting a track with content requires confirmation. History captures the complete track bundle—clips, text overlays, image overlays and keyframes—so undo can restore the collection atomically without orphaning owners.

## Process behavior

The undo/redo stack is intentionally session memory; the authoritative project state is persisted in Room. Process restart restores the edited project but begins a fresh undo session rather than serializing executable history commands.

## Snapshots

`SnapshotService` is separate from transient undo. Named project snapshots serialize persisted editor state and support create, restore and delete. Snapshot restore is transactional and is intended for explicit recovery/checkpoints across editing sessions.
