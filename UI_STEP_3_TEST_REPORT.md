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
- bounded project-thumbnail implementation exists and is wired into Home;
- Preferences DataStore is present;
- the proxy-policy decision is explicit rather than represented by a placebo toggle;
- the dedicated Step 3 Home/product-flow instrumentation test exists;
- recommended-first export UI is present;
- no INTERNET permission;
- no WebView;
- no listed telemetry SDK families;
- no known whole-source `readBytes()` / source-sized ByteArray pattern.

### JVM / Regression Gate

`./gradlew test --stacktrace`

This includes existing Android Step 1/2/3 and UI regression tests plus expanded Step 3 product-presentation coverage. Product-specific JVM assertions cover filename safety, all canvas presets, persisted appearance choices, resolution/codec/HDR labels, every export status, every export failure presentation and major recovery guidance paths.

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

The second workflow job installs the exact generated runtime bundle and executes both the Step 3 product flow and protected editor regressions:

- `HomeComposeTest` — onboarding-aware Home → Settings/proxy policy → New Project → Editor → Export product flow;
- `EditorWorkspaceVisualCertificationTest`;
- `LongTimelineWorkspaceSmokeTest`;
- `ContextualToolbarComposeTest`.

It also validates Review fresh install, cold launch and in-place install/update behavior.

The authoritative pass/fail result is the latest successful workflow run against the exact final branch HEAD. The final response records that run ID, SHA, artifacts and APK hashes rather than hard-coding a self-invalidating run in this source-controlled report.

## Project Thumbnail Verification Boundary

Home project cards now request a real bounded preview from the first media asset. The implementation prefers Android provider thumbnails, uses sampled image decoding as fallback, and extracts only a bounded video frame when required. Failure to retrieve a thumbnail falls back safely to a media-type icon; it does not load the whole source into RAM.

Compilation, lint and automated UI regression are workflow gates. Provider/device-specific thumbnail appearance remains observable during physical review.

## Step 3 Development Failure History

The first Step 3 workflow attempt, run `34013938220`, correctly failed during Kotlin compilation because the two new Compose files explicitly imported the scoped `weight` symbol. That was a source error, not an infrastructure failure. The fix removed those explicit imports while retaining valid `Modifier.weight(...)` calls inside Row/Column scopes.

A later audit identified that the initially green certification set exercised protected editor/contextual tests but did not explicitly execute the Step 3 Home/product flow. That coverage gap was corrected by replacing the stale Home test with the current Step 3 flow and adding it to the API-35 workflow class list. The same audit expanded product-specific JVM coverage and replaced the generic Home project icon with a bounded real thumbnail implementation.

No existing test was disabled and no compiler/lint gate was weakened to make the branch pass.

## Required Final Evidence

The final independent review must use the latest successful Step 3 workflow run against the final branch HEAD, not the first failed run, the earlier pre-gap certification, or any intermediate commit. The final response records that run ID, exact SHA, artifact IDs and APK hashes after CI completes.

## Physical Device Boundary

Automated tests do not constitute physical-device certification. Real SAF picking, real device export, Open/Share, large-font/TalkBack behavior, physical project-thumbnail/provider behavior and hardware encoder behavior remain subject to the separate physical-device review document.
