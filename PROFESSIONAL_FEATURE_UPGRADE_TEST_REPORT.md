# Professional upgrade automated test report

This source document defines the gates. The CI-generated copy in `VideoFlow-Professional-Upgrade-Completion-Report` records the exact tested commit and successful run, avoiding a post-certification documentation commit.

## Required evidence

| Gate | Status |
| --- | --- |
| Full Step 1–4 unit regression plus professional parameter/range/parity tests | PENDING |
| Android lint; Debug, Review and instrumentation compilation | PENDING |
| Offline model checksums, size/pack inclusion, signature and manifest audit | PENDING |
| API-35 retained Step-4 model/runtime/tracking/history tests | PENDING |
| Retained original final AI export and OutputValidator | PENDING |
| Retained Home/editor/contextual/long-timeline Compose tests | PENDING |
| Real audio extraction, waveform, history, DB reopen and snapshot restore | PENDING |
| Real Audio → Effects → Enhance → corrected AI product-panel workflow | PENDING |
| Combined all-effects + Enhance + AI production export | PENDING |
| Real segmented export, cancellation, checkpoint reuse and assembly | PENDING |
| Retained Step 1–3 Room, migrations, proxy, media, player and native export tests | PENDING |
| Review fresh install, launch and in-place update | PENDING |

`AiLongJobPlanTest` exercises the actual lazy scheduler at 30 minutes and multi-hour/large-byte/overflow boundaries. `AiTemporalCheckpointTest` checks real binary state restoration and malformed allocation rejection. `VisualPreviewFinalParityTest` checks the actual shared stage configurations for every creative effect at trim/speed/resume offsets. These tests do not imply a 30-minute neural run or measured physical 4K fidelity.

## Bugs found by certification

- Android 8 compatibility: guarded API-27 scaled frame retrieval and API-28 packet-size inspection.
- AAC extraction: negative priming timestamps are skipped as priming, rather than mistaken for end-of-file. The real extraction test remains unchanged.
- Checkpoint telemetry: PSS is a Long, matching Android's API.
- Temporal restoration: preserve previous tile dimensions and reject mismatched patch geometry.
- UI/source geometry: keep ROI image and mask in one fitted coordinate space; preserve rotated source aspect in final composition.
- Paused shader previews: enable Media3's bounded replay cache and interleave redraw requests with frame callbacks. The version-pinned renderer adapter is documented in `VIDEO_EFFECTS_ARCHITECTURE.md` and the product workflow requires a rendered frame.

No existing test was removed or weakened. The snapshot format assertion changed from 3 to 4 because version 4 includes visual sidecar state; restore still accepts formats 2 and 3. New fixtures are generated deterministically in CI. Neural endurance remains a separate configurable real harness.

Physical device results: **NOT VERIFIED**.
