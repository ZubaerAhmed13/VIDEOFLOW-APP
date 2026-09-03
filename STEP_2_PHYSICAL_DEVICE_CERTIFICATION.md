# VideoFlow Android — Step 2 Physical Device Certification

## Purpose

This document is the authoritative physical-device acceptance protocol for Step 2 of 5.

Hosted CI, JVM tests and emulator instrumentation cannot replace this gate. Step 2 may be declared **COMPLETE** only after the editor workflow below is executed on a real Android device and every required row is truthfully recorded as **PASS**.

Allowed result values:

- PASS
- FAIL
- PARTIAL
- NOT VERIFIED
- NOT APPLICABLE

Do not convert an unexecuted physical check into PASS from code inspection, emulator evidence or Step 1 evidence.

## Current status

**STEP 2 PHYSICAL ACCEPTANCE: NOT VERIFIED**

The Step 1 physical-device report remains useful background evidence for large-media reference access and the previously used Motorola edge 60, but it does not satisfy this Step 2 editor/proxy acceptance scenario.

## Certified build identity

Record the exact build used for the physical run.

| Field | Result |
|---|---|
| Git commit SHA | NOT VERIFIED |
| GitHub Actions Step 2 run ID | NOT VERIFIED |
| APK type | Debug recommended for evidence capture |
| Installed APK SHA-256 | NOT VERIFIED |
| Package | `com.videoflow.app.debug` for Debug |
| Version name | NOT VERIFIED |

The physical result is valid only for the recorded APK/SHA. If editor behavior changes afterward, repeat the affected physical gate.

## Device record

| Field | Result |
|---|---|
| Manufacturer | NOT VERIFIED |
| Model | NOT VERIFIED |
| Android version/API | NOT VERIFIED |
| ABI | NOT VERIFIED |
| RAM | NOT VERIFIED |
| Free storage before proxy | NOT VERIFIED |
| Battery/charging state | NOT VERIFIED |
| Thermal status at start | NOT VERIFIED |

## Media record

Use real media selected through Android's document picker. Do not copy a multi-gigabyte source into app-private storage merely to run this test.

| Field | Result |
|---|---|
| Real video | NOT VERIFIED |
| Video source size | NOT VERIFIED |
| Video codec | NOT VERIFIED |
| Video resolution | NOT VERIFIED |
| Video duration | NOT VERIFIED |
| Multi-GB video used | NOT VERIFIED — preferred; use the Step 1 source when practical |
| Real image | NOT VERIFIED |
| Image format/resolution | NOT VERIFIED |
| Real audio clip | NOT VERIFIED |
| Audio codec/duration | NOT VERIFIED |

## Required physical editor workflow

Perform these operations in one real Step 2 project. Every numbered item is required unless explicitly marked optional.

1. Install the exact certified Step 2 Debug APK and launch it successfully.
2. Create a new project.
3. Import at least one real video, one real image and one real audio clip using the system picker.
4. Confirm the video remains reference-based; do not create an original-sized private copy.
5. Add the video clip to a compatible video track.
6. Move the clip on the timeline and confirm its new start position is retained.
7. Trim the clip start and/or end and confirm the source remains non-destructively referenced.
8. Split the clip at a valid playhead position.
9. Duplicate a clip instance and confirm both instances reference the same media asset.
10. Delete a clip instance and confirm the source asset itself remains available.
11. Add a text overlay and confirm it appears in preview at the intended time.
12. Add the real image as an overlay and confirm timing/preview.
13. Adjust scale and confirm the preview changes correctly.
14. Adjust rotation and confirm the preview changes correctly.
15. Adjust opacity and confirm the preview changes correctly.
16. Add the real audio clip to an audio track.
17. Adjust audio gain and confirm the edit is retained.
18. Add an audio fade and confirm the fade values remain bounded to the clip duration.
19. Add at least two keyframes to an editable property.
20. Preview the keyframed animation and confirm interpolation behaves as configured (HOLD or LINEAR).
21. Perform Undo and confirm the latest semantic edit is reversed.
22. Perform Redo and confirm that edit is reapplied.
23. Save a project snapshot and confirm it is listed/restorable.
24. Generate a real proxy for the video. Balanced/720p is recommended unless device/storage constraints justify another Step 2 mode.
25. Confirm proxy generation reports progress and reaches READY without ANR/crash.
26. Confirm the proxy file exists in app-private `files/proxies` storage and is materially a derived file rather than a source-sized original copy.
27. Confirm timeline preview uses the ready proxy while the original remains the authoritative media source for future rendering.
28. Scrub/seek and play the edited timeline with video, text/image overlay and audio present.
29. Force-stop the app from Android/ADB.
30. Reopen the app.
31. Confirm the project, tracks, clip positions, trims/splits, overlays, audio settings, keyframes, proxy association and snapshot are restored.
32. Make one additional edit after reopen and confirm autosave continues to work.
33. Observe the app during the workflow for crashes, ANRs, severe stalls or unbounded memory growth.

