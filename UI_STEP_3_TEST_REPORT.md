# VideoFlow UI Step 3 — Test Report

## Source Baseline

- Repository: `ZubaerAhmed13/VIDEOFLOW-APP`
- Dedicated branch: `ui-step3-product-polish`
- Certified UI Step 2 base SHA: `7f29a708ab24b9be3c9b4f48f381e26e44dcf987`
- UI Step 2 baseline certification run: `33995478794`

## Step 3 Test Strategy

The exact-head Step 3 workflow is `.github/workflows/android-ui-step3-ci.yml` (`VideoFlow Android UI Step 3 Certification`). A Step 3 implementation is not accepted from a local or stale build; the workflow runs from the branch HEAD that produced the APK artifacts.

### Static / Architecture Gate

The workflow checks:

- required Step 3 product/export source exists;
- Preferences DataStore is present;
- recommended-first export UI is present;
- no INTERNET permission;
- no WebView;
- no listed telemetry SDK families;
- no known whole-source `readBytes()` / source-sized ByteArray pattern.

### JVM / Regression Gate

`./gradlew test --stacktrace`

This includes existing Android Step 1/2/3 and UI regression tests plus new Step 3 presentation tests.

### Android Lint

`./gradlew lint --stacktrace`

### Instrumentation Compilation

`./gradlew compileDebugAndroidTestKotlin --stacktrace`

### Packaging

The workflow assembles:

- Debug APK
- Review APK
- Release APK
- Debug instrumentation APK

The Review APK must have application ID `com.videoflow.app.review` and match the stable non-production review signing fingerprint used by the established VideoFlow certification chain.

### API 35 Emulator

The second workflow job installs the exact generated runtime bundle and executes existing editor regression instrumentation:

- `EditorWorkspaceVisualCertificationTest`
- `LongTimelineWorkspaceSmokeTest`
- `ContextualToolbarComposeTest`

It also validates Review install, launch and in-place install/update behavior.

## Step 3 Development Failure History

The first Step 3 workflow attempt, run `34013938220`, correctly failed during Kotlin compilation because the two new Compose files explicitly imported the scoped `weight` symbol. That was a source error, not an infrastructure failure. The fix removed those explicit imports while retaining valid `Modifier.weight(...)` calls inside Row/Column scopes.

No test was disabled and no compiler/lint gate was weakened to make the branch pass.

## Required Final Evidence

The final independent review must use the latest successful Step 3 workflow run against the final branch HEAD, not the first failed run or any intermediate commit. The final response records that run ID, exact SHA, artifact IDs and APK hashes after CI completes.

## Physical Device Boundary

Automated tests do not constitute physical-device certification. Real SAF picking, real device export, Open/Share, large-font/TalkBack behavior and hardware encoder behavior remain subject to the separate physical-device review document.
