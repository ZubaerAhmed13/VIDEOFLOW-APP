# VideoFlow Android — Step 4 AI Watermark Studio Physical Review

## Certification state

**Physical device status: NOT VERIFIED**

Do not change this status to PASS until the exact automated-certified Review APK has been installed and tested on a physical Android phone. Do not substitute a locally rebuilt APK or another branch build.

## Device/build evidence

Record before testing:

- Certified branch:
- Certified commit SHA:
- GitHub Actions run:
- Review APK SHA-256:
- Phone manufacturer/model:
- Android version/API:
- Available RAM:
- Available storage before test:
- Hardware decoder/encoder notes if relevant:

## 1. Local/offline runtime

- [ ] Enable airplane mode / disable Wi-Fi and mobile data.
- [ ] Launch VideoFlow Review successfully.
- [ ] Open a project with a video source.
- [ ] Open AI Watermark Studio.
- [ ] Local model runtime reports ready without network access.
- [ ] No network requirement/error appears.

## 2. Mask and preview

- [ ] Draw/move/resize ROI around a visible watermark/logo.
- [ ] Adjust effect start/end range.
- [ ] Generate AI Preview.
- [ ] Preview completes without crash/ANR.
- [ ] Preview changes only the intended ROI.
- [ ] Source media remains unchanged outside the project.

## 3. Moving watermark tracking

Use a clip where the watermark/logo position moves.

- [ ] Track movement.
- [ ] Review the generated anchors across the selected time range.
- [ ] Scrub near the beginning, middle and end.
- [ ] ROI follows the intended target rather than remaining at the starting location.
- [ ] No visible restoration of the original watermark occurs merely because the ROI moved away from its starting coordinates.
- [ ] Feathering follows the current tracked ROI on every checked frame.
- [ ] No edge tearing, displaced patch, or stale-location blend is visible.

## 4. AI Apply / Update / Enable / Remove

- [ ] Apply a new AI Watermark effect.
- [ ] Reopen and edit the applied effect.
- [ ] Update the effect.
- [ ] Disable the effect and confirm it no longer applies.
- [ ] Re-enable it.
- [ ] Remove it.
- [ ] Restart the app and confirm persisted state is correct after each retained change.

## 5. AI Undo/Redo

Use the editor's normal Undo/Redo controls, not a test-only control.

- [ ] Apply AI Watermark → Undo removes/restores the exact previous sidecar state.
- [ ] Redo restores the exact applied effect.
- [ ] Update AI Watermark → Undo restores the previous ROI/timing/settings.
- [ ] Redo restores the updated state.
- [ ] Disable/Enable → Undo and Redo restore the exact toggle state.
- [ ] Remove → Undo restores the removed effect.
- [ ] Redo removes it again.
- [ ] If Watermark Studio is open, its applied-effects list refreshes after Undo/Redo.
- [ ] Existing non-AI editor Undo/Redo still works after AI history operations.

## 6. Final AI export

Use an enabled AI effect and export through the normal product Export flow.

- [ ] Smart Copy is unavailable/blocked for the AI-edited project.
- [ ] Final render starts successfully.
- [ ] Export completes without crash/ANR.
- [ ] Output file is playable.
- [ ] Video and audio remain synchronized.
- [ ] Output duration is correct.
- [ ] Output resolution/frame rate match the chosen source-preservation/export settings.
- [ ] AI reconstruction is visible in the final exported video, not only in preview.
- [ ] Moving tracked ROI remains correct in the final exported video.
- [ ] No stale start-ROI blend is visible after the ROI moves.
- [ ] No unintended whole-frame color shift is visible.
- [ ] No unexpected severe quality loss outside the AI ROI is visible.

## 7. Source fidelity / quality review

Record source and output metadata where available:

- source resolution:
- output resolution:
- source frame rate:
- output frame rate:
- source codec:
- output codec:
- source HDR/color information:
- output HDR/color information:
- source audio:
- output audio:

Visual checks:

- [ ] fine detail outside ROI remains acceptable
- [ ] motion remains smooth
- [ ] gradients do not show new severe banding
- [ ] text/edges outside ROI remain stable
- [ ] no new blocking/corruption
- [ ] ROI feather edge is visually acceptable
- [ ] temporal stability is acceptable across adjacent frames

## 8. Large/original-resolution source behavior

When suitable physical test media is available:

- [ ] 1080p source final AI export
- [ ] 2K source final AI export
- [ ] 4K source final AI export
- [ ] original-resolution source remains authoritative
- [ ] bounded ROI processing does not require loading the whole source video into RAM
- [ ] storage failure/preflight is understandable and non-destructive

Large-file/4K physical execution is a product certification item; emulator CI is not a substitute for device thermal/performance behavior.

## Result

Mark exactly one after completing all required checks:

- [ ] PASS — Step 4 physical review completed on the exact certified Review APK.
- [ ] FAIL — defect(s) found; record them below and return to a narrow correction + new exact-head CI certification.
- [x] NOT VERIFIED — default until physical execution is performed.

### Defects / observations

- None recorded yet; physical execution has not been performed.

## Completion rule

Automated green CI is necessary but does not by itself change this document to PASS. If a physical defect requires a source change, the old certified APK is superseded: fix narrowly, rerun the complete Step-4 CI on the new exact HEAD, then repeat this checklist using the newly certified Review APK.
