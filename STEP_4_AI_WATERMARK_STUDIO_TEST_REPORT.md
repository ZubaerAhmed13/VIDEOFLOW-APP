# VideoFlow Android — Step 4 AI Watermark Studio Test Report

## Purpose

This document defines the evidence required to call the Step-4 software implementation and automated certification complete. It is intentionally commit-stable: the authoritative run ID, artifact IDs and hashes are the GitHub Actions evidence produced for this exact HEAD, not values written back into the repository after certification.

## Unit regression

Required command: `./gradlew test --stacktrace`

Step-4 specific coverage includes:

- normalized ROI interpolation and motion anchors
- original-resolution pixel/OpenGL mapping
- bounded 4K/8K tile planning and working-set guardrails
- dual LaMa model catalog/checksums
- moving-ROI frame-local feather calculation
- source-preservation/Smart Copy policy regressions
- all retained Step 1–3 unit tests

### Moving-ROI blend regression

`AiWatermarkMathTest.movingRoiFeather_usesCurrentFrameTargetNotStartTarget` verifies the defect correction. At a tracked/moved timestamp, a center pixel of the moved target must have full feather weight against the current target and zero weight against the stale starting target.

## API-35 local AI runtime certification

Class: `com.videoflow.app.ai.Step4AiRuntimeInstrumentedTest`

Required coverage:

1. checksum-pinned dual model pack installs and opens offline
2. preview inference runs against a real decoded video frame
3. bounded local ROI tracking returns ordered normalized motion anchors
4. AI sidecar Apply/Update/Remove remains non-destructive
5. AI history Undo/Redo restores exact before/after sidecar state

The workflow must reject instrumentation crashes/failures and require an `OK (N tests)` result.

## API-35 final AI export certification

Class: `com.videoflow.app.ai.Step4AiFinalExportInstrumentedTest`

This is a separate hard gate from preview inference.

The test must:

- stage the deterministic real H.264/AAC MP4 fixture through a content URI
- build a real `FinalRenderPlan`
- persist an enabled AI Watermark sidecar effect using `lama-512-int8-v1`
- include motion anchors so the final path exercises frame-local ROI resolution
- instantiate the production `Media3RenderEngine`
- pass production AI preflight
- execute the actual final LaMa effect during render
- write the production output destination
- pass production output validation
- verify non-trivial output size
- verify 320×240 H.264 video
- verify AAC audio
- verify approximately 30 fps
- calculate SHA-256 of the rendered output
- emit `FINAL_AI_EXPORT_CERTIFIED ... validation=true`

The workflow requires both `OK (1 test)` and the explicit certification marker. A render that only compiles, opens the model, or produces an unvalidated file is not sufficient.

## Existing product/editor regression

The exact same API-35 job must continue to run the existing editor certification classes:

- `HomeComposeTest`
- `EditorWorkspaceVisualCertificationTest`
- `LongTimelineWorkspaceSmokeTest`
- `ContextualToolbarComposeTest`

The expected current suite contains eight tests. Step-4 fixes must not weaken, skip or delete these regressions merely to obtain a green workflow.

## Build/package verification

Required before API-35 execution:

- Android lint PASS
- debug androidTest compilation PASS
- Debug APK assembly PASS
- Review APK assembly PASS
- androidTest APK assembly PASS
- Review package ID verification PASS
- Review signing fingerprint verification PASS
- final LaMa asset embedded PASS
- preview LaMa asset embedded PASS
- deterministic MP4 fixture embedded in androidTest APK PASS
- runtime-bundle SHA-256 verification PASS

## Review APK lifecycle

API-35 must also verify:

- fresh Review installation succeeds
- cold launcher start results in a live Review process
- in-place Review update succeeds
- relaunch after update results in a live Review process

## Privacy/offline audit

CI must continue to enforce:

- no application INTERNET permission
- no AI HTTP/Retrofit/OkHttp/DownloadManager path
- no cloud AI fallback
- no analytics/crash-telemetry SDK introduced by Step 4
- no full-file `readBytes()`/`readAllBytes()` media path in the AI implementation

The model files are downloaded only by CI while producing the build and are checksum/size verified before packaging. Product runtime AI remains local/offline.

## Pass criteria

Automated Step-4 certification is PASS only when both workflow jobs for the exact final HEAD complete successfully, including the final AI export gate. If any required stage is pending, skipped unexpectedly, cancelled, or red, automated completion remains NOT COMPLETE.

Physical-phone review remains separate and is recorded in `STEP_4_AI_WATERMARK_STUDIO_PHYSICAL_REVIEW.md`.
