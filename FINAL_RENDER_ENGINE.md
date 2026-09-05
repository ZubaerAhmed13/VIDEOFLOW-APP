# VideoFlow Android Step 3 — Final Render Engine

## Scope

This document describes the Step 3 native final-render path. Step 1/2 project state remains authoritative and non-destructive. Proxies are preview/editing aids only; final rendering resolves every timeline asset back to its original SAF-backed source.

## Immutable render contract

`RenderPlan`/`FinalRenderPlan` contains project resolution, rational frame rate, project background ARGB, tracks, clips, overlays, keyframes, duration, and a map of `OriginalRenderSource` records. The original-source map carries the persistent source URI and media metadata needed for final decoding. No proxy path is substituted into the final plan.

## Pipeline

1. `ExportCoordinator` snapshots the current project into a final render plan.
2. Export settings are resolved and capability-checked before rendering.
3. `Media3RenderEngine.prepare()` validates encoder support, output size estimate, destination writability/free space, HDR policy, and mixed colour metadata.
4. `Media3CompositionBuilder` converts the plan into a Media3 `Composition`, using original source URIs and bounded raster assets for project background/text where required.
5. Media3 decoders read source media through Android URI/data-source infrastructure; full source files are never loaded into memory.
6. Media3 effects/composition perform video transforms/crop/rotation/flip/opacity/keyframed composition.
7. `DefaultEncoderFactory` creates the requested native encoder with format fallback disabled.
8. `SafMediaMuxerFactory` writes encoded MP4 samples directly to the selected SAF file descriptor.
9. `OutputValidator` reopens the final URI and verifies container, tracks, dimensions, duration, frame rate, codec/audio and applicable colour/HDR metadata.
10. Only a validated output may become `COMPLETED`.

## Encoder and fallback policy

Video is H.264 by default; HEVC is allowed only when the selected device capability supports the exact requested configuration. Silent Media3 format fallback is disabled. If Media3 reports a fallback, Step 3 fails the export instead of silently changing codec, resolution, HDR policy or another requested output property.

## Direct output

The normal path does not create a second full-size MP4 in app-private storage. `SafMediaMuxerFactory` adapts Media3 muxer writes to Android `MediaMuxer(FileDescriptor, MUXER_OUTPUT_MPEG_4)` using the user-selected content URI. Small bounded raster files may exist temporarily for generated background/text layers and are deleted after the job.

## Resource lifecycle

Rendering is serialized with a mutex. The active Transformer/completion handle is tracked for cancellation. Cancellation or failure cancels the transformer, truncates the destination where possible, deletes bounded raster scratch files, and never reports a fake completed output. Successful completion also cleans scratch resources after validation.

## Resolution and cadence

Export presets include 480p, 720p, 1080p, 1440p, DCI 2K, UHD 4K and DCI 4K. Cadence uses rational `FrameRate`; 23.976, 29.97 and 59.94 are not simplified to integer rates in the domain model. Static generated layers are scheduled so they do not force an upward rounded cadence.

## Current certification status

The architecture is implemented. Automated JVM/lint/instrumentation/APK certification is performed by `.github/workflows/android-step3-ci.yml`. Real-device 1080p, mandatory 4K30, screen-off/background, multi-GB source, visual colour and A/V-sync gates remain separately recorded in `STEP_3_PHYSICAL_DEVICE_CERTIFICATION.md` and must pass before Step 3 can be declared COMPLETE.
