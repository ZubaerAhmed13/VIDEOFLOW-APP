#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if new in text and old not in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match in {path}, got {count}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))


# 1. Remove the orphan professional PreciseTrim route. Exact precision remains inside EditorTool.Trim.
tool_file = "app/src/main/java/com/videoflow/app/ui/editor/ProfessionalEditorTool.kt"
text = read(tool_file)
text = re.sub(r"\n\s*data class PreciseTrim\(override val clipId: String\) : ProfessionalEditorTool", "", text)
if "PreciseTrim" in text:
    raise SystemExit("Orphan ProfessionalEditorTool.PreciseTrim remained")
write(tool_file, text)

# 2. AI panel language must match the lightweight Done behavior actually implemented.
panel = "app/src/main/java/com/videoflow/app/ui/ai/WatermarkStudioPanel.kt"
text = read(panel)
text = text.replace(
    "AI Watermark Studio: mask -> time -> track -> still/moving preview -> non-destructive Apply.",
    "AI Watermark Studio: mask -> duration -> track -> optional preview -> non-destructive Done.",
)
text = text.replace(" — review ROI before Apply", " — review ROI before Done")
text = text.replace('StepTitle("4", "Apply non-destructively")', 'StepTitle("4", "Done")')
text = text.replace(
    'Text("Apply saves the editable AI effect, then prepares bounded processed segments for normal timeline playback. Source media is never overwritten.", color = VideoFlowEditorColors.SecondaryText)',
    'Text("Done saves the editable AI effect immediately and returns to the editor. Moving preview runs only when you request it in Preview; final reconstruction happens only during Export. Source media is never overwritten.", color = VideoFlowEditorColors.SecondaryText)',
)
text = text.replace('contentDescription = "Apply AI removal"', 'contentDescription = "Save AI removal"')
text = text.replace(') { Text(if (editingEffectId == null) "Apply" else "Update") }', ') { Text(if (editingEffectId == null) "Done" else "Save") }')
for stale in (
    'StepTitle("4", "Apply non-destructively")',
    'contentDescription = "Apply AI removal"',
    "then prepares bounded processed segments for normal timeline playback",
):
    if stale in text:
        raise SystemExit(f"Stale AI Done semantics remained: {stale}")
write(panel, text)

# 3. Professional controls instrumentation validates one Trim entry + embedded precise controls.
controls = "app/src/androidTest/java/com/videoflow/app/ui/ProfessionalToolControlsTest.kt"
text = read(controls)
old_test = '''    @Test fun contextualRailRoutesEveryProfessionalTool() {
        var selected: ProfessionalEditorTool?=null
        rule.setContent { MaterialTheme { EditorBottomToolbar(EditorSelection.Clip("clip"),"video/mp4",{},{},{},{ selected=it }) } }
        for ((label,expected) in listOf("Audio" to ProfessionalEditorTool.AudioExtract("clip"),"Effects" to ProfessionalEditorTool.Effects("clip"),
            "Enhance" to ProfessionalEditorTool.Enhance("clip"),"AI Tools" to ProfessionalEditorTool.AiWatermark("clip"),"Precise Trim" to ProfessionalEditorTool.PreciseTrim("clip"))) {
            rule.onNodeWithContentDescription(label).performScrollTo().performClick()
            assertEquals(expected,selected)
        }
    }
'''
new_test = '''    @Test fun contextualRailHasOneTrimAndRoutesProfessionalTools() {
        var selectedTool: EditorTool?=null
        var selectedProfessional: ProfessionalEditorTool?=null
        rule.setContent {
            MaterialTheme {
                EditorBottomToolbar(
                    EditorSelection.Clip("clip"),
                    "video/mp4",
                    {},
                    { selectedTool=it },
                    {},
                    { selectedProfessional=it }
                )
            }
        }
        rule.onNodeWithContentDescription("Trim").performScrollTo().performClick()
        assertEquals(EditorTool.Trim("clip"),selectedTool)
        rule.onAllNodesWithContentDescription("Precise Trim").assertCountEquals(0)
        for ((label,expected) in listOf(
            "Audio" to ProfessionalEditorTool.AudioExtract("clip"),
            "Effects" to ProfessionalEditorTool.Effects("clip"),
            "Enhance" to ProfessionalEditorTool.Enhance("clip"),
            "AI Tools" to ProfessionalEditorTool.AiWatermark("clip")
        )) {
            rule.onNodeWithContentDescription(label).performScrollTo().performClick()
            assertEquals(expected,selectedProfessional)
        }
    }
'''
if old_test in text:
    text = text.replace(old_test, new_test, 1)
elif new_test not in text:
    raise SystemExit("Could not update professional toolbar instrumentation")
if "ProfessionalEditorTool.PreciseTrim" in text:
    raise SystemExit("ProfessionalToolControlsTest still references orphan PreciseTrim")
write(controls, text)

# 4. Product workflow uses the isolated client and current Duration/Cover/Done UX.
product = "app/src/androidTest/java/com/videoflow/app/ui/ProfessionalProductWorkflowTest.kt"
text = read(product)
old_ctor = '''            val manager=AiModelPackManager(context)
            val previewEngine=LocalWatermarkPreviewEngine(context,manager)
            val processedPreviewManager=AiProcessedPreviewManager(context,editor,projects,ai,manager)
            val aiVm=WatermarkStudioViewModel(ai,manager,previewEngine,processedPreviewManager,LocalRoiTracker(previewEngine),history)
'''
new_ctor = '''            val manager=AiModelPackManager(context)
            val previewEngine=LocalWatermarkPreviewEngine(context,manager)
            val aiVm=WatermarkStudioViewModel(
                ai,
                AiPreviewProcessClient(context),
                LocalPreviewFrameDecoder(context),
                AiPreviewCacheController(context),
                LocalRoiTracker(previewEngine),
                history
            )
'''
if old_ctor in text:
    text = text.replace(old_ctor, new_ctor, 1)
elif new_ctor not in text:
    raise SystemExit("Could not update ProfessionalProductWorkflowTest AI ViewModel construction")
text = text.replace('rule.onNodeWithText("Time",substring=false).performClick()', 'rule.onNodeWithText("Duration",substring=false).performClick()')
text = text.replace('rule.onNodeWithText("Select",substring=false).performClick()', 'rule.onNodeWithText("Cover",substring=false).performClick()')
text = text.replace('rule.onNodeWithContentDescription("AI stage Apply").performClick()', 'rule.onNodeWithContentDescription("AI stage Done").performClick()')
text = text.replace('rule.onNodeWithContentDescription("Apply AI removal").assertIsEnabled().performScrollTo().performClick()', 'rule.onNodeWithContentDescription("Save AI removal").assertIsEnabled().performScrollTo().performClick()')
old_wait = '''            // Apply persists the edit first, then prepares real processed moving editor-preview media.
            // Require that lifecycle to start, and keep the same bounded AI budget as the Step-5
            // product integration gate. This remains fail-closed on either preview error or timeout.
            rule.waitUntil(30_000) {
                tool==null || aiVm.state.value.error!=null ||
                    aiVm.state.value.busy==WatermarkStudioBusy.PREPARING_EDITOR_PREVIEW
            }
            rule.waitUntil(180_000) { tool==null || aiVm.state.value.error!=null }
            assertNull("AI commit or processed editor-preview preparation failed",aiVm.state.value.error)
            assertNull("Applied panel must close after processed editor preview is ready",tool)
'''
new_wait = '''            // Done persists one non-destructive edit definition and returns promptly. Heavy moving
            // preview is explicit in Preview; final reconstruction remains isolated in Export.
            rule.waitUntil(30_000) { tool==null || aiVm.state.value.error!=null }
            assertNull("AI Done failed",aiVm.state.value.error)
            assertNull("Done must return to the editor promptly",tool)
'''
if old_wait in text:
    text = text.replace(old_wait, new_wait, 1)
elif new_wait not in text:
    raise SystemExit("Could not update ProfessionalProductWorkflowTest Done lifecycle")
for stale in (
    "ProfessionalEditorTool.PreciseTrim",
    'onNodeWithText("Time",substring=false)',
    'onNodeWithText("Select",substring=false)',
    'contentDescription("AI stage Apply")',
    'contentDescription("Apply AI removal")',
    "WatermarkStudioBusy.PREPARING_EDITOR_PREVIEW",
):
    if stale in text:
        raise SystemExit(f"Stale professional product expectation remained: {stale}")
write(product, text)

# 5. Compact 88dp timeline rows intentionally keep lock in Track Settings rather than the lane.
#    The visual smoke test should require the settings entry and primary controls, not an obsolete
#    dedicated row lock icon. Lock functionality remains certified through TrackSettingsPanel.
workspace_test = "app/src/androidTest/java/com/videoflow/app/ui/EditorWorkspaceVisualCertificationTest.kt"
text = read(workspace_test)
text = text.replace('        rule.onNodeWithContentDescription("Lock Video 1").fetchSemanticsNode()\n', '')
if 'onNodeWithContentDescription("Lock Video 1")' in text:
    raise SystemExit("Stale per-row Lock Video 1 assertion remained")
if 'onNodeWithContentDescription("Open Video 1 settings")' not in text:
    raise SystemExit("Track Settings accessibility assertion is missing")
write(workspace_test, text)

print("Final editor cleanup applied")
