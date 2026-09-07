# Step 5 final architecture

Repository: `ZubaerAhmed13/VIDEOFLOW-APP`  
Baseline branch: `professional-feature-ux-upgrade`  
Verified baseline: `7e23f342c6c7fb5563bd62b1f22bf7b4684e366d`  
Step 5 branch: `step5-final-hardening-certification`  
Exact candidate commit: `{{COMMIT}}`  
Workflow: {{RUN_URL}} (run `{{RUN_ID}}`)  
Automated status: **{{AUTOMATED_STATUS}}**  
Physical certification: **NOT VERIFIED**

Source documents are certification templates. Only the report artifact generated after all exact-commit gates succeed establishes a software PASS. No merge or production release is authorized by this report.


The existing Kotlin/Compose, Room, SAF and Media3 architecture is retained. No original is copied on import. Source identity uses content URIs and bounded sampled fingerprints. Final rendering resolves original assets; editing proxies never become final sources.

## Data and rendering

Room transactions own timeline mutations; AtomicFile sidecars own Effects, Enhance and AI parameters. Cross-instance mutexes serialize sidecar operations. Snapshot format 4 preserves visual edits, AI anchors and extracted audio references with older snapshot support. History applies a change successfully before consuming its Undo/Redo entry; a failed restore remains retryable. Project deletion refuses active export ownership, deletes orphan keyframes transactionally and cleans only project-owned derived files after database success.

The defined image pipeline is original decode → ordered AI regions → Enhance → ordered Effects → crop/transform/opacity → timeline composition and overlays → requested encode → output validation. AI regions retain stable sidecar order; visual effects sort by order then stable ID. Preview and final use the same VisualStage interpretation of timing and parameters. Source-local times incorporate trim and speed; segmented rendering explicitly offsets visual times and clips static overlay ranges.

Long timeline layout uses a 12,000 dp maximum window, independent of Long project duration. A whole-project navigator reaches later windows without collapsing timestamps. Clips and overlays are composed only when they intersect the visible horizontal viewport. Tracks use lazy vertical composition; effect lists remain proportional to authored objects, never decoded frames. Deep zoom preserves its logical anchor with Double calculations and Long timestamps. Keyframe markers and clipped trim handles retain their time ownership.

## Export ownership and recovery

A bounded foreground request channel serializes up to 16 waiting requests. The coordinator prevents duplicate execution of non-queued jobs. Cancel identifies the active job. Source/destination equality, document identities, canonical paths and regular-file device/inode aliases are checked before every output probe or truncate. Unrelated existing destinations are subject to Android's create-document selection; users choose a new output.

Android 15 uses mediaProcessing; API 29–34 uses the declared dataSync type and permission. Android timeout stops the service promptly and preserves a readable interrupted state. Cancellation cleanup runs under NonCancellable and truncates invalid final output after repeating the source guard. Startup identifies earlier-process jobs, clears accessible abandoned output, preserves project edits and valid segments, and excludes newly created jobs. If a provider is unavailable or the original cannot be resolved, cleanup cannot safely truncate that destination; the job still remains interrupted, never successful.

See the long-media report for supported checkpoint combinations. Settings exposes checkpoint and derived-audio sizes; explicit checkpoint cleanup acquires the renderer lease and refuses active jobs. Models and extracted project audio are excluded from that cleanup.

## Resource bounds

Final LaMa remains fixed 512, one inference worker plus at most two queued tasks. Temporal checkpoint data is capped at 128 MiB during writes and runtime updates; finished regions are pruned. Full frames stay in the GPU path; CPU work uses bounded ROI tiles. Smart Copy uses a bounded 1–64 MiB sample buffer; checkpoint assembly uses 16 MiB and rejects oversized encoded samples. Fingerprinting uses 256 KiB chunks and three 4 MiB regions. Thermal stress slows worker pressure without changing model, dimensions or FPS. Android may stop background processing after its platform time allowance; that is distinct from supported input duration.

Audio gain automation pre-sorts immutable keyframes once and performs logarithmic lookup per PCM sample. The dense-keyframe gate checks 1,000 lookups against 2,000 keys beyond the one-hour offset, with fewer than 20,000 indexed reads and explicit base/HOLD/duplicate/endpoint semantics.

The repeated-render gate exposed retained EGL worker state in Media3 1.11.0's `MultipleInputVideoGraph.SingleContextGlObjectsProvider.release`. A narrowly scoped AGP ASM visitor adds `EGL14.eglReleaseThread()` after the existing owned-context destruction, without terminating the display shared by a live preview. It targets only that exact class/method, fails if the method changes, and applies to every app variant. This does not replace the compositor or alter rendered pixels. Reference: https://github.com/androidx/media/blob/1.11.0/libraries/effect/src/main/java/androidx/media3/effect/MultipleInputVideoGraph.java. The resource gate must validate the correction on the exact APK.

SAF MP4 muxing registers video before audio while preserving logical Media3 track IDs, independent of encoder initialization order. Encoded content and timestamps are unchanged; Android decoder compatibility is checked with sequential image/PCM decoding.

The compositor now reserves a transparent primary CFR clock, registers visible layers from front to back, and puts a full-canvas background last. Media3's first registered input is the top layer. Video/image fit uses pixel-space scale because OverlayMatrixProvider already applies input/output dimension ratios; rasterized text keeps its native output-pixel size. This removes the previous 8-by-8 background square above video and double scaling of unequal-size inputs. The geometry test renders a 320-by-240 original into 640-by-360 output and checks letterbox background, video coverage and visible text above opaque video.
