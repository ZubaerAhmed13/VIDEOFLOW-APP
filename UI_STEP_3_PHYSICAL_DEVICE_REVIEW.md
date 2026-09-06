# VideoFlow UI Step 3 — Physical Device Review

## Status

**NOT YET VERIFIED ON A PHYSICAL ANDROID DEVICE**

This file intentionally does not pre-fill PASS results. Automated JVM, lint, APK, emulator and install checks are valuable but do not replace a real device review.

## APK To Test

Use the exact `VideoFlow_Android_UI_Step3_Review.apk` artifact produced by the latest green `VideoFlow Android UI Step 3 Certification` workflow for the final branch HEAD.

Do not use a locally rebuilt APK or an APK from an earlier commit for final certification.

## Device Information

- Manufacturer: NOT RECORDED
- Model: NOT RECORDED
- Android version: NOT RECORDED
- API level: NOT RECORDED
- Screen size/class: NOT RECORDED
- Available storage at start: NOT RECORDED

## Required Checklist

| Area | Status | Evidence / Notes |
|---|---|---|
| Review APK installs | NOT VERIFIED | Physical device required |
| Cold launch | NOT VERIFIED | Physical device required |
| Onboarding | NOT VERIFIED | 3 screens + Skip / New Project |
| Home | NOT VERIFIED | Brand, New Project, recent projects |
| Manual New Project | NOT VERIFIED | Test 16:9, 9:16, 1:1, 4:5 |
| Start from Media | NOT VERIFIED | Real Android document picker |
| Picker cancellation | NOT VERIFIED | Must not leave broken project |
| Editor opens | NOT VERIFIED | Preview/transport/timeline/context tools |
| Trim / Split / Crop / Transform | NOT VERIFIED | Spot-check protected UI Step 1/2 |
| Text / Image / Audio | NOT VERIFIED | Spot-check protected UI Step 1/2 |
| Keyframes | NOT VERIFIED | HOLD/LINEAR behavior remains available |
| Simple Export | NOT VERIFIED | Recommended settings first |
| Save destination | NOT VERIFIED | Real CreateDocument provider |
| Real export progress | NOT VERIFIED | Observe real job percentage/stages |
| Cancel export | NOT VERIFIED | Confirm CANCELLED, no success state |
| Export success | NOT VERIFIED | Output validation must pass |
| Open Video | NOT VERIFIED | content URI + granted permission |
| Share | NOT VERIFIED | Android Sharesheet |
| Advanced Export | NOT VERIFIED | Codec/FPS/quality/audio/colour |
| Invalid configuration | NOT VERIFIED | Must block/explain, no silent downgrade |
| Missing original | NOT VERIFIED | Locate Original flow |
| Changed original | NOT VERIFIED | Review Source flow |
| Permission lost | NOT VERIFIED | Locate Original flow |
| Proxy editing | NOT VERIFIED | Final export still uses original |
| Clear editing proxies | NOT VERIFIED | Originals/edits must remain intact |
| Settings | NOT VERIFIED | Performance/Storage/Privacy/A11y/About/Diagnostics |
| Portrait | NOT VERIFIED | Physical device required |
| Landscape | NOT VERIFIED | Physical device required |
| 150% font | NOT VERIFIED | Physical device required |
| 200% font | NOT VERIFIED | Physical device required |
| TalkBack spot check | NOT VERIFIED | Physical device required |
| Long/large media | NOT VERIFIED | Real media source required |

## Export Media Recommendation

For a meaningful physical review, test at least:

1. one short 1080p H.264 clip with audio;
2. one portrait source;
3. one source with a non-integer common frame rate such as 29.97 or 59.94 if available;
4. one larger source suitable for proxy editing;
5. HEVC only on a device where the capability page reports support.

## Approval Rule

This document can only be changed from NOT VERIFIED after recording a real physical device, exact certified Review APK hash, actual user-flow results and actual export evidence. Until then, UI Step 3 cannot be labeled fully COMPLETE under the master prompt's success rule.