## Proxy record

| Field | Result |
|---|---|
| Proxy mode | NOT VERIFIED |
| Proxy resolution | NOT VERIFIED |
| Proxy codec | Expected H.264/AAC; physical result NOT VERIFIED |
| Proxy generation progress | NOT VERIFIED |
| Proxy generation result | NOT VERIFIED |
| Proxy file size | NOT VERIFIED |
| Proxy persisted after reopen | NOT VERIFIED |
| Preview selected proxy | NOT VERIFIED |
| Original retained as authoritative source | NOT VERIFIED |
| Cancellation tested | NOT VERIFIED — recommended hardening; not part of the minimum master physical sequence |
| Proxy deletion tested | NOT VERIFIED — recommended hardening; not part of the minimum master physical sequence |

## Required final physical report

| Gate | Result |
|---|---|
| Device | NOT VERIFIED |
| Android API | NOT VERIFIED |
| RAM | NOT VERIFIED |
| Source size | NOT VERIFIED |
| Source codec | NOT VERIFIED |
| Source resolution | NOT VERIFIED |
| Import | NOT VERIFIED |
| Generate Proxy | NOT VERIFIED |
| Add clip | NOT VERIFIED |
| Move | NOT VERIFIED |
| Trim | NOT VERIFIED |
| Split | NOT VERIFIED |
| Duplicate | NOT VERIFIED |
| Delete | NOT VERIFIED |
| Text | NOT VERIFIED |
| Image | NOT VERIFIED |
| Scale | NOT VERIFIED |
| Rotation | NOT VERIFIED |
| Opacity | NOT VERIFIED |
| Audio | NOT VERIFIED |
| Gain | NOT VERIFIED |
| Fade | NOT VERIFIED |
| Two keyframes | NOT VERIFIED |
| Preview animation | NOT VERIFIED |
| Timeline playback/scrub | NOT VERIFIED |
| Undo | NOT VERIFIED |
| Redo | NOT VERIFIED |
| Snapshot | NOT VERIFIED |
| Force-stop restore | NOT VERIFIED |
| Edit after reopen/autosave | NOT VERIFIED |
| No crash/ANR | NOT VERIFIED |
| Memory remained bounded | NOT VERIFIED |
| Storage behavior acceptable | NOT VERIFIED |

## Evidence capture helper

Use the repository helper from a computer with Android platform-tools and USB debugging enabled:

```bash
bash scripts/step2-physical-certification.sh start
```

After importing the media and building the initial timeline:

```bash
bash scripts/step2-physical-certification.sh checkpoint timeline-edited
```

Immediately before proxy generation:

```bash
bash scripts/step2-physical-certification.sh checkpoint before-proxy
```

After the proxy reaches READY:

```bash
bash scripts/step2-physical-certification.sh checkpoint after-proxy
```

After completing Undo/Redo, keyframes and snapshot:

```bash
bash scripts/step2-physical-certification.sh checkpoint before-restart
```

Then perform a real force-stop/reopen through the helper:

```bash
bash scripts/step2-physical-certification.sh force-stop-reopen
```

After confirming restoration and making one more edit:

```bash
bash scripts/step2-physical-certification.sh finalize
```

The helper records device identity, package identity, memory, storage, thermal/battery diagnostics, proxy-directory state, logcat crash/ANR scan and timestamped checkpoints. It does **not** manufacture PASS results for manual editor actions. Fill the generated `MANUAL_RESULTS.md` from what was actually observed.

## Failure policy

Any required physical operation that fails, loses state, produces an ANR/crash, creates an unexplained original-sized private copy, corrupts the project, or cannot be completed is a Step 2 blocker. Fix the root cause, rerun automated Step 1 + Step 2 certification, then repeat the affected physical scenario.

## Completion rule

Step 2 is **COMPLETE** only when all of the following are simultaneously true:

1. Step 1 regression certification is PASS on the final Step 2 SHA.
2. Step 2 JVM/lint/build certification is PASS on that SHA.
3. Step 2 API-35 emulator instrumentation is PASS on that SHA.
4. The required real-device editor workflow above is PASS on the certified APK.
5. The final physical report contains no required FAIL, PARTIAL or NOT VERIFIED row.
6. Step 3 final rendering/export, Step 4 AI and Step 5 production release work have not been substituted for missing Step 2 evidence.
