# Step 5 final physical test package

Run only after the package manifest says automated PASS. All physical statuses start NOT VERIFIED. Do not publish or merge this candidate on the basis of emulator results.

1. Install Android platform-tools and Python 3. Connect one authorized phone by USB; confirm `adb devices`.
2. Run `python physical.py verify`, then `python physical.py install`. This verifies all APK hashes before installation. Review is the manual acceptance build; Debug plus androidTest runs the automated harness.
3. Open Debug and import the original through Android's file picker so its URI remains readable. Use the exact persisted content URI. For local MediaStore items, `adb shell content query --uri content://media/external/video/media --projection _id:_display_name` helps identify the original; document providers can use different document URIs. Do not use a guessed URI or a proxy. A URI permission failure is a failed setup, not an endurance PASS.
4. Record device, codec, resolution/FPS, source size, source SHA, charging, free storage and ambient conditions. Complete `STEP_5_PHYSICAL_CERTIFICATION.md` against the actual outputs.
5. Example 30-minute run: `python physical.py endurance --source-uri 'content://YOUR_GRANTED_ORIGINAL_URI' --end-us 1800000000 --roi 0.70,0.05,0.90,0.15`. Use 3600000000 for one hour. Set a source-appropriate ROI. The script does not download or substitute test media.
6. Run 1080p first, then real 4K if the phone supports the requested encoder. Include an approximately 3 GB or larger real source. Test cancellation/retry separately through the normal product notification and export screen.
7. Review and listen to start/middle/end and checkpoint boundaries. Check source resolution/FPS, unchanged-region colour, audio drift, patch borders, flicker and tile seams. A successful harness is not a visual acceptance result.
8. `python physical.py capture` creates an additional diagnostics folder. Results may contain the supplied media URI; share only evidence you intend to disclose.

Result folders are generated under `results/<UTC timestamp>/`: exact identity, device/memory/thermal/battery/storage logs, instrumentation log, endurance JSONL, and a result JSON whose visual acceptance remains NOT VERIFIED until manual review. Use a separate folder per source/device/configuration. Successful exported MP4s remain in the phone's media collection; the log/JSONL records their URI and hash.

The Debug instrumentation APK cannot target Review because their package IDs differ. No production signing key is included. A test failure requires a fix and another exact-head automated certification before final release.
