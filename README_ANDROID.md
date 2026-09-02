# VideoFlow Android — Step 1 Foundation

Native Android Step 1 for VideoFlow. This repository intentionally stops before timeline editing, proxy generation, export/rendering, AI, recording, and final store packaging.

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

Timeline, proxy creation, clip editing, compositor/export, GPU rendering, offline AI watermark reconstruction, tracking, advanced audio, recorder/camera/screen recorder, export workers, final signing and store release packaging.
