# VideoFlow Android Professional

Native Android implementation of VideoFlow. This repository is dedicated to the Android application and does not contain the earlier web/WASM editor.

## Current release gate

**Step 1 — Native Android foundation + large-media reference architecture**

Step 2 must not begin until Step 1 passes the complete certification gate.

### Step 1 principles

- Kotlin + Jetpack Compose + Material 3
- Android native media APIs and Media3; no WebView/browser wrapper
- SAF/content-URI source references; original media is not copied on import
- `Long`-safe file-size/duration/offset handling for multi-GB media
- Room project persistence and relink identity checks
- Bounded sampled SHA-256 fingerprinting
- Native preview/seek
- Device codec/capability diagnostics
- No `INTERNET` permission, ads, or telemetry

See `README_ANDROID.md` and the architecture documents for details.

## Gradle wrapper bootstrap

The repository intentionally stores the wrapper scripts/properties as source. CI installs Gradle 9.6.0 and regenerates the standard `gradle-wrapper.jar` before invoking `./gradlew`. A developer without the wrapper JAR can run `gradle wrapper --gradle-version 9.6.0` once after cloning.
