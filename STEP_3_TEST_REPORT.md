# VideoFlow Android Step 3 — Test Report

## Status

Automated certification is defined by `.github/workflows/android-step3-ci.yml`. Final Step 3 status must remain `NOT COMPLETE` until the physical-device report passes every mandatory gate.

## Automated JVM coverage

The Step 3 JVM suite includes export/settings/capability/64-bit/render-plan contract tests in addition to all retained Step 1/Step 2 regression tests. The dedicated workflow records exact PASS/FAIL/SKIP totals from Gradle XML instead of hardcoding a count.

Key Step 3 contract coverage includes:

- every required output-resolution preset;
- distinct rational 23.976/24/25/29.97/30/50/59.94/60 rates;
- project background propagation into immutable final render state;
- multi-hour >4 GiB 64-bit size estimation;
- multi-hour rational frame timestamp stability;
- settings persistence/codec/domain invariants;
- original-source final render authority;
- exact capability rejection rather than silent format fallback.

## Lint/build gates

The workflow requires:

1. prohibited architecture pattern audit;
2. Step 3 architecture/document contract;
3. full JVM regression suite;
4. Android lint with no fatal errors;
5. Step 3 instrumentation compilation;
6. debug APK, instrumentation APK and test-signed release APK assembly;
7. SHA-256 verification of the exact runtime bundle.

## Emulator instrumentation

API 35 runs the repository's complete instrumentation suite using the exact APKs produced by the build job. Step 3-specific tests include:

- `SafMediaMuxerFactoryInstrumentedTest`: remuxes real H.264/AAC samples directly into a MediaStore `content://` destination and reopens it;
- `NativeRenderEngineInstrumentedTest`: renders a real original `content://` H.264/AAC fixture through native composition/encoder/direct SAF MP4 and requires post-render validation to pass.

The workflow fails on instrumentation crashes/failures and records the exact test count.

## Output validation

A Transformer completion callback alone is insufficient. The output URI must reopen successfully and match required structural metadata. Invalid output is never certified as COMPLETE.

## Cancellation/background/large-media gates

Automated code/tests cover cancellation state, direct-output cleanup and 64-bit arithmetic. Activity recreation and foreground-job architecture remain regression targets. Real screen-off/background rendering, real multi-GB source rendering and long physical encodes are mandatory physical certification items.

## Current physical status

1080p physical export: NOT VERIFIED

4K30 physical export: NOT VERIFIED

Background + screen-off: NOT VERIFIED

Cancel/partial-output cleanup on phone: NOT VERIFIED

Real multi-GB source: NOT VERIFIED

Visual colour/range/gradient: NOT VERIFIED

A/V sync, gain, fade, distortion: NOT VERIFIED

HEVC: NOT VERIFIED / capability dependent

HDR: HDR EXPORT NOT VERIFIED

10-bit: NOT VERIFIED

## Completion rule

Do not change the above physical states to PASS without recorded evidence from the exact certified APK on a real Android device. Emulator success is necessary but not sufficient for Step 3 COMPLETE.
