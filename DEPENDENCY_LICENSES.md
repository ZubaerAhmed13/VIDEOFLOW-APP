# VideoFlow Android — Dependency and AI Model License Record

## Scope and release rule

This document replaces the obsolete Step-1-only license note. It describes the direct Android dependencies and the AI model artifacts used by the Step-4 Android product as defined by `app/build.gradle.kts`, `app/src/main/java/com/videoflow/app/domain/ai/WatermarkModels.kt`, and `.github/workflows/android-step4-ai-ci.yml`.

It is a technical attribution/compliance record, not legal advice. A production distributor must keep the applicable upstream license texts, copyright notices, NOTICE files, and third-party notices required by the shipped artifacts. Test/build-only tools are identified separately because they are not packaged into the VideoFlow APK.

## Shipped application dependencies

| Component | Version / source of truth | Use in VideoFlow | Upstream license | Distribution note |
| --- | --- | --- | --- | --- |
| AndroidX Core KTX | `androidx.core:core-ktx:1.19.0` | Android platform helpers | Apache-2.0 | Retain Apache-2.0 notices applicable to the distributed library. |
| AndroidX Activity Compose | `androidx.activity:activity-compose:1.13.0` | Compose activity integration | Apache-2.0 | Same AndroidX notice obligation. |
| AndroidX Lifecycle | `2.11.0` runtime KTX / runtime Compose / ViewModel Compose | Lifecycle and ViewModel integration | Apache-2.0 | Same AndroidX notice obligation. |
| AndroidX Navigation Compose | `2.10.0` | App navigation | Apache-2.0 | Same AndroidX notice obligation. |
| AndroidX DataStore Preferences | `1.2.1` | Local product preferences | Apache-2.0 | Same AndroidX notice obligation. |
| Jetpack Compose | BOM `2026.08.00`; UI, tooling preview, Material 3, material icons extended | Product UI | Apache-2.0 | Same AndroidX/Compose notice obligation. Debug-only UI tooling is not part of the release runtime surface. |
| AndroidX Room | `2.8.3` runtime + KTX | Project/editor persistence | Apache-2.0 | Room compiler is build-time only; runtime/ktx ship as dependencies. |
| AndroidX Media3 | `1.11.0` common, ExoPlayer, UI, Transformer, Effect | Playback and render/export pipeline | Apache-2.0 | Retain applicable AndroidX notices. |
| ONNX Runtime Android | `com.microsoft.onnxruntime:onnxruntime-android:1.29.0` | Local Step-4 LaMa inference | MIT | Microsoft ONNX Runtime is MIT licensed. Preserve the MIT copyright/license notice and applicable ONNX Runtime third-party notices distributed with the runtime. |
| Dagger / Hilt Android | `com.google.dagger:hilt-android:2.60.1` | Dependency injection | Apache-2.0 | Hilt compiler is build-time only. |
| AndroidX Hilt Navigation Compose | `1.4.0` | Hilt ViewModel/navigation integration | Apache-2.0 | Retain applicable AndroidX notices. |
| Kotlin / kotlinx.coroutines runtime brought by the Kotlin/Android dependency graph | versions resolved by the Gradle build | Kotlin language/runtime and coroutines used throughout the app | Apache-2.0 | Preserve applicable Kotlin/coroutines notices when redistributed. |

Authoritative upstream references:

- AndroidX / Jetpack source and licensing: https://android.googlesource.com/platform/frameworks/support/
- Dagger / Hilt: https://github.com/google/dagger
- Kotlin coroutines: https://github.com/Kotlin/kotlinx.coroutines
- ONNX Runtime: https://github.com/microsoft/onnxruntime — upstream repository declares the project MIT licensed and ships `LICENSE` / `ThirdPartyNotices.txt`.

## Step-4 AI model artifacts shipped in the CI-produced APK

VideoFlow does **not** download models at product runtime. The Step-4 GitHub Actions build downloads the two approved artifacts, verifies both byte size and SHA-256, and packages the verified bytes under `assets/models/`. `AiModelPackManager` then verifies the same expected size/hash again before installing a model into app-private storage.

| VideoFlow model ID | APK asset | Build source | Exact bytes | Required SHA-256 | Declared license |
| --- | --- | --- | ---: | --- | --- |
| `lama-512-int8-v1` | `assets/models/lama-512-int8.onnx` | `https://huggingface.co/g-ronimo/lama/resolve/main/lama_512_int8.onnx?download=true` | 62,074,990 | `cab19978adc306622fe37ef60d4a52103b99c98141d499c2a2366a7ed1255dbe` | Apache-2.0 |
| `lama-dynamic-int8-v1` | `assets/models/lama-dynamic-int8.onnx` | `https://huggingface.co/g-ronimo/lama/resolve/main/lama_int8.onnx?download=true` | 61,512,617 | `1941214c210399eb815eb2d32570ba91d5e6c4ac3de4c939bd3fb09300454972` | Apache-2.0 |

