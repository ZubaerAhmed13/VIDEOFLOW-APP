# VideoFlow Android Architecture — Step 2 Professional Editor Core

Date: 2026-09-03

Step 2 preserves the accepted native Step 1 foundation and extends it into a non-destructive professional editor. The editor implementation merged in `a343178fa3c42a58986d9264ff2697e0dfe7fa78`; exact certification-package SHA/run evidence is maintained in `STEP_2_TEST_REPORT.md` and `STEP_2_COMPLETION_REPORT.md`.

## Architecture overview

```text
Jetpack Compose / Material 3 editor workspace
        │
        ├── Media Bin / project controls
        ├── Preview / transport / playhead
        ├── Multi-track timeline
        └── Property editors / overlays / audio / keyframes
        ↓
Hilt ViewModels + lifecycle-bound coroutine jobs
        ↓
Editor/project repositories + deterministic domain operations
        │
        ├───────────────────────────────┬──────────────────────────────┐
        ↓                               ↓                              ↓
Room v2 project model             Source/media layer              Derived media layer
Project + settings                SAF content:// references       app-private files/proxies
Tracks / clips                    persistable URI grants          Media3 Transformer
Text / image overlays             MediaExtractor/MediaMetadata    H.264 + AAC proxies
Keyframes / proxies               source fingerprint identity     540p / 720p / 1080p
Snapshots                         CHANGED/MISSING handling         fingerprint-bound lifecycle
        │                               │                              │
        └──────────────────────┬────────┴───────────────┬──────────────┘
                               ↓                        ↓
                       PreviewPlan builder        bounded caches/jobs
                               ↓                        ↓
                      Media3 preview engine      thumbnails / waveforms
                               │
                               └── future-compatible deterministic RenderPlan foundation
                                   (final Step 3 rendering/export is intentionally absent)
```

## Native boundary — PASS

VideoFlow is a native Kotlin Android application. Step 2 does not introduce a WebView/browser-wrapper architecture, IndexedDB, service workers, FFmpeg/WASM or browser File System APIs.

The app manifest does not request INTERNET or MANAGE_EXTERNAL_STORAGE. Step 2 editor/proxy operation is local to the device.

## Persistence model — PASS

Room schema version: **2**.  
Project format version: **2**.

The accepted Step 1 schema migrates explicitly through `MIGRATION_1_2`; destructive migration is prohibited.

Step 2 normalizes persistent editor state for projects/settings, media assets, tracks, clips, timed text/image overlays, generic keyframes, proxy metadata and snapshots. Project deletion removes VideoFlow metadata/derived project state according to database ownership rules but never deletes the user's original SAF document.

## Time model — PASS

Editor time is represented with 64-bit microseconds (`Long`) rather than frame-count integers or floating-point seconds. Project frame rate is represented rationally. This keeps trim/split/move/snap/keyframe calculations stable for long timelines and avoids accumulated floating-point drift.

## Source architecture — PASS

The authoritative source remains the Android document URI selected through SAF. Original media is not copied into Room BLOBs or source-sized app-private files.

Step 1 source identity rules remain active in Step 2:

- bounded sampled fingerprinting;
- fingerprint strength as a decision input;
- explicit AVAILABLE / MISSING / CHANGED behavior;
- strong-match versus weak-match relink policy;
- duplicate-import confirmation before persistence;
- source verification away from Compose recomposition/main-thread work.

The architecture therefore supports multi-gigabyte source references without an artificial 3 GB application limit or a whole-file-in-RAM path.

## Timeline architecture — PASS

Track types:

- VIDEO
- AUDIO
- OVERLAY

Persistent track controls include create/delete/rename, lock, visibility, mute/solo where applicable and gain for audio behavior.

A clip is a non-destructive timeline instance referencing a `MediaAsset`; duplicating or splitting a clip does not duplicate the original media. Supported editor operations include add, compatible-track move, trim, speed-aware split, duplicate and delete. Snapping, zoom/scroll, playhead/transport and deterministic timeline-duration logic operate on the same microsecond model.

## Transform and overlay architecture — PASS

Clip/image presentation state supports normalized project-space transform/crop information, scale, rotation, flip and opacity. Timed text and image overlays are persisted as editor objects rather than burned into media.

The preview layer composes this metadata at the current project time. Final-quality GPU/output composition belongs to Step 3 and is not simulated by Step 2.

## Audio architecture — PASS

