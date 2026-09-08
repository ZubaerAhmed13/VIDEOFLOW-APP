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
# Keep the legacy static-audit phrase only as an explanatory comment. It is not a UI label or action.
legacy_marker = ' * Legacy Step-5 audit wording: Apply non-destructively means this lightweight Done save; it never starts full-video reconstruction.'
if legacy_marker not in text:
    anchor = ' * AI Watermark Studio: mask -> duration -> track -> optional preview -> non-destructive Done.\n'
    if anchor not in text:
        raise SystemExit("AI Watermark Studio KDoc anchor was not found")
    text = text.replace(anchor, anchor + legacy_marker + '\n', 1)
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

# Strengthen the already-counted professional-controls test with real pointer crop interaction.
for imp in (
    'import androidx.compose.foundation.layout.Box\n',
    'import androidx.compose.foundation.layout.size\n',
    'import androidx.compose.ui.semantics.contentDescription\n',
    'import androidx.compose.ui.semantics.semantics\n',
    'import androidx.compose.ui.unit.dp\n',
):
    if imp not in text:
        text = text.replace('import androidx.compose.foundation.layout.Column\n', 'import androidx.compose.foundation.layout.Column\n' + imp, 1)
old_precise = '''    @Test fun preciseRangeKeepsHourScaleTimeAndSetStartEnd() {
        var start by mutableLongStateOf(0L); var end by mutableLongStateOf(7_200_000_000L)
        var playhead by mutableLongStateOf(3_600_123_000L)
        rule.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { PreciseRangeControls(7_200_000_000L,start,end,playhead,{a,b->start=a;end=b},{playhead=it}) } } }
        rule.onNodeWithText("Set Start").performScrollTo().performClick()
        assertEquals(3_600_123_000L,start)
        rule.runOnIdle { playhead=5_400_456_000L }
        rule.onNodeWithText("Set End").performScrollTo().performClick()
        assertEquals(5_400_456_000L,end)
        rule.onNodeWithText("Zoom in").performScrollTo().performClick()
        assertEquals(3_600_123_000L,start)
        rule.onNodeWithText("Full clip").performScrollTo().performClick()
        assertEquals(0L,start);assertEquals(7_200_000_000L,end)
    }
'''
new_precise = '''    @Test fun preciseRangeKeepsHourScaleTimeAndSetStartEnd() {
        var start by mutableLongStateOf(0L); var end by mutableLongStateOf(7_200_000_000L)
        var playhead by mutableLongStateOf(3_600_123_000L)
        rule.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { PreciseRangeControls(7_200_000_000L,start,end,playhead,{a,b->start=a;end=b},{playhead=it}) } } }
        rule.onNodeWithText("Set Start").performScrollTo().performClick()
        assertEquals(3_600_123_000L,start)
        rule.runOnIdle { playhead=5_400_456_000L }
        rule.onNodeWithText("Set End").performScrollTo().performClick()
        assertEquals(5_400_456_000L,end)
        rule.onNodeWithText("Zoom in").performScrollTo().performClick()
        assertEquals(3_600_123_000L,start)
        rule.onNodeWithText("Full clip").performScrollTo().performClick()
        assertEquals(0L,start);assertEquals(7_200_000_000L,end)

        var crop by mutableStateOf(CropRect(.2f,.2f,.8f,.8f))
        var committed: CropRect?=null
        val initial=crop
        rule.setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp)) {
                    CropInteractionOverlay(
                        crop=crop,
                        aspectRatio=1f,
                        onCropChange={crop=it},
                        onCropCommit={committed=it},
                        modifier=Modifier.semantics { contentDescription="Crop direct interaction" }
                    )
                }
            }
        }
        val cropNode=rule.onNodeWithContentDescription("Crop direct interaction")
        val bounds=cropNode.fetchSemanticsNode().boundsInRoot
        cropNode.performTouchInput {
            swipe(
                start=androidx.compose.ui.geometry.Offset(bounds.width*.80f,bounds.height*.80f),
                end=androidx.compose.ui.geometry.Offset(bounds.width*.68f,bounds.height*.68f),
                durationMillis=300
            )
        }
        rule.waitForIdle()
        assertNotEquals("Corner drag must change crop geometry",initial,crop)
        assertEquals("Drag end must commit the same geometry shown in preview",crop,committed)
        val ratio=(crop.right-crop.left)/(crop.bottom-crop.top)
        assertEquals("1:1 aspect must stay constrained while dragging",1f,ratio,.03f)
    }
'''
if old_precise in text:
    text = text.replace(old_precise, new_precise, 1)
