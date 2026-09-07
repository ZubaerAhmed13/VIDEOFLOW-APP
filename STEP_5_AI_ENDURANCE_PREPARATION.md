# AI endurance preparation

Repository: `ZubaerAhmed13/VIDEOFLOW-APP`  
Baseline branch: `professional-feature-ux-upgrade`  
Verified baseline: `7e23f342c6c7fb5563bd62b1f22bf7b4684e366d`  
Step 5 branch: `step5-final-hardening-certification`  
Exact candidate commit: `{{COMMIT}}`  
Workflow: {{RUN_URL}} (run `{{RUN_ID}}`)  
Automated status: **{{AUTOMATED_STATUS}}**  
Physical certification: **NOT VERIFIED**

Source documents are certification templates. Only the report artifact generated after all exact-commit gates succeed establishes a software PASS. No merge or production release is authorized by this report.


Run only after exact-head automated certification is PASS. No phone has been used during software development.

Required media: 30-minute 1080p SDR H.264/AAC with a persistent known watermark and audio markers; 30-minute 3840×2160 SDR if the device advertises the selected encoder; a real approximately 3 GB or larger source; optional one-hour clip. Include stationary, slowly moving and difficult watermarks, camera motion, cuts, temporary occlusion and manual corrections. Use source-aware FPS, and review source codec/resolution/rotation before starting.

The included physical.py verifies package APK checksums before installation. Review is for normal manual acceptance; Debug plus the matching androidTest APK runs the retained LongAiEnduranceInstrumentedTest. The instrumentation targets `com.videoflow.app.debug`, not Review. Both contain the same production source/model implementation, with distinct application IDs and signing/build configuration.

The endurance harness accepts an original content URI readable by Debug, Long start/end microseconds, normalized ROI and checkpoint interval. It writes through the production original-resolution segmented renderer, retains successful output, hashes it, and records PSS, thermal state, progress, elapsed time and geometry. It does not load the whole source or silently reduce quality. The script captures logcat, memory, thermal/battery/storage state and the JSONL directory. It does not mark visual inspection or long A/V listening as passed.

Keep charging state and ambient conditions recorded. A low battery should prompt charging, not an artificial export-duration restriction. Stop safely from the product notification to test cancellation separately; then retry the same compatible project/settings. For complex compositions expect a safe full restart, as documented in the compatibility table.