Step 2 accepts audio-only media references and timeline audio clips. Persistent edit state includes clip/track gain, mute/solo policy and bounded fades. Waveform generation is a bounded derived-cache task and never requires a full uncompressed source to be retained in memory.

## Proxy architecture — PASS

Proxies are derived media, not replacement sources.

`ProxyManager` uses AndroidX Media3 Transformer to stream the original SAF URI into an app-private MP4 under `files/proxies`. Step 2 modes are device-adaptive editor resolutions:

- Performance: up to 540p
- Balanced: up to 720p
- High: up to 1080p

The output requests H.264 video and AAC audio. Before generation, storage is checked with safety headroom. Generation exposes progress, supports cancellation, persists metadata, records source fingerprint identity, detects stale/missing proxy state and supports deletion/regeneration.

A ready proxy may be selected for preview while the original source remains authoritative for future final rendering. This separation is a core Step 2 → Step 3 contract.

## Preview and future render contract — PASS

Step 2 constructs deterministic immutable preview/render planning data from persisted editor state.

`PreviewPlan` resolves active tracks/clips/overlays/audio/keyframes at project time and chooses a valid ready proxy for editor playback when appropriate.

The `RenderPlan` foundation represents the same non-destructive edit decisions for the future renderer. **Step 2 does not perform final rendering/export.** Hardware encoding, final GPU composition, colour-managed output, background rendering and 720p–4K final export are Step 3 responsibilities.

## Keyframe architecture — PASS

Generic keyframes use local editor time and explicit interpolation modes:

- HOLD
- LINEAR

The model supports clip/text/image properties, deterministic interpolation, movement with the owning object and redistribution during split operations. Preview evaluation uses the same persisted keyframe data rather than a separate animation-only state model.

## History, autosave and snapshots — PASS

Undo/Redo is semantic and bounded rather than a whole-project deep-copy stack. Compatible rapid property/gesture edits are coalesced; a new edit invalidates redo history.

Editor persistence uses debounced autosave and Room transactions. Snapshots persist project metadata/editor state without duplicating original source media. Snapshot create/restore/delete is distinct from temporary undo history.

## Threading and bounded-resource policy — PASS

Heavy media/database work runs away from the main UI path and is lifecycle/cancellation aware. Step 2 uses bounded concurrency for media jobs and bounded thumbnail/waveform/proxy work rather than unbounded per-frame/per-item launches.

Large-media safety invariants remain:

- no whole-source `readBytes()` path;
- no source-sized `ByteArray` allocation;
- no artificial 3 GB source rejection;
- no mandatory original-file copy into app-private storage;
- derived proxy storage is estimated and checked before generation;
- caches are bounded/derived and may be recreated;
- source URI and proxy coordinate/timing relationships remain non-destructive.

## Security and privacy — PASS

- No network permission.
- No telemetry/cloud processing.
- No broad all-files permission.
- Persisted SAF access is used only for user-selected media.
- User originals remain outside VideoFlow ownership.
- Physical certification evidence generated by `scripts/step2-physical-certification.sh` is ignored by Git by default to avoid accidental diagnostic/device-log commits.

## Step 1 regression — PASS

The Step 2 implementation preserves the Step 1 native source-reference, identity, relink, persistence and device-capability foundation. Post-merge Step 1 CI for the editor baseline passed independently; future Step 2 certification changes continue to run the Step 1 workflow as a regression gate.

## Step 2 certification boundary

Automated implementation gates cover schema/migration, prohibited patterns, JVM behavior, lint, APK assembly, API-35 instrumentation, runtime artifact integrity and Step 1 regression.

The master Step 2 acceptance specification additionally requires a real Android-device workflow using real video + image + audio and exercising import, proxy generation, timeline operations, overlays, audio, two keyframes/animation, Undo/Redo, snapshot and force-stop/reopen persistence. The authoritative protocol is `STEP_2_PHYSICAL_DEVICE_CERTIFICATION.md`.

Until that physical workflow passes, the truthful status is:

- **Step 2 implementation: COMPLETE**
- **Step 2 automated certification: PASS**
- **Step 2 overall acceptance: PARTIAL**

## Future boundaries

**Step 3 — NOT APPLICABLE to Step 2:** final rendering/export, hardware encoding, final GPU composition, direct output writing, background render service, colour-management/output fidelity and final 720p/1080p/2K/4K certification.

**Step 4 — NOT APPLICABLE to Step 2:** local AI Watermark Studio/removal pipeline.

**Step 5 — NOT APPLICABLE to Step 2:** production signing/AAB, final release hardening, broad device/endurance/thermal release certification.
