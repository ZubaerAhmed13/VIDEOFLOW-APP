#!/usr/bin/env bash
set -e
mkdir -p step4-emulator-reports
adb install -r step4-runtime/VideoFlow_Step5_Debug.apk
adb install -r step4-runtime/VideoFlow_Step5_Debug-androidTest.apk

adb shell svc wifi disable
adb shell svc data disable
adb shell pm grant com.videoflow.app.debug android.permission.POST_NOTIFICATIONS
adb shell am instrument -w -r -e class com.videoflow.app.step5.Step5AiOutsideRoiTest,com.videoflow.app.step5.Step5RecoverySecurityTest,com.videoflow.app.step5.Step5QualityExportTest,com.videoflow.app.step5.Step5CompositionGeometryTest,com.videoflow.app.step5.Step5AudioVideoSyncTest,com.videoflow.app.step5.Step5CheckpointOverlayTest,com.videoflow.app.step5.Step5ProductIntegrationTest com.videoflow.app.debug.test/androidx.test.runner.AndroidJUnitRunner > step4-emulator-reports/step5-integration.txt 2>&1
cat step4-emulator-reports/step5-integration.txt
adb pull /sdcard/Android/data/com.videoflow.app.debug/files/step5-evidence step4-emulator-reports/ || true
adb pull /sdcard/Android/data/com.videoflow.app.debug/files/professional-screenshots step4-emulator-reports/ || true
adb logcat -d -v threadtime > step4-emulator-reports/step5-logcat.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/step5-integration.txt || exit 1
grep -E -q 'OK \(13 tests\)' step4-emulator-reports/step5-integration.txt
grep -q 'STEP5_OVERLAY_CHECKPOINT_CERTIFIED' step4-emulator-reports/step5-integration.txt
grep -q 'STEP5_PRODUCT_INTEGRATION_CERTIFIED' step4-emulator-reports/step5-integration.txt
for configuration in portrait landscape tablet; do
  if [ "$configuration" = landscape ]; then adb shell wm size 1280x720; adb shell wm density 240; fi
  if [ "$configuration" = tablet ]; then adb shell wm size 1920x1200; adb shell wm density 160; fi
  adb shell settings put system font_scale 1.3
  adb shell am instrument -w -r -e class com.videoflow.app.step5.Step5TimelineAccessibilityTest com.videoflow.app.debug.test/androidx.test.runner.AndroidJUnitRunner > "step4-emulator-reports/step5-layout-$configuration.txt" 2>&1
  cat "step4-emulator-reports/step5-layout-$configuration.txt"
  ! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' "step4-emulator-reports/step5-layout-$configuration.txt" || exit 1
  grep -E -q 'OK \(1 test\)' "step4-emulator-reports/step5-layout-$configuration.txt"
done
adb shell wm size reset
adb shell wm density reset
adb shell settings put system font_scale 1.0
adb shell am instrument -w -r -e class 'com.videoflow.app.step5.Step5ProcessDeathTest#startRealForegroundAiJob' com.videoflow.app.debug.test/androidx.test.runner.AndroidJUnitRunner > step4-emulator-reports/step5-process-start.txt 2>&1
cat step4-emulator-reports/step5-process-start.txt
grep -E -q 'OK \(1 test\)' step4-emulator-reports/step5-process-start.txt
adb shell am force-stop com.videoflow.app.debug
adb shell am instrument -w -r -e class 'com.videoflow.app.step5.Step5ProcessDeathTest#restartRecognizesInterruptedJobAndPreservesEditableProject' com.videoflow.app.debug.test/androidx.test.runner.AndroidJUnitRunner > step4-emulator-reports/step5-process-recovery.txt 2>&1
cat step4-emulator-reports/step5-process-recovery.txt
grep -E -q 'OK \(1 test\)' step4-emulator-reports/step5-process-recovery.txt
adb pull /sdcard/Android/data/com.videoflow.app.debug/files/step5-evidence step4-emulator-reports/
test -s step4-emulator-reports/step5-evidence/quality.jsonl
test -s step4-emulator-reports/step5-evidence/av-sync.jsonl

