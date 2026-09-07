# VIDEOFLOW ANDROID PROFESSIONAL — STEP 5 RELEASE BLOCKER CORRECTION REPORT

## Certification identity

- Branch: `step5-final-hardening-certification`
- Protected toolbar baseline: `86ea2a9`
- Exact certified commit: `{{COMMIT}}`
- Certification workflow run: `{{RUN_ID}}`
- Certification URL: `{{RUN_URL}}`
- Automated software certification: **{{AUTOMATED_STATUS}}**
- Physical-device certification: **NOT VERIFIED**

`{{AUTOMATED_STATUS}}` is substituted only inside the exact-head completion artifact after every required build, architecture, privacy, packaging, API-35 runtime, regression, Review APK, and evidence job succeeds. A failed or skipped prerequisite cannot produce an automated PASS report.

## No-sacrifice boundary

The production toolbar correction at commit `86ea2a9` is protected. Step 5 hardening does not revert, redesign, reorder, or deliberately reduce the editor toolset. The final export path remains independent from AI preview caches and continues to render from original project/source authority at the selected export settings. Physical-device evidence is not inferred from emulator evidence.

---

# BLOCKER 1 — EDITOR TOOLBAR DENSITY / GEOMETRY CERTIFICATION

## Root cause

The production spacing correction already existed at the protected baseline, but Step 5 did not yet have sufficient geometry/font-scale evidence to prove the tighter toolbar remained readable, reachable, and non-clipped across compact and larger editor widths.

## Files involved

- `app/src/main/java/com/videoflow/app/ui/editor/EditorChrome.kt` — protected production implementation; intentionally not redesigned by this correction work.
- `app/src/androidTest/java/com/videoflow/app/ui/Step5ToolbarGeometryComposeTest.kt` — Step 5 geometry/font-scale coverage.
- `app/src/androidTest/java/com/videoflow/app/ui/ContextualToolbarComposeTest.kt` — retained toolbar behavior coverage.

## Architecture / correction

The toolbar remains horizontally scrollable and context-sensitive with the existing production ordering and semantics. Certification was added around the protected implementation rather than solving a test requirement by changing the UI again.

## Automated tests

Coverage exercises representative widths including 360, 393, 412, 480, and 600 dp and font scales including 1.0, 1.15, 1.3, and 1.5. Assertions cover minimum touch targets, spacing/containment, label visibility, horizontal reachability, and end padding. API-35 layout execution additionally runs portrait, landscape, and tablet configurations with enlarged system font scale.

## Automated status

**{{AUTOMATED_STATUS}}** — valid only when the exact-head workflow succeeds.

## Follow-up risk

Real-device one-handed reach, OEM font rendering, gesture navigation insets, and human visual judgment remain **NOT VERIFIED** until physical review.

---

# BLOCKER 2 — MOVING PROCESSED AI WATERMARK PREVIEW IN THE NORMAL EDITOR

## Root cause

The earlier Watermark Studio path could produce still/specialized preview output, but normal editor playback did not continuously substitute processed AI video over active watermark-removal ranges. That meant metadata could be saved without giving the user the required moving processed result directly on the normal timeline.

## Files changed / involved

- `app/src/main/java/com/videoflow/app/ai/watermark/AiProcessedPreviewManager.kt`
- `app/src/main/java/com/videoflow/app/domain/editor/AiPreviewPlaybackResolver.kt`
- `app/src/main/java/com/videoflow/app/ui/VideoPlayer.kt`
- `app/src/main/java/com/videoflow/app/ui/editor/PreviewWorkspace.kt`
- `app/src/main/java/com/videoflow/app/ui/editor/FlowComposeBridge.kt`
- `app/src/main/java/com/videoflow/app/ui/ai/WatermarkStudioViewModel.kt`
- `app/src/main/java/com/videoflow/app/ui/ai/WatermarkStudioPanel.kt`
- `app/src/test/java/com/videoflow/app/domain/editor/AiPreviewPlaybackResolverTest.kt`
- `app/src/test/java/com/videoflow/app/ai/watermark/AiPreviewIdentityTest.kt`
- `app/src/androidTest/java/com/videoflow/app/ai/Step5AiMovingPreviewInstrumentedTest.kt`

## Architecture / correction

Applied AI edits are persisted first. Preview preparation then generates bounded processed MP4 segments only for active AI ranges, using proxy/source media appropriate for responsive preview and a deterministic state identity. The normal editor builds one Media3 playlist that stitches ordinary source/proxy segments outside active ranges with processed cached segments inside active ranges.

The cache identity includes source fingerprint/geometry/timing, clip speed, proxy identity, AI ranges/ROI/tracking context, model identity/hash, and relevant edit state so stale processed previews are rejected when the effect state changes. Preview work is cancellable and progress-aware.

This preview path is deliberately separate from final export. Final export compiles the persisted project and active AI effects against original source authority and selected export settings; it does not treat the preview cache as final-render input.

## Automated tests

