# VideoFlow Android — Physical Device Step 1 Certification

Date: 2026-09-03

Certified software baseline: `9e414a65afa8a0a6235a794f056e61846b631bce`

GitHub Actions baseline: `33746474694` (run #43) — automated status **PASS**.

Physical-device certification status: **PARTIAL**.

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
| HEVC decode | PARTIAL — physical 1280×690 HEVC playback observed; capability screen not yet recorded |
| HEVC encode | NOT VERIFIED |
| HEVC 4K30 | NOT VERIFIED |
| HEVC 4K60 | NOT VERIFIED |
| VP9 | NOT VERIFIED |
| AV1 | NOT VERIFIED |

## Physical evidence captured on 2026-09-03

The certified debug APK was installed and launched successfully on a physical Android phone.

Observed physical-media evidence:

- small H.264/AAC MP4: 5.85 MB, 1360×768, playback observed
- large HEVC source A: 1.26 GB, 1280×690, 03:17:20, 1 video + 2 audio tracks
- large HEVC source B: 1.03 GB, 1280×720, 02:36:22
- large-source fingerprint mode: `STRONG_THREE_REGION`
- sampled bytes for the 1.26 GB source: 12.00 MB
- persisted URI permission displayed by the app
- playback of the 1.26 GB HEVC source observed at 02:05:09 of 03:17:21, approximately 63% into the file

This is valid physical evidence for reference-based import, bounded fingerprinting and large-source playback below the >3 GB certification threshold. It does not replace the genuine >3 GB gate.

## Test source record

Do not record personal media content. A sanitized fixture name is sufficient.

| Field | Result |
|---|---|
| Sanitized filename | Physical HEVC large-source A |
| Size | 1.26 GB — below mandatory >3 GB threshold |
| Duration | 03:17:20 |
| Resolution | 1280×690 |
| FPS | NOT VERIFIED |
| Video codec | HEVC |
| Audio codec | AAC-family MIME reported as `audio/mp4a-latm` |
| Storage/provider | Android document picker; persisted URI permission displayed |

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
| Certified APK install/launch | PASS |
| Project creation UI | PASS |
| System document-picker Add Media flow | PASS |
| Persisted URI permission shown on device | PASS |
| Large-source bounded fingerprint below 3 GB | PASS — 1.26 GB source, 12.00 MB sampled |
| Large-source physical playback below 3 GB | PASS — 1.26 GB HEVC source |
| Deep seek below 3 GB | PARTIAL — playback observed at ~63%; 25/50/75/95 sequence not yet completed |
| Genuine >3 GB import | NOT VERIFIED |
| Source status AVAILABLE after >3 GB import | NOT VERIFIED |
| >3 GB preview | NOT VERIFIED |
| >3 GB 25% seek | NOT VERIFIED |
| >3 GB 50% seek | NOT VERIFIED |
| >3 GB 75% seek | NOT VERIFIED |
| >3 GB 95% seek | NOT VERIFIED |
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
| App storage before media additions | 23.69 MB total |
| User data before | 147 kB |
| Cache before | 180 kB |
| App storage after 3 media items | 23.74 MB total |
| User data after | 201 kB |
| Cache after | 180 kB |
| Total-storage delta | approximately +0.05 MB (~50 kB) |
| User-data delta | +54 kB |
| Imported media represented in project | approximately 2.30 GB total (1.26 GB + 1.03 GB + 5.85 MB) |
| Source-sized copy observed | PASS — no source-sized increase observed for the ~2.30 GB imported set |
| Mandatory >3 GB storage-delta gate | NOT VERIFIED |

This is strong physical evidence that VideoFlow is storing references/metadata rather than copying the tested multi-gigabyte source set into app-private storage. The mandatory >3 GB single-source storage-delta test remains separate and NOT VERIFIED.

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

Physical-device Step 1 certification: **PARTIAL**.

The reference-based storage design, persisted URI display, bounded large-source fingerprinting and physical playback below 3 GB now have direct device evidence. The mandatory genuine >3 GB source, complete >3 GB seek sequence, force-stop/reboot persistence, physical relink/missing-source flow, memory/thermal measurements, capability screen and accessibility checks remain open.

Overall Step 1: **PARTIAL** until those required physical gates are recorded.
