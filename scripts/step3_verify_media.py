#!/usr/bin/env python3
"""VideoFlow Android Step 3 physical-output metadata verifier.

This helper verifies objective MP4 properties from a real exported file using ffprobe.
It does not replace required visual, audible, background, cancellation, or large-media
physical observations.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import shutil
import subprocess
import sys
from fractions import Fraction
from pathlib import Path


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser()
    p.add_argument("file", type=Path)
    p.add_argument("--label", default="VideoFlow Step 3 physical export")
    p.add_argument("--expected-width", type=int)
    p.add_argument("--expected-height", type=int)
    p.add_argument("--expected-fps-num", type=int)
    p.add_argument("--expected-fps-den", type=int, default=1)
    p.add_argument("--expected-codec", choices=["h264", "hevc"])
    p.add_argument("--expect-audio", choices=["yes", "no"])
    p.add_argument("--expected-duration-seconds", type=float)
    p.add_argument("--duration-tolerance-seconds", type=float, default=0.12)
    p.add_argument("--fps-tolerance", type=float, default=0.03)
    p.add_argument("--report-prefix", type=Path)
    return p.parse_args()


def rational(text: str | None) -> float | None:
    if not text or text in {"0/0", "N/A"}:
        return None
    try:
        return float(Fraction(text))
    except Exception:
        return None


def ffprobe(path: Path) -> dict:
    if shutil.which("ffprobe") is None:
        raise RuntimeError("ffprobe is required (install FFmpeg).")
    cmd = [
        "ffprobe", "-v", "error",
        "-show_streams", "-show_format",
        "-of", "json", str(path),
    ]
    result = subprocess.run(cmd, check=True, capture_output=True, text=True)
    return json.loads(result.stdout)


def main() -> int:
    args = parse_args()
    path = args.file.expanduser().resolve()
    if not path.is_file():
        print(f"ERROR: file not found: {path}", file=sys.stderr)
        return 2

    try:
        raw = ffprobe(path)
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2

    streams = raw.get("streams", [])
    video = next((s for s in streams if s.get("codec_type") == "video"), None)
    audio = next((s for s in streams if s.get("codec_type") == "audio"), None)
    fmt = raw.get("format", {})

    if video is None:
        print("ERROR: no video stream found", file=sys.stderr)
        return 2

    fps = rational(video.get("avg_frame_rate")) or rational(video.get("r_frame_rate"))
    duration = None
    for candidate in (video.get("duration"), fmt.get("duration")):
        try:
            if candidate not in (None, "N/A"):
                duration = float(candidate)
                break
        except (TypeError, ValueError):
            pass

    observed = {
        "label": args.label,
        "file": str(path),
        "size_bytes": path.stat().st_size,
        "container": fmt.get("format_name"),
        "duration_seconds": duration,
        "video": {
            "codec": video.get("codec_name"),
            "profile": video.get("profile"),
            "width": video.get("width"),
            "height": video.get("height"),
            "avg_frame_rate": video.get("avg_frame_rate"),
            "r_frame_rate": video.get("r_frame_rate"),
            "fps": fps,
            "pixel_format": video.get("pix_fmt"),
            "bit_rate": video.get("bit_rate"),
            "color_space": video.get("color_space"),
            "color_range": video.get("color_range"),
            "color_transfer": video.get("color_transfer"),
            "color_primaries": video.get("color_primaries"),
        },
        "audio": None if audio is None else {
            "codec": audio.get("codec_name"),
            "sample_rate": audio.get("sample_rate"),
            "channels": audio.get("channels"),
            "channel_layout": audio.get("channel_layout"),
            "bit_rate": audio.get("bit_rate"),
        },
    }

    checks: list[tuple[str, bool, str]] = []
    if args.expected_width is not None:
        checks.append(("width", video.get("width") == args.expected_width, f"expected {args.expected_width}, got {video.get('width')}"))
    if args.expected_height is not None:
        checks.append(("height", video.get("height") == args.expected_height, f"expected {args.expected_height}, got {video.get('height')}"))
    if args.expected_codec is not None:
        checks.append(("video codec", video.get("codec_name") == args.expected_codec, f"expected {args.expected_codec}, got {video.get('codec_name')}"))
    if args.expect_audio is not None:
        expected = args.expect_audio == "yes"
        checks.append(("audio presence", (audio is not None) == expected, f"expected audio={expected}, got audio={audio is not None}"))
    if args.expected_fps_num is not None:
        expected_fps = args.expected_fps_num / args.expected_fps_den
        ok = fps is not None and math.isfinite(fps) and abs(fps - expected_fps) <= args.fps_tolerance
        checks.append(("frame rate", ok, f"expected {expected_fps:.6f} ± {args.fps_tolerance:.6f}, got {fps}"))
    if args.expected_duration_seconds is not None:
        ok = duration is not None and abs(duration - args.expected_duration_seconds) <= args.duration_tolerance_seconds
        checks.append(("duration", ok, f"expected {args.expected_duration_seconds:.3f}s ± {args.duration_tolerance_seconds:.3f}s, got {duration}"))

    report = {
        "observed": observed,
        "checks": [{"name": name, "passed": passed, "detail": detail} for name, passed, detail in checks],
        "overall_objective_result": "PASS" if checks and all(p for _, p, _ in checks) else ("FAIL" if checks else "OBSERVED_ONLY"),
        "note": "Objective media metadata only; physical visual/audio/background/cancellation/large-media gates still require real-device evidence.",
    }

    print(json.dumps(report, indent=2, sort_keys=True))

    if args.report_prefix:
        prefix = args.report_prefix.expanduser()
        prefix.parent.mkdir(parents=True, exist_ok=True)
        prefix.with_suffix(".json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        md = [
            f"# {args.label}",
            "",
            f"Objective result: **{report['overall_objective_result']}**",
            "",
            f"- File: `{path}`",
            f"- Size: {observed['size_bytes']} bytes",
            f"- Video: {video.get('codec_name')} {video.get('width')}×{video.get('height')} @ {fps if fps is not None else 'unknown'} fps",
            f"- Pixel format: {video.get('pix_fmt')}",
            f"- Colour: primaries={video.get('color_primaries')} transfer={video.get('color_transfer')} space={video.get('color_space')} range={video.get('color_range')}",
            f"- Audio: {'none' if audio is None else str(audio.get('codec_name')) + ' / ' + str(audio.get('sample_rate')) + ' Hz / ' + str(audio.get('channels')) + ' ch'}",
            f"- Duration: {duration}",
            "",
            "## Checks",
            "",
        ]
        if checks:
            for name, passed, detail in checks:
                md.append(f"- {'PASS' if passed else 'FAIL'} — {name}: {detail}")
        else:
            md.append("- No expectations supplied; metadata captured only.")
        md += ["", report["note"], ""]
        prefix.with_suffix(".md").write_text("\n".join(md), encoding="utf-8")

    return 0 if not checks or all(p for _, p, _ in checks) else 1


if __name__ == "__main__":
    raise SystemExit(main())
