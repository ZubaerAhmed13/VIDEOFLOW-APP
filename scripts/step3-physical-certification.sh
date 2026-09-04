#!/usr/bin/env bash
set -euo pipefail

# VideoFlow Android Step 3 physical-device evidence helper.
# This script gathers repeatable device/app diagnostics around a manually executed
# physical export. It does not mark any certification gate PASS by itself.

APK_PATH="${1:-}"
REPORT_DIR="${2:-step3-physical-evidence}"
PKG="com.videoflow.app.debug"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required." >&2
  exit 2
fi

if [ -n "$APK_PATH" ] && [ ! -f "$APK_PATH" ]; then
  echo "APK not found: $APK_PATH" >&2
  exit 2
fi

mkdir -p "$REPORT_DIR"

adb start-server >/dev/null
DEVICE_COUNT=$(adb devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')
if [ "$DEVICE_COUNT" -ne 1 ]; then
  echo "Connect exactly one authorized physical Android device; found $DEVICE_COUNT." >&2
  exit 3
fi

if [ -n "$APK_PATH" ]; then
  sha256sum "$APK_PATH" | tee "$REPORT_DIR/apk-sha256.txt"
  adb install -r "$APK_PATH" | tee "$REPORT_DIR/install.txt"
fi

{
  echo "UTC captured: $(date -u +%FT%TZ)"
  echo "Serial: $(adb get-serialno)"
  echo "Manufacturer: $(adb shell getprop ro.product.manufacturer | tr -d '\r')"
  echo "Model: $(adb shell getprop ro.product.model | tr -d '\r')"
  echo "Android: $(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "API: $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "ABI: $(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
} | tee "$REPORT_DIR/device.txt"

adb shell cat /proc/meminfo > "$REPORT_DIR/meminfo-before.txt"
adb shell df -k > "$REPORT_DIR/storage-before.txt"
adb shell dumpsys media.codec > "$REPORT_DIR/media-codec-before.txt" 2>/dev/null || true
adb shell dumpsys battery > "$REPORT_DIR/battery-before.txt" 2>/dev/null || true
adb shell dumpsys thermalservice > "$REPORT_DIR/thermal-before.txt" 2>/dev/null || true
adb shell dumpsys package "$PKG" > "$REPORT_DIR/package.txt" 2>/dev/null || true

cat <<'EOF'

Device evidence captured.

Now perform the Step 3 physical scenarios from STEP_3_PHYSICAL_DEVICE_CERTIFICATION.md using the exact certified APK:
  1. real 1080p H.264 High export
  2. real 4K30 export
  3. leave app + screen off during export
  4. cancel a real export and inspect cleanup
  5. render from a real multi-GB original source
  6. inspect colour/range/gradient/detail
  7. inspect audio gain/fade/distortion and A/V sync

When the export test finishes, press Enter here to capture post-test diagnostics.
EOF
read -r

adb shell cat /proc/meminfo > "$REPORT_DIR/meminfo-after.txt"
adb shell df -k > "$REPORT_DIR/storage-after.txt"
adb shell dumpsys meminfo "$PKG" > "$REPORT_DIR/app-meminfo-after.txt" 2>/dev/null || true
adb shell dumpsys battery > "$REPORT_DIR/battery-after.txt" 2>/dev/null || true
adb shell dumpsys thermalservice > "$REPORT_DIR/thermal-after.txt" 2>/dev/null || true
adb logcat -d -v threadtime > "$REPORT_DIR/logcat.txt" 2>/dev/null || true

cat > "$REPORT_DIR/README.txt" <<'EOF'
This directory is evidence only. Copy measured source/output metadata and observed results into STEP_3_PHYSICAL_DEVICE_CERTIFICATION.md. Do not mark a gate PASS unless the exact scenario was actually executed and verified.
EOF

echo "Post-test diagnostics captured in $REPORT_DIR"