adb shell am instrument -w -r -e class com.videoflow.app.ui.ProfessionalProductWorkflowTest com.videoflow.app.debug.test/androidx.test.runner.AndroidJUnitRunner > step4-emulator-reports/product-panels.txt 2>&1
adb pull /sdcard/Android/data/com.videoflow.app.debug/files/professional-screenshots step4-emulator-reports/ || true
adb logcat -d -v threadtime > step4-emulator-reports/player-logcat.txt
cat step4-emulator-reports/product-panels.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/product-panels.txt || exit 1
grep -E -q 'OK \(1 test\)' step4-emulator-reports/product-panels.txt
grep -q 'PROFESSIONAL_PRODUCT_PANELS_CERTIFIED' step4-emulator-reports/product-panels.txt
test -s step4-emulator-reports/professional-screenshots/ai-preview.png

adb shell am instrument -w -r -e class com.videoflow.app.ai.Step4AiRuntimeInstrumentedTest com.videoflow.app.debug.test/androidx.test.runner.AndroidJUnitRunner > step4-emulator-reports/ai-runtime.txt 2>&1
cat step4-emulator-reports/ai-runtime.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/ai-runtime.txt || exit 1
grep -E -q 'OK \([0-9]+ tests?\)' step4-emulator-reports/ai-runtime.txt

adb shell am instrument -w -r -e class com.videoflow.app.ai.Step4AiFinalExportInstrumentedTest com.videoflow.app.debug.test/androidx.test.runner.AndroidJUnitRunner > step4-emulator-reports/final-ai-export.txt 2>&1
cat step4-emulator-reports/final-ai-export.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/final-ai-export.txt || exit 1
grep -E -q 'OK \(1 test\)' step4-emulator-reports/final-ai-export.txt
grep -q 'FINAL_AI_EXPORT_CERTIFIED' step4-emulator-reports/final-ai-export.txt

adb shell am instrument -w -r -e class com.videoflow.app.ui.HomeComposeTest,com.videoflow.app.ui.EditorWorkspaceVisualCertificationTest,com.videoflow.app.ui.LongTimelineWorkspaceSmokeTest,com.videoflow.app.ui.ContextualToolbarComposeTest com.videoflow.app.debug.test/androidx.test.runner.AndroidJUnitRunner > step4-emulator-reports/editor-regression.txt 2>&1
cat step4-emulator-reports/editor-regression.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/editor-regression.txt || exit 1
grep -E -q 'OK \([0-9]+ tests?\)' step4-emulator-reports/editor-regression.txt

adb shell am instrument -w -r -e class com.videoflow.app.editor.ProfessionalAudioPersistenceTest,com.videoflow.app.ui.ProfessionalToolControlsTest,com.videoflow.app.ai.ProfessionalCombinedExportInstrumentedTest,com.videoflow.app.ai.ProfessionalCheckpointExportInstrumentedTest com.videoflow.app.debug.test/androidx.test.runner.AndroidJUnitRunner > step4-emulator-reports/professional-upgrade.txt 2>&1
adb pull /sdcard/Android/data/com.videoflow.app.debug/files/professional-screenshots step4-emulator-reports/ || true
cat step4-emulator-reports/professional-upgrade.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/professional-upgrade.txt || exit 1
grep -E -q 'OK \(5 tests\)' step4-emulator-reports/professional-upgrade.txt
grep -q 'PROFESSIONAL_COMBINED_EXPORT_CERTIFIED' step4-emulator-reports/professional-upgrade.txt
grep -q 'PROFESSIONAL_CHECKPOINT_EXPORT_CERTIFIED' step4-emulator-reports/professional-upgrade.txt

adb shell am instrument -w -r -e class com.videoflow.app.db.RoomPersistenceTest,com.videoflow.app.db.Step2MigrationTest,com.videoflow.app.editor.Step2ComplexPersistenceTest,com.videoflow.app.editor.ProxyManagerInstrumentationTest,com.videoflow.app.media.MediaAnalyzerInstrumentationTest,com.videoflow.app.media.PlayerInstrumentationTest,com.videoflow.app.export.NativeRenderEngineInstrumentedTest,com.videoflow.app.export.SafMediaMuxerFactoryInstrumentedTest com.videoflow.app.debug.test/androidx.test.runner.AndroidJUnitRunner > step4-emulator-reports/retained-product-regression.txt 2>&1
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