- Deterministic multi-range playlist segmentation and source/timeline mapping.
- Seek/boundary continuity across processed and ordinary ranges.
- Cache identity/invalidation behavior.
- Real local LaMa moving processed-preview instrumentation with generated video material.
- Required `STEP5_AI_MOVING_PREVIEW_CERTIFIED` runtime evidence token.
- Final certifier explicitly requires `ai-moving-preview.txt` to contain exactly one passing instrumented test and the certification token.

## Automated status

**{{AUTOMATED_STATUS}}** — valid only when the exact-head workflow succeeds.

## Follow-up risk

Sustained long-duration AI preview generation, thermal throttling, device-specific decoder behavior, and subjective temporal-removal quality remain physical-device review items.

---

# BLOCKER 3 — EXPORT CRASH CONTAINMENT / EDITOR SURVIVAL

## Root cause

Heavy codec, GL, muxing, and local-ONNX export work needed a stronger failure boundary. A fatal or escaped export-process failure must not take down the editor process or leave an active job looking successful/indeterminate.

## Files changed / involved

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/videoflow/app/di/AppModule.kt`
- `app/src/main/java/com/videoflow/app/export/ExportProcessIdentity.kt`
- `app/src/main/java/com/videoflow/app/export/ExportSafety.kt`
- `app/src/main/java/com/videoflow/app/export/ExportForegroundService.kt`
- `app/src/main/java/com/videoflow/app/VideoFlowApplication.kt`
- `app/src/test/java/com/videoflow/app/export/ExportFailureClassifierTest.kt`
- `app/src/androidTest/java/com/videoflow/app/step5/Step5ProcessDeathTest.kt`

## Architecture / correction

`ExportForegroundService` runs in the isolated app process suffix `:export`. Room multi-instance invalidation lets the editor/main process observe persisted job changes written by the export process. Main-process-only startup/recovery logic prevents the export process from incorrectly marking its own work interrupted.

An outer export-service boundary classifies escaped non-fatal failures, invalidates/truncates unusable partial output, persists FAILED/INTERRUPTED state, and writes bounded redacted local diagnostics. Fatal VM/linkage failures are deliberately not swallowed; process isolation is the containment mechanism for those categories.

The main process periodically reconciles persisted active jobs against the actual `:export` process. If export died, the job is marked interrupted and the editable project remains intact.

## Automated tests

- Export failure classifier unit coverage.
- Real foreground export launched in `:export`.
- Independent main/editor PID and export PID capture.
- Shell kills only the export PID.
- Main/editor PID must remain unchanged.
- Export process must be absent after kill.
- Persisted recovery must mark the job interrupted/cancelled-safe while preserving project timeline and AI edit state.
- Final certifier requires the `STEP5_EXPORT_PROCESS_ISOLATION_CERTIFIED` evidence marker.

## Automated status

**{{AUTOMATED_STATUS}}** — valid only when the exact-head workflow succeeds.

## Follow-up risk

OEM process-management policy, true low-memory kills, vendor codec/native crashes, and prolonged background execution remain physical-device certification items.

---

# BLOCKER 4 — PROCESS-DEATH TEST SOURCE / DESTINATION AUTHORITY

## Root cause

Two successive test-fixture assumptions were too weak for the real production contracts. A MediaStore destination used by the first isolation fixture could fail before the kill point on the hosted emulator. Changing both source and destination to private FileProvider URIs solved cross-process determinism but correctly failed `FinalRenderPlanCompiler`, because FileProvider source URIs do not provide the persistable SAF permission required for background export.

The production permission check was correct and was not weakened.

## Files changed / involved

- `app/src/androidTest/java/com/videoflow/app/step5/Step5ProcessDeathTest.kt`
- `app/src/androidTest/java/com/videoflow/app/step5/Step5MediaFixture.kt` — existing app-owned MediaStore fixture reused.
- `app/src/main/java/com/videoflow/app/data/project/ProjectRepository.kt` — production authority logic inspected, not weakened for the test.
- `app/src/main/java/com/videoflow/app/domain/export/FinalRenderPlan.kt` — production `permissionPersisted` export gate retained.
- `app/src/androidTest/java/com/videoflow/app/export/SafMediaMuxerFactoryInstrumentedTest.kt` — retained direct MediaStore/SAF-path evidence.

## Architecture / correction

The final isolation fixture uses an **app-owned MediaStore source** created through the target app resolver. `ProjectRepository.persistReadPermission` recognizes an app-owned MediaStore row as durable source authority by verifying `OWNER_PACKAGE_NAME`, satisfying the same background-export contract without fake flags or bypasses.

The process-death test uses an **app-private FileProvider destination** only for deterministic cross-process output/truncation during forced process death. That destination is not a replacement for user-selected SAF/MediaStore coverage. The retained direct-SAF/MediaStore mux instrumentation separately verifies real content-URI MP4 writing and finalization.

## Automated tests

- Background export must compile with genuine durable source authority.
- Isolated process must reach RENDERING before the shell kill.
- Partial private test output must be truncated after recovery.
- Direct MediaStore content-URI muxing remains separately exercised by `SafMediaMuxerFactoryInstrumentedTest`.

## Automated status

**{{AUTOMATED_STATUS}}** — valid only when the exact-head workflow succeeds.

## Follow-up risk

Physical providers such as OEM file managers, SD cards, cloud-backed DocumentsProviders, and removable storage need device/provider testing.

---

# BLOCKER 5 — RETAINED TEST / UI DEPENDENCY DRIFT

## Root cause

Two retained product-flow instrumentation tests still instantiated the former `WatermarkStudioViewModel` constructor after moving processed preview became a real production dependency. Retained UI automation also referenced obsolete preview wording, and one Compose semantics import was unavailable in the current test API.

## Files changed / involved

- Retained Watermark Studio/product-flow instrumentation files.
- `app/src/androidTest/java/com/videoflow/app/ui/Step5ToolbarGeometryComposeTest.kt`.

## Architecture / correction

Tests now wire the real `AiProcessedPreviewManager` dependency instead of bypassing the production graph. UI automation uses the current `Generate Still Preview` wording. The unsupported semantics import was removed without altering production semantics.

## Automated tests

Instrumentation/Compose compilation must pass before APK assembly. Product-panel and retained professional/editor regressions then execute on API 35.

## Automated status

**{{AUTOMATED_STATUS}}** — valid only when the exact-head workflow succeeds.

## Follow-up risk

Future production constructor/label changes still require tests to evolve with the product rather than pinning obsolete UX strings.

---

# BLOCKER 6 — FAIL-CLOSED FINAL CERTIFICATION / REVIEW APK HANDOFF

## Root cause

Intermediate green jobs are not enough to call Step 5 complete. The exact-head report must consume evidence from the same commit and explicitly include the moving-AI-preview and isolated-export-process proofs. The requested human-test APK also needs an unambiguous Review filename rather than being confused with a production release.

## Files changed / involved

- `scripts/step5/run_api35.sh`
- `scripts/step5/certify.py`
- `.github/workflows/android-step5-certification.yml`
- `STEP_5_RELEASE_BLOCKER_CORRECTION_REPORT.md`

## Architecture / correction

The final report job depends on build, API-35 runtime, long-media architecture, and privacy/package integrity jobs. `certify.py` rejects missing/failed/skipped prerequisite evidence, validates JUnit XML, verifies APK hashes/model hashes/offline permissions/signature, parses required API-35 logs, and now explicitly requires:

- `ai-moving-preview.txt` with one passing test and `STEP5_AI_MOVING_PREVIEW_CERTIFIED`;
- `step5-export-process-isolation.txt` with `STEP5_EXPORT_PROCESS_ISOLATION_CERTIFIED`;
- the exact source commit identity;
- all measurement JSONL evidence expected by Step 5.

The exact-head completion artifact additionally exposes the already verified Review build as **`VideoFlow-Step5-Review.apk`**. This is a Review APK for human/physical certification, not a production-release claim.

## Automated tests / gates

The exact same commit must pass all of the following before automated completion:

- pinned local LaMa SHA-256/size verification;
- offline/privacy architecture gate;
- complete JVM regression and Step 5 architecture contracts;
- Android lint;
- instrumentation/Compose compilation;
- Debug, Review, and androidTest APK assembly;
- Media3 bytecode/lifecycle/timing checks;
- Review signature, offline permission, and model-pack verification;
- long-media / approximately 3 GB architecture certification;
- API-35 portrait/landscape/tablet execution;
- isolated export-process kill/recovery;
- Step 5 integration, quality/colour, composition geometry, A/V sync, security/recovery, and product suites;
- actual moving processed AI preview instrumentation;
- final local-AI export instrumentation;
- protected toolbar regression;
- retained professional and legacy regressions including direct SAF muxing;
- Review APK fresh install, cold launch, in-place update install, and relaunch;
- exact-head report generation from successful prerequisite evidence.

## Automated status

**{{AUTOMATED_STATUS}}** — valid only when the exact-head workflow succeeds.

## Follow-up risk

The Review APK is not called production-ready until physical-device certification and any later release-signing/distribution requirements are completed.

---

# PHYSICAL-DEVICE BOUNDARY

Physical-device certification is intentionally **NOT VERIFIED** in this report. Emulator/architecture evidence cannot prove:

- real-device thermal and battery behavior;
- OEM codec/GL/native-driver behavior;
- sustained 30-minute-or-longer local-AI processing;
- approximately 3 GB sources on real storage providers;
- device-specific 720p/1080p/2K/4K decode/encode capability;
- real-world colour/HDR rendering across displays;
- final human visual quality and interaction review.

Those items remain for the supplied physical certification package. No automated result may be relabeled as physical-device evidence.

# COMPLETION RULE

Step 5 software hardening is automated-complete only when this report is generated in the exact-head completion artifact with **Automated software certification: PASS**, all prerequisite jobs succeeded on `{{COMMIT}}`, and the Review APK/evidence artifacts are present. Any failed or skipped required job keeps Step 5 blocked. Physical-device status remains **NOT VERIFIED** until separately executed on Android hardware.
