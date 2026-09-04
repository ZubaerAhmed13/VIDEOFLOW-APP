# VideoFlow Android Step 3 — Large Media Export

## Non-negotiable rules

There is no artificial 3 GB source limit and no deliberate 4 GB output rejection. Media sizes, durations, estimates and timeline positions use 64-bit representations where applicable. Source files remain referenced through Android URIs and are never copied wholesale into app-private storage merely to render.

## Source access

The final render plan resolves every clip to its original `content://` source. Native extractors/decoders stream from those sources. Forbidden patterns such as `readBytes()`, source-sized `ByteArray` allocation, whole-video frame lists and whole-program PCM buffers are blocked by CI audits and architecture review.

## Output path

The normal final-render path writes encoded MP4 samples directly to the user-selected SAF file descriptor through `SafMediaMuxerFactory`. This avoids a second full-size MP4 in app-private storage and avoids a multi-gigabyte copy after encoding.

Small temporary raster resources for generated background/text layers are permitted because they are bounded and independent of source/output file size. They are deleted after the job.

## 64-bit output sizing

`ExportMath` estimates payload and safety-margin bytes with `Long` arithmetic. Certification includes a multi-hour/high-bitrate case whose estimate exceeds 4 GiB without overflow. Frame timestamps also remain stable for multi-hour rational-rate projects.

## Storage preflight

When the selected provider exposes a real file descriptor filesystem, the renderer uses `fstatvfs` and checked multiplication to estimate available bytes. If available capacity is below the estimated required bytes, export is rejected before rendering. If capacity cannot be queried reliably, the UI reports that uncertainty and an actual out-of-space write fails explicitly.

## Cancellation/failure cleanup

Partial encoded output is never promoted to completed status. Failed/cancelled exports truncate the selected destination where possible and remove bounded scratch resources.

## >4 GB considerations

MP4/container and filesystem support still depend on the Android provider/device/filesystem. The application itself introduces no 4 GB cap. A real >4 GB physical output is a separate certification target; if impractical, it must remain `NOT VERIFIED` rather than being claimed from arithmetic tests alone.

## Physical large-source gate

Final Step 3 acceptance requires a real multi-GB source on a physical device, preferably the already certified Step 1 large source. Evidence must show rendering begins from the original reference, no source-sized private copy appears, memory remains bounded, and the output validates successfully.
