# VideoFlow Final Editor Quality — Physical Device Review

## Rule

Do **not** pre-fill PASS. The user's same physical phone where preview lag and contrast problems were observed is authoritative for this correction phase.

## Build Identity

- Branch: `editor-final-quality-source-preservation`
- Certified HEAD: ______________________________
- CI run: ______________________________
- Review APK: `VideoFlow_Android_FinalEditorQuality_Review.apk`
- Review APK SHA-256: ______________________________
- Review application ID: `com.videoflow.app.review`

## Device

- Device manufacturer/model: ______________________________
- Android version: ______________________________
- API level: ______________________________
- Available RAM/storage if relevant: ______________________________

## Problem Source Metadata

Use the original clip that exposed playback lag first.

- File/display name: ______________________________
- Codec: ______________________________
- Resolution: ______________________________
- Frame rate: ______________________________
- Approx. bitrate: ______________________________
- Duration: ______________________________
- File size: ______________________________
- Audio codec/sample rate/channels: ______________________________
- SDR/HDR if known: ______________________________

## 1. Playback

- Exact Review APK installed: NOT VERIFIED
- Original problematic clip tested: NOT VERIFIED
- Original preview active: NOT VERIFIED
- Proxy preview active/available: NOT VERIFIED
- Sustained playback visibly smooth: NOT VERIFIED
- Play/pause response: NOT VERIFIED
- Scrubbing/seek response: NOT VERIFIED
- Audio remains synchronized: NOT VERIFIED
- Before/after improvement recorded: NOT VERIFIED

Notes:

__________________________________________________________________

## 2. Contrast / Accessibility

Inspect Trim/Precise Trim first, then Crop, Speed, Transform, Export and Settings.

- From/Start readable: NOT VERIFIED
- To/End readable: NOT VERIFIED
- Duration readable: NOT VERIFIED
- Validation/error text readable: NOT VERIFIED
- Crop text/icons readable: NOT VERIFIED
- Speed text/icons readable: NOT VERIFIED
- Transform text/icons readable: NOT VERIFIED
- Export + Export Mode readable: NOT VERIFIED
- Settings readable: NOT VERIFIED
- Landscape editor usable: NOT VERIFIED
- Large font / accessibility check: NOT VERIFIED

Notes:

__________________________________________________________________

## 3. Precise Trim

Required test:

- From = `00:00:03.000`
- To = `00:00:15.000`

Verify:

- fields accept valid exact entry: NOT VERIFIED
- invalid Start/End rejected clearly: NOT VERIFIED
- visual trim range/handles agree with fields: NOT VERIFIED
- preview updates to trimmed range: NOT VERIFIED
- persisted project retains trim after reopen: NOT VERIFIED
- Undo/Redo behaves correctly: NOT VERIFIED
- output duration/boundaries are correct for source timing: NOT VERIFIED

Observed normalized source-sample values if different:

- From: ______________________________
- To: ______________________________

## 4. Merge Videos

Choose at least three videos.

- multi-select works: NOT VERIFIED
- selected order displayed: NOT VERIFIED
- reorder up/down works: NOT VERIFIED
- remove/add-more works: NOT VERIFIED
- create merge project works: NOT VERIFIED
- editor previews complete order: NOT VERIFIED
- per-clip Precise Trim works: NOT VERIFIED
- project survives restart/reopen: NOT VERIFIED
- original files remain unchanged: NOT VERIFIED

Notes:

__________________________________________________________________

## 5. Smart Copy

Use compatible MP4 clips if available.

- Smart Copy is shown only when eligible: NOT VERIFIED
- compatible merge/trim packet-copy completes: NOT VERIFIED
- output container is valid/playable: NOT VERIFIED
- A/V sync: NOT VERIFIED
- expected speed advantage observed: NOT VERIFIED
- no visible generational re-encode change: NOT VERIFIED
- incompatible codec/sample configuration disables Smart Copy: NOT VERIFIED
- non-keyframe exact trim does not silently move boundary: NOT VERIFIED
- no silent rendered fallback while labeled Smart Copy: NOT VERIFIED

Output metadata / notes:

__________________________________________________________________

## 6. Match Source / Source Fidelity

Record one source before rendering:

- source resolution: ______________________________
- source rational/observed FPS: ______________________________
- source codec: ______________________________
- source approx. bitrate: ______________________________
- source colour/HDR: ______________________________
- source audio properties: ______________________________

Perform an edit that requires rendering (for example Crop/Text/Transform), export Match Source, then record:

- output resolution: ______________________________
- output FPS: ______________________________
- output codec: ______________________________
- output approx. bitrate/file size: ______________________________
- output colour/HDR: ______________________________
- output audio properties: ______________________________

Result checks:

- no silent 29.97 -> 30 change: NOT VERIFIED
- no silent unsupported codec claim: NOT VERIFIED
- source/project resolution authority correct: NOT VERIFIED
- colour/HDR policy correct: NOT VERIFIED
- audio properties acceptable: NOT VERIFIED
- estimated size language remains approximate: NOT VERIFIED
- visible quality acceptable: NOT VERIFIED

## 7. Visual Quality Check

Inspect:

- fine detail: NOT VERIFIED
- text edges: NOT VERIFIED
- motion: NOT VERIFIED
- gradients: NOT VERIFIED
- colour: NOT VERIFIED
- banding: NOT VERIFIED
- blocking/artifacts: NOT VERIFIED

Do not describe rendered output as mathematically lossless.

## Final Physical Result

- Playback: NOT VERIFIED
- Contrast: NOT VERIFIED
- Precise Trim: NOT VERIFIED
- Merge: NOT VERIFIED
- Smart Copy: NOT VERIFIED
- Match Source: NOT VERIFIED

### Physical certification decision

**NOT VERIFIED**

Only change this decision after testing the exact certified Review APK on the same target phone. Any unresolved visible lag, unreadable text, Trim disagreement, broken Merge behavior, false Smart Copy, source-fidelity regression, crash, or A/V-sync problem is a hard blocker.