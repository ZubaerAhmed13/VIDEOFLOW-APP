# Quality, colour and audio certification

Repository: `ZubaerAhmed13/VIDEOFLOW-APP`  
Baseline branch: `professional-feature-ux-upgrade`  
Verified baseline: `7e23f342c6c7fb5563bd62b1f22bf7b4684e366d`  
Step 5 branch: `step5-final-hardening-certification`  
Exact candidate commit: `{{COMMIT}}`  
Workflow: {{RUN_URL}} (run `{{RUN_ID}}`)  
Automated status: **{{AUTOMATED_STATUS}}**  
Physical certification: **NOT VERIFIED**

Source documents are certification templates. Only the report artifact generated after all exact-commit gates succeed establishes a software PASS. No merge or production release is authorized by this report.


The software gate uses actual decoded export pixels, not only serialized parameters. Step5QualityExportTest renders every shipped Effect at full intensity and all 11 Enhance controls at their UI endpoints (Sharpen and Vignette use 0–1; other adjustments use −1–1). Neutral/default and reset paths are compared against an identity export. The source includes saturated moving imagery, gray and skin-like patches. Retained preview tests measure paused native video pixels after parameter changes; shared-stage tests cover timing, ordering, trim and speed.

Measured criteria: identity SDR RGB mean error below 12/255; non-identity controls change sampled decoded pixels by more than 0.25/255; zero endpoints/reset remain within 2/255 of identity. These bounds acknowledge codec re-encoding and establish operational behavior, not perceptual perfection. The AI-specific gate runs final LaMa, requires a target-region change above 1/255 and outside-region error below 5/255 with a codec-block margin around the ROI. Actual numbers are in quality.jsonl and ai-roi-quality.jsonl.

Flash-and-tone media provides two known synchronized events. Step5AudioVideoSyncTest decodes video frames and PCM, verifies expected event timing after trim, and compares streams at 0.5×, 1× and 2× with Effects/Enhance. Tolerance is 66,667 µs (two 30 fps frames); actual event timestamps are in av-sync.jsonl. This is short automated sync evidence. Long-job sync and temporal drift require the physical runs. Segmented and integrated AI export tests separately validate media tracks, duration and cadence.

Decoded amplitude windows verify timeline fades at 0.5× and 2×; Media3 applies speed before the gain processor, so automation must not divide the time by speed a second time. A real 44.1 kHz stereo AAC fixture verifies bounded resampling to requested 48 kHz mono and stereo. OutputValidator requires requested dimensions, video/audio MIME, sample rate/channels, video-track duration, overall duration and measured cadence. It probes bounded opening/middle/end cadence windows rather than relying solely on an FPS label. Final encoding normalizes source timestamps to the explicitly requested CFR policy; source timing is not inferred only from frame index. Source-aware 23.976/24/25/29.97/30/50/59.94/60 policies remain covered by retained tests. A separate real VFR input export validates 29.97 and 59.94 output cadence. The primary compositor image clock uses a speed-adjusted integer image rate to retain the requested fractional cadence instead of rounding down. An arbitrary VFR file is not promised packet-identical timing after effects.

HDR status is **PARTIAL**: HDR metadata is detected; AI/visual processing requiring SDR refuses implicit conversion and requires the explicit Convert to SDR option. Compatible preservation paths remain capability-checked and output-validated. No universal HDR/HEVC/device colour guarantee is claimed. HDR displays, vendor codecs, rotations and challenging gradients/skin tones need the physical visual checklist.
