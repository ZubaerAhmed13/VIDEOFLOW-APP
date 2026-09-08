#!/usr/bin/env bash
set -e
mkdir -p step4-emulator-reports
PACKAGE_ID=com.videoflow.app.debug
HANDSHAKE_DIR="/sdcard/Android/data/$PACKAGE_ID/files/step5-handshake"
adb install -r step4-runtime/VideoFlow_Step5_Debug.apk
adb install -r step4-runtime/VideoFlow_Step5_Debug-androidTest.apk

adb shell svc wifi disable
adb shell svc data disable
adb shell pm grant "$PACKAGE_ID" android.permission.POST_NOTIFICATIONS
for configuration in portrait landscape tablet; do
  if [ "$configuration" = landscape ]; then adb shell wm size 1280x720; adb shell wm density 240; fi
  if [ "$configuration" = tablet ]; then adb shell wm size 1920x1200; adb shell wm density 160; fi
  adb shell settings put system font_scale 1.3
  adb shell am instrument -w -r -e class com.videoflow.app.step5.Step5TimelineAccessibilityTest "$PACKAGE_ID.test/androidx.test.runner.AndroidJUnitRunner" > "step4-emulator-reports/step5-layout-$configuration.txt" 2>&1
  cat "step4-emulator-reports/step5-layout-$configuration.txt"
  ! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' "step4-emulator-reports/step5-layout-$configuration.txt" || exit 1
  grep -E -q 'OK \(1 test\)' "step4-emulator-reports/step5-layout-$configuration.txt"
done
adb shell wm size reset
adb shell wm density reset
adb shell settings put system font_scale 1.0

# Start a genuine AI export while instrumentation remains alive. The target test publishes a
# host-visible ready flag only after the persisted job reaches RENDERING. App-specific external
# test storage is used only for synchronization because adb shell can reliably observe it.
adb shell rm -rf "$HANDSHAKE_DIR" >/dev/null 2>&1 || true
adb shell mkdir -p "$HANDSHAKE_DIR"
adb shell am instrument -w -r -e class 'com.videoflow.app.step5.Step5ProcessDeathTest#startRealForegroundAiJob' "$PACKAGE_ID.test/androidx.test.runner.AndroidJUnitRunner" > step4-emulator-reports/step5-process-start.txt 2>&1 &
PROCESS_TEST_HOST_PID=$!
MAIN_PID=""
EXPORT_PID=""
PROCESS_READY=0
for attempt in $(seq 1 240); do
  MAIN_PID="$(adb shell pidof "$PACKAGE_ID" 2>/dev/null | tr -d '\r' | awk '{print $1}')"
  EXPORT_PID="$(adb shell pidof "$PACKAGE_ID:export" 2>/dev/null | tr -d '\r' | awk '{print $1}')"
  if [ -n "$MAIN_PID" ] && [ -n "$EXPORT_PID" ] && adb shell test -f "$HANDSHAKE_DIR/ready-for-export-kill.flag" >/dev/null 2>&1; then
    PROCESS_READY=1
    break
  fi
  if ! kill -0 "$PROCESS_TEST_HOST_PID" 2>/dev/null; then
    wait "$PROCESS_TEST_HOST_PID" || true
    cat step4-emulator-reports/step5-process-start.txt
    echo "Process-death setup instrumentation exited before the export kill handshake became ready." >&2
    exit 1
  fi
  sleep 0.25
done
if [ "$PROCESS_READY" -ne 1 ]; then
  cat step4-emulator-reports/step5-process-start.txt || true
  echo "Timed out waiting for simultaneous main/:export PIDs and the real RENDERING handshake." >&2
  kill "$PROCESS_TEST_HOST_PID" >/dev/null 2>&1 || true
  wait "$PROCESS_TEST_HOST_PID" || true
  exit 1
fi
test -n "$MAIN_PID"
test -n "$EXPORT_PID"
echo "MAIN_PID_BEFORE=$MAIN_PID EXPORT_PID_BEFORE=$EXPORT_PID" | tee -a step4-emulator-reports/step5-process-start.txt

# Kill ONLY :export while the instrumentation process intentionally keeps MainActivity alive.
adb shell kill -9 "$EXPORT_PID"
sleep 2
MAIN_AFTER="$(adb shell pidof "$PACKAGE_ID" | tr -d '\r' | awk '{print $1}')"
EXPORT_AFTER="$(adb shell pidof "$PACKAGE_ID:export" | tr -d '\r' | awk '{print $1}')"
test -n "$MAIN_AFTER"
test "$MAIN_AFTER" = "$MAIN_PID"
test -z "$EXPORT_AFTER"
echo "STEP5_EXPORT_PROCESS_ISOLATION_CERTIFIED main_pid=$MAIN_AFTER killed_export_pid=$EXPORT_PID" | tee step4-emulator-reports/step5-export-process-isolation.txt

