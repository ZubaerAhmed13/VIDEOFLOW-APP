# VideoFlow Android — Step 4 AI Watermark Studio Completion Report

## Status boundary

Step 4 implements the local/offline AI Watermark Studio on top of the approved Step 1–3 editor. This report documents the implemented software boundary. **Automated completion is valid only when the GitHub Actions run attached to this exact repository HEAD is green.** Do not edit this report after that green run merely to insert its run number, because doing so would create a new untested HEAD.

Physical-phone visual/performance review is a separate gate and must remain NOT VERIFIED until executed on real target hardware.

## Implemented product workflow

The selected-video workflow is:

1. Mask and timing
2. Optional local motion tracking
3. Local AI preview
4. Non-destructive Apply/Update
5. Standard rendered export using the original source media

The product does not overwrite source media. Applied AI regions are persisted in an app-private project sidecar and remain editable. Smart Copy is not allowed while a pixel-changing AI reconstruction is active.

## Local AI runtime

- ONNX Runtime Android, offline only
- No INTERNET permission in the application manifest
- No AI network client, downloader, analytics, telemetry or cloud fallback
- Checksum-pinned dual LaMa pack
  - final model: `lama-512-int8-v1`
  - preview model: `lama-dynamic-int8-v1`
- NNAPI is attempted when available; CPU remains the correctness fallback
- Final processing is bounded to ROI tiles instead of whole-frame AI buffers
- Original-resolution source dimensions remain authoritative for final AI reconstruction

## Final AI export integration

Final export is integrated into the production `Media3RenderEngine`/`Media3CompositionBuilder` path rather than a test-only exporter.

When an enabled AI Watermark effect exists:

- Smart Copy is rejected because pixels must change.
- Final LaMa model installation/readiness is part of render preparation.
- AI reconstruction executes before crop/transform against original source dimensions.
- Output is written through the production destination path.
- Production output validation checks the rendered file.

The Step-4 API-35 workflow contains a dedicated `Step4AiFinalExportInstrumentedTest`. It must execute a real enabled AI sidecar effect using the FINAL model, render an MP4, pass `OutputValidator`, verify expected video/audio properties, calculate an output SHA-256, and emit `FINAL_AI_EXPORT_CERTIFIED`. CI explicitly fails if that marker or the single-test PASS result is absent.

## AI Undo/Redo integration

AI mutations now use the same `EditHistoryService` stack as other editor operations.

Covered mutations:

- Apply AI Watermark
- Update AI Watermark
- Enable AI Watermark
- Disable AI Watermark
- Remove AI Watermark

Each history record stores deterministic before/after project-sidecar snapshots. Undo restores the exact previous sidecar state; Redo restores the exact following state. The AI sidecar is not falsely wrapped inside a Room transaction. Instead, its own atomic file replacement is performed first and the project metadata touch uses a real Room transaction.

`AiWatermarkRepository` emits local change notifications after successful atomic replacement, allowing an already-open Watermark Studio to refresh after editor Undo/Redo.

The API-35 runtime suite includes a real sidecar history round trip that performs Apply state → Undo → exact previous state → Redo → exact applied state.

## Moving-ROI final blend correction

A final-render defect was found in the temporal/moving ROI path: feather blending used the ROI at `clipLocalStartUs` even when the tracked ROI had moved at the current presentation timestamp. This could reduce the feather weight to zero outside the starting ROI and restore original pixels at a valid moved target.

Correction:

- The current frame logical ROI is calculated from `effect.roiAt(presentationTimeUs)`.
- That exact logical target is passed into final inpainting/blending.
- Feather weight is calculated against that current target.
- Pixels outside the current target receive zero replacement weight.
- A pure unit regression proves that a moved ROI center is fully weighted against the moved target but receives zero weight against the old start target.

## Preserved behavior

The correction does not intentionally remove or weaken:

- Step 1 project/media foundation
- large-source reference-based access
- Step 2/3 editor behavior
- precise trim
- Merge workflow
- source-preservation export modes
- project persistence
- Review signing identity
- existing editor regressions
- local/offline privacy requirements

## Automated completion gates

The exact-head Step-4 workflow must pass all of the following before automated Step-4 completion can be claimed:

- architecture/product/privacy audit
- full Step 1–4 unit regression
- Android lint
- instrumentation/Compose compilation
- Debug, Review and androidTest assembly
- Review identity/signature verification
- embedded dual-model verification
- exact runtime-bundle checksum verification
- API-35 local runtime/model/preview/tracking tests
- API-35 AI Undo/Redo sidecar history test
- API-35 real FINAL AI export test with `FINAL_AI_EXPORT_CERTIFIED`
- existing 8 editor/product regression tests
- Review fresh install
- Review cold launch
- Review in-place update
- Review relaunch
- evidence artifact upload

## Completion labels

Use these labels literally:

- **Software implementation:** COMPLETE only after source changes compile and all required tests pass.
- **Automated Step-4 certification:** COMPLETE / PASS only after the workflow associated with the exact HEAD is green.
- **Physical-phone certification:** NOT VERIFIED until the physical checklist is executed.
- **Overall Step 4:** do not call fully certified for release hardware until physical review passes.

No automatic merge is authorized by this report.
