# VideoFlow Playback Performance Architecture

## Scope

This document covers the final editor-quality correction before Android Step 4 AI. It addresses the real-phone report that preview playback was visibly lagging. It does not claim physical-device success until the exact Review APK is retested on the same phone and problematic source.

## Root Cause Found

The editor playhead is intentionally updated frequently while playback is active. Before this correction, preview position changes could drive repeated ExoPlayer seeks closely behind those high-frequency UI updates. A seek can flush/reposition decoder state, so chasing the UI clock is materially more expensive than allowing the native player clock to advance normally.

The player itself was already conceptually reusable, but playback-position synchronization needed a clear policy boundary. UI playhead state is not allowed to become decoder lifecycle authority.

## Corrected Player Lifetime

`NativeVideoPlayer` and `NativeAudioPreview` now create ExoPlayer with `remember(uri)`. Player identity therefore follows the actual preview media URI, not the current playhead, recomposition count, transport state, speed, or volume.

Normal recomposition does not recreate the decoder or surface. Lifecycle stop pauses playback and disposal releases the player.

## Seek Policy

`PreviewPlaybackPolicy` separates two cases:

- Paused/scrubbing: seek when requested position differs by more than 40 ms, preserving responsive manual navigation.
- Playing: allow the player clock to run independently and only correct drift/discontinuity greater than 1,000 ms.

This prevents normal high-frequency playhead publication from repeatedly forcing decoder seeks while retaining explicit repositioning behavior.

## Compose State Strategy

Transport/playhead UI may still publish frequently. The important correction is that this high-frequency state is no longer treated as a command to flush/reseek the player on every tick.

The broader editor currently still updates its UI playhead at approximately a 16 ms cadence. This phase does not claim that every Compose recomposition has been eliminated or that the full tree is performance-optimal. Physical profiling remains authoritative.

## Original vs Proxy Preview

Existing VideoFlow preview planning remains intact:

- available READY proxies may be used for editing preview according to the existing proxy policy;
- original media remains the source of truth for final render/export;
- proxy use does not alter original-source identity, relinking, or final export mapping.

No source is copied into app memory merely to improve playback.

## Audio Synchronization

Video and audio preview use the same drift-aware seek policy. Playback speed, volume, and play/pause state are applied separately from position correction. Final A/V synchronization must still be checked on the exact physical Review build.

## Regression Requirements

Automated coverage includes player seek-policy unit tests, existing Media3/player instrumentation, editor workspace regressions, long-timeline smoke tests, lint, and APK build/signature checks.

## Physical Result

**NOT VERIFIED.**

Required final certification:

1. Install the exact final `VideoFlow_Android_FinalEditorQuality_Review.apk`.
2. Use the same phone where lag was observed.
3. Test the original problematic source first.
4. Record whether original or proxy preview is active.
5. Verify sustained playback, scrubbing, seek response, and A/V sync.
6. Compare before/after behavior and record the result in `FINAL_EDITOR_QUALITY_PHYSICAL_REVIEW.md`.

A green emulator/CI result does not replace this test.