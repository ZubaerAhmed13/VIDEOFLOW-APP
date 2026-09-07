# Step 5 baseline audit

Repository: `ZubaerAhmed13/VIDEOFLOW-APP`. Baseline branch: `professional-feature-ux-upgrade`.
Fetched exact baseline: `7e23f342c6c7fb5563bd62b1f22bf7b4684e366d`.
Step 5 branch: `step5-final-hardening-certification`.
Existing exact-baseline certification: https://github.com/ZubaerAhmed13/VIDEOFLOW-APP/actions/runs/34111052452 — success, 132 unit tests and 33 API-35 tests. A dedicated unchanged-production baseline workflow repeats these gates before hardening.

## Inspection scope

Inspected actual production entry points, manifest, export coordinator/service/repository, Media3 and Smart Copy renderers, output validator, checkpoint planner/assembly, AI and visual sidecars, audio extraction, project deletion, history/snapshots/migrations, timeline/preview/tool panels, settings/cache ownership and diagnostics. Searched all production and test sources/workflows for TODO/FIXME, unimplemented code, ignored tests, success bypasses, unbounded queues, whole-source reads and unsafe narrowing. No AGENTS.md was present.

The existing implementation is retained: native Compose/Media3, SAF originals, Room migrations, parameter-only edits, offline embedded dual LaMa models, original-resolution ROI processing, bounded inference and direct SAF output. No production TODO, unimplemented exception, ignored test or unconditional CI success bypass was found. The word “fake” occurs in a history comment explaining why a cross-storage operation is not represented as a Room transaction. Empty dismiss-only dialog buttons and optional composable callbacks are not fake product implementations. Existing checksum and privacy gates remain mandatory.

## Concrete findings to resolve

| Finding | Source / consequence | Step 5 action |
|---|---|---|
| No central source/destination alias guard | Coordinator and renderers can open the selected destination for truncation | Reject original/derived input aliases before any write, including descriptor identity where available; test source preservation |
| Foreground timeout and legacy service type | ExportForegroundService has no Android 15 timeout callback; only mediaProcessing declared | Handle platform timeout, preserve interrupted state, use the correct pre-35 foreground type and matching permissions |
| Foreground job ownership | Repeated start intents overwrite notification ownership while coordinator serializes work | Keep one active notification/cancellation owner; prevent duplicate job execution and handle queued requests safely |
| Cancellation persistence | Coordinator catches cancellation then performs cancellable Room writes | Persist terminal/interrupted state and clean partial output in a non-cancellable cleanup section |
| A/V proof insufficient | OutputValidator checks maximum track duration and the first 240 video samples | Add bounded timestamp windows, per-track coverage/sync expectations and real trim/speed/segmented A/V evidence |
| Smart Copy failure cleanup / priming | Packet loop treats negative timestamps as end; failed output is not uniformly cleaned | Skip codec priming correctly, enforce packet bounds/cancellation and clean failed output |
| AI sidecar concurrent writes | AtomicFile protects a write, but read-modify-write operations lack serialization | Serialize mutations and test concurrent region persistence |
| History failure ordering | Undo/Redo removes its entry before applying persistence | Preserve retryable history when a write fails; serialize replay |
| Project deletion cache ownership | Derived audio/AI jobs cleaned, but proxy/thumbnail/waveform files outlive cascaded rows | Capture owned paths/asset IDs before deletion and remove only app-owned derived files |
| Settings cache awareness | Proxy storage is counted; abandoned checkpoint/temp cache is not surfaced | Expose safe cache usage/cleanup without deleting originals or active jobs |
| Timeline scale/zoom | Pinch zoom changes scale without preserving the gesture anchor; narrow essential zoom controls | Preserve zoom anchor, verify long spans and viewport behavior, strengthen semantics/touch targets |
| Tool recreation | Professional active tool and AI draft fields use non-saveable remember; disposal resets sessions | Preserve transient edits across configuration changes without committing Cancelled drafts |
| Checkpoint subset / identity | Current resume covers one normal-speed source without overlays/audio edits; implicit string identity | Extract explicit compatibility/identity policy, broaden only safe cases, preserve restart fallback and test invalidation |
| Quality/HDR evidence | Explicit HDR safeguards exist; preview/final parameter checks and short exports do not prove colour/A/V for all edits | Add deterministic SDR pixel, outside-ROI, orientation/cadence and HDR-decision tests; retain unsupported HDR limits |
| Certification breadth | Prior workflow certifies upgrade flows, not the full Step 5 product/long-value matrix | Add integrated project/export/reopen, long-value, security, accessibility and recovery gates; retain all prior suites |

## Existing safeguards checked

Fingerprinting uses bounded first/middle/end samples and Long offsets. Thumbnails are bounded and evict old cache entries. AI queues are limited to one worker plus two waiting tasks; temporal snapshots reject excessive allocation. Export encoder fallback is disabled. HDR AI/visual edits require explicit SDR conversion. Final exports use original source mappings. Smart Copy rejects pixel edits. Room uses explicit migrations and foreign keys, not destructive fallback. Model catalog byte sizes/SHA-256/licenses and merged-manifest network removal are retained. The preview renderer has a documented, version-pinned Media3 graph adapter with a method-local RestrictedApi annotation and real visible-frame checks.

## Limits and certification boundary

Baseline success is not Step 5 completion. Real 30-minute/one-hour neural endurance, approximately 3 GB physical sources, device 4K fidelity/performance, thermals, battery and TalkBack remain NOT VERIFIED. No production signing, merge or publication is authorized by this phase. Software gates and the physical-test package must complete first.

Android platform source for service lifecycle hardening: https://developer.android.com/develop/background-work/services/fgs/timeout and https://developer.android.com/about/versions/14/changes/fgs-types-required .