# Acknowledge the external kill so the test can close ActivityScenario and finish normally.
adb shell touch "$HANDSHAKE_DIR/export-killed.flag"
if ! wait "$PROCESS_TEST_HOST_PID"; then
  cat step4-emulator-reports/step5-process-start.txt
  exit 1
fi
cat step4-emulator-reports/step5-process-start.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/step5-process-start.txt || exit 1
grep -E -q 'OK \(1 test\)' step4-emulator-reports/step5-process-start.txt
sleep 8

adb shell am instrument -w -r -e class 'com.videoflow.app.step5.Step5ProcessDeathTest#restartRecognizesInterruptedJobAndPreservesEditableProject' "$PACKAGE_ID.test/androidx.test.runner.AndroidJUnitRunner" > step4-emulator-reports/step5-process-recovery.txt 2>&1
cat step4-emulator-reports/step5-process-recovery.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/step5-process-recovery.txt || exit 1
grep -E -q 'OK \(1 test\)' step4-emulator-reports/step5-process-recovery.txt

adb shell am instrument -w -r -e class com.videoflow.app.step5.Step5AiOutsideRoiTest,com.videoflow.app.step5.Step5RecoverySecurityTest,com.videoflow.app.step5.Step5QualityExportTest,com.videoflow.app.step5.Step5CompositionGeometryTest,com.videoflow.app.step5.Step5AudioVideoSyncTest,com.videoflow.app.step5.Step5CheckpointOverlayTest,com.videoflow.app.step5.Step5ProductIntegrationTest "$PACKAGE_ID.test/androidx.test.runner.AndroidJUnitRunner" > step4-emulator-reports/step5-integration.txt 2>&1
cat step4-emulator-reports/step5-integration.txt
adb pull /sdcard/Android/data/$PACKAGE_ID/files/step5-evidence step4-emulator-reports/ || true
adb pull /sdcard/Android/data/$PACKAGE_ID/files/professional-screenshots step4-emulator-reports/ || true
adb logcat -d -v threadtime > step4-emulator-reports/step5-logcat.txt
for measurement in fd-details.txt fd-phases.jsonl sync-diagnostics.txt av-sync.jsonl audio-fades.jsonl resources.jsonl; do
  if [ -f "step4-emulator-reports/step5-evidence/$measurement" ]; then cat "step4-emulator-reports/step5-evidence/$measurement"; fi
done
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/step5-integration.txt || exit 1
grep -E -q 'OK \(13 tests\)' step4-emulator-reports/step5-integration.txt
grep -q 'STEP5_OVERLAY_CHECKPOINT_CERTIFIED' step4-emulator-reports/step5-integration.txt
grep -q 'STEP5_PRODUCT_INTEGRATION_CERTIFIED' step4-emulator-reports/step5-integration.txt
adb pull /sdcard/Android/data/$PACKAGE_ID/files/step5-evidence step4-emulator-reports/
test -s step4-emulator-reports/step5-evidence/quality.jsonl
test -s step4-emulator-reports/step5-evidence/av-sync.jsonl

adb shell am instrument -w -r -e class com.videoflow.app.ui.ProfessionalProductWorkflowTest "$PACKAGE_ID.test/androidx.test.runner.AndroidJUnitRunner" > step4-emulator-reports/product-panels.txt 2>&1
adb pull /sdcard/Android/data/$PACKAGE_ID/files/professional-screenshots step4-emulator-reports/ || true
adb logcat -d -v threadtime > step4-emulator-reports/player-logcat.txt
cat step4-emulator-reports/product-panels.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/product-panels.txt || exit 1
grep -E -q 'OK \(1 test\)' step4-emulator-reports/product-panels.txt
grep -q 'PROFESSIONAL_PRODUCT_PANELS_CERTIFIED' step4-emulator-reports/product-panels.txt
test -s step4-emulator-reports/professional-screenshots/ai-preview.png

adb shell am instrument -w -r -e class com.videoflow.app.ai.Step4AiRuntimeInstrumentedTest "$PACKAGE_ID.test/androidx.test.runner.AndroidJUnitRunner" > step4-emulator-reports/ai-runtime.txt 2>&1
cat step4-emulator-reports/ai-runtime.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/ai-runtime.txt || exit 1
grep -E -q 'OK \([0-9]+ tests?\)' step4-emulator-reports/ai-runtime.txt

