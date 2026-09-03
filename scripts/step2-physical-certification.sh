#!/usr/bin/env bash
set -euo pipefail

APP_ID="${VIDEOFLOW_APP_ID:-com.videoflow.app.debug}"
SESSION_FILE="${VIDEOFLOW_SESSION_FILE:-.step2-physical-session}"
EVIDENCE_ROOT="${VIDEOFLOW_EVIDENCE_ROOT:-step2-physical-evidence}"

usage() {
  cat <<'EOF'
VideoFlow Android Step 2 physical certification evidence helper

Usage:
  bash scripts/step2-physical-certification.sh start [evidence-dir]
  bash scripts/step2-physical-certification.sh checkpoint <label>
  bash scripts/step2-physical-certification.sh force-stop-reopen
  bash scripts/step2-physical-certification.sh finalize
  bash scripts/step2-physical-certification.sh status

Environment:
  VIDEOFLOW_APP_ID         package to inspect (default: com.videoflow.app.debug)
  VIDEOFLOW_EVIDENCE_ROOT  default parent evidence directory
  VIDEOFLOW_SESSION_FILE   local file used to remember the active evidence directory

This helper captures objective device/package/memory/storage/log evidence. It does not
mark manual editor actions PASS. Record those observations in MANUAL_RESULTS.md.
EOF
}

require_adb() {
  command -v adb >/dev/null 2>&1 || {
    echo "ERROR: adb was not found. Install Android platform-tools and retry." >&2
    exit 1
  }

  adb start-server >/dev/null
  local count
  count="$(adb devices | awk '$2 == "device" {c++} END {print c+0}')"
  if [ "$count" -ne 1 ]; then
    echo "ERROR: exactly one authorized Android device must be connected; found $count." >&2
    adb devices -l >&2 || true
    exit 1
  fi

  adb shell pm path "$APP_ID" >/dev/null 2>&1 || {
    echo "ERROR: package $APP_ID is not installed on the connected device." >&2
    echo "Install the exact Step 2 Debug APK first, or set VIDEOFLOW_APP_ID." >&2
    exit 1
  }
}

sanitize_label() {
  printf '%s' "$1" | tr '[:space:]/:' '---' | tr -cd '[:alnum:]_.-'
}

active_session() {
  if [ ! -s "$SESSION_FILE" ]; then
    echo "ERROR: no active Step 2 physical session. Run 'start' first." >&2
    exit 1
  fi
  cat "$SESSION_FILE"
}

safe_capture() {
  local output="$1"
  shift
  if "$@" >"$output" 2>&1; then
    return 0
  fi
  {
    echo
    echo "[command unavailable or failed; evidence retained rather than hidden]"
  } >>"$output"
  return 0
}

capture_installed_apk_identity() {
  local dir="$1"
  local remote_apk
  remote_apk="$(adb shell pm path "$APP_ID" | sed -n 's/^package://p' | head -n 1 | tr -d '\r')"
  printf 'Package: %s\nAPK path: %s\n' "$APP_ID" "$remote_apk" >"$dir/installed-package.txt"
  adb shell dumpsys package "$APP_ID" >>"$dir/installed-package.txt" 2>&1 || true

  if [ -n "$remote_apk" ]; then
    local temp_apk="$dir/.installed-base.apk"
    if adb pull "$remote_apk" "$temp_apk" >/dev/null 2>&1; then
      sha256sum "$temp_apk" | sed 's#  .*/#  #' >"$dir/INSTALLED_APK_SHA256.txt"
      rm -f "$temp_apk"
    else
      echo "Installed APK pull was not permitted by this device; SHA-256 not captured." >"$dir/INSTALLED_APK_SHA256.txt"
    fi
  fi
}

capture_app_private_storage() {
  local dir="$1"
  {
    echo "Package: $APP_ID"
    echo "Captured: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo
    echo "== app-private total (KiB) =="
    adb shell run-as "$APP_ID" sh -c 'du -sk . 2>/dev/null || true' 2>&1 || true
    echo
    echo "== files directory =="
    adb shell run-as "$APP_ID" sh -c 'ls -lah files 2>/dev/null || true' 2>&1 || true
    echo
    echo "== files/proxies directory =="
    adb shell run-as "$APP_ID" sh -c 'ls -lah files/proxies 2>/dev/null || true' 2>&1 || true
    echo
    echo "== cache directory =="
    adb shell run-as "$APP_ID" sh -c 'du -sk cache 2>/dev/null || true; ls -lah cache 2>/dev/null || true' 2>&1 || true
  } >"$dir/app-private-storage.txt"
}

