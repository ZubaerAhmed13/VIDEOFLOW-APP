# UX Step 2 — Trim / Speed / Crop Completion Report

## Baseline and scope
- Starting branch: `ux-step1-editor-shell-layout`
- Starting certified SHA: `0123b734f687dfdbf0589b6040aeb4d8642c5968`
- Working branch: `ux-step2-trim-speed-crop`
- Software implementation SHA before final certification metadata: `6d4e1bb1891800aa2d33b6746eee2742e5f0c64d`
- Final SHA: `__FINAL_SHA__` (runtime-bound in the authoritative certification artifact)
- Architecture reused from Step 1: Main Editor / Focused Tool workspace, dominant preview, pinned Cancel/Done action bar, safe-area handling, responsive portrait/landscape shell, bounded timeline and vertical track scrolling.
- Scope remains limited to Trim, Speed and Crop. Effects/Enhance are deferred to Step 3; AI Watermark UX is deferred to Step 4.

## Trim
### Previous behavior / root cause
Focused Trim committed through the shared direct timeline-edge `trimClipStart` primitive. That primitive intentionally advances `timelineStartUs` together with `sourceStartUs`, which is correct for direct edge editing but produced an unexplained leading gap and could make a focused-trimmed clip appear to disappear from the current timeline context.

### Corrected behavior
Focused Trim now uses the dedicated `TimelineEngine.trimSourceRangeKeepingTimelineAnchor(...)` operation through one atomic repository mutation, `trimClipContentsKeepingTimelineAnchor(...)`. It changes `sourceStartUs` / `sourceEndUs` while preserving the clip's intended `timelineStartUs`. Existing direct timeline edge-trim semantics remain unchanged. Duration-dependent fades are clamped to the new shortened duration.

Normal Trim presents human-readable Start / End / Duration labels such as `12.6 sec`, `1 min 39 sec`, and hour-scale values without engineering-style leading groups. `Precise` remains inside Trim and retains exact `HH:MM:SS.mmm` controls with Long-microsecond arithmetic.

The filmstrip uses the existing thumbnail/cache infrastructure and requests ten distributed source timestamps through `sampleTrimFilmstrip(...)`. Sampling is bounded to 2–10 frames, uses 192 px exact thumbnails, does not decode the whole video, and does not load a complete large source into memory.

Trim remains draft-only while editing. Cancel leaves persistent state untouched; Done performs one coherent commit/history entry. The selected clip remains the same logical clip after Done, and the focused anchor-preserving operation prevents an unexplained leading timeline gap.

## Speed
The existing 0.25×–4.00× engine range is retained. Common presets include 0.50×, 0.75×, 1.00×, 1.25×, 1.50× and 2.00×, with 0.25×, 3.00× and 4.00× still reachable. The primary value is formatted consistently to two decimals.

Original / Result durations are human-readable and calculated from authoritative source-duration microseconds. Speed edits live in `ContextualPreviewDraft.speed`; the preview player consumes the draft `effectivePreviewSpeed` immediately before Done. Done persists once through the existing editor/history path; Cancel drops the draft. Media3 remains the authoritative final video/audio speed path, so the UX change does not introduce a second processing layer.

## Crop
### Previous stretching / preview mismatch root cause
The original Compose preview divided X scale by crop width and Y scale by crop height independently. For non-source-aspect crops, those factors differed and visibly deformed people and objects. An intermediate uniform-scale approximation removed that deformation but still represented committed crop through Compose scale/translation while final export used a real Media3 `Crop`, leaving avoidable preview/export geometry divergence.

### Corrected source-space contract
Crop is now a true shared source-space operation. `Media3CropEffect.kt` converts normalized `CropRect(left, top, right, bottom)` coordinates into Media3 normalized-device crop bounds through one helper. Both final render and committed editor preview call the same `media3CropEffectOrNull(...)` function, so there is one authoritative crop conversion rather than parallel preview/export math.

During active Crop editing the editor deliberately shows the full undistorted source beneath the interactive crop mask. Outside Crop mode, the committed crop is applied by Media3 before the normal user transform. Crop itself introduces no independent X/Y scaling; any non-uniform transform can only come from an explicit Transform edit. `PlayerView` remains in aspect-fit mode, while final composition uses the same crop stage plus uniform `aspectFitScale()`.

