# VideoFlow Android — Physical Device Step 1 Certification

Use the final certified Step 1 Debug APK. Do not change any physical-device row to **PASS** without direct evidence.

## Certification record

```text
Device manufacturer: NOT VERIFIED
Model: NOT VERIFIED
Android version: NOT VERIFIED
API: NOT VERIFIED
ABI: NOT VERIFIED
RAM: NOT VERIFIED
Available storage: NOT VERIFIED

Test filename/sanitized label: NOT VERIFIED
Size: NOT VERIFIED
Duration: NOT VERIFIED
Resolution: NOT VERIFIED
FPS: NOT VERIFIED
Video codec: NOT VERIFIED
Audio codec: NOT VERIFIED
Storage/provider: NOT VERIFIED

App storage before: NOT VERIFIED
App storage after: NOT VERIFIED
Storage delta: NOT VERIFIED

Import: NOT VERIFIED
Preview: NOT VERIFIED
25% seek: NOT VERIFIED
50% seek: NOT VERIFIED
75% seek: NOT VERIFIED
95% seek: NOT VERIFIED
Force-stop reopen: NOT VERIFIED
Reboot reopen: NOT VERIFIED
Missing-source handling: NOT VERIFIED
Strong relink original: NOT VERIFIED
Wrong-file relink rejection: NOT VERIFIED
Launcher icon device check: NOT VERIFIED
Accessibility basic check: NOT VERIFIED

Import memory: NOT VERIFIED
Fingerprint memory: NOT VERIFIED
Preview/late-seek memory: NOT VERIFIED
Thermal observation: NOT VERIFIED

H.264 decode/encode/4K30/4K60: NOT VERIFIED
HEVC decode/encode/4K30/4K60: NOT VERIFIED
VP9: NOT VERIFIED
AV1: NOT VERIFIED
```

## Required setup

- Android phone/tablet running API 26 or newer
- at least one genuine encoded video larger than 3 GB; sparse placeholders do not qualify
- enough free storage to open/play that file normally
- preferably a document provider that supports persisted read URI grants

## Procedure

1. Install the final `VideoFlow_Android_Step1_Debug.apk`.
2. Confirm the VideoFlow launcher icon is visible and is not the generic Android icon.
3. Open VideoFlow > Settings > Device Capability and record manufacturer/model, API, ABI, RAM, available storage, H.264/HEVC/VP9/AV1 inventory, and 4K30/4K60 results exactly as detected.
4. Record Android Settings > Apps > VideoFlow > Storage usage before media import and preserve a screenshot where possible.
5. Create a new project.
6. Tap **Add Media** and select the genuine >3 GB encoded video through Android's system document picker.
7. Confirm there is no file-size rejection/crash, metadata and fingerprint processing finish, the project remains responsive, and source status becomes `AVAILABLE`.
8. Record VideoFlow app storage again and calculate the delta. The increase must remain metadata/cache-scale and must not approximate the >3 GB source size.
9. Preview the file and seek around 25%, 50%, 75% and 95% of duration; verify playback resumes near each point.
10. Save/leave the project, force-stop VideoFlow, reopen it, and verify the project/source reference and preview still work.
11. Reboot the device, reopen VideoFlow, and verify persisted access when the selected provider supports persistable URI permissions. If the provider does not support persistence, record that provider limitation and repeat with a persistence-capable provider where reasonably available.
12. Temporarily make the source unavailable (for example removable/provider-controlled storage), reopen the project, and verify `MISSING` / `PERMISSION_LOST` is presented without a crash and **Locate Original** is offered.
13. Restore/select the true original via **Locate Original**. With a strong-capable provider, verify a strong match reconnects and preview works.
14. Repeat Locate Original with a different video. Verify the mismatch is rejected and the saved source reference is not replaced.
15. If a provider with limited seek/stat behavior is available, test weak identity and confirm VideoFlow does not call it a strong match. If no such provider is available, record this physical case as **NOT VERIFIED**; automated weak-provider coverage remains separate evidence.
16. If the provider permits same-document content replacement, replace the underlying content while keeping the logical reference and verify `CHANGED`. If not practical on the physical provider, do not fabricate a physical PASS; repository instrumentation covers this logic independently.
17. Check duplicate import: select the same source again, verify the duplicate dialog appears, choose **Cancel** and confirm no second project media entry; repeat and choose **Add Anyway** to confirm an explicit second reference is added.
18. With TalkBack or Android accessibility inspection, verify critical controls are identified: New Project, Add Media, Settings, Locate Original, and duplicate-dialog actions.
19. During >3 GB import/fingerprint/preview/late seek, record memory using Android Studio Memory Profiler, `adb shell dumpsys meminfo`, or another valid measurement. Record Java/native/total app memory where available.
20. Record thermal behavior (normal/warm/throttled/thermal warning) without overstating Step 1 as long-duration export certification.

## PASS criteria

- no app-private source-sized copy is created
- no arbitrary 3 GB size-cap error appears
- >3 GB metadata/fingerprint completes with bounded behavior
- preview and 25/50/75/95% seek work
- project survives force-stop/reopen
- persisted access survives reboot with a persistence-capable provider
- missing source is handled safely
- strong original relink succeeds and wrong-file relink is rejected
- memory remains bounded rather than scaling with full source size
- actual-device codec/4K information is reported truthfully
- launcher icon and critical accessibility semantics work on device

## Current status

Physical-device certification: **NOT VERIFIED**.

Automated remediation certification is recorded separately in `STEP_1_TEST_REPORT.md`; automated results must not be used to convert the physical rows above to PASS.
