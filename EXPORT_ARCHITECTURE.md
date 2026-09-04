# VideoFlow Android Step 3 — Export Architecture

## User workflow

Editor → Export screen → preset/advanced settings → destination document → preflight → foreground export job → native render → post-render validation → Export History/open output.

## Export settings domain

Export settings are resolved before rendering and include output size, rational frame rate, video codec, quality, bitrate mode/bitrate, AAC bitrate, audio sample rate/channels, HDR policy and upscale state. Presets cover source/project resolution, 480p, 720p, 1080p, 1440p, DCI 2K, UHD 4K and DCI 4K. Supported cadence choices include 23.976, 24, 25, 29.97, 30, 50, 59.94 and 60 fps.

## Preflight

Preflight verifies:

- a non-empty timeline;
- requested encoder/codec/resolution/frame-rate support;
- HDR policy compatibility;
- output size estimate using 64-bit arithmetic;
- selected destination writability;
- destination capacity when the SAF provider exposes reliable free-space data;
- exact-request policy with no silent Media3 format fallback.

If destination capacity cannot be queried, the user receives an explicit warning and out-of-space writes fail as errors.

## Destination and muxing

Output is created through Android's document/content URI model. Encoded MP4 samples are written directly to the selected file descriptor by `SafMediaMuxerFactory`; the renderer does not create a second full-size app-private MP4 for the normal path. This preserves large-output scalability and avoids a duplicate multi-gigabyte copy.

## Background execution

The export job uses Android foreground media-processing semantics and persistent progress/state so rendering can continue while the editor is not visible. Cancellation is explicit. Activity recreation must observe the persisted job rather than own the render lifecycle.

## Completion semantics

`COMPLETED` is not set merely because Transformer finished encoding. The final URI is reopened and validated. Invalid/corrupt/wrong-format output is a failed export. Cancellation or failure truncates the selected destination where possible so a partial file cannot be mistaken for a valid final output.

## Export history

Completed/failed/cancelled jobs retain useful job metadata such as destination, selected settings, progress/result state and validation/error details. The final output URI remains the user-openable authority.

## Certification

`.github/workflows/android-step3-ci.yml` packages exact debug/release APKs, instrumentation APK, source ZIP, reports and SHA-256 manifests. Overall Step 3 status remains blocked until the physical-device matrix passes.