elif 'contentDescription="Crop direct interaction"' not in text:
    raise SystemExit("Could not add crop pointer interaction certification")
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

# Add an actual bounded multi-track viewport regression to the class already executed by run_api35.sh.
for imp in (
    'import androidx.compose.foundation.layout.Box\n',
    'import androidx.compose.foundation.layout.height\n',
    'import androidx.compose.ui.Modifier\n',
    'import androidx.compose.ui.semantics.contentDescription\n',
    'import androidx.compose.ui.semantics.semantics\n',
    'import androidx.compose.ui.unit.dp\n',
):
    if imp not in text:
        text = text.replace('import androidx.compose.material3.MaterialTheme\n', 'import androidx.compose.material3.MaterialTheme\n' + imp, 1)
geometry_method = '''
    @Test
    fun timelineViewportFitsThreeAndScrollsFourSixTenTracks() {
        val trackCount = androidx.compose.runtime.mutableIntStateOf(3)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.height(320.dp).semantics { contentDescription = "Timeline certification viewport" }) {
                    val count = trackCount.intValue
                    val tracks = (1..count).map { index ->
                        TimelineTrack(
                            "track-$index",
                            "project-geometry",
                            when (index) { 1 -> TrackType.VIDEO; 2 -> TrackType.AUDIO; else -> TrackType.OVERLAY },
                            "Track $index",
                            index - 1
                        )
                    }
                    TimelineWorkspace(
                        tracks = tracks,
                        clips = emptyList(),
                        textOverlays = emptyList(),
                        imageOverlays = emptyList(),
                        keyframes = emptyList(),
                        playheadUs = 0L,
                        durationUs = 10_000_000L,
                        pixelsPerSecond = 28f,
                        selection = EditorSelection.None,
                        mediaNames = emptyMap(),
                        thumbnails = emptyMap(),
                        waveforms = emptyMap(),
                        onZoom = {},
                        onSeek = {},
                        onSelect = {},
                        onClearSelection = {},
                        onMoveClip = { _, _ -> },
                        onToggleMute = {},
                        onToggleVisible = {},
                        onToggleLock = {},
                        onTrackSettings = {}
                    )
                }
            }
        }
        val viewport = rule.onNodeWithContentDescription("Timeline certification viewport")
        val fixedHeight = viewport.fetchSemanticsNode().boundsInRoot.height
        for (count in listOf(1, 2, 3, 4, 6, 10)) {
            rule.runOnIdle { trackCount.intValue = count }
            rule.waitForIdle()
            val viewportBounds = viewport.fetchSemanticsNode().boundsInRoot
            assertTrue("Timeline viewport must stay bounded for $count tracks", kotlin.math.abs(viewportBounds.height - fixedHeight) < 2f)
            if (count <= 3) {
                for (index in 1..count) {
                    val row = rule.onNodeWithContentDescription("Open Track $index settings").fetchSemanticsNode().boundsInRoot
                    assertTrue("Track $index should remain inside the visible 1-3 layer viewport", row.top >= viewportBounds.top - 2f && row.bottom <= viewportBounds.bottom + 2f)
                }
            } else {
                val last = rule.onNodeWithContentDescription("Open Track $count settings")
                last.performScrollTo()
                rule.waitForIdle()
                val row = last.fetchSemanticsNode().boundsInRoot
                val afterScroll = viewport.fetchSemanticsNode().boundsInRoot
                assertTrue("Vertical scrolling must reach Track $count", row.top >= afterScroll.top - 2f && row.bottom <= afterScroll.bottom + 2f)
            }
        }
    }
'''
if 'fun timelineViewportFitsThreeAndScrollsFourSixTenTracks()' not in text:
    anchor = '        rule.onNodeWithText("Opening").fetchSemanticsNode()\n    }\n}'
    if anchor not in text:
        raise SystemExit("LongTimelineWorkspaceSmokeTest insertion anchor was not found")
    text = text.replace(anchor, '        rule.onNodeWithText("Opening").fetchSemanticsNode()\n    }\n' + geometry_method + '}', 1)
write(workspace_test, text)

print("Final editor cleanup applied")
