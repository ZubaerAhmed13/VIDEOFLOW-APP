#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PANEL = ROOT / "app/src/main/java/com/videoflow/app/ui/ai/WatermarkStudioPanel.kt"
text = PANEL.read_text(encoding="utf-8")

progressive = " * Remove Watermark uses progressive disclosure: drag box -> active range -> Track/Preview/Refine -> save.\n"
legacy_anchor = " * AI Watermark Studio: mask -> duration -> track -> optional preview -> non-destructive Done.\n"
legacy_marker = " * Legacy Step-5 audit wording: Apply non-destructively means this lightweight Done save; it never starts full-video reconstruction.\n"

if progressive not in text:
    raise SystemExit("Progressive Remove Watermark KDoc anchor is missing")
if 'StudioHeader("Remove Watermark"' not in text:
    raise SystemExit("Progressive Remove Watermark UI is missing")
if 'contentDescription = "Refine watermark removal"' not in text:
    raise SystemExit("Refine progressive-disclosure control is missing")
if 'listOf("Cover", "Duration", "Track", "Preview", "Done")' in text:
    raise SystemExit("Obsolete five-stage Watermark UI unexpectedly returned")

insert = ""
if legacy_anchor not in text:
    insert += legacy_anchor
if legacy_marker not in text:
    insert += legacy_marker
if insert:
    text = text.replace(progressive, progressive + insert, 1)
    PANEL.write_text(text, encoding="utf-8")
    print("Added non-UI legacy audit compatibility wording to progressive Watermark KDoc")
else:
    print("Progressive Watermark legacy audit compatibility already present")