### Model provenance

- Model distribution repository: `g-ronimo/lama` — https://huggingface.co/g-ronimo/lama
- The Hugging Face repository/model card declares **Apache-2.0** and describes the ONNX inpainting variants, including `lama_int8.onnx` and `lama_512_int8.onnx`.
- Original LaMa research implementation: `advimman/lama` — https://github.com/advimman/lama — published under **Apache License 2.0**.
- `g-ronimo/lama` describes the INT8 variants as weight-compressed/quantized ONNX derivatives of the LaMa model; the fixed 512 variant also uses a fixed-size graph transformation for the FFT path.

### Apache-2.0 model redistribution obligations

When distributing the APK containing these model bytes:

1. include or otherwise provide the Apache License 2.0 text with the product's third-party notices;
2. preserve applicable copyright, patent, attribution, and license notices from the upstream material;
3. preserve applicable upstream `NOTICE` content if such a NOTICE is supplied with the redistributed material;
4. clearly identify VideoFlow-specific modifications if VideoFlow itself later modifies the model files rather than packaging the checksum-pinned upstream artifacts unchanged;
5. do not use upstream names/trademarks to imply endorsement.

The current Step-4 build does not edit the downloaded model bytes: CI accepts only the exact size/hash listed above.

## ONNX Runtime notice

The runtime dependency used by Step 4 is `onnxruntime-android:1.29.0`. Microsoft ONNX Runtime's upstream repository declares the project under the **MIT License**. Release attribution must retain the MIT license/copyright notice and review the ONNX Runtime `ThirdPartyNotices.txt` corresponding to the distributed runtime version because the binary can include third-party components in addition to Microsoft-authored code.

Upstream references:

- https://github.com/microsoft/onnxruntime/blob/main/LICENSE
- https://github.com/microsoft/onnxruntime/blob/main/ThirdPartyNotices.txt

## Build/test-only dependencies and tools

These are required to build or certify VideoFlow but are not intentionally shipped as product runtime features:

| Component | Current use | License / status note |
| --- | --- | --- |
| Room compiler / KSP processors | code generation at build time | Apache-2.0 ecosystem tooling; not an app runtime feature |
| Hilt compiler | dependency-injection code generation | Apache-2.0; build-time only |
| JUnit `4.13.2` | JVM unit tests | EPL-1.0; test-only |
| kotlinx-coroutines-test `1.10.2` | coroutine unit tests | Apache-2.0; test-only |
| AndroidX Test (`junit`, runner, rules, core-ktx) `1.3.0` / `1.7.0` | instrumentation tests | Apache-2.0; test-only |
| UI Automator `2.4.0` | Android instrumentation | Apache-2.0; test-only |
| Compose UI test libraries | Compose instrumentation | Apache-2.0; test-only |
| FFmpeg / ffprobe on GitHub runner | generates and inspects the deterministic H.264/AAC certification fixture | CI tool only; no FFmpeg binary/library is intentionally packaged in the VideoFlow APK. FFmpeg's applicable license depends on the runner build/configuration and enabled codecs. |
| Android SDK/platform/build tools/emulator | compilation, packaging and API-35 certification | Google/Android SDK tooling terms; not packaged as VideoFlow runtime libraries |
| GitHub Actions used by CI | checkout, Java/Gradle/Android setup, artifact handling, emulator runner | CI-only automation; not packaged in the APK |

## Transitive dependency rule

`app/build.gradle.kts` is the direct dependency source of truth, while Gradle resolves additional transitive modules. Before public production distribution, the release process must archive the resolved dependency graph for the exact release HEAD and preserve all license/NOTICE obligations for those transitive artifacts. A direct-dependency table alone must never be interpreted as permission to discard notices embedded in or accompanying transitive libraries.

For the certified build, the dependency graph must be resolved from the same exact Git commit used to assemble the release/review artifact; dependency versions must not be inferred from a different branch or a later build.

## Privacy and model-delivery boundary

License compliance is independent of the Step-4 offline/privacy guarantee:

- the product APK does not request `android.permission.INTERNET` or `android.permission.ACCESS_NETWORK_STATE`;
- there is no runtime model downloader or cloud AI fallback;
- GitHub Actions network access used to obtain build dependencies and the checksum-pinned model pack is a build-time activity only;
- the model bytes used at runtime are the verified assets packaged into the APK and copied into app-private storage.

## Verification source of truth

The following must remain mutually consistent:

1. `app/build.gradle.kts` — dependency coordinates/versions;
2. `WatermarkModels.kt` — model IDs, paths, SHA-256 values, byte sizes and declared license;
3. `android-step4-ai-ci.yml` — exact model download sources and build-time checksum/size gates;
4. this document — distribution/attribution record.

A future dependency/model change is incomplete until this document and the relevant checksum/license evidence are updated in the same change set and the exact-head CI passes.