capture_snapshot() {
  local session="$1"
  local raw_label="$2"
  local label
  label="$(sanitize_label "$raw_label")"
  [ -n "$label" ] || label="checkpoint"
  local dir="$session/checkpoints/$label"
  mkdir -p "$dir"

  adb devices -l >"$dir/adb-devices.txt"
  {
    echo "Captured: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "Serial: $(adb get-serialno 2>/dev/null | tr -d '\r')"
    echo "Manufacturer: $(adb shell getprop ro.product.manufacturer | tr -d '\r')"
    echo "Model: $(adb shell getprop ro.product.model | tr -d '\r')"
    echo "Device: $(adb shell getprop ro.product.device | tr -d '\r')"
    echo "Android: $(adb shell getprop ro.build.version.release | tr -d '\r')"
    echo "API: $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
    echo "ABI: $(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
    echo "Build fingerprint: $(adb shell getprop ro.build.fingerprint | tr -d '\r')"
  } >"$dir/device.txt"

  safe_capture "$dir/meminfo-system.txt" adb shell cat /proc/meminfo
  safe_capture "$dir/meminfo-app.txt" adb shell dumpsys meminfo "$APP_ID"
  safe_capture "$dir/storage.txt" adb shell df -k /data /sdcard
  safe_capture "$dir/battery.txt" adb shell dumpsys battery
  safe_capture "$dir/thermal.txt" adb shell dumpsys thermalservice
  safe_capture "$dir/activity-process.txt" adb shell dumpsys activity processes
  capture_installed_apk_identity "$dir"
  capture_app_private_storage "$dir"

  echo "Captured checkpoint '$label' in $dir"
}

write_manual_template() {
  local session="$1"
  cat >"$session/MANUAL_RESULTS.md" <<'EOF'
# VideoFlow Android — Step 2 Physical Manual Results

Replace every required `NOT VERIFIED` value with what was actually observed. Do not mark a row PASS from code inspection alone.

## Build/device/media

- Git commit SHA: NOT VERIFIED
- GitHub Actions Step 2 run ID: NOT VERIFIED
- Installed APK SHA-256: NOT VERIFIED
- Device: NOT VERIFIED
- Android API: NOT VERIFIED
- RAM: NOT VERIFIED
- Source size: NOT VERIFIED
- Source codec: NOT VERIFIED
- Source resolution: NOT VERIFIED
- Real image: NOT VERIFIED
- Real audio clip: NOT VERIFIED
- Proxy mode: NOT VERIFIED
- Proxy resolution: NOT VERIFIED
- Proxy file size: NOT VERIFIED

## Required workflow

- Import: NOT VERIFIED
- Generate Proxy: NOT VERIFIED
- Add clip: NOT VERIFIED
- Move: NOT VERIFIED
- Trim: NOT VERIFIED
- Split: NOT VERIFIED
- Duplicate: NOT VERIFIED
- Delete: NOT VERIFIED
- Add text: NOT VERIFIED
- Add image: NOT VERIFIED
- Adjust scale: NOT VERIFIED
- Adjust rotation: NOT VERIFIED
- Adjust opacity: NOT VERIFIED
- Add audio: NOT VERIFIED
- Adjust gain: NOT VERIFIED
- Add fade: NOT VERIFIED
- Add two keyframes: NOT VERIFIED
- Preview animation: NOT VERIFIED
- Timeline playback/scrub: NOT VERIFIED
- Undo: NOT VERIFIED
- Redo: NOT VERIFIED
- Save snapshot: NOT VERIFIED
- Force-stop restore: NOT VERIFIED
- Edit after reopen/autosave: NOT VERIFIED
- Proxy persisted after reopen: NOT VERIFIED
- Project/tracks/clips/overlays/audio/keyframes restored: NOT VERIFIED
- No crash/ANR: NOT VERIFIED
- Memory remained bounded: NOT VERIFIED
- Storage behavior acceptable: NOT VERIFIED

## Notes

Add concise observations, including any lag, warnings, thermal throttling, proxy duration, unexpected storage increase, or recovery behavior.

## Final decision

STEP 2 PHYSICAL ACCEPTANCE: NOT VERIFIED
EOF
}

