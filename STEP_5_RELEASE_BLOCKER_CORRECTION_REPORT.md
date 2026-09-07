# VIDEOFLOW ANDROID PROFESSIONAL — STEP 5 RELEASE BLOCKER CORRECTION REPORT

## Status

- Branch: `step5-final-hardening-certification`
- Protected toolbar baseline: `86ea2a9`
- Exact certified commit: `{{COMMIT}}`
- Certification workflow run: `{{RUN_ID}}`
- Certification URL: `{{RUN_URL}}`
- Automated software certification: **{{AUTOMATED_STATUS}}**
- Physical-device certification: **NOT VERIFIED**

This report records the final Step 5 software-hardening corrections applied after the original completion work. The automated status above is substituted only by the fail-closed exact-head certification workflow after all required build, architecture, privacy, packaging, API-35 runtime, regression, Review APK, and evidence jobs succeed.

## No-sacrifice boundary

The production editor toolbar baseline at commit `86ea2a9` is protected. The Step 5 hardening branch must remain based on that commit without reverting or redesigning the production toolbar. Added toolbar instrumentation is certification coverage only; it does not replace the protected production implementation.

The final certification must verify the branch is not behind the protected baseline and that no unintended production-toolbar regression was introduced.

## Release blockers corrected

### 1. Export service type-safety / compile blocker

The isolated export service boundary contained nullable-access compiler failures. These were corrected narrowly at the service boundary without changing the export architecture, direct-SAF policy, source-fidelity policy, or editor process isolation.

### 2. AI preview certification drift

The Watermark Studio UI evolved from the old single `Generate AI Preview` wording into distinct still and moving preview controls. Retained certification was updated to validate the current UX without restoring obsolete user-visible wording.

### 3. Real processed moving-preview coverage

Step 5 adds instrumentation that exercises the actual `AiProcessedPreviewManager` media path rather than validating metadata alone. Coverage includes real local LaMa processing, generated MP4 playback material, multiple AI ranges, moving ROI/tracking state, audio-bearing media, cache identity/invalidation, and processed/original timeline stitching.

### 4. AI cache and timeline continuity coverage

Focused JVM coverage now validates processed-preview cache invalidation and multi-range playback resolution/seek continuity. This ensures stale AI previews are not silently reused when effect identity changes and that processed segments resolve deterministically against the original timeline.

### 5. Export failure classification coverage

Focused regression coverage validates export failure classification so destination, codec/muxer, cancellation, process-interruption, and unknown failures remain distinguishable rather than collapsing into ambiguous success/failure behavior.

### 6. Toolbar geometry and font-scale coverage

Instrumentation covers the protected toolbar across 360, 393, 412, 480, and 600 dp widths and font scales 1.0, 1.15, 1.3, and 1.5. It checks minimum touch targets, cell spacing, label containment, horizontal reachability, and end padding without changing the production toolbar baseline.

### 7. Instrumentation dependency-graph drift

Two retained product-flow instrumentation tests still instantiated the former `WatermarkStudioViewModel` constructor. They now wire the real `AiProcessedPreviewManager` dependency and use the current `Generate Still Preview` control. A Compose semantics import that is unavailable in the current test API was also removed. These are test-harness compatibility fixes, not product behavior changes.

### 8. Isolated export-process death certification

The Step 5 process-death test launches a genuine foreground export in `:export`, records the editor/main PID and export PID independently, kills only the export process, verifies the editor/main PID survives unchanged, and then verifies persisted recovery marks the interrupted job without destroying the editable project.

The process-isolation fixture uses the app's existing private `FileProvider` storage so both app processes can deterministically reopen the same test source and destination. This change is confined to the process-death certification fixture. It does **not** replace or weaken direct user-selected SAF/MediaStore export coverage: the retained `SafMediaMuxerFactoryInstrumentedTest` continues to certify direct MP4 muxing into a MediaStore content URI.

## Required automated release gates

The exact-head workflow is fail-closed. Step 5 automated software completion requires all of the following to pass on the same commit:

- pinned local LaMa model download, SHA-256 and size verification;
- offline/privacy architecture gate;
- complete JVM regression suite and Step 5 architecture contracts;
- Android lint;
- Android instrumentation compilation;
- Debug, Review, and androidTest APK assembly;
- Media3 bytecode/lifecycle/timing checks;
- Review APK signature, offline permission, and bundled-model verification;
- long-media / approximately 3 GB architecture certification;
- privacy/model/release-candidate package integrity;
- API-35 portrait, landscape, and tablet accessibility execution;
- isolated `:export` process kill with unchanged editor/main PID and persisted recovery;
- Step 5 integration, quality/colour, composition geometry, audio/video sync, recovery/security, and product workflow suites;
- actual processed-video AI moving-preview instrumentation;
- final local-AI export instrumentation;
- protected toolbar geometry/font-scale regression;
- retained professional and legacy product regression suites, including direct SAF muxing;
- Review APK fresh installation, cold launch, in-place update installation, and relaunch;
- exact-head evidence/report job proving all prerequisite jobs succeeded.

## Physical-device boundary

Physical-device certification is intentionally not claimed by this report. Emulator and architecture evidence cannot prove real-device thermal behavior, OEM codec behavior, sustained 30-minute-or-longer local-AI processing, approximately 3 GB source handling on actual storage providers, device-specific 4K codec capability, battery/thermal throttling, or final human visual/interaction review.

Those items remain **NOT VERIFIED** until the supplied Step 5 physical certification package is run on appropriate Android hardware. No automated result may be relabeled as physical-device evidence.

## Completion rule

Step 5 software hardening may be called automated-complete only when this report is included in the exact certified commit shown above and the exact-head certification workflow reports **PASS**. If any required job fails or is skipped because a prerequisite failed, automated completion remains blocked.
