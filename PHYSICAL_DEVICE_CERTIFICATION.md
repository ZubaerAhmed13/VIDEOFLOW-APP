# VideoFlow Android — Physical Device Step 1 Certification

Use the certified Step 1 Debug APK. Do not change any physical-device row to PASS without direct evidence.

## Required setup

- Android phone/tablet running API 26 or newer
- at least one genuine encoded video larger than 3 GB
- enough free storage to open/play that file normally

## Procedure

1. Install `VideoFlow_Android_Step1_Debug.apk`.
2. Open VideoFlow > Settings > Device Capability and record device model, API version, RAM, free storage, codec inventory and 4K30/4K60 results.
3. Record Android Settings > Apps > VideoFlow > Storage usage before media import.
4. Create a new project.
5. Tap Add Media and select the genuine >3 GB video through the system document picker.
6. Confirm metadata/fingerprint processing completes and Source Status is AVAILABLE.
7. Record VideoFlow app storage again. The increase must remain metadata/cache-scale and must not approximate the source-file size.
8. Preview the file and seek around 25%, 50%, 75% and 95% of duration.
9. Exit the project, force-stop VideoFlow, reopen it and verify the project/source reopens.
10. Reboot the device, reopen VideoFlow and verify the source remains accessible when the provider supports persistable URI permissions.
11. Temporarily make the source unavailable, verify the unavailable/offline state, then use Locate Original to relink it.
12. During import/fingerprint/preview, record peak app memory and note any Android thermal warning or severe throttling.
13. Preserve screenshots/recordings of the Device Capability and source-status screens as evidence.

## PASS criteria

- no app-private source-sized copy is created
- no artificial size-cap error appears
- >3 GB media metadata/fingerprint completes
- preview and late seek work
- project survives force-stop/reopen
- persistable access survives reboot where supported by the document provider
- relink validates the original source correctly
- memory remains bounded rather than scaling with full source size
- actual-device codec/4K information is reported without fabricated support claims

## Current status

Physical-device certification: **NOT VERIFIED**.

Automated certification remains **PASS** under GitHub Actions run `33734581374` (run #8).
