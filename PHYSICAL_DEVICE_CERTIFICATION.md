# VideoFlow Android — Physical Device Step 1 Certification

Date: 2026-09-03

Certified software baseline: `9e414a65afa8a0a6235a794f056e61846b631bce`

GitHub Actions baseline: `33746474694` (run #43) — automated status **PASS**.

Physical-device certification status: **NOT VERIFIED**.

Use only **PASS / FAIL / PARTIAL / NOT VERIFIED / NOT APPLICABLE** for recorded results.

## Required device

- Physical Android phone/tablet, API 26 or newer.
- Enough free storage for normal playback of the test source.
- Genuine encoded video larger than 3 GB. A sparse/placeholder file is not valid evidence.
- Prefer a document provider that supports persistable URI read grants for the reboot test.

## Device record

| Field | Result |
|---|---|
| Manufacturer | NOT VERIFIED |
| Model | NOT VERIFIED |
| Android version | NOT VERIFIED |
| API | NOT VERIFIED |
| ABI | NOT VERIFIED |
| RAM | NOT VERIFIED |
| Available storage | NOT VERIFIED |

## Codec/capability record

The requirement is truthful detection, not universal support.

| Capability | Result |
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

## Test source record

Do not record personal media content. A sanitized fixture name is sufficient.

| Field | Result |
|---|---|
| Sanitized filename | NOT VERIFIED |
| Size | NOT VERIFIED |
| Duration | NOT VERIFIED |
| Resolution | NOT VERIFIED |
| FPS | NOT VERIFIED |
| Video codec | NOT VERIFIED |
| Audio codec | NOT VERIFIED |
| Storage/provider | NOT VERIFIED |

## Required procedure and result record

1. Install `VideoFlow_Android_Step1_Debug.apk` from certified main run #43.
2. Record Settings → Apps → VideoFlow → Storage before import.
3. Open VideoFlow → Settings → Device Capability and record device/capability fields above.
4. Create a project and select the genuine >3 GB encoded video using Add Media/system document picker.
5. Confirm no size-rejection/crash; metadata and bounded fingerprint complete and source is AVAILABLE.
6. Record app storage after import and calculate delta.
7. Preview the source.
8. Seek to approximately 25%, 50%, 75%, and 95%; confirm playback resumes.
9. Force-stop VideoFlow, reopen, open the saved project, and verify the source/preview.
10. Reboot the device and repeat project/source open with a provider supporting persistence.
11. Make the source unavailable in a controlled provider/removable-storage scenario; verify MISSING or PERMISSION_LOST and Locate Original.
12. Restore/select the correct original and verify safe relink.
13. Select a different video and verify mismatch rejection.
14. Record memory during import/fingerprint/preview/late seek using `adb shell dumpsys meminfo`, Android Studio profiler, or another valid device measurement.
15. Note thermal warning/throttling observation without overstating long-duration export certification.
16. Verify launcher icon and critical UI controls on device; use TalkBack/basic accessibility check for New Project, Add Media, Settings, Locate Original and duplicate dialog buttons.

## Result matrix

| Physical gate | Result |
|---|---|
| Genuine >3 GB import | NOT VERIFIED |
| Source status AVAILABLE after import | NOT VERIFIED |
| Preview | NOT VERIFIED |
| 25% seek | NOT VERIFIED |
| 50% seek | NOT VERIFIED |
| 75% seek | NOT VERIFIED |
| 95% seek | NOT VERIFIED |
| Force-stop/reopen | NOT VERIFIED |
| Reboot/reopen | NOT VERIFIED |
| Missing source behavior | NOT VERIFIED |
| Correct-source relink | NOT VERIFIED |
| Wrong-file rejection | NOT VERIFIED |
| Launcher icon device appearance | NOT VERIFIED |
| Basic accessibility device check | NOT VERIFIED |

## Storage evidence

| Field | Result |
|---|---|
| App storage before | NOT VERIFIED |
| App storage after | NOT VERIFIED |
| Delta | NOT VERIFIED |
| Source copied into app storage | NOT VERIFIED |

PASS criterion: the app-storage increase remains metadata/cache scale and does not approximate the >3 GB source size.

## Memory evidence

| Stage | Result |
|---|---|
| Import | NOT VERIFIED |
| Fingerprint | NOT VERIFIED |
| Preview | NOT VERIFIED |
| Late seek | NOT VERIFIED |
| Bounded conclusion | NOT VERIFIED |

The physical PASS criterion is not a single fixed MiB number; memory must remain working-set/decoder scale rather than source-size proportional and must not approach multi-gigabyte allocation merely because the source exceeds 3 GB.

## Thermal evidence

Thermal observation: **NOT VERIFIED**.

## Weak-provider physical case

Weak/non-seekable provider physical test: **NOT VERIFIED**.

This specific provider case may remain NOT VERIFIED when no suitable physical provider is available because the weak-identity behavior is covered by automated unit/instrumentation tests. It must never be represented as a physical PASS without evidence.

## Current conclusion

Automated implementation baseline: **PASS**.

Physical-device Step 1 certification: **NOT VERIFIED**.

Overall Step 1: **PARTIAL** until the required physical evidence is recorded.
