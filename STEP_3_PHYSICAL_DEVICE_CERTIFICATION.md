# VideoFlow Android Step 3 — Physical Device Certification

## Rule

Step 3 cannot be declared COMPLETE from emulator CI alone. Use the exact `VideoFlow_Android_Step3_Debug.apk` or test-signed `VideoFlow_Android_Step3_Release.apk` produced by the passing Step 3 certification run and record the SHA-256 here.

Certified GitHub SHA: PENDING

Certified APK SHA-256: PENDING

Overall physical result: **NOT VERIFIED**

## Device

Manufacturer: PENDING

Model: PENDING

Android version: PENDING

API level: PENDING

RAM: PENDING

Storage before: PENDING

Storage after: PENDING

## Required project/source

Use real 4K video, real audio and a real image. The project must exercise video, text, image, audio, transform, crop, keyframes, gain and fade. Prefer also the previously validated multi-GB original source.

Source size: PENDING

Source resolution: PENDING

Source FPS: PENDING

Source codec: PENDING

Source colour: PENDING

## Test A — real 1080p H.264 High

Settings: 1920×1080, project FPS, H.264, High quality.

Container opens: NOT VERIFIED

Resolution correct: NOT VERIFIED

FPS correct: NOT VERIFIED

Duration/timeline correct: NOT VERIFIED

Text/image/transforms/crop/keyframes correct: NOT VERIFIED

Audio present/correct: NOT VERIFIED

Result: NOT VERIFIED

## Test B — mandatory real 4K30

Settings: 3840×2160, 30 fps or supported project/source cadence, H.264 or HEVC according to capability.

Container opens: NOT VERIFIED

3840×2160 confirmed: NOT VERIFIED

FPS confirmed: NOT VERIFIED

Duration confirmed: NOT VERIFIED

Audio confirmed: NOT VERIFIED

Detail/text/gradient/motion inspected: NOT VERIFIED

Result: NOT VERIFIED

## Test C — 1080p60 where supported

Device capability: PENDING

Result: NOT VERIFIED / NOT APPLICABLE

## Test D — 4K60 where supported

Device capability: PENDING

Result: NOT VERIFIED / NOT APPLICABLE

## Test E — HEVC where supported

Device capability: PENDING

Output codec: PENDING

Result: NOT VERIFIED / NOT APPLICABLE

## Test F — background + screen off

Start a real export, leave the app, turn the screen off, wait, return, and validate the output.

Foreground notification/progress present: NOT VERIFIED

Export continued: NOT VERIFIED

Output validated: NOT VERIFIED

Result: NOT VERIFIED

## Test G — cancellation

Start a real export and cancel it.

Job becomes CANCELLED: NOT VERIFIED

Renderer/encoder stops: NOT VERIFIED

No fake completed file: NOT VERIFIED

Partial destination cleaned/truncated: NOT VERIFIED

Result: NOT VERIFIED

## Test H — real multi-GB original source

No artificial 3 GB rejection: NOT VERIFIED

No source-sized app-private copy: NOT VERIFIED

Render starts from original source reference: NOT VERIFIED

Memory remains bounded: NOT VERIFIED

Output completes/validates: NOT VERIFIED

Result: NOT VERIFIED

## Test I — colour/range

Use known real/reference content.

No washed-out image: NOT VERIFIED

No crushed blacks: NOT VERIFIED

No unexpected tint: NOT VERIFIED

No gamma shift: NOT VERIFIED

Source colour standard/range/transfer: PENDING

Output colour standard/range/transfer: PENDING

Result: NOT VERIFIED

## Test J — audio and A/V sync

Volume/gain correct: NOT VERIFIED

Fade correct: NOT VERIFIED

No unexpected distortion: NOT VERIFIED

A/V sync within approximately one output frame: NOT VERIFIED

Result: NOT VERIFIED

## Test K — HDR / 10-bit

Only run with compatible real HDR/10-bit input, display/device and encoder.

HDR preservation: HDR EXPORT NOT VERIFIED

10-bit handling: NOT VERIFIED

If the device is incapable, mark NOT APPLICABLE rather than PASS.

## Output record

Output resolution: PENDING

Output FPS: PENDING

Output codec: PENDING

Output bitrate: PENDING

Output colour: PENDING

Output file size: PENDING

Render duration: PENDING

Average rendering speed: PENDING

Peak memory if available: PENDING

Thermal behavior: PENDING

## Acceptance

Change `Overall physical result` to PASS only after every mandatory gate above has evidence. Until then Step 3 remains NOT COMPLETE.
