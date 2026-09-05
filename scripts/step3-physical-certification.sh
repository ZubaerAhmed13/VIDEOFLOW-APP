#!/usr/bin/env bash
set -euo pipefail

# VideoFlow Android Step 3 physical-device evidence helper.
#
# This script intentionally does NOT mark Step 3 COMPLETE by itself. It captures
# repeatable evidence from one authorized REAL Android device and guides the
# mandatory physical scenarios. Objective exported-media metadata can then be
# verified with scripts/step3_verify_media.py.

APK_PATH="${1:-}"
REPORT_DIR="${2:-step3-physical-evidence-$(date -u +%Y%m%dT%H%M%SZ)}"
PKG="${VIDEOFLOW_PACKAGE:-com.videoflow.app.debug}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "$1 is required." >&2
    exit 2
  fi
}

hash_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1"
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1"
  else
    echo "A SHA-256 tool is required (sha256sum or shasum)." >&2
    exit 2
  fi
}

require_cmd adb

if [ -n "$APK_PATH" ] && [ ! -f "$APK_PATH" ]; then
  echo "APK not found: $APK_PATH" >&2
  exit 2
fi

mkdir -p "$REPORT_DIR/checkpoints"
OBS="$REPORT_DIR/scenario-observations.tsv"
printf 'scenario\tresult\tnotes\n' > "$OBS"

