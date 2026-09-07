# Visual effects

`VideoEffectNode` defines identity, target, type, half-open clip-local microsecond range, intensity, enabled state and deterministic order. `VisualEditsRepository` stores versioned parameter-only JSON using Android AtomicFile. No source pixels are changed by Apply.

The initial library contains Blur, Vignette, Sharpen, Fade, Monochrome, Sepia, Film grain, RGB split, Glitch, VHS, Chromatic aberration, Pulse, Flicker, Zoom pulse, Shake and Camera vibration. These are original GLSL effects, not external effect packs. Every visible option is connected to the same `VisualEffectPipeline` for ExoPlayer preview and production Media3 composition. Temporal range tests use Long timestamps before shader animation parameters are calculated.

Render order: original source AI → crop → Enhance → creative effects → timeline transform/composition and overlays → encoding. Effects therefore operate in cropped image space before the existing compositor's placement transforms. This order is deterministic.

Smart Copy rejects enabled nonzero visual edits. HDR Effects/Enhance preservation is not certified: the renderer requires explicit SDR conversion when these effects are used with HDR. It does not silently discard HDR.

Effects appear as timeline indicators. The browser supports multiple instances, categories, intensity, exact range, reset, enable/disable, remove and transient preview. Persistent changes join the existing history stack and format-4 snapshots; legacy snapshots remain readable.

Outstanding: pixel-level preview/export parity certification across different crop/proxy configurations, accessible independent lanes for overlapping indicators, and direct whole-effect dragging.
