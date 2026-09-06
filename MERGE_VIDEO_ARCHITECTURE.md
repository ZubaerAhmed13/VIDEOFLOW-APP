# VideoFlow Merge Video Architecture

## Product Workflow

`Merge Videos` is a first-class Home action. It is not a help message telling the user to place clips manually on V1.

The workflow is:

1. Open Merge Videos.
2. Select multiple videos with Android `OpenMultipleDocuments` using `video/*`.
3. Inspect the ordered selection.
4. Move items up/down or remove them.
5. Add more videos if required.
6. Create and preview the merge.
7. Continue inside the normal VideoFlow editor for per-clip Trim and other edits.
8. Export with Smart Copy when genuinely compatible or a rendered mode such as Match Source otherwise.

## SAF / No-Copy Integration

Selection analysis uses `MediaAnalyzer`. Project creation uses the normal `ProjectRepository.addMedia` path, preserving persisted Android document references, metadata, fingerprinting, source revalidation, and relinking. Originals are not copied into a second merge-specific media store.

An intentionally repeated source is allowed. The existing explicit duplicate-confirm path creates another media reference when the user selected the same video more than once.

## Project Integration

Merge builds a normal persisted VideoFlow project and reuses the existing timeline/editor architecture. Each selected video becomes a sequential `TimelineClip` through `EditorRepository.addClip`; the next clip starts at the previous clip's timeline end.

The first ordered source is the explicit initial project canvas/FPS authority. `SourceMediaAuthority` preserves its source pixel dimensions (with only even-dimension encoder normalization) and rational frame rate instead of silently reducing a 4K source to a 1080p-class project. Multi-source Match Source behavior is still governed by project/output authority and capability checks.

## Reordering

Ordering is represented explicitly and the pure `MergeOrdering` policy is unit-tested, including intentional duplicate entries. Reordering changes the sequence used to create the timeline; it does not modify source files.

## Preview and Trim

After creation, the user opens the same editor used by ordinary projects. This preserves existing preview, proxy, thumbnails, waveforms, relinking, precise Trim, Split, Crop, Speed, Transform, Text, Audio, keyframes, Undo/Redo, snapshots, and export behavior.

## Compatibility Detection

Smart Copy is considered only for a narrow encoded-stream-compatible graph. Static policy rejects cases including:

- gaps/overlaps or multiple active video tracks;
- transforms, crop, opacity, speed, gain, fades, overlays or keyframes;
- unsupported container/codec combinations;
- incompatible dimensions, rotation, codec, audio sample rate/channels, colour/HDR properties.

Runtime preflight then compares actual `MediaFormat` sample descriptions, including codec configuration data (CSD), profile/level and colour metadata. Exact trimmed video starts must be sync samples.

## Packet-Copy Path

For a compatible project, `SmartCopyEngine` uses `MediaExtractor` + `MediaMuxer`. Encoded H.264/HEVC video and optional AAC samples are copied without video re-encoding. Presentation timestamps are rebased to each clip's timeline start so the output remains continuous in the selected order.

## Audio Handling

AAC audio is packet-copied only when audio sample descriptions are compatible across sources. Sample-rate/channel or codec configuration mismatch makes Smart Copy unavailable. VideoFlow does not blindly concatenate 44.1 kHz and 48 kHz streams into one MP4 track.

## Fallback Rendering

Incompatible merge projects remain normal editor projects and can be exported by the established native render pipeline. Match Source/Source Fidelity is the high-fidelity rendered option. A Smart Copy request never silently turns into a rendered export while retaining the Smart Copy label.

## Certification

Automated coverage verifies ordering, duplicate selection behavior, Smart Copy graph eligibility, audio mismatch rejection, Match Source policy, and the first-class Merge/Export Mode UI in the API-35 product flow.

Physical three-video selection/reorder/preview/export remains **NOT VERIFIED** until the exact Review APK is tested on the target phone.