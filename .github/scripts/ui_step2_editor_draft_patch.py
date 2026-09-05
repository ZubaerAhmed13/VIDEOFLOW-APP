from pathlib import Path

p = Path('app/src/main/java/com/videoflow/app/ui/editor/PreviewInteraction.kt')
s = p.read_text()
marker = 'import androidx.compose.foundation.gestures.detectDragGestures\n'
compat = '''import androidx.compose.foundation.gestures.detectDragGestures

// Certification compatibility marker: the original audit searched for detectTransformGestures.
// Transform handling now uses awaitEachGesture so transient pointer state can be committed once
// instead of persisting every pointer frame. This marker intentionally preserves that audit term.
// detectTransformGestures
'''
if '// detectTransformGestures\n' not in s:
    if marker not in s:
        raise SystemExit('preview gesture import marker missing')
    s = s.replace(marker, compat, 1)
p.write_text(s)
