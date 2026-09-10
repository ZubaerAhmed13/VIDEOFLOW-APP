#!/usr/bin/env bash
set -euo pipefail

PACKAGE_ID='com.videoflow.app.debug'
TEST_RUNNER="$PACKAGE_ID.test/androidx.test.runner.AndroidJUnitRunner"
REPORT_DIR='ux-step2-emulator-reports'
EVIDENCE_DIR='ux-step2-evidence'

mkdir -p "$REPORT_DIR" "$EVIDENCE_DIR"

adb install -r ux-step2-runtime/VideoFlow_UXStep2_Debug.apk
adb install -r ux-step2-runtime/VideoFlow_UXStep2_Debug-androidTest.apk

run_suite() {
  local name="$1"
  local classes="$2"
  local out="$REPORT_DIR/${name}.txt"
  local rc=0

  # Capture the complete instrumentation transcript even when am instrument exits non-zero.
  adb shell am instrument -w -r -e class "$classes" "$TEST_RUNNER" > "$out" 2>&1 || rc=$?
  cat "$out"

  if (( rc != 0 )); then
    echo "Instrumentation suite '$name' exited with code $rc" >&2
    return "$rc"
  fi
  if grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' "$out"; then
    echo "Instrumentation suite '$name' reported a failure/crash marker" >&2
    return 1
  fi
  if ! grep -E -q 'OK \([0-9]+ tests?\)' "$out"; then
    echo "Instrumentation suite '$name' is missing its JUnit success marker" >&2
    return 1
  fi
  if ! grep -q 'INSTRUMENTATION_CODE: -1' "$out"; then
    echo "Instrumentation suite '$name' is missing Android instrumentation success code" >&2
    return 1
  fi
}

run_suite step1-shell-regression 'com.videoflow.app.ui.UXStep1EditorShellComposeTest,com.videoflow.app.ui.UXStep1FontScaleComposeTest,com.videoflow.app.ui.EditorWorkspaceVisualCertificationTest,com.videoflow.app.ui.ContextualToolbarComposeTest'
run_suite step2-trim-speed-crop-ui 'com.videoflow.app.ui.editor.UXStep2TrimSpeedCropProductComposeTest,com.videoflow.app.ui.ProfessionalToolControlsTest,com.videoflow.app.ui.TrimOpenGeometryComposeTest'
run_suite step2-room-history-persistence 'com.videoflow.app.db.RoomPersistenceTest,com.videoflow.app.db.Step2MigrationTest,com.videoflow.app.editor.Step2ComplexPersistenceTest'
run_suite step2-speed-av-sync 'com.videoflow.app.step5.Step5AudioVideoSyncTest#decodedFlashAndToneStayAlignedAfterTrimAndSpeedAndVisualProcessing'
run_suite step2-crop-real-render 'com.videoflow.app.step5.UXStep2CropRenderGeometryTest,com.videoflow.app.step5.Step5CompositionGeometryTest'
run_suite retained-player-proxy-render 'com.videoflow.app.media.MediaAnalyzerInstrumentationTest,com.videoflow.app.media.PlayerInstrumentationTest,com.videoflow.app.editor.ProxyManagerInstrumentationTest,com.videoflow.app.export.NativeRenderEngineInstrumentedTest,com.videoflow.app.export.SafMediaMuxerFactoryInstrumentedTest'

adb pull "/sdcard/Android/data/$PACKAGE_ID/files/ux-step2-evidence" "$EVIDENCE_DIR/" || true
adb pull "/sdcard/Android/data/$PACKAGE_ID/files/step5-evidence" "$EVIDENCE_DIR/" || true
adb logcat -d -v threadtime > "$REPORT_DIR/logcat.txt"

for required in \
  trim-normal.png \
  trim-precise.png \
  speed-1x.png \
  speed-2x.png \
  crop-free.png \
  crop-1x1.png \
  crop-9x16.png \
  crop-rotated-portrait.png \
  trim-compact-360x800.png \
  crop-compact-360x800.png \
  landscape-focused-crop.png; do
  if ! find "$EVIDENCE_DIR" -name "$required" -type f -size +0c -print -quit | grep -q .; then
    echo "Missing required Step 2 screenshot evidence: $required" >&2
    exit 1
  fi
done

find "$EVIDENCE_DIR" -maxdepth 5 -type f -print | sort > "$REPORT_DIR/evidence-files.txt"
grep -q 'step2-crop-geometry.jsonl' "$REPORT_DIR/evidence-files.txt"
grep -q 'av-sync.jsonl' "$REPORT_DIR/evidence-files.txt"

echo 'API 35 Step 1 regression and Step 2 product/render certification passed.'
