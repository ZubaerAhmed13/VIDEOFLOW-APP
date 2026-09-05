# VideoFlow Android Step 3 — Quality & Colour Certification

## Certification rule

This file records only evidence that has actually been produced. Emulator/CI evidence is useful but does not replace the mandatory real-device 1080p/4K/colour/A-V acceptance gates.

## Automated structural certification

| Check | Status | Evidence / rule |
|---|---|---|
| Requested resolution model | AUTOMATED | Step 3 JVM contract test covers 480p, 720p, 1080p, 1440p, DCI 2K, UHD 4K, DCI 4K. |
| Rational FPS model | AUTOMATED | 23.976/24/25/29.97/30/50/59.94/60 remain distinct rational choices. |
| Output reopen/track validation | AUTOMATED | `OutputValidator` reopens the final URI and rejects invalid output. |
| Original-source authority | AUTOMATED/ARCHITECTURAL | `FinalRenderPlan.originalSources`; render path does not use proxy as final authority. |
| Direct SAF MP4 mux | AUTOMATED INSTRUMENTATION TARGET | `SafMediaMuxerFactoryInstrumentedTest`. |
| Native video+audio render | AUTOMATED INSTRUMENTATION TARGET | `NativeRenderEngineInstrumentedTest`. |
| Colour metadata expectation | AUTOMATED | Homogeneous visible-source standard/range/transfer is compared when available. |
| Mixed colour timeline | AUTOMATED | Explicit warning instead of false single-standard preservation claim. |
| HDR preserve semantics | AUTOMATED LOGIC | Preserve request requires compatible path; silent fallback is refused. |
| >4 GiB arithmetic | AUTOMATED | Multi-hour 100 Mbps estimate exceeds 4 GiB using 64-bit arithmetic. |

## Physical reference-quality matrix

The following remain mandatory before Step 3 COMPLETE:

| Item | Source | Output | Status |
|---|---|---|---|
| 1080p High H.264 | real media | 1920×1080 | NOT VERIFIED |
| 4K30 | real 4K media | 3840×2160 | NOT VERIFIED |
| 1080p60 where supported | real 60 fps | 1920×1080 | NOT VERIFIED |
| 4K60 where supported | real 4K60 | 3840×2160 | NOT APPLICABLE / NOT VERIFIED pending device capability |
| HEVC | real media | capability-dependent | NOT VERIFIED |
| Full-range reference | known fixture/content | same intended range | NOT VERIFIED |
| Limited-range reference | known fixture/content | same intended range | NOT VERIFIED |
| Gradient/detail | known reference | encoded output | NOT VERIFIED |
| Text/image overlay isolation | Step 2 project | encoded output | NOT VERIFIED on physical device |
| A/V sync | sync reference | encoded output | NOT VERIFIED |
| HDR preservation | real HDR | compatible HDR output | HDR EXPORT NOT VERIFIED |
| Ten-bit handling | real 10-bit | compatible output | NOT VERIFIED |

## Metric fields required by the Step 3 specification

Resolution: pending physical result

FPS: pending physical result

Codec: pending physical result

Source colour: pending physical result

Output colour: pending physical result

Range: pending physical result

Transfer: pending physical result

PSNR: NOT MEASURED — do not claim

SSIM: NOT IMPLEMENTED/MEASURED — do not claim

Visual inspection: NOT VERIFIED on physical reference set

## Visual acceptance criteria

Physical review must confirm no obvious washed-out image, crushed blacks, unexpected tint, gamma shift, severe detail loss, broken gradients, overlay placement error, motion cadence error or audible sync/distortion problem.

## Known limitations / truthful boundaries

- Android metadata availability varies by codec/device/provider; missing extractor fields cannot be invented.
- Mixed-colour source timelines cannot have every conflicting source standard represented as one homogeneous output-track metadata value.
- HDR and 10-bit certification remains blocked until a compatible real device/source/output combination is tested.
- Emulator encoder behaviour is not a substitute for hardware-specific physical-device quality certification.
