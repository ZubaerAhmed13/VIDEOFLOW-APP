# Visual effects

`VideoEffectNode` defines identity, target, type, half-open clip-local microsecond range, intensity, enabled state and deterministic order. `VisualEditsRepository` stores versioned parameter-only JSON using Android AtomicFile. No source pixels are changed by Apply.

The initial library contains Blur, Vignette, Sharpen, Fade, Monochrome, Sepia, Film grain, RGB split, Glitch, VHS, Chromatic aberration, Pulse, Flicker, Zoom pulse, Shake and Camera vibration. These are original GLSL effects, not external effect packs. Every visible option is connected to the same `VisualEffectPipeline` for ExoPlayer preview and production Media3 composition. Temporal range tests use Long timestamps before shader animation parameters are calculated.

Render order: original source AI → Enhance → creative effects → crop → timeline transform/composition and overlays → encoding. Effects operate in source image space on both preview and export paths before the existing compositor's placement transforms. This intentionally keeps spatial effects consistent with the source-based player preview. This order is deterministic.

Smart Copy rejects enabled nonzero visual edits. HDR Effects/Enhance preservation is not certified: the renderer requires explicit SDR conversion when these effects are used with HDR. It does not silently discard HDR.

Effects appear as timeline indicators. The browser supports multiple instances, categories, intensity, exact range, whole-range dragging, reset, enable/disable, duplicate, remove and transient preview. Independent 48-dp effect rows open the exact selected effect. Persistent changes join the existing history stack and format-4 snapshots; legacy snapshots remain readable.

`VisualPreviewFinalParityTest` checks the shared production stage configurations at trim/speed/segment offsets for every effect, including half-open boundaries and disabled identity. Real combined export runs all sixteen shaders. Pixel-level visual fidelity across physical devices and proxy resolutions remains a Step-5 review item.
