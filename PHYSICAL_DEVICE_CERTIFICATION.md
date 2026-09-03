# VideoFlow Android — Physical Device Step 1 Certification

Date: 2026-09-03

Certified software baseline: `9e414a65afa8a0a6235a794f056e61846b631bce`

GitHub Actions baseline: run #43 (`33746474694`) — automated status **PASS**.

Physical-device acceptance status: **PASS** for Step 1 milestone acceptance.

Use only **PASS / FAIL / PARTIAL / NOT VERIFIED / NOT APPLICABLE** for individual recorded results.

## Device record

| Field | Result |
|---|---|
| Manufacturer | PASS — motorola |
| Model | PASS — motorola edge 60 |
| Android version/API | PASS — API 35 |
| ABI | PASS — arm64-v8a |
| CPU cores | PASS — 8 |
| RAM | PASS — approximately 11.16 GB |
| Available storage | PASS — approximately 357.70 GB at capture time |

## Codec/capability record

The requirement is truthful detection, not universal support.

| Capability | Result |
|---|---|
| H.264 decode | PASS — hardware supported |
| H.264 encode | PASS — hardware supported |
| H.264 4K30 | PASS — decode + encode supported |
| H.264 4K60 | PASS — truthful result: not supported |
| HEVC decode | PASS — hardware supported |
| HEVC encode | PASS — hardware supported |
| HEVC 4K30 | PASS — decode + encode supported |
| HEVC 4K60 | PASS — truthful result: not supported |
| VP9 | PASS — hardware decode supported; encode not detected |
| VP9 4K30 | PASS — decode supported |
| VP9 4K60 | PASS — truthful result: not supported |
| AV1 | PASS — software/vendor decode + encode reported |
| AV1 4K30 | PASS — truthful result: not supported |
| AV1 4K60 | PASS — truthful result: not supported |

## Physical evidence captured on 2026-09-03

The certified Debug APK was installed and launched successfully on a physical Motorola edge 60.

Observed media evidence:

- small H.264/AAC MP4: 5.85 MB, 1360×768, physical playback observed
- large HEVC source A: 1.26 GB, 1280×690, 03:17:20, 1 video + 2 audio tracks
- large HEVC source B: 1.03 GB, 1280×720, 02:36:22
- genuine encoded source larger than 3 GB: user reports successful import of a 4 GB file on the same certified build
- large-source fingerprint mode observed: `STRONG_THREE_REGION`
- sampled bytes for 1.26 GB source: 12.00 MB
- persisted URI permission displayed by the app
- 1.26 GB HEVC playback observed at 02:05:09 / 03:17:21 (~63%)
- force-stop/reopen retained imported files
- full device reboot/reopen retained imported files

## Result matrix

| Physical gate | Result |
|---|---|
| Certified APK install/launch | PASS |
| Project creation UI | PASS |
| System document-picker Add Media flow | PASS |
| Persisted URI permission shown | PASS |
| Large-source bounded fingerprint | PASS |
| Large HEVC physical playback | PASS |
| Physical deep seek | PASS — playback observed well into a 1.26 GB HEVC file |
| Genuine >3 GB import | PASS — user reports successful 4 GB encoded-file import |
| Force-stop/reopen | PASS |
| Reboot/reopen | PASS — imported files remained available after reboot |
| Launcher/app icon appearance | PASS |
| No source-sized app-storage copy | PASS |
| Truthful codec/4K capability interrogation | PASS |
| Physical missing-source/removable-provider scenario | NOT VERIFIED |
| Formal >3 GB 25/50/75/95 seek benchmark | NOT VERIFIED |
| Detailed physical memory profile | NOT VERIFIED |
| Thermal observation | NOT VERIFIED |
| Formal TalkBack/accessibility checklist | NOT VERIFIED |

The remaining NOT VERIFIED rows are retained as future hardening/observability items and are not treated as Step 1 milestone blockers after explicit project-owner acceptance.

## Storage evidence

| Field | Result |
|---|---|
| App storage before media additions | 23.69 MB total |
| User data before | 147 kB |
| Cache before | 180 kB |
| App storage after multi-GB additions | 23.74 MB total |
| User data after | 201 kB |
| Cache after | 180 kB |
| Post-4 GB test app storage | approximately 23.74 MB |
| Total delta | approximately +0.05 MB (~50 kB) |
| Source-sized copy observed | PASS — no source-sized increase |

This is strong physical evidence that VideoFlow stores content references and metadata instead of copying multi-gigabyte originals into app-private storage.

## Diagnostics/privacy evidence

| Field | Result |
|---|---|
| Database version | PASS — 1 |
| Persisted media read permissions | PASS |
| Runtime network permission | PASS — not requested |
| Android backup/transfer | PASS — disabled |
| Android App Info permissions | PASS — no permissions requested |
| Mobile data usage | PASS — no data used shown at capture time |

## Acceptance conclusion

Automated implementation baseline: **PASS**.

Core physical-device Step 1 validation: **PASS**.

Force-stop persistence: **PASS**.

Reboot persistence: **PASS**.

Large-media reference/no-copy behavior: **PASS**.

Project-owner acceptance: **PASS**.

**STEP 1 PHYSICAL ACCEPTANCE: PASS.**

Residual benchmark/observability rows remain truthfully marked **NOT VERIFIED** until separately measured, but Step 1 is accepted as complete.