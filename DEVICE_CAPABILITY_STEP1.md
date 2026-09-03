# Device Capability — Step 1

`DeviceCapabilityRepository` interrogates `MediaCodecList.ALL_CODECS` instead of relying on hard-coded device tables. It reports H.264/AVC, HEVC, VP9 and AV1 encoder/decoder availability.

Where Android exposes the information, `MediaCodecInfo.isHardwareAccelerated` is used to distinguish hardware acceleration. Resolution/rate support is queried with `VideoCapabilities.areSizeAndRateSupported()` for 3840×2160 at 30 fps and 60 fps, separately for encode and decode.

A high-resolution display is never treated as proof of 4K media capability. Unknown capability remains unknown/not detected rather than being converted into a fabricated support claim.

## Certification status

Actual runtime `MediaCodecList` interrogation on the API-35 x86_64 emulator: **PASS** in GitHub Actions run `33734581374`.

Actual-phone codec inventory, hardware/software codec classification and real-device 3840×2160 @ 30/60 fps capability evidence: **NOT VERIFIED** until the certified APK is executed on physical Android hardware.
