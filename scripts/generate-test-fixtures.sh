#!/usr/bin/env bash
set -euo pipefail
if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg is required to generate instrumentation fixtures." >&2
  exit 1
fi
for dir in app/src/androidTest/assets app/src/debug/assets; do
  mkdir -p "$dir"
  ffmpeg -hide_banner -loglevel error -y -f lavfi -i testsrc2=size=320x240:rate=30 -f lavfi -i sine=frequency=1000:sample_rate=48000 -t 3 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest "$dir/sample_av.mp4"
  ffmpeg -hide_banner -loglevel error -y -f lavfi -i testsrc2=size=320x240:rate=30000/1001 -t 3 -c:v libx264 -pix_fmt yuv420p -an "$dir/sample_video_only.mp4"
  ffmpeg -hide_banner -loglevel error -y -f lavfi -i testsrc2=size=240x320:rate=30 -t 3 -c:v libx264 -pix_fmt yuv420p -metadata:s:v:0 rotate=90 -an "$dir/sample_rotated.mp4"
  head -c 1024 /dev/urandom > "$dir/malformed.mp4"
done
