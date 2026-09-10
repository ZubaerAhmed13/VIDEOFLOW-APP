# UX Step 2 — Trim / Speed / Crop Completion Report

## Baseline
- Starting branch: `ux-step1-editor-shell-layout`
- Starting SHA: `0123b734f687dfdbf0589b6040aeb4d8642c5968`
- Working branch: `ux-step2-trim-speed-crop`
- Architecture reused: Step-1 Main Editor / Focused Tool workspace, pinned Cancel/Done action bar, safe-area handling, responsive shell, bounded timeline and track scrolling.

## Trim
Previous focused Trim committed through the shared direct-edge `trimClipStart`, which advances `timelineStartUs` as source trim-in moves. Corrected focused Trim uses `TimelineEngine.trimSourceRangeKeepingTimelineAnchor` through one atomic repository mutation: `sourceStartUs/sourceEndUs` change while `timelineStartUs` remains fixed. Direct timeline edge trim keeps its existing semantics. Normal labels are human-readable; `Precise` remains inside Trim with exact timecode. Filmstrip remains real, bounded, cached sampling and requests ten samples.

## Speed
The existing 0.25×–4× range is retained. Common presets include 0.5×, 0.75×, 1×, 1.25×, 1.5× and 2×. Original/Result durations are human-readable. Long-microsecond source duration remains authoritative. Slider/preset changes update transient preview speed; only Done persists. Audio/export keeps the existing Media3 speed path.

## Crop
Root cause: the old Compose preview divided X by crop width and Y by crop height independently. Corrected preview uses one uniform crop scale and source-content-aware translation. During Crop, the full undistorted source is shown beneath the normalized crop overlay. The interaction surface is fitted to the rotated source display rect, excluding letterbox/pillarbox bars. Original/Free and required ratio presets remain available. Final export retains Media3 `Crop` and uniform `aspectFitScale`.

## State / Undo / Persistence
Trim/Speed/Crop use focused draft sessions. Cancel does not persist drafts. Done persists once. Focused Trim now performs one atomic source-range mutation before one history entry. Existing Speed/Crop history and Room persistence paths remain in place.

## Tests / CI
Added `UXStep2TrimSpeedCropCoreTest` covering focused Trim anchoring, direct-edge non-regression, human duration, speed arithmetic, uniform crop geometry, and rotation-aware dimensions.
Dedicated workflow: `VideoFlow UX Step 2 Trim Speed Crop Certification`.
Source implementation landed at `e4be484eeca6525f9979f9ed825d891f7a79a37a`; this report update intentionally triggers a user-authored exact-head certification run after the Actions-generated source commit, because GitHub does not recursively trigger workflows from `GITHUB_TOKEN` pushes.

## Files changed
Use `git diff 0123b734f687dfdbf0589b6040aeb4d8642c5968...HEAD --name-only` as the authoritative list.

## Certification
- Step-2 CI run ID: **PENDING exact-head workflow**
- Exact certified SHA: **PENDING exact-head workflow**
- Step-1 regression: **PENDING exact-head workflow**
- Physical-device certification: **NOT RUN**

## Deferrals
- Effects / Enhance UX: deferred to Step 3.
- AI Watermark UX: deferred to Step 4.
- Final whole-app UX certification: NOT RUN.
- Do not proceed automatically to Step 3.
