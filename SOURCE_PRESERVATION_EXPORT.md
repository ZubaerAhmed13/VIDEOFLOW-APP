# VideoFlow Source Preservation Export

## Principle

VideoFlow exposes two technically different ways to preserve source characteristics. They must never be conflated.

### Smart Copy

`Smart Copy` means a genuine packet-copy/remux path. Compatible encoded video/audio samples are written into the output container without video re-encoding.

### Match Source / Source Fidelity

`Match Source` is for edits that require rendering: Crop, Text, Transform, Speed, compositing, future AI Watermark Removal, and other pixel-changing operations. It preserves source/project characteristics as closely as the Android device and renderer allow. It is not mathematically lossless and it does not promise identical file size.

## Smart Copy Eligibility

The pure `SourcePreservationPolicy` first verifies the edit graph and persisted source metadata. Smart Copy requires a gap-free sequential video timeline beginning at zero, one active video track, speed 1x, identity transform/crop/opacity, no gain/fades, no overlays/keyframes, MP4-compatible H.264 or HEVC video, optional AAC audio, and compatible static source properties.

If that static check passes, `SmartCopyEngine` performs runtime checks using `MediaExtractor`:

- actual video/audio track presence;
- codec MIME;
- width/height;
- frame-rate representation;
- profile/level;
- rotation;
- colour standard/transfer/range;
- audio sample rate/channels;
- codec-specific data CSD-0/CSD-1/CSD-2;
- exact Smart Copy trim start is a video sync sample.

A mismatch disables Smart Copy with a reason. It does not blindly packet-merge incompatible streams.

## Smart Copy Execution

`SmartCopyEngine` uses Android `MediaExtractor` and `MediaMuxer`. Samples within each clip's source range are copied into an MP4 output track, with presentation timestamps rebased to the clip's timeline position. This is a real no-video-reencode path.

The export coordinator has a dedicated Smart Copy route. If Smart Copy preflight or execution fails, the job fails as Smart Copy; no rendered fallback is silently started.

## Match Source Metadata

Source/project fidelity policy carries or derives:

- output width/height and aspect authority;
- rational frame rate;
- H.264/HEVC codec family where supported;
- source-aware video bitrate target;
- AAC audio sample rate/channels;
- colour standard;
- colour transfer;
- colour range;
- HDR presence/preservation policy;
- rotation/orientation through the render/source model.

`SourceMediaAuthority` prevents source-created or Merge projects from silently reducing a 4K/8K source canvas to 1080p. Only even-dimension normalization is applied at project authority creation. Device encoder capability remains a later export preflight responsibility.

## Rational FPS

Known fractional rates are kept rational:

- 23.976 -> 24000/1001;
- 29.97 -> 30000/1001;
- 59.94 -> 60000/1001.

Valid nonpreset rates are represented rationally rather than defaulting to 30 fps. Export capability validation may reject an unsupported rate, but it must not silently change project authority.

## Multi-Source Projects

Different source formats cannot all be globally “matched” simultaneously. When sources are heterogeneous, VideoFlow uses the persisted project canvas and rational project frame rate as output authority and warns that the project contains multiple source formats. It does not secretly claim all sources share one codec/colour/audio description.

For the dedicated Merge workflow, the first ordered source is explicitly documented as the initial project authority. Subsequent incompatible sources are rendered appropriately; Smart Copy still requires all encoded streams to pass compatibility checks.

## Source Fidelity Bitrate

Rendered Source Fidelity uses source bitrate as an input/floor when available and compares it with a high-quality source-size/FPS/codec bitrate model. Quality takes priority over forcing the old file size. A lower bitrate is not chosen merely to imitate the source byte count.

## File-Size Estimates

Rendered output is an estimate based on duration and target bitrates. Smart Copy may estimate retained payload from source byte size and trimmed duration. Large-source arithmetic uses `BigInteger`/`Long` safe paths so estimates above 2/4 GB do not overflow 32-bit integers.

Allowed product language:

- Smart Copy: `No re-encoding when technically compatible.`
- Match Source: `Preserves source/project characteristics as closely as the device and renderer allow.`
- Source Fidelity: `Prioritizes visual quality and source-compatible settings.`

Not allowed for rendered edits:

- `100% same quality after every edit`;
- `Always same file size`;
- `Zero quality loss`;
- `Lossless edited export`.

## HEVC / HDR / 10-bit Limitations

HEVC and HDR preservation remain capability-gated. If the device cannot satisfy the requested codec, resolution/FPS, Main10/HDR path, VideoFlow must expose a problem/warning rather than silently claiming source preservation. SDR conversion occurs only under an explicit HDR policy.

## Validation

Rendered exports continue through the established output validator for readable container/tracks, requested resolution/codec, measured cadence, duration, expected audio and colour/HDR expectations. Smart Copy outputs are also reopened/validated by the export coordinator before completion.

## Automated Coverage

Tests cover Smart Copy graph eligibility, transformed edit rejection, audio sample-rate mismatch, Match Source 4K/29.97/HEVC/audio authority, mixed-source project authority, and large-size estimation.

## Physical Status

Smart Copy quality/A/V sync and rendered Match Source metadata/visual comparison are **NOT VERIFIED** on the target phone until the exact final Review APK is tested.