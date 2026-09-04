# VideoFlow Android Step 3 — Colour Pipeline

## Objective

Preserve source colour behaviour through the native Android render path without silently changing range, transfer or HDR policy. Step 3 must never claim HDR/10-bit preservation unless both implementation and device-level certification prove it.

## Source metadata

`OriginalRenderSource` carries source colour standard, range, transfer and HDR-static-info presence when Android media metadata exposes them. Visible source clips are inspected before export.

## Homogeneous vs mixed timelines

If all visible video sources agree on colour standard/range/transfer, `Media3RenderEngine` derives an `OutputColourExpectation` and `OutputValidator` requires the encoded video track to retain the expected values where the Android extractor exposes them.

If visible sources disagree, the engine emits a `MIXED_SOURCE_COLOUR` warning. It does not falsely assert that one output track preserved multiple conflicting source standards simultaneously.

## HDR policy

`HdrPolicy.PRESERVE_WHEN_COMPATIBLE` requests preservation only when the source is HDR and the exact selected encoder/output configuration supports it. `HdrPolicy.CONVERT_TO_SDR` is an explicit conversion request and therefore does not require source HDR metadata to remain on the final track.

Media3 format fallback is disabled. If the framework attempts a fallback that would silently change the requested colour/HDR behaviour, export fails instead of being marked complete.

## Range and transfer

For homogeneous SDR material, output validation compares available colour-standard, colour-range and colour-transfer metadata. Physical certification additionally checks known full/limited range content for washed blacks, crushed blacks, unexpected tint and gamma shifts.

## Generated layers

Project background, text and image overlays are composited into the same output surface. The project background ARGB is carried in the immutable final render plan rather than defaulting to black. Generated raster layers are bounded scratch resources and do not modify originals.

## HDR and 10-bit claims

A real HDR source, compatible physical display/device, compatible encoder and output metadata validation are required before HDR preservation can be marked PASS. Ten-bit handling is likewise NOT VERIFIED until a real compatible encode is measured. Unsupported devices may report NOT APPLICABLE rather than silently down-convert under a preserve request.

## Quality verification

Automated validation proves structural metadata invariants. Visual/metric quality certification belongs in `QUALITY_AND_COLOUR_CERTIFICATION.md` and the physical-device report. PSNR/SSIM are not claimed unless actually measured against a deterministic reference with an appropriate comparison path.