adb shell am instrument -w -r -e class com.videoflow.app.ai.Step5AiMovingPreviewInstrumentedTest "$PACKAGE_ID.test/androidx.test.runner.AndroidJUnitRunner" > step4-emulator-reports/ai-moving-preview.txt 2>&1
cat step4-emulator-reports/ai-moving-preview.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/ai-moving-preview.txt || exit 1
grep -E -q 'OK \(1 test\)' step4-emulator-reports/ai-moving-preview.txt
grep -q 'STEP5_AI_MOVING_PREVIEW_CERTIFIED' step4-emulator-reports/ai-moving-preview.txt

adb shell am instrument -w -r -e class com.videoflow.app.ai.Step4AiFinalExportInstrumentedTest "$PACKAGE_ID.test/androidx.test.runner.AndroidJUnitRunner" > step4-emulator-reports/final-ai-export.txt 2>&1
cat step4-emulator-reports/final-ai-export.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/final-ai-export.txt || exit 1
grep -E -q 'OK \(1 test\)' step4-emulator-reports/final-ai-export.txt
grep -q 'FINAL_AI_EXPORT_CERTIFIED' step4-emulator-reports/final-ai-export.txt

adb shell am instrument -w -r -e class com.videoflow.app.ui.HomeComposeTest,com.videoflow.app.ui.EditorWorkspaceVisualCertificationTest,com.videoflow.app.ui.LongTimelineWorkspaceSmokeTest,com.videoflow.app.ui.ContextualToolbarComposeTest,com.videoflow.app.ui.Step5ToolbarGeometryComposeTest "$PACKAGE_ID.test/androidx.test.runner.AndroidJUnitRunner" > step4-emulator-reports/editor-regression.txt 2>&1
cat step4-emulator-reports/editor-regression.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/editor-regression.txt || exit 1
grep -E -q 'OK \([0-9]+ tests?\)' step4-emulator-reports/editor-regression.txt

adb shell am instrument -w -r -e class com.videoflow.app.editor.ProfessionalAudioPersistenceTest,com.videoflow.app.ui.ProfessionalToolControlsTest,com.videoflow.app.ai.ProfessionalCombinedExportInstrumentedTest,com.videoflow.app.ai.ProfessionalCheckpointExportInstrumentedTest "$PACKAGE_ID.test/androidx.test.runner.AndroidJUnitRunner" > step4-emulator-reports/professional-upgrade.txt 2>&1
adb pull /sdcard/Android/data/$PACKAGE_ID/files/professional-screenshots step4-emulator-reports/ || true
cat step4-emulator-reports/professional-upgrade.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/professional-upgrade.txt || exit 1
grep -E -q 'OK \(5 tests\)' step4-emulator-reports/professional-upgrade.txt
grep -q 'PROFESSIONAL_COMBINED_EXPORT_CERTIFIED' step4-emulator-reports/professional-upgrade.txt
grep -q 'PROFESSIONAL_CHECKPOINT_EXPORT_CERTIFIED' step4-emulator-reports/professional-upgrade.txt

adb shell am instrument -w -r -e class com.videoflow.app.db.RoomPersistenceTest,com.videoflow.app.db.Step2MigrationTest,com.videoflow.app.editor.Step2ComplexPersistenceTest,com.videoflow.app.editor.ProxyManagerInstrumentationTest,com.videoflow.app.media.MediaAnalyzerInstrumentationTest,com.videoflow.app.media.PlayerInstrumentationTest,com.videoflow.app.export.NativeRenderEngineInstrumentedTest,com.videoflow.app.export.SafMediaMuxerFactoryInstrumentedTest "$PACKAGE_ID.test/androidx.test.runner.AndroidJUnitRunner" > step4-emulator-reports/retained-product-regression.txt 2>&1
cat step4-emulator-reports/retained-product-regression.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/retained-product-regression.txt || exit 1
grep -E -q 'OK \([0-9]+ tests?\)' step4-emulator-reports/retained-product-regression.txt

adb uninstall com.videoflow.app.review >/dev/null 2>&1 || true
adb install step4-runtime/VideoFlow_Step5_Review.apk | tee step4-emulator-reports/review-fresh-install.txt
grep -q 'Success' step4-emulator-reports/review-fresh-install.txt
adb shell monkey -p com.videoflow.app.review -c android.intent.category.LAUNCHER 1 > step4-emulator-reports/review-cold-launch.txt
sleep 2
adb shell pidof com.videoflow.app.review | tee -a step4-emulator-reports/review-cold-launch.txt
adb install -r step4-runtime/VideoFlow_Step5_Review.apk | tee step4-emulator-reports/review-in-place-update.txt
grep -q 'Success' step4-emulator-reports/review-in-place-update.txt
adb shell monkey -p com.videoflow.app.review -c android.intent.category.LAUNCHER 1 >> step4-emulator-reports/review-in-place-update.txt
sleep 2
adb shell pidof com.videoflow.app.review | tee -a step4-emulator-reports/review-in-place-update.txt
