# VideoFlow Android — Step 1 Foundation

Native Android Step 1 for VideoFlow. This repository intentionally stops before timeline editing, proxy generation, export/rendering, AI, recording, and final store packaging.

## Current certification status

**Overall Step 1 status: PARTIAL**

Automated certification is **PASS**. GitHub Actions run `33734581374` (run #8) passed clean build, JVM/unit tests, Android lint, instrumentation compilation, Debug APK assembly, test-signed Release APK assembly, checksum verification, API-35 emulator boot, APK installation and the complete Android instrumentation suite (`OK (9 tests)`).

Physical-device-only checks remain **NOT VERIFIED**: genuine encoded >3 GB import/preview/late-seek/reopen, persisted SAF access after reboot, source-storage delta, device memory/thermal measurements, and actual-device hardware codec/4K capability evidence.

See:

- `STEP_1_COMPLETION_REPORT.md`
- `STEP_1_TEST_REPORT.md`
- `LARGE_MEDIA_STEP1.md`
- `DEVICE_CAPABILITY_STEP1.md`

Step 2 has not been started.

## Technology

- Kotlin, Jetpack Compose, Material 3
- Hilt dependency injection
- Android Storage Access Framework (`OpenDocument` / `content://`)
- `ParcelFileDescriptor` / `FileDescriptor`
- `MediaExtractor` for track metadata
- AndroidX Media3 / ExoPlayer for native preview and seek
- Room for project/reference persistence
- structured coroutines via ViewModel scopes and IO dispatchers

## SDK and app identity

- minSdk 26
- compileSdk 37 / targetSdk 37
- applicationId `com.videoflow.app`
- Step 1 version `1.0.0-alpha01`, versionCode 1

## Build

The CI workflow installs Gradle 9.6.0, generates the standard wrapper binary/scripts, and then invokes `./gradlew`. A fresh local clone without `gradle-wrapper.jar` can bootstrap once with:

```bash
gradle wrapper --gradle-version 9.6.0
```

Then use:

```bash
./gradlew clean
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew connectedDebugAndroidTest
```

## Step 1 implementation

The source includes create/list/open projects, real document-URI selection, persistable read permission when the provider permits it, genuine media metadata analysis, bounded fingerprinting, Room persistence, native Media3 preview/seek, missing-source state, fingerprint-based relink, codec capability interrogation, local diagnostics, adaptive Compose foundations, system dark/light theme, and no runtime network permission.

## Deferred by the five-step plan

Timeline, proxy creation, clip editing, compositor/export, GPU rendering, offline AI watermark reconstruction, tracking, advanced audio, recorder, export workers, final signing and store release packaging.
