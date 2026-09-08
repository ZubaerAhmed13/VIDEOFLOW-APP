#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / "app/src/androidTest/java/com/videoflow/app/step5/Step5ProductIntegrationTest.kt"
text = path.read_text(encoding="utf-8")

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
    raise SystemExit("Could not update Step5ProductIntegrationTest WatermarkStudioViewModel construction")

text = text.replace('rule.onNodeWithText("Time",substring=false).performClick()', 'rule.onNodeWithText("Duration",substring=false).performClick()')
text = text.replace('rule.onNodeWithText("Select",substring=false).performClick()', 'rule.onNodeWithText("Cover",substring=false).performClick()')
text = text.replace('rule.onNodeWithContentDescription("AI stage Apply").performClick()', 'rule.onNodeWithContentDescription("AI stage Done").performClick()')
text = text.replace('rule.onNodeWithContentDescription("Apply AI removal").assertIsEnabled().performScrollTo().performClick()', 'rule.onNodeWithContentDescription("Save AI removal").assertIsEnabled().performScrollTo().performClick()')

old_wait = '''            // Apply now atomically persists the edit and then prepares the real processed moving
            // timeline-preview media before closing the panel. Prove that modern lifecycle is
            // entered, then allow the same bounded AI budget used by the explicit preview test.
            rule.waitUntil(30_000) {
                tool==null || aiVm.state.value.error!=null ||
                    aiVm.state.value.busy==WatermarkStudioBusy.PREPARING_EDITOR_PREVIEW
            }
            rule.waitUntil(180_000) { tool==null || aiVm.state.value.error!=null }
            assertNull("AI commit or processed editor-preview preparation failed",aiVm.state.value.error)
            assertNull("Applied panel must close after processed editor preview is ready",tool)
'''
new_wait = '''            // Done atomically persists one non-destructive edit definition and returns promptly.
            // Moving preview is an explicit Preview action; final reconstruction remains in Export.
            rule.waitUntil(30_000) { tool==null || aiVm.state.value.error!=null }
            assertNull("AI Done failed",aiVm.state.value.error)
            assertNull("Done must return to the editor promptly",tool)
'''
if old_wait in text:
    text = text.replace(old_wait, new_wait, 1)
elif new_wait not in text:
    raise SystemExit("Could not update Step5ProductIntegrationTest Done lifecycle")

text = text.replace(
    "// Stage chip and commit button both say Apply; the chip exists before opening the stage.",
    "// Done is a lightweight non-destructive save; Preview remains explicit and optional.",
)

for stale in (
    "processedPreviewManager=AiProcessedPreviewManager",
    "WatermarkStudioViewModel(ai,manager,previewEngine,processedPreviewManager",
    'onNodeWithText("Time",substring=false)',
    'onNodeWithText("Select",substring=false)',
    'onNodeWithContentDescription("AI stage Apply")',
    'onNodeWithContentDescription("Apply AI removal")',
    "WatermarkStudioBusy.PREPARING_EDITOR_PREVIEW",
):
    if stale in text:
        raise SystemExit(f"Stale Step5 product integration expectation remained: {stale}")

path.write_text(text, encoding="utf-8")
print("Step5 product integration cleanup applied")
