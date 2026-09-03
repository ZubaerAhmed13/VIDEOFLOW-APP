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

## Physical-device capability evidence

Actual target-phone results remain **NOT VERIFIED** until the certified Debug APK is installed on a real device and the Device Capability screen is recorded.

| Physical capability | Status |
|---|---|
| H.264 decode | NOT VERIFIED |
| H.264 encode | NOT VERIFIED |
| H.264 4K30 | NOT VERIFIED |
| H.264 4K60 | NOT VERIFIED |
| HEVC decode | NOT VERIFIED |
| HEVC encode | NOT VERIFIED |
| HEVC 4K30 | NOT VERIFIED |
| HEVC 4K60 | NOT VERIFIED |
| VP9 | NOT VERIFIED |
| AV1 | NOT VERIFIED |
| Actual physical RAM/storage profile | NOT VERIFIED |

Unsupported physical codecs are not a Step 1 failure by themselves. Incorrect/fabricated detection would be a failure; the physical test must record the device's truthful result.
