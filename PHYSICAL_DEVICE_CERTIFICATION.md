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
| Manufacturer | PASS — motorola |
| Model | PASS — motorola edge 60 |
| Android version/API | PASS — API 35 |
| ABI | PASS — arm64-v8a |
| CPU cores | PASS — 8 |
| RAM | PASS — 11.16 GB |
| Available storage | PASS — 357.70 GB |

## Codec/capability record

The requirement is truthful detection, not universal support.

| Capability | Result |
|---|---|
| H.264 decode | PASS — hardware supported |
| H.264 encode | PASS — hardware supported |
| H.264 4K30 | PASS — decode + encode supported |
| H.264 4K60 | PASS — truthful result: decode + encode not supported |
| HEVC decode | PASS — hardware supported |
| HEVC encode | PASS — hardware supported |
| HEVC 4K30 | PASS — decode + encode supported |
| HEVC 4K60 | PASS — truthful result: decode + encode not supported |
| VP9 | PASS — hardware decode supported; encode not detected |
| VP9 4K30 | PASS — decode supported |
| VP9 4K60 | PASS — truthful result: decode not supported |
| AV1 | PASS — decode/encode software/vendor reported |
| AV1 4K30 | PASS — truthful result: decode/encode not supported |
| AV1 4K60 | PASS — truthful result: decode/encode not supported |

## Physical evidence captured on 2026-09-03

The certified debug APK was installed and launched successfully on a physical Motorola edge 60.

Observed physical-media evidence:

- small H.264/AAC MP4: 5.85 MB, 1360×768, playback observed
- large HEVC source A: 1.26 GB, 1280×690, 03:17:20, 1 video + 2 audio tracks
- large HEVC source B: 1.03 GB, 1280×720, 02:36:22
- genuine encoded source larger than 3 GB: user reports successful import of a 4 GB file on the same certified build
- large-source fingerprint mode observed: `STRONG_THREE_REGION`
- sampled bytes for the 1.26 GB source: 12.00 MB
- persisted URI permission displayed by the app
- playback of the 1.26 GB HEVC source observed at 02:05:09 of 03:17:21, approximately 63% into the file
- after force-stopping and reopening VideoFlow, the imported files remained in the project; force-stop persistence therefore passed

The exact metadata screen for the 4 GB file has not yet been captured in a screenshot, so its detailed duration/resolution/codec fields remain unrecorded even though the import itself was reported successful.

## Test source record

Do not record personal media content. A sanitized fixture name is sufficient.

| Field | Result |
|---|---|
| Sanitized filename | Genuine 4 GB encoded physical-device source |
| Size | PASS — user reports 4 GB |
| Duration | NOT VERIFIED |
| Resolution | NOT VERIFIED |
| FPS | NOT VERIFIED |
| Video codec | NOT VERIFIED |
| Audio codec | NOT VERIFIED |
| Storage/provider | Android document picker / persisted URI workflow |

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
| Deep seek below 3 GB | PARTIAL — playback observed at ~63%; formal 25/50/75/95 sequence not yet completed |
| Genuine >3 GB import | PASS — user reports successful 4 GB encoded-file import on the physical device |
| Source status AVAILABLE after >3 GB import | PARTIAL — import persisted; explicit AVAILABLE screenshot for the 4 GB source not yet captured |
| >3 GB preview | NOT VERIFIED |
| >3 GB 25% seek | NOT VERIFIED |
| >3 GB 50% seek | NOT VERIFIED |
| >3 GB 75% seek | NOT VERIFIED |
| >3 GB 95% seek | NOT VERIFIED |
| Force-stop/reopen | PASS — files remained after force stop and reopen |
| Reboot/reopen | NOT VERIFIED |
| Missing source behavior | NOT VERIFIED |
| Correct-source relink | NOT VERIFIED |
| Wrong-file rejection | NOT VERIFIED |
| Launcher icon device appearance | PASS — launcher/app icon visible in Android App Info |
| Basic accessibility device check | NOT VERIFIED |

## Storage evidence

| Field | Result |
|---|---|
| App storage before media additions | 23.69 MB total |
| User data before | 147 kB |
| Cache before | 180 kB |
| App storage after three earlier media items | 23.74 MB total |
| User data after earlier media additions | 201 kB |
| Cache after | 180 kB |
| Post-4 GB test app storage | 23.74 MB shown in Android App Info |
| Total-storage delta from original baseline | approximately +0.05 MB (~50 kB) |
| Imported media represented before the 4 GB addition | approximately 2.30 GB total |
| Genuine >3 GB source added | PASS — 4 GB reported |
| Source-sized copy observed | PASS — no source-sized increase observed; app storage remained 23.74 MB |
| Mandatory >3 GB storage-delta gate | PASS — post-4 GB app storage remained metadata-scale rather than increasing by gigabytes |

This is strong physical evidence that VideoFlow stores references/metadata rather than copying imported multi-gigabyte source media into app-private storage.

## Diagnostics/privacy evidence

| Field | Result |
|---|---|
| Database version | PASS — 1 |
| Persisted media read permissions | PASS — 3 |
| Runtime network permission | PASS — not requested |
| Android backup/transfer | PASS — disabled |
| Android App Info permissions | PASS — no permissions requested |
| Mobile data usage | PASS — no data used shown |

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

Direct physical evidence now covers installation, device capability interrogation, multi-gigabyte reference-based storage, a genuine 4 GB import, force-stop persistence, persisted URI state, privacy diagnostics, and large HEVC playback below 3 GB. Remaining open gates are >3 GB preview/seek, reboot persistence, physical missing/relink behavior, memory/thermal measurement, and accessibility.

Overall Step 1: **PARTIAL** until those remaining required physical gates are recorded.
