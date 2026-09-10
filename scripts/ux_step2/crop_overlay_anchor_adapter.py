#!/usr/bin/env python3
from pathlib import Path
import re
import sys

path = Path('app/src/main/java/com/videoflow/app/ui/editor/PreviewInteraction.kt')
text = path.read_text(encoding='utf-8')
mode = sys.argv[1] if len(sys.argv) > 1 else ''

if mode == 'prepare':
    marker = '''/* UX_STEP2_ANCHOR_SHIM_BEGIN\n    Box(\n        modifier = modifier\n            .clipToBounds()\n            .pointerInput(crop, aspectRatio) {\nUX_STEP2_ANCHOR_SHIM_END */\n'''
    if 'UX_STEP2_ANCHOR_SHIM_BEGIN' not in text:
        anchor = '    Canvas(\n        modifier = modifier\n'
        if anchor not in text:
            raise SystemExit('Canvas crop overlay anchor not found')
        text = text.replace(anchor, marker + anchor, 1)
elif mode == 'finish':
    text, count = re.subn(r'/\* UX_STEP2_ANCHOR_SHIM_BEGIN.*?UX_STEP2_ANCHOR_SHIM_END \*/\n', '', text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f'Expected one temporary anchor shim, removed {count}')
    old = '''            .fillMaxSize()\n            .pointerInput(Unit) {\n'''
    new = '''            .fillMaxSize()\n            .semantics { contentDescription = "Crop rectangle. Drag to move; drag edges or corners to resize." }\n            .pointerInput(Unit) {\n'''
    if old not in text:
        raise SystemExit('Production Canvas modifier anchor not found')
    text = text.replace(old, new, 1)
else:
    raise SystemExit('usage: crop_overlay_anchor_adapter.py prepare|finish')

path.write_text(text, encoding='utf-8')
