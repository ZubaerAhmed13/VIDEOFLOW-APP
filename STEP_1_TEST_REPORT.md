# VideoFlow Android — Step 1 Test Report

Date: 2026-09-03

Certification commit: `93567c48a1591ec9512a7aa02375ca8a27e534ee`

GitHub Actions certification run: `33734581374` (run #8)

Status vocabulary in this report is restricted to: **PASS / FAIL / PARTIAL / NOT VERIFIED / NOT APPLICABLE**.

## Executive result

**Overall Step 1 test status: PARTIAL**

The native Android Step 1 implementation passes all automated build, static-analysis, JVM/unit, APK assembly, APK integrity, API-35 emulator, Room, MediaExtractor, Media3 and Compose certification gates currently executable in CI. Physical-device-only certification items are explicitly **NOT VERIFIED** and therefore Step 1 is not declared complete yet.

## CI certification matrix

| Gate | Status | Evidence |
|---|---|---|
| Android API 37 compilation environment | PASS | Run #8 build job |
| Clean build | PASS | `./gradlew clean` |
| JVM/unit tests | PASS | `./gradlew test` |
| Android lint | PASS | `./gradlew lint` |
| Instrumentation test compilation | PASS | `compileDebugAndroidTestKotlin` |
| Debug APK assembly | PASS | `assembleDebug` |
| Instrumentation APK assembly | PASS | `assembleDebugAndroidTest` |
| Test-signed release APK assembly | PASS | `assembleRelease` |
| Prohibited-pattern audit | PASS | No WebView, INTERNET permission, source-sized `readBytes()` or unfinished Step 1 markers detected |
| Runtime APK checksum verification | PASS | SHA-256 bundle verification before emulator execution |
| API-35 emulator boot | PASS | Android emulator boot completed with KVM |
| Debug APK installation | PASS | Installed successfully |
| Instrumentation APK installation | PASS | Installed successfully |
| API-35 instrumentation suite | PASS | `OK (9 tests)` |
| Emulator certification job | PASS | Run #8 emulator job concluded success |

## Instrumentation coverage

The API-35 emulator certification executed nine Android instrumentation tests and reported `OK (9 tests)`.

### Room persistence

- Project create / rename / delete behavior: **PASS**
- Original media is not deleted by project-row deletion: **PASS**
- 10 GB media-size metadata persists across database close/reopen: **PASS**

### Device capability interrogation

- Actual `MediaCodecList` interrogation executes on Android runtime: **PASS**

### MediaExtractor / content URI analysis

- Real MP4 through a `content://` test provider with video + audio: **PASS**
- Video-only MP4 without audio track: **PASS**
- Malformed media produces a controlled failure: **PASS**
- Rotated MP4 fixture is readable without fabricating rotation metadata: **PASS**

### Media3 playback

- Media3 prepares the real MP4 fixture: **PASS**
- Duration is available: **PASS**
- Seek operations across multiple positions in the fixture execute: **PASS**
- Pause operation executes: **PASS**

### Compose UI

- Home screen: **PASS**
- New Project flow: **PASS**
- Project detail screen: **PASS**
- Add Media affordance: **PASS**

## JVM/unit coverage

### Large-value safety

The codebase uses `Long` for media sizes, byte offsets, durations and related counters. Tests exercise logical media sizes including 500 MB, 2 GB, 3 GB, 5 GB, 10 GB and 100 GB and offsets above `Int.MAX_VALUE`.

Status: **PASS**

### `VideoFlowSampleSHA256-v1`

Automated tests cover:

- deterministic hashing: **PASS**
- first-region mutation sensitivity: **PASS**
- middle-region mutation sensitivity: **PASS**
- end-region mutation sensitivity: **PASS**
- bounded small-file streaming: **PASS**
- truncated-reader handling: **PASS**
- cancellation: **PASS**
- 100 GB structural reader with bounded 12 MiB sampling and 64-bit offsets: **PASS**

### Relink identity policy

- Exact fingerprint / known identity match: **PASS**
- Fingerprint mismatch rejection: **PASS**
- Missing fingerprint rejection: **PASS**
- Known size mismatch rejection: **PASS**
- Known dimension mismatch rejection: **PASS**

### Local diagnostics

- 200-event bounded ring behavior: **PASS**
- Diagnostic message truncation: **PASS**

## Architecture / privacy verification

| Requirement | Status |
|---|---|
| Native Kotlin Android application | PASS |
| Jetpack Compose / Material 3 | PASS |
| Hilt dependency injection | PASS |
| Room persistence | PASS |
| Coroutines / IO dispatching foundation | PASS |
| Media3 / ExoPlayer preview | PASS |
| MediaExtractor metadata analysis | PASS |
| SAF OpenDocument / `content://` source architecture | PASS |
| Persistable read-permission attempt | PASS |
| Reference-based source model; original media not copied on normal import path | PASS |
| No artificial source-file-size cap in application model | PASS |
| 64-bit `Long` size/offset model | PASS |
| No WebView/browser wrapper architecture | PASS |
| No `android.permission.INTERNET` in application manifest | PASS |
| No broad legacy storage permission | PASS |
| No telemetry/ads/crash-upload subsystem implemented | PASS |

## Physical-device certification

The following cannot be truthfully certified by GitHub Actions or an x86_64 emulator and remain **NOT VERIFIED** until executed on an actual Android phone/tablet:

| Physical-device gate | Status |
|---|---|
| Genuine encoded source video >3 GB selected through the Android document picker | NOT VERIFIED |
| Import completes without making an app-private copy of the >3 GB source | NOT VERIFIED |
| Measured app-storage delta remains metadata/cache-scale rather than source-size-scale | NOT VERIFIED |
| Preview of genuine >3 GB source on device | NOT VERIFIED |
| Late seek near the end of genuine >3 GB source | NOT VERIFIED |
| Save project, force-stop, reopen and resume source access | NOT VERIFIED |
| Persisted SAF read grant survives device reboot when provider supports persistable permissions | NOT VERIFIED |
| Relink flow against a physically removed/restored source | NOT VERIFIED |
| Peak Java/native memory during genuine >3 GB import/fingerprint/preview | NOT VERIFIED |
| Thermal behavior during prolonged large-file preview/fingerprinting | NOT VERIFIED |
| Actual phone hardware codec inventory and hardware/software classification | NOT VERIFIED |
| Actual-device 3840×2160 @ 30 fps capability interrogation | NOT VERIFIED |
| Actual-device 3840×2160 @ 60 fps capability interrogation | NOT VERIFIED |

## Certified artifact hashes

- `VideoFlow_Android_Step1_Debug.apk`
  - SHA-256: `907e97b96ed1290e0bdeba76501d9fcabeb8ddc34c12dc3b9f693ac74689745b`
- `VideoFlow_Android_Step1_Release.apk`
  - SHA-256: `be5f9f04f010371b162116feab707fe48858ac98dd3c3f32071c8b5280c521a4`
- `VideoFlow_Android_Step1_Source.zip`
  - SHA-256: `803b56e82196a9ff2bdbd11ad30d011e0ad05f87afe3be146c4db4f8b99c3321`

The release APK produced by Step 1 CI is **test-signed** for certification. Production signing belongs to the later release step.

## Final test verdict

**PARTIAL**

Automated Step 1 certification is **PASS**. Physical-device certification is **NOT VERIFIED**. No physical-device item is represented as PASS without direct evidence.
