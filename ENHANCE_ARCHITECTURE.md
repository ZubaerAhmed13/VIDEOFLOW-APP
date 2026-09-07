# Enhance

Exposure, Brightness, Contrast, Highlights, Shadows, Saturation, Temperature, Tint, Sharpen, Clarity and Vignette share the `EnhanceParameters` definition and the real preview/export shader. Defaults are identity. Sliders update transient draft state; Done persists once; Cancel discards the draft; Reset removes adjustments.

Auto Enhance reads three bounded representative frames locally, measures brightness/dark/highlight statistics and proposes small editable exposure, shadow and highlight changes. It does not upload media, claim super-resolution, upscale the image or change frame rate/aspect ratio. Android 8 uses a bounded display-copy fallback where scaled-frame retrieval is unavailable.

Physical colour/quality certification is NOT VERIFIED. Automated shader execution and parameter identity/bounds tests are required; a successful compile alone is insufficient.
