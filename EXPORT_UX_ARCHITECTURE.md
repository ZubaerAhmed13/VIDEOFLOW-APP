# VideoFlow — Export UX Architecture

## Principle

**Recommended settings first → Advanced controls only when requested.**

The UI presentation is intentionally simpler than the renderer. It does not remove renderer capabilities and it does not move codec/capability logic into Compose.

## Simple Export

The default screen exposes:

- editable MP4 file name;
- resolution;
- quality preset;
- resolved project frame rate summary;
- approximate file-size estimate;
- Advanced Settings;
- Android document destination;
- Export Video.

Default domain settings remain:

- Resolution: Match Project / SOURCE
- Frame rate: project frame rate
- Codec: H.264 / AVC
- Quality: High Quality
- Audio: AAC-LC, 48 kHz, stereo, 256 kbps
- Colour/HDR: Preserve compatible colour information

## Quality Mapping

The user labels map one-to-one to existing `ExportQuality` values and therefore to real `ExportMath.selectVideoBitrate()` behavior:

| User label | Domain value |
|---|---|
| Smaller File | SMALL |
| Recommended | BALANCED |
| High Quality | HIGH |
| Maximum | MAXIMUM |

No label is cosmetic.

## Resolution

Simple mode shows Match Project, 720p, 1080p, 1440p/QHD and 4K UHD. Advanced mode additionally exposes backend-supported 480p, DCI 2K, DCI 4K and custom even dimensions. Selecting a size larger than project canvas generates the explicit warning that enlargement cannot create additional source detail.

## Frame Rate

Default is Same as Project. Advanced selections preserve rational values:

- 23.976 = 24000/1001
- 24 = 24/1
- 25 = 25/1
- 29.97 = 30000/1001
- 30 = 30/1
- 50 = 50/1
- 59.94 = 60000/1001
- 60 = 60/1

29.97 is not rounded to 30 and 59.94 is not rounded to 60.

## Codec

Codec is Advanced-only:

- H.264 / AVC — Best compatibility
- HEVC / H.265 — More efficient on supported devices

The existing `ExportCapabilityValidator` remains authoritative. Unsupported combinations become blocking `ExportProblem` values; the UI does not silently switch codec, resolution, frame rate, or HDR policy.

## Bitrate

Advanced mode exposes the existing bitrate mode and displays the real resolved video bitrate in Mbps. Audio bitrate choices update the existing `ExportSettings.audioBitrate`. Raw integer bits-per-second values are not shown to normal users.

## Colour / HDR

Advanced labels map directly to the existing HDR policy:

- Preserve compatible colour information
- Require compatible HDR preservation
- Convert HDR to SDR

The UI does not offer HDR enhancement, Dolby Vision, 10-bit toggles, or any other capability not proven by the backend.

## Capability Filtering and Validation

The backend validator checks encoder compatibility for the resolved settings. The UI shows current blocking problems and recovery-oriented messages. There is no silent downgrade. A future UI may hide more invalid candidates proactively, but selecting an invalid candidate cannot start an export because `ExportUiState.canStart` requires an empty problem list.

## Preflight and Source Authority

`ExportRepository.compileFinalPlan()` remains the source preflight. Step 3 fixes the presentation state so compile/source problems are retained when settings are recomputed rather than accidentally replaced by capability-only results.

Final export continues to use original-source mappings. Proxy availability never converts a missing original into an export-ready state.

## File Name and Destination

The file name is editable and sanitized for document-provider safety. The suggested name is passed to Android `CreateDocument`. The export job stores the same sanitized display name.

Normal UI never renders the raw `content://` URI. It states that the destination was selected through Android's document picker.

## Size Estimate

The displayed estimate is approximate and derives from the current resolved video bitrate, audio bitrate and project duration using existing 64-bit/BigInteger-safe `ExportMath.estimateOutputSize()`. The simple screen displays the payload estimate rather than claiming an exact final byte count. Storage headroom remains available in the domain estimate.

## Progress

Progress is read from the real persisted `ExportJob.progress`. No timer animation is used. User stages are:

- Preparing
- Rendering
- Finalizing
- Validating

TalkBack semantics expose the progress range. No fake ETA is shown.

## Cancellation

Cancellation calls the existing `ExportCoordinator.cancel()`. The UI requires confirmation. A cancelled job is displayed as Cancelled, never success.

## Completion

A completed job produces a dedicated session success screen with:

- Export Complete;
- file name;
- resolved size / frame rate;
- project duration when available;
- Open Video;
- Share;
- Done.

Open and Share use the content URI plus temporary read permission; no `file://` URI is used.

## Failure / Retry

Known `ExportFailureCode` values are mapped to user-facing recovery text. Stack traces and Java/MediaCodec class names are not shown by default. Retry uses the current valid settings/destination and cannot start while another active job exists.

## History

History uses friendly statuses. Open/Share are shown only for completed exports. Failed/interrupted jobs show mapped failure guidance.
