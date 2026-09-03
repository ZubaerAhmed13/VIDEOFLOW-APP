# VideoFlow Android — Professional 5-Step Build

Native Android implementation of VideoFlow. Step 1 foundation is preserved and Step 2 adds the professional non-destructive editor core. Final rendering/export (Step 3), local AI Watermark Studio (Step 4) and production hardening/signing (Step 5) remain intentionally outside this stage.

## Step 2 status

**Overall status: PARTIAL until physical-device acceptance is verified.**

Implemented Step 2 areas:

- Room database v2 with explicit migration from accepted Step 1.
- Media Bin for referenced video/audio/image assets.
- VIDEO/AUDIO/OVERLAY tracks and non-destructive clips.
- Move, trim, split, duplicate, delete, snapping, zoom and multi-hour timeline timebase.
- Transform, crop, rotation, flip, opacity and speed controls.
- Timed text and image overlays.
- Audio-only clips, clip/track dB gain, mute/solo, fades and bounded waveforms.
- Native Media3 proxy generation with Performance 540p, Balanced 720p and High 1080p modes.
- Proxy persistence/fingerprint binding/stale detection/cancel/delete and offline-original proxy editing architecture.
- Deterministic PreviewPlan and future RenderPlan foundation.
- Generic HOLD/LINEAR keyframes for clip/text/image properties.
- Semantic undo/redo with bounded coalescing, autosave and snapshots.
- Bounded cached thumbnails and controlled heavy-media concurrency.

See `STEP_2_COMPLETION_REPORT.md`, `STEP_2_TEST_REPORT.md` and the Step 2 architecture documents for certification detail.

## Large-media and privacy invariants

- Original sources remain SAF/content-URI references; imports do not make source-sized project copies.
- No artificial 3 GB source limit.
- No whole-source `readBytes()` or file-size `ByteArray` patterns in production media paths.
- No WebView foundation.
- No INTERNET or MANAGE_EXTERNAL_STORAGE permission.
- No telemetry/cloud processing.

## Technology

- Kotlin + Jetpack Compose + Material 3
- Hilt
- Room
- Storage Access Framework / persisted content URI permissions
- MediaExtractor / MediaCodec
- AndroidX Media3 / ExoPlayer
- Media3 Transformer for native proxy generation
- 64-bit microsecond editor timebase and rational project frame rate

## SDK and app identity

- minSdk 26
- compileSdk 37 / targetSdk 37
- applicationId `com.videoflow.app`

## Build and certification

CI bootstraps Gradle 9.6.0 and runs clean, unit tests, Android lint, instrumentation compilation, Debug/Release APK builds and API-35 emulator instrumentation. Exact tested artifacts are packaged with SHA-256 values.

Local equivalent commands:

```bash
./gradlew clean
./gradlew test
./gradlew lint
./gradlew compileDebugAndroidTestKotlin
./gradlew assembleDebug assembleDebugAndroidTest
./gradlew assembleRelease
```

Required Step 2 delivery names are:

- `VideoFlow_Android_Step2_Debug.apk`
- `VideoFlow_Android_Step2_Release.apk`
- `VideoFlow_Android_Step2_Source.zip`

Test signing is acceptable at Step 2. Production signing/AAB belongs to Step 5.

## Physical acceptance

The master Step 2 gate requires a real-device workflow using real video + image + audio, proxy generation, timeline editing, keyframes, undo/redo, snapshots and process reopen; preferably it also reuses the Step 1 multi-gigabyte source. Hosted CI cannot truthfully substitute for that device evidence, so physical acceptance remains **NOT VERIFIED** until recorded.
