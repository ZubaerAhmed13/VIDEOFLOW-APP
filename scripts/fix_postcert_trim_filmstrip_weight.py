#!/usr/bin/env python3
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/java/com/videoflow/app/ui/TrimFilmstripPreview.kt"
text = path.read_text(encoding="utf-8")
needle = "import androidx.compose.foundation.layout.weight\n"
if text.count(needle) != 1:
    raise SystemExit(f"expected exactly one generated weight import, got {text.count(needle)}")
# RowScope.weight is a scoped public extension. Importing the internal top-level parent-data
# property breaks on the current Compose version; inside Row { ... }, Modifier.weight resolves
# correctly through the RowScope receiver without this import.
path.write_text(text.replace(needle, "", 1), encoding="utf-8")
print("Removed invalid TrimFilmstripPreview weight import")
