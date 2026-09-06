# VideoFlow Android Step 4 — AI Watermark Studio Handoff

## Boundary

Android Step 4 AI Watermark Studio is **not implemented in this correction phase**. The planned feature is not abandoned; it is intentionally deferred until final editor quality and source-preservation behavior has passed independent review.

No inactive production `Watermark — Coming Soon` button is added. No fake AI inference path is exposed.

## Why This Phase Comes First

Step 4 will depend on stable editor playback, precise timing, source identity, project persistence, original/proxy mapping, high-fidelity rendered export, local processing and robust large-media behavior. Those foundations must be trustworthy before an AI media transform is inserted.

## Planned Product Entry

The future AI entry belongs in the selected visual-clip contextual tool experience. Final naming may be `Remove`, `Watermark`, or another reviewed label. It should only become visible when a real functional Step 4 implementation exists.

## Planned Workflow

A professional Step 4 implementation should:

1. use the selected source clip and current timeline range as authority;
2. define one or more watermark regions/ROIs with time ranges;
3. support local model/runtime capability checks before starting work;
4. process bounded ROI/frame windows rather than loading an entire large source into RAM;
5. use original-resolution mapping even when editing preview uses a proxy;
6. provide progress, cancellation, recoverable errors and diagnostics;
7. preserve deterministic project state and reload/relink behavior;
8. feed the processed result into the normal editor/render graph without changing unrelated tools;
9. keep final output validation and source-fidelity policy active.

## Render Requirement

AI Watermark Removal changes decoded pixels. It therefore **always requires rendering** for the affected output. It is never eligible for Smart Copy merely because the surrounding timeline is otherwise packet-copy compatible.

When Step 4 is implemented, Match Source / Source Fidelity should be the natural high-fidelity export policy for an AI-edited project:

- preserve project/source canvas authority;
- preserve rational FPS;
- preserve codec family when device capability allows;
- preserve colour/HDR semantics where supported;
- preserve audio characteristics;
- use source-aware high-fidelity bitrate rather than forcing the source file size.

## Source / Proxy Contract

Proxies may be used for responsive interaction or tracking previews, but final AI/render work must map ROI/timing back to the original source correctly. Original media references remain the source of truth; no design should quietly substitute proxy pixels into final export.

## Large-Media Contract

Step 4 must preserve the project's large-media principles:

- reference-based sources;
- no whole-file RAM loading;
- bounded/chunked or micro-batched work;
- 64-bit offsets/sizes;
- storage checks;
- direct-to-disk where applicable;
- cancellation/recovery;
- device-aware capability fallback.

## Colour and Quality Contract

AI frame processing must not silently introduce colour-space changes. Future Step 4 certification should explicitly test Rec.709 and, where the renderer/runtime claims support, HDR/10-bit paths. Any conversion must be declared and capability-gated.

## Privacy Contract

Current VideoFlow remains local-first and has no INTERNET permission. Step 4 should preserve that architecture unless a future product decision explicitly changes the privacy model and is separately reviewed. The intended Watermark Studio target is local AI.

## Integration Points Prepared by This Phase

- stable original/proxy source mapping;
- exact microsecond/sample-based Trim timing;
- normal persisted timeline/project model;
- source-preservation metadata/policy;
- rendered Match Source path;
- explicit Smart Copy exclusion for pixel-changing edits;
- editor accessibility/theme foundations;
- existing cancellation/background export architecture.

## Step 4 Readiness Gate

Step 4 is **NOT READY** until this final editor-quality phase passes:

- exact-head automated certification;
- exact Review APK fresh install/update certification;
- same-phone playback/contrast/Trim/Merge/Smart Copy/Match Source review;
- independent approval.

After that approval, Step 4 should begin as a separate scoped implementation. This document is a handoff only.