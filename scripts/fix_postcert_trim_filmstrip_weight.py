#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
needle = "import androidx.compose.foundation.layout.weight\n"
paths = [
    "app/src/main/java/com/videoflow/app/ui/TrimFilmstripPreview.kt",
    "app/src/androidTest/java/com/videoflow/app/ui/TrimOpenGeometryComposeTest.kt",
]

for relative in paths:
    path = root / relative
    text = path.read_text(encoding="utf-8")
    count = text.count(needle)
    if count != 1:
        raise SystemExit(f"{relative}: expected exactly one generated weight import, got {count}")
    # RowScope/ColumnScope weight is a scoped public extension. Importing the internal top-level
    # parent-data property breaks on the current Compose version; inside Row/Column receivers,
    # Modifier.weight resolves correctly without this explicit import.
    path.write_text(text.replace(needle, "", 1), encoding="utf-8")
    print(f"Removed invalid scoped weight import from {relative}")