Persistent crop coordinates remain normalized source-space `left/top/right/bottom` values. The crop interaction surface is fitted to the rotation-aware displayed source rect, so letterbox/pillarbox bars are excluded from crop touch coordinates. Ratio changes preserve the current crop center where possible and choose the largest valid in-bounds region. Required presets remain: Free, Original, 1:1, 4:5, 3:4, 4:3, 3:2, 16:9 and 9:16. Original derives from the source display aspect after rotation.

Crop Done now delegates persistence/dismissal through one callback path rather than dismissing once in the panel and again after persistence. Cancel remains draft-only. Final export stays original-source/native-render based; no bitmap/screenshot export path, forced 720p downgrade, or crop-induced X/Y stretching was introduced.

## Undo / Redo / persistence
Trim, Speed and Crop continue to use the existing edit-history and Room persistence architecture. Focused Trim records one coherent history entry after its single atomic source-range mutation. Speed and Crop persist only on Done. Existing Room/persistence, migration, undo/redo and editor repository regression suites are included in certification.

## Responsive and interaction certification
The dedicated Step-2 product Compose suite exercises the production Trim, Speed and Crop panels, the production focused-tool shell, pinned X/Done actions, human/precise Trim modes, Speed duration feedback, required Crop ratio behavior, rotation-aware Crop mapping, and portrait/landscape focused layouts. Evidence screenshots are generated for Trim normal, Trim precise, Speed 1×, Speed 2×, Crop Free, Crop 1:1, Crop 9:16, rotated portrait Crop, compact Trim, compact Crop and landscape focused Crop.

A real-render Crop geometry test renders a deterministic white square through Original, 1:1 and 9:16 crops and verifies that the square remains approximately square rather than becoming an ellipse/rectangle due to non-uniform scaling. The retained A/V synchronization test exercises speed with real video/audio events and checks drift tolerance. Step-1 shell/font/visual regression and retained Room, migration, player, proxy and native-render suites are also run on API 35.

The API-35 emulator harness is intentionally POSIX-shell compatible because `reactivecircus/android-emulator-runner@v2` executes its `script:` body with `/bin/sh`. Bash-specific `set -o pipefail` is therefore not used inside that action body; strict `set -eu` remains enabled while the individual instrumentation suite checks explicitly validate success markers and reject failure/crash markers. Bash-only workflow steps outside the emulator action continue to declare `shell: bash` and retain `set -euo pipefail`.

## Files changed
The authoritative final list is produced by:

`git diff 0123b734f687dfdbf0589b6040aeb4d8642c5968...__FINAL_SHA__ --name-only`

Production changes are confined to Trim/Speed/Crop editor state, UI, preview geometry, repository/domain helpers and the small shared Media3 crop-effect adapter, plus tests, this report and the dedicated Step-2 certification workflow. Step-2 source-scope certification rejects changes under the AI and proxy implementation directories.

## Certification binding
- Dedicated workflow: `VideoFlow UX Step 2 Trim Speed Crop Certification`
- Step-2 CI run ID: `__CI_RUN_ID__`
- Exact certified SHA: `__FINAL_SHA__`
- Automated certification: `__AUTOMATED_CERTIFICATION_STATUS__`
- Step-1 regression: `__STEP1_REGRESSION_STATUS__`
- Physical-device certification: **NOT RUN**
- Final whole-app UX certification: **NOT RUN**

The checked-in report intentionally contains runtime tokens for the CI run ID and final commit SHA because a Git commit cannot contain its own hash. The final successful exact-head workflow replaces those tokens and uploads the authoritative, immutable completion-report artifact together with `EXACT_HEAD.txt`, `CI_RUN_ID.txt` and the certification evidence.

## Known limitations / explicit deferrals
- Physical-device certification is intentionally NOT RUN in UX Step 2.
- Final whole-app UX certification is intentionally NOT RUN.
- Effects / Enhance UX: deferred to Step 3.
- AI Watermark UX: deferred to Step 4.
- No Step 3 work is started automatically from this branch.
