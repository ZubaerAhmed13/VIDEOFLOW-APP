# VideoFlow Android — Step 1 Test Report

Date: 2026-09-03

Certified code commit: `9e414a65afa8a0a6235a794f056e61846b631bce`

GitHub Actions certification: `33746474694` (run #43)

Status vocabulary: **PASS / FAIL / PARTIAL / NOT VERIFIED / NOT APPLICABLE**.

## Overall status

**PARTIAL**

All Step 1 software-remediation, build, static-analysis, JVM/unit, APK-assembly and API-35 emulator gates are **PASS**. Genuine >3 GB physical-device certification remains **NOT VERIFIED**, so Step 1 is not declared complete.

## Software remediation

| Gate | Status | Evidence |
|---|---|---|
| Real `SourceStatus.CHANGED` detection | PASS | Same `content://` URI with different underlying media becomes CHANGED in repository instrumentation |
| Fingerprint-strength-aware identity | PASS | `FULL_SMALL_FILE`, `STRONG_THREE_REGION`, `WEAK_FIRST_REGION_ONLY`, `UNAVAILABLE` participate in decisions |
| Strong relink | PASS | Strong equivalent identity required; SHA/critical metadata mismatch rejected |
| Weak relink safety | PASS | Weak match is never called strong and requires explicit confirmation |
| Unavailable fingerprint safety | PASS | Never auto-identified as exact |
| Duplicate confirmation | PASS | Duplicate candidate is not inserted into Room before user confirmation |
| Duplicate cancel | PASS | Room row count remains unchanged |
| Duplicate Add Anyway | PASS | Prepared candidate can be inserted explicitly without re-analysis |
| CHANGED playback safety | PASS | CHANGED source is not treated as unquestionably playable original; Locate Original is offered |
| Launcher icon | PASS | Adaptive, round and Android 13 monochrome resources configured |
| Backup/data extraction | PASS | `allowBackup=false`; cloud backup/device transfer exclusions configured |
| Accessibility semantics | PASS | Critical New Project, Settings and Add Media controls are covered by Compose semantics test; relink/duplicate buttons are labeled text controls |

## CI

| Gate | Status | Evidence |
|---|---|---|
| Clean | PASS | `./gradlew clean` |
| Unit tests | PASS | **23 tests, 0 failures, 0 skipped** |
| Lint | PASS | 0 errors; 12 non-blocking warnings reviewed |
| Instrumentation compile | PASS | `compileDebugAndroidTestKotlin` |
| Debug APK | PASS | `assembleDebug` |
| AndroidTest APK | PASS | `assembleDebugAndroidTest` |
| Test-signed release APK | PASS | `assembleRelease` |
| Prohibited-pattern audit | PASS | No WebView, INTERNET permission, source-sized `readBytes()`/ByteArray pattern, or unfinished Step 1 marker |
| API-35 emulator | PASS | KVM-backed Google APIs x86_64 runtime |
| Instrumentation | PASS | **OK (16 tests)**, 0 failures |

The remaining lint warnings are non-blocking version-availability/style/debug-test-provider items. Missing launcher-icon and modern backup/data-extraction warnings that motivated this remediation are resolved. Dependency versions were intentionally not churned merely to silence newer-version notices.

## JVM/unit coverage — 23 tests

- `FingerprintEngineTest`: 6/6 PASS, including first/middle/end sensitivity, bounded small-file hashing, truncation, cancellation and 100 GB structural sampling.
- `RelinkPolicyTest`: 14/14 PASS, covering strong/weak/unavailable identity, mismatches, metadata contradictions and CHANGED classification.
- `LargeValueTest`: 2/2 PASS, including values beyond 32-bit range.
- `LocalDiagnosticLogTest`: 1/1 PASS.

## API-35 instrumentation — 16 tests

- Room create/rename/delete and no original-media deletion: PASS.
- Room reopen with 10 GB metadata value: PASS.
- Device `MediaCodecList` interrogation: PASS.
- MP4 A/V metadata: PASS.
- Video-only MP4: PASS.
- Malformed media controlled failure: PASS.
- Rotation fixture handling: PASS.
- Media3 prepare/seek: PASS.
- Same URI changed underlying media -> CHANGED: PASS.
- Unchanged source -> AVAILABLE: PASS.
- Pipe/non-seekable provider preserves weak fingerprint strength: PASS.
- Duplicate candidate no pre-confirmation write: PASS.
- Duplicate cancel leaves Room unchanged: PASS.
- Wrong strong relink rejected and original URI retained: PASS.
- Missing provider source -> MISSING: PASS.
- Compose Home/New Project/Project/Add Media plus critical semantics: PASS.

## Large-media structural evidence

- No artificial application source-size cap: PASS.
- `Long` media sizes/offsets/counters: PASS.
- Logical 500 MB, 2 GB, 3 GB, 5 GB, 10 GB and 100 GB test values: PASS.
- 100 GB random-access fingerprint fixture uses offsets above `Int.MAX_VALUE`: PASS.
- Normal large-file fingerprint samples approximately 12 MiB total using 256 KiB working buffers: PASS.
- Normal import path is URI/reference based and contains no source-sized copy implementation: PASS.

## Physical-device certification

| Gate | Status |
|---|---|
| Genuine encoded >3 GB Android document | NOT VERIFIED |
| >3 GB import | NOT VERIFIED |
| >3 GB preview | NOT VERIFIED |
| 25% / 50% / 75% / 95% seek | NOT VERIFIED |
| App-storage before/after delta | NOT VERIFIED |
| No source-sized physical app-storage increase | NOT VERIFIED |
| Force-stop/reopen | NOT VERIFIED |
| Reboot persisted-URI access | NOT VERIFIED |
| Physical missing-source flow | NOT VERIFIED |
| Physical strong relink | NOT VERIFIED |
| Physical wrong-file relink rejection | NOT VERIFIED |
| Peak import/fingerprint/preview memory | NOT VERIFIED |
| Thermal observation | NOT VERIFIED |
| Actual-device codec/4K profile | NOT VERIFIED |
| Launcher icon on physical launcher | NOT VERIFIED |

## Certified main artifacts

- `VideoFlow_Android_Step1_Debug.apk`
  - Size: **23,330,809 bytes**
  - SHA-256: `f35112112c84b71722631ec612f06a6d26ad7d17ad5caca3b72f8d1ce4292dd9`
- `VideoFlow_Android_Step1_Release.apk`
  - Size: **15,910,984 bytes**
  - SHA-256: `13fc101491df31c58906c63bb37c98cd17eff93edd16d0e664bbcb85c6304855`
- `VideoFlow_Android_Step1_Source.zip`
  - Size: **82,101 bytes**
  - SHA-256: `1ed0f0ee5f371a25403822ae5d9b3a9ffc259d0ea74367b7917216f35d042236`

Release signing status: **test-signed using the debug signing configuration**. Production signing is outside Step 1.

## Verdict

Automated Step 1 certification: **PASS**.

Physical-device certification: **NOT VERIFIED**.

Overall Step 1: **PARTIAL**.
