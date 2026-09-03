# Device Capability — Step 1

Date: 2026-09-03

Certified software baseline: main commit `9e414a65afa8a0a6235a794f056e61846b631bce`, run #43 (`33746474694`).

## DeviceCapabilityProfile — PASS

VideoFlow records/reportable local technical capability fields including:

- Android API level
- manufacturer and model
- supported ABIs
- CPU core count
- approximate total RAM
- available internal storage
- persisted media read-permission count

No unique advertising/device identifier is required.

## Codec interrogation — PASS

The native Android capability layer interrogates `MediaCodecList` for:

- H.264 / AVC encode and decode
- HEVC / H.265 encode and decode
- VP9 encode/decode availability where exposed
- AV1 encode/decode availability where exposed
- hardware/software classification where Android APIs provide it
- 3840×2160 @ 30 fps support where query APIs allow
- 3840×2160 @ 60 fps support where query APIs allow

The application reports detected capability rather than assuming every device supports every codec/resolution.

## Automated runtime evidence — PASS

API-35 instrumentation includes actual Android runtime codec-list interrogation and completed successfully in main run #43 as part of **OK (16 tests)**.

## Physical-device capability evidence — PASS

The certified Debug APK was executed on a physical Motorola edge 60 and the Device Capability / Diagnostics screens were recorded.

### Physical device profile

| Field | Result |
|---|---|
| Manufacturer / model | motorola motorola edge 60 |
| Android API | 35 |
| ABI | arm64-v8a |
| CPU cores | 8 |
| RAM | 11.16 GB |
| Available storage | 357.70 GB |
| Database version | 1 |
| Persisted media read permissions | 3 |
| Runtime network permission | not requested |
| Android backup/transfer | disabled |

### Physical codec result

| Capability | Physical result |
|---|---|
| H.264 / AVC decode | PASS — hardware supported |
| H.264 / AVC encode | PASS — hardware supported |
| H.264 4K30 decode | PASS — supported |
| H.264 4K30 encode | PASS — supported |
| H.264 4K60 decode | PASS — truthful result: not supported |
| H.264 4K60 encode | PASS — truthful result: not supported |
| HEVC decode | PASS — hardware supported |
| HEVC encode | PASS — hardware supported |
| HEVC 4K30 decode | PASS — supported |
| HEVC 4K30 encode | PASS — supported |
| HEVC 4K60 decode | PASS — truthful result: not supported |
| HEVC 4K60 encode | PASS — truthful result: not supported |
| VP9 decode | PASS — hardware supported |
| VP9 encode | PASS — truthful result: not detected |
| VP9 4K30 decode | PASS — supported |
| VP9 4K60 decode | PASS — truthful result: not supported |
| AV1 decode | PASS — software/vendor reported |
| AV1 encode | PASS — software/vendor reported |
| AV1 4K30 decode/encode | PASS — truthful result: not supported |
| AV1 4K60 decode/encode | PASS — truthful result: not supported |

Unsupported physical codecs or frame-rate/resolution combinations are not Step 1 failures. The Step 1 requirement is truthful interrogation and reporting; the physical screen now confirms that behavior on the tested device.
