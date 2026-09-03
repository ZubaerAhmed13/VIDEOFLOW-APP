# VideoFlow Android Professional

Native Android implementation of VideoFlow. This repository is dedicated to the Android application and does not contain the earlier web/WASM editor.

## Project status

### Step 1 — Native Android foundation + large-media reference architecture

**STATUS: COMPLETE — accepted 2026-09-03**

Step 1 passed its automated build/unit/lint/emulator certification and was subsequently validated on a physical Android device. Physical evidence includes persisted URI access, reference-based multi-gigabyte media handling, bounded large-file fingerprinting, physical playback/seek, successful reported 4 GB import, metadata-scale app storage, force-stop persistence, reboot persistence and truthful codec/4K capability interrogation.

Step 2 may now begin from the accepted Step 1 baseline.

### Step 1 principles preserved

- Kotlin + Jetpack Compose + Material 3
- Android native media APIs and Media3; no WebView/browser wrapper
- SAF/content-URI source references; original media is not copied on import
- `Long`-safe file-size/duration/offset handling for multi-GB media
- Room project persistence and relink identity checks
- Bounded sampled SHA-256 fingerprinting
- Native preview/seek
- Device codec/capability diagnostics
- No `INTERNET` permission, ads, or telemetry

See `README_ANDROID.md`, `STEP_1_COMPLETION_REPORT.md`, `PHYSICAL_DEVICE_CERTIFICATION.md`, and the architecture documents for detailed evidence.

## Gradle wrapper bootstrap

The repository intentionally stores the wrapper scripts/properties as source. CI installs Gradle 9.6.0 and regenerates the standard `gradle-wrapper.jar` before invoking `./gradlew`. A developer without the wrapper JAR can run `gradle wrapper --gradle-version 9.6.0` once after cloning.