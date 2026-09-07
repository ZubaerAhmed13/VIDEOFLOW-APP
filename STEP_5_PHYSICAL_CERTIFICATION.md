# Final physical certification — NOT VERIFIED

Repository: `ZubaerAhmed13/VIDEOFLOW-APP`  
Baseline branch: `professional-feature-ux-upgrade`  
Verified baseline: `7e23f342c6c7fb5563bd62b1f22bf7b4684e366d`  
Step 5 branch: `step5-final-hardening-certification`  
Exact candidate commit: `{{COMMIT}}`  
Workflow: {{RUN_URL}} (run `{{RUN_ID}}`)  
Automated status: **{{AUTOMATED_STATUS}}**  
Physical certification: **NOT VERIFIED**

Source documents are certification templates. Only the report artifact generated after all exact-commit gates succeed establishes a software PASS. No merge or production release is authorized by this report.


Software must be complete before running this checklist. Use the exact checksummed RC package and record device model, Android version, chipset/RAM, free storage, selected codec, charging state and ambient conditions.

| Gate | Required evidence | Status |
|---|---|---|
| Review install / fresh launch / in-place update | APK hash, screenshots, no crash | NOT VERIFIED |
| Project lifecycle | Create, import via SAF, close/reopen, reboot, lost-permission relink | NOT VERIFIED |
| Editing | Trim, split, move, zoom, crop/rotation, speed, audio gain/fades, text/image layers | NOT VERIFIED |
| History / snapshots | Cross-tool Undo/Redo, snapshot restore, original remains unchanged | NOT VERIFIED |
| Effects / Enhance | All controls, min/max/reset, Cancel/Done, portrait and landscape preview | NOT VERIFIED |
| AI workflow | Select/Time/Track/Preview/Apply; stationary/moving; add/update/delete corrections; multiple regions | NOT VERIFIED |
| AI quality | Same-time Before/After, Fast/Detailed, feather/context, edges/seams/flicker/outside-ROI colour | NOT VERIFIED |
| 30-minute 1080p AI | Complete, no crash/OOM, responsive app, bounded memory trend, validated output | NOT VERIFIED |
| 30-minute 4K AI where supported | Requested 3840×2160, FPS, no silent downscale, same endurance criteria | NOT VERIFIED |
| Optional one-hour AI | Completion and stable long-term PSS, A/V and temporal quality | NOT VERIFIED |
| Approximately 3 GB source | SAF reference import, scrub near end, trim/export, source SHA unchanged | NOT VERIFIED |
| A/V and colour | Inspect beginning/middle/end and segment boundaries; listen for drift, pops, repeated/missing audio | NOT VERIFIED |
| Recovery | Notification cancel, background/task removal, force-stop/restart, storage-full and revoked URI | NOT VERIFIED |
| Thermal / battery | Record time series; quality remains fixed under throttling | NOT VERIFIED |
| Accessibility | TalkBack, focus order, labels, announcements, font scaling, contrast, narrow and wide layouts | NOT VERIFIED |
| HDR / HEVC / rotations | Device-supported preservation or explicit SDR conversion; 0/90/180/270 and portrait | NOT VERIFIED |

Pass a duration/geometry target only after the actual media was rendered and visually/listening-reviewed. Architecture math and emulator output do not substitute. Any physical failure blocks final release certification, requires a fix, and requires exact-head automated certification again.
