#!/usr/bin/env python3
from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


timeline_path = Path("app/src/main/java/com/videoflow/app/ui/editor/TimelineWorkspace.kt")
timeline = timeline_path.read_text(encoding="utf-8")
indicator = "TimedEffectIndicators(clips,revision,horizontal,totalWidth,pixelsPerSecond,onSelect,onSeek,onProfessionalTool,originUs,windowEndUs)"
if timeline.count(indicator) == 1:
    timeline = replace_once(timeline, "                    " + indicator + "\n", "", "remove leading effect lanes")
    tail = """                            laneHeight = trackRowHeight
                        )
                    }
                }
"""
    replacement = """                            laneHeight = trackRowHeight
                        )
                    }
                    // Keep the first three real tracks at the top of the bounded viewport.
                    // Legacy effect lanes remain reachable after the track stack instead of
                    // consuming one of the three primary visible track rows.
                    """ + indicator + """
                }
"""
    timeline = replace_once(timeline, tail, replacement, "append effect lanes after tracks")
timeline = timeline.replace(
    'contentDescription = "More options for ${track.name}"',
    'contentDescription = "Open ${track.name} settings"'
)
timeline_path.write_text(timeline, encoding="utf-8")

scaffold_path = Path("app/src/main/java/com/videoflow/app/ui/editor/EditorWorkspaceScaffold.kt")
scaffold = scaffold_path.read_text(encoding="utf-8")
if "import androidx.compose.foundation.layout.heightIn" not in scaffold:
    scaffold = replace_once(
        scaffold,
        "import androidx.compose.foundation.layout.height\n",
        "import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.heightIn\n",
        "heightIn import",
    )
target = """                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("editor-preview-slot")
"""
replacement = """                Modifier
                    .fillMaxWidth()
                    .heightIn(min = metrics.minimumPreviewHeight)
                    .weight(1f)
                    .testTag("editor-preview-slot")
"""
if target in scaffold:
    scaffold = replace_once(scaffold, target, replacement, "minimum preview height")
scaffold_path.write_text(scaffold, encoding="utf-8")

print("UX Step 1 post-review correction applied")