start_session() {
  require_adb
  local requested="${1:-}"
  local session
  if [ -n "$requested" ]; then
    session="$requested"
  else
    session="$EVIDENCE_ROOT/$(date -u +%Y%m%dT%H%M%SZ)"
  fi
  mkdir -p "$session/checkpoints"
  printf '%s\n' "$session" >"$SESSION_FILE"

  {
    echo "VideoFlow Android Step 2 physical certification evidence"
    echo "Started UTC: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "Package: $APP_ID"
    echo "Host: $(uname -a 2>/dev/null || true)"
    echo "ADB: $(adb version 2>/dev/null | head -n 1 || true)"
  } >"$session/SESSION.txt"

  write_manual_template "$session"
  adb logcat -c >/dev/null 2>&1 || true
  capture_snapshot "$session" baseline

  cat <<EOF
Step 2 physical evidence session started:
  $session

Now perform the required editor workflow from STEP_2_PHYSICAL_DEVICE_CERTIFICATION.md.
Capture checkpoints after meaningful phases, especially before and after proxy generation.
EOF
}

checkpoint() {
  require_adb
  local label="${1:-}"
  [ -n "$label" ] || {
    echo "ERROR: checkpoint requires a label." >&2
    exit 1
  }
  capture_snapshot "$(active_session)" "$label"
}

force_stop_reopen() {
  require_adb
  local session
  session="$(active_session)"
  capture_snapshot "$session" before-force-stop
  adb shell am force-stop "$APP_ID"
  sleep 2
  capture_snapshot "$session" after-force-stop
  adb shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || {
    echo "ERROR: package could not be relaunched via launcher intent." >&2
    exit 1
  }
  sleep 4
  capture_snapshot "$session" after-reopen
  echo "Force-stop/reopen captured. Verify restored editor state manually before continuing."
}

finalize_session() {
  require_adb
  local session
  session="$(active_session)"
  capture_snapshot "$session" final
  adb logcat -d -v threadtime >"$session/logcat.txt" 2>&1 || true

  if grep -E -i 'FATAL EXCEPTION|ANR in com\.videoflow\.app|Process: com\.videoflow\.app.*has died|INSTRUMENTATION_FAILED' "$session/logcat.txt" >"$session/CRASH_ANR_SCAN.txt"; then
    echo "Potential crash/ANR signatures were found. Review CRASH_ANR_SCAN.txt; physical acceptance cannot be PASS until explained/resolved." | tee "$session/FINALIZE_STATUS.txt"
  else
    echo "No matching VideoFlow crash/ANR signature found in the captured logcat window. Manual workflow results are still required." | tee "$session/FINALIZE_STATUS.txt"
    : >"$session/CRASH_ANR_SCAN.txt"
  fi

  {
    echo "Finalized UTC: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "Package: $APP_ID"
    echo "Evidence directory: $session"
    echo "Manual results: $session/MANUAL_RESULTS.md"
    echo "Required protocol: STEP_2_PHYSICAL_DEVICE_CERTIFICATION.md"
  } >"$session/EVIDENCE_INDEX.txt"

  echo
  echo "Evidence capture finalized: $session"
  echo "Complete MANUAL_RESULTS.md from observed behavior. Do not declare COMPLETE while required rows remain NOT VERIFIED/PARTIAL/FAIL."
}

show_status() {
  local session
  session="$(active_session)"
  echo "Active session: $session"
  [ -f "$session/SESSION.txt" ] && cat "$session/SESSION.txt"
  echo
  echo "Checkpoints:"
  find "$session/checkpoints" -mindepth 1 -maxdepth 1 -type d -printf '  %f\n' 2>/dev/null | sort || true
  echo
  [ -f "$session/FINALIZE_STATUS.txt" ] && cat "$session/FINALIZE_STATUS.txt" || echo "Not finalized."
}

command="${1:-}"
case "$command" in
  start)
    shift
    start_session "${1:-}"
    ;;
  checkpoint)
    shift
    checkpoint "${1:-}"
    ;;
  force-stop-reopen)
    force_stop_reopen
    ;;
  finalize)
    finalize_session
    ;;
  status)
    show_status
    ;;
  -h|--help|help|"")
    usage
    ;;
  *)
    echo "ERROR: unknown command '$command'." >&2
    usage >&2
    exit 2
    ;;
esac