adb start-server >/dev/null
DEVICE_COUNT=$(adb devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')
if [ "$DEVICE_COUNT" -ne 1 ]; then
  echo "Connect exactly one authorized Android device; found $DEVICE_COUNT." >&2
  exit 3
fi

QEMU=$(adb shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')
HARDWARE=$(adb shell getprop ro.hardware 2>/dev/null | tr -d '\r')
if [ "$QEMU" = "1" ] || printf '%s' "$HARDWARE" | grep -Eqi 'ranchu|goldfish'; then
  echo "Physical certification refuses an emulator. Connect a real Android phone/tablet." >&2
  exit 4
fi

if [ -n "$APK_PATH" ]; then
  hash_file "$APK_PATH" | tee "$REPORT_DIR/apk-sha256.txt"
  adb install -r "$APK_PATH" | tee "$REPORT_DIR/install.txt"
fi

{
  echo "UTC captured: $(date -u +%FT%TZ)"
  echo "Serial: $(adb get-serialno)"
  echo "Manufacturer: $(adb shell getprop ro.product.manufacturer | tr -d '\r')"
  echo "Model: $(adb shell getprop ro.product.model | tr -d '\r')"
  echo "Device: $(adb shell getprop ro.product.device | tr -d '\r')"
  echo "Build fingerprint: $(adb shell getprop ro.build.fingerprint | tr -d '\r')"
  echo "Android: $(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "API: $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "ABI: $(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "Hardware: $HARDWARE"
  echo "QEMU: ${QEMU:-0}"
  echo "Screen: $(adb shell wm size | tr -d '\r')"
} | tee "$REPORT_DIR/device.txt"

adb shell dumpsys package "$PKG" > "$REPORT_DIR/package.txt" 2>/dev/null || true
adb shell pm path "$PKG" > "$REPORT_DIR/installed-package-path.txt" 2>/dev/null || true
adb shell dumpsys media.codec > "$REPORT_DIR/media-codec.txt" 2>/dev/null || true
adb shell dumpsys SurfaceFlinger > "$REPORT_DIR/surfaceflinger.txt" 2>/dev/null || true
adb shell cat /proc/meminfo > "$REPORT_DIR/meminfo-baseline.txt" 2>/dev/null || true
adb shell df -k > "$REPORT_DIR/storage-baseline.txt" 2>/dev/null || true
adb shell dumpsys battery > "$REPORT_DIR/battery-baseline.txt" 2>/dev/null || true
adb shell dumpsys thermalservice > "$REPORT_DIR/thermal-baseline.txt" 2>/dev/null || true
adb logcat -c >/dev/null 2>&1 || true

capture_checkpoint() {
  local name="$1"
  local dir="$REPORT_DIR/checkpoints/$name"
  mkdir -p "$dir"
  date -u +%FT%TZ > "$dir/utc.txt"
  adb shell cat /proc/meminfo > "$dir/meminfo.txt" 2>/dev/null || true
  adb shell df -k > "$dir/storage.txt" 2>/dev/null || true
  adb shell dumpsys meminfo "$PKG" > "$dir/app-meminfo.txt" 2>/dev/null || true
  adb shell dumpsys battery > "$dir/battery.txt" 2>/dev/null || true
  adb shell dumpsys thermalservice > "$dir/thermal.txt" 2>/dev/null || true
  adb shell dumpsys activity services "$PKG" > "$dir/services.txt" 2>/dev/null || true
  adb shell dumpsys notification > "$dir/notifications.txt" 2>/dev/null || true
  adb shell dumpsys power > "$dir/power.txt" 2>/dev/null || true
  adb shell dumpsys window > "$dir/window.txt" 2>/dev/null || true
  adb exec-out screencap -p > "$dir/screen.png" 2>/dev/null || true
}

record_scenario() {
  local id="$1"
  local title="$2"
  local allow_na="$3"
  echo
  echo "================================================================"
  echo "$id — $title"
  echo "================================================================"
  echo "Perform this scenario with the exact certified APK and real source media."
  echo "When finished, enter PASS, FAIL${allow_na:+, or NA} and a short evidence note."
  local result
  while true; do
    read -r -p "Result: " result
    result=$(printf '%s' "$result" | tr '[:lower:]' '[:upper:]')
    if [ "$result" = "PASS" ] || [ "$result" = "FAIL" ] || { [ -n "$allow_na" ] && [ "$result" = "NA" ]; }; then
      break
    fi
    echo "Enter PASS or FAIL${allow_na:+ or NA}."
  done
  local notes
  read -r -p "Evidence/notes: " notes
  printf '%s\t%s\t%s\n' "$id" "$result" "${notes//$'\t'/ }" >> "$OBS"
  capture_checkpoint "$id"
}

cat <<EOF

Real-device baseline captured in:
  $REPORT_DIR

Use the exact Step 3 certified APK. The script records diagnostics and your explicit
observations, but PASS must reflect a scenario you actually performed and verified.
For exported MP4 files, copy them to this computer and run scripts/step3_verify_media.py
for objective codec/resolution/FPS/duration/colour/audio metadata.
EOF

record_scenario "A_1080P_H264" "Real 1920x1080 H.264 High export: file opens, correct FPS/duration, overlays/transforms/crop/keyframes, audio" ""
record_scenario "B_4K30" "Mandatory real 3840x2160 export at 30 fps or supported project/source cadence; inspect detail, text, gradients and motion" ""
record_scenario "C_1080P60" "1080p60 export where device encoder supports it" "yes"
record_scenario "D_4K60" "4K60 export where device encoder supports it" "yes"
record_scenario "E_HEVC" "HEVC export where device encoder supports requested resolution/FPS" "yes"

echo
read -r -p "For Test F, start a sufficiently long real export now, then press Enter. "
capture_checkpoint "F_BACKGROUND_before_screen_off"
echo "Turning the physical device screen off through adb for 60 seconds."
adb shell input keyevent 26 >/dev/null 2>&1 || true
sleep 60
capture_checkpoint "F_BACKGROUND_screen_off_60s"
adb shell input keyevent 26 >/dev/null 2>&1 || true
echo "Screen toggled back on. Unlock if necessary, return to VideoFlow and verify the export continued and ultimately validates."
record_scenario "F_BACKGROUND_SCREEN_OFF" "Foreground export continues while app is backgrounded and screen is off; notification/progress remains; final output validates" ""

record_scenario "G_CANCELLATION" "Cancel a real export: job becomes CANCELLED, encoder stops, no fake completed output, partial destination is deleted or truncated" ""
record_scenario "H_MULTI_GB" "Render from a real multi-GB original (prefer >=3 GB): no artificial rejection, no source-sized app-private copy, bounded memory, valid output" ""
record_scenario "I_COLOUR_RANGE" "Known/reference content: no washed image, crushed blacks, tint, unexpected gamma/range change; record source/output colour metadata" ""
record_scenario "J_AUDIO_AV_SYNC" "Audio gain/fade correct, no unexpected distortion, and A/V sync is within approximately one output frame" ""
record_scenario "K_HDR_10BIT" "HDR/10-bit preservation on compatible real input/display/device/encoder only" "yes"

capture_checkpoint "FINAL"
adb logcat -d -v threadtime > "$REPORT_DIR/logcat.txt" 2>/dev/null || true

PASS_COUNT=$(awk -F '\t' 'NR>1 && $2=="PASS" {c++} END {print c+0}' "$OBS")
FAIL_COUNT=$(awk -F '\t' 'NR>1 && $2=="FAIL" {c++} END {print c+0}' "$OBS")
NA_COUNT=$(awk -F '\t' 'NR>1 && $2=="NA" {c++} END {print c+0}' "$OBS")

cat > "$REPORT_DIR/README.txt" <<EOF
VideoFlow Android Step 3 physical-device evidence

This evidence was captured from a real Android device. It is not an automatic declaration
of Step 3 completion. Objective MP4 metadata should be verified with:

  python3 scripts/step3_verify_media.py <output.mp4> [expected options]

Scenario summary:
  PASS: $PASS_COUNT
  FAIL: $FAIL_COUNT
  NA:   $NA_COUNT

Mandatory scenarios A, B, F, G, H, I and J must all have genuine PASS evidence.
C, D, E and K may be NA only when hardware/input capability genuinely does not apply.
Copy final measured values and observations into STEP_3_PHYSICAL_DEVICE_CERTIFICATION.md.
Do not mark Step 3 COMPLETE if any mandatory gate is FAIL or unverified.
EOF

{
  echo "# VideoFlow Android Step 3 — Captured Physical Evidence"
  echo
  echo "Generated UTC: $(date -u +%FT%TZ)"
  echo
  echo "## Device"
  echo
  sed 's/^/- /' "$REPORT_DIR/device.txt"
  echo
  echo "## Scenario observations"
  echo
  echo "| Scenario | Result | Notes |"
  echo "|---|---|---|"
  awk -F '\t' 'NR>1 {gsub(/\|/, "\\|", $3); printf "| %s | %s | %s |\n", $1, $2, $3}' "$OBS"
  echo
  echo "## Summary"
  echo
  echo "- PASS: $PASS_COUNT"
  echo "- FAIL: $FAIL_COUNT"
  echo "- NA: $NA_COUNT"
  echo
  echo "This generated report is evidence only. Final Step 3 approval still requires review of every mandatory gate and objective output metadata."
} > "$REPORT_DIR/PHYSICAL_EVIDENCE_SUMMARY.md"

echo
echo "Physical evidence capture finished: $REPORT_DIR"
echo "Review PHYSICAL_EVIDENCE_SUMMARY.md and verify exported media with step3_verify_media.py."
