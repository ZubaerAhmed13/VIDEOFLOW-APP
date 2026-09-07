# Long-media software certification

Repository: `ZubaerAhmed13/VIDEOFLOW-APP`  
Baseline branch: `professional-feature-ux-upgrade`  
Verified baseline: `7e23f342c6c7fb5563bd62b1f22bf7b4684e366d`  
Step 5 branch: `step5-final-hardening-certification`  
Exact candidate commit: `{{COMMIT}}`  
Workflow: {{RUN_URL}} (run `{{RUN_ID}}`)  
Automated status: **{{AUTOMATED_STATUS}}**  
Physical certification: **NOT VERIFIED**

Source documents are certification templates. Only the report artifact generated after all exact-commit gates succeed establishes a software PASS. No merge or production release is authorized by this report.


| Target | Automated evidence | Scope |
|---|---|---|
| 30 minutes | Step5ArchitectureTest, retained AiLongJobPlanTest | Exact segment coverage, progress, storage and resume offsets |
| One hour | Step5ArchitectureTest | 3,600,000,000 µs plus ragged tail; no Int truncation |
| 2 / 4 / 8 hours | Step5ArchitectureTest | Long math, seek windows, storage and lazy schedules |
| >2 GB / 3 GiB / 6 GiB | Step5ArchitectureTest, FingerprintEngineTest | Real bounded reader calls at 64-bit offsets; no large test allocation |
| 3840×2160 and portrait | Step5ArchitectureTest, AiWatermarkMathTest, export capability tests | Full ROI coverage, bounded tiles, rotation geometry, requested output planning |

These are architectural tests, not a 30-minute or multi-hour encoded endurance run. Physical 30-minute 1080p, supported 4K, approximately 3 GB source, and optional one-hour results remain NOT VERIFIED. There is no artificial 30-minute maximum. Geometry does not certify any particular phone encoder.

## Resume compatibility

| Composition | Behavior |
|---|---|
| One video at time zero, normal speed, compatible AAC or silence | Segmented resume when AI is active and duration reaches the checkpoint interval |
| Source trim | Supported; source offsets remain absolute |
| Static Effects / Enhance / crop / transform / multiple AI regions | Supported under the same single-video constraints |
| Static text and image overlays | Step 5 extension: intersect and offset each segment's overlay intervals |
| Overlay or clip keyframes | Safe full render restart |
| Nonzero video placement, multiple clips, separate extracted audio, changed speed, processed audio gain/fades | Safe full render restart |
| Corrupt or stale segment/state/hash | Re-render from first invalid segment; do not reuse later stale chain entries |

Checkpoint/recovery status is **PARTIAL** because seamless resume is deliberately limited to compositions with verified assembly semantics. Other compositions remain fully exportable through the existing full composition pipeline.

The identity digest binds the complete render plan, source URI/metadata/fingerprint, fresh sampled source fingerprint, settings (codec, size, FPS, bitrate, audio, HDR), visual state, AI anchors/ranges/parameters, model hash and segment interval/version. Unit tests mutate these identities. Completed video and temporal state are independently hashed. Assembly checks codec configuration, dimensions, increasing PTS and gaps; AAC is copied once from the source, avoiding per-segment encoder priming. The retained checkpoint test and new cross-instance static-overlay test perform real cancellation, reuse and final validation.
