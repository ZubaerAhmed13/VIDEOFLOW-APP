from pathlib import Path

ROOT = Path('.')


def replace_once(path: str, old: str, new: str) -> None:
    p = ROOT / path
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{path}: expected exactly one match, found {count}')
    p.write_text(text.replace(old, new, 1))


# 1) Add pure, unit-testable interaction math used by the final Step 2 implementation.
helper = ROOT / 'app/src/main/java/com/videoflow/app/ui/editor/ContextualEditingMath.kt'
helper.parent.mkdir(parents=True, exist_ok=True)
helper.write_text('''package com.videoflow.app.ui.editor

import kotlin.math.abs

/** Small pure helpers shared by direct-manipulation UI and its JVM regression tests. */
internal fun displayDimensionsForRotation(
    encodedWidth: Int,
    encodedHeight: Int,
    rotationDegrees: Int?
): Pair<Int, Int> {
    require(encodedWidth > 0 && encodedHeight > 0)
    val normalized = (((rotationDegrees ?: 0) % 360) + 360) % 360
    return if (normalized == 90 || normalized == 270) {
        encodedHeight to encodedWidth
    } else {
        encodedWidth to encodedHeight
    }
}

internal fun snapNormalizedToCenter(
    value: Float,
    center: Float = 0.5f,
    threshold: Float = 0.018f
): Float {
    val bounded = value.coerceIn(0f, 1f)
    return if (abs(bounded - center) < threshold) center else bounded
}

internal fun keyframeMarkerFraction(timeUs: Long, ownerDurationUs: Long): Float {
    if (ownerDurationUs <= 0L) return 0f
    return (timeUs.toDouble() / ownerDurationUs.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

internal fun timelineAutoScrollDelta(
    visibleXPx: Float,
    viewportWidthPx: Float,
    edgePx: Float,
    maxStepPx: Float
): Float {
    if (viewportWidthPx <= 0f || edgePx <= 0f || maxStepPx <= 0f) return 0f
    return when {
        visibleXPx < edgePx -> -maxStepPx * ((edgePx - visibleXPx) / edgePx).coerceIn(0f, 1f)
        visibleXPx > viewportWidthPx - edgePx ->
            maxStepPx * ((visibleXPx - (viewportWidthPx - edgePx)) / edgePx).coerceIn(0f, 1f)
        else -> 0f
    }
}
''')

# 2) Crop presets must use display orientation, not encoded orientation.
replace_once(
    'app/src/main/java/com/videoflow/app/ui/editor/ContextualToolPanels.kt',
    '''        val sw = asset?.width ?: return\n        val sh = asset.height ?: return\n        val targetAspect = w.toFloat() / h.toFloat()\n        val normalizedAspect = targetAspect / (sw.toFloat() / sh.toFloat())\n        update(centeredCrop(sw, sh, targetAspect), normalizedAspect)''',
    '''        val encodedWidth = asset?.width ?: return\n        val encodedHeight = asset.height ?: return\n        val (sw, sh) = displayDimensionsForRotation(encodedWidth, encodedHeight, asset.rotationDegrees)\n        val targetAspect = w.toFloat() / h.toFloat()\n        val normalizedAspect = targetAspect / (sw.toFloat() / sh.toFloat())\n        update(centeredCrop(sw, sh, targetAspect), normalizedAspect)'''
)

# 3) Route center snap through tested pure math.
replace_once(
    'app/src/main/java/com/videoflow/app/ui/screens/EditorScreen.kt',
    'import com.videoflow.app.ui.editor.PreviewTransformDraft\n',
    'import com.videoflow.app.ui.editor.PreviewTransformDraft\nimport com.videoflow.app.ui.editor.snapNormalizedToCenter\n'
)
replace_once(
    'app/src/main/java/com/videoflow/app/ui/screens/EditorScreen.kt',
    '''            x = if (abs(rawX - 0.5f) < 0.018f) 0.5f else rawX,\n            y = if (abs(rawY - 0.5f) < 0.018f) 0.5f else rawY,''',
    '''            x = snapNormalizedToCenter(rawX),\n            y = snapNormalizedToCenter(rawY),'''
)

# 4) Route timeline edge auto-scroll and keyframe placement through tested pure math.
replace_once(
    'app/src/main/java/com/videoflow/app/ui/editor/TimelineWorkspace.kt',
    '''        val delta = when {\n            visibleX < edgePx -> -maxStepPx * ((edgePx - visibleX) / edgePx).coerceIn(0f, 1f)\n            visibleX > viewportWidthPx - edgePx -> maxStepPx * ((visibleX - (viewportWidthPx - edgePx)) / edgePx).coerceIn(0f, 1f)\n            else -> 0f\n        }''',
    '''        val delta = timelineAutoScrollDelta(visibleX, viewportWidthPx, edgePx, maxStepPx)'''
)
replace_once(
    'app/src/main/java/com/videoflow/app/ui/editor/TimelineWorkspace.kt',
    '''            val x = (frame.timeUs.toFloat() / ownerDurationUs.toFloat()).coerceIn(0f, 1f) * size.width''',
    '''            val x = keyframeMarkerFraction(frame.timeUs, ownerDurationUs) * size.width'''
)

# 5) Explicit JVM behavior tests for late Step 2 hardening.
test = ROOT / 'app/src/test/java/com/videoflow/app/ui/editor/ContextualEditingMathTest.kt'
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text('''package com.videoflow.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class ContextualEditingMathTest {

    @Test
    fun displayDimensions_swapForQuarterTurnRotation() {
        assertEquals(1080 to 1920, displayDimensionsForRotation(1920, 1080, 90))
        assertEquals(1080 to 1920, displayDimensionsForRotation(1920, 1080, 270))
        assertEquals(1920 to 1080, displayDimensionsForRotation(1920, 1080, 0))
        assertEquals(1920 to 1080, displayDimensionsForRotation(1920, 1080, 180))
        assertEquals(1080 to 1920, displayDimensionsForRotation(1920, 1080, -90))
    }

    @Test
    fun centerSnap_onlyChangesValuesInsideTolerance() {
        assertEquals(0.5f, snapNormalizedToCenter(0.49f), 0.00001f)
        assertEquals(0.5f, snapNormalizedToCenter(0.51f), 0.00001f)
        assertEquals(0.47f, snapNormalizedToCenter(0.47f), 0.00001f)
        assertEquals(0.53f, snapNormalizedToCenter(0.53f), 0.00001f)
    }

    @Test
    fun keyframeMarker_usesOwnerDurationNotLastKeyframe() {
        assertEquals(0.5f, keyframeMarkerFraction(5_000_000L, 10_000_000L), 0.00001f)
        assertEquals(0f, keyframeMarkerFraction(-1L, 10_000_000L), 0.00001f)
        assertEquals(1f, keyframeMarkerFraction(11_000_000L, 10_000_000L), 0.00001f)
        assertEquals(0f, keyframeMarkerFraction(1L, 0L), 0.00001f)
    }

    @Test
    fun timelineAutoScroll_isDirectionalProportionalAndBounded() {
        assertEquals(-10f, timelineAutoScrollDelta(50f, 1000f, 100f, 20f), 0.00001f)
        assertEquals(10f, timelineAutoScrollDelta(950f, 1000f, 100f, 20f), 0.00001f)
        assertEquals(0f, timelineAutoScrollDelta(500f, 1000f, 100f, 20f), 0.00001f)
        assertEquals(-20f, timelineAutoScrollDelta(-100f, 1000f, 100f, 20f), 0.00001f)
        assertEquals(20f, timelineAutoScrollDelta(1200f, 1000f, 100f, 20f), 0.00001f)
    }
}
''')

# 6) Static architecture contract for the specific late fixes that are otherwise hard to unit-test.
contract = ROOT / 'app/src/test/java/com/videoflow/app/ui/editor/Step2LateInteractionContractTest.kt'
contract.write_text('''package com.videoflow.app.ui.editor

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Step2LateInteractionContractTest {

    private fun source(relative: String): String {
        val candidates = listOf(
            Path.of("src/main/java/com/videoflow/app", relative),
            Path.of("app/src/main/java/com/videoflow/app", relative)
        )
        val path = candidates.firstOrNull { Files.exists(it) }
            ?: error("Cannot locate source file $relative from ${Path.of("").toAbsolutePath()}")
        return Files.readString(path)
    }

    @Test
    fun contextualTools_areNonModalAndFreeCropIsReal() {
        val panels = source("ui/editor/ContextualToolPanels.kt")
        assertFalse(panels.contains("ModalBottomSheet"))
        assertTrue(panels.contains("OutlinedButton(onClick = { update(crop, null) })"))
        assertTrue(panels.contains("displayDimensionsForRotation(encodedWidth, encodedHeight, asset.rotationDegrees)"))
    }

    @Test
    fun directGestures_haveTransientAndCommitBoundaries() {
        val interaction = source("ui/editor/PreviewInteraction.kt")
        assertTrue(interaction.contains("onCropChange(constrained)"))
        assertTrue(interaction.contains("onCropCommit(working)"))
        assertTrue(interaction.contains("latestGesture("))
        assertTrue(interaction.contains("latestEnd()"))

        val screen = source("ui/screens/EditorScreen.kt")
        val start = screen.indexOf("fun transformGesture(")
        assertTrue(start >= 0)
        val next = screen.indexOf("\\n    fun ", start + 5).let { if (it < 0) screen.length else it }
        val gestureBody = screen.substring(start, next)
        assertTrue(gestureBody.contains("previewDraft = previewDraft.copy"))
        assertFalse(gestureBody.contains("contextualVm."))
        assertTrue(gestureBody.contains("snapNormalizedToCenter(rawX)"))
        assertTrue(gestureBody.contains("snapNormalizedToCenter(rawY)"))
    }

    @Test
    fun timelineUsesDurationMarkersAndEdgeAutoScroll() {
        val timeline = source("ui/editor/TimelineWorkspace.kt")
        assertTrue(timeline.contains("timelineAutoScrollDelta(visibleX, viewportWidthPx, edgePx, maxStepPx)"))
        assertTrue(timeline.contains("horizontal.dispatchRawDelta(delta)"))
        assertTrue(timeline.contains("keyframeMarkerFraction(frame.timeUs, ownerDurationUs) * size.width"))
        assertTrue(timeline.contains("textOverlays.forEach"))
        assertTrue(timeline.contains("imageOverlays.forEach"))
    }

    @Test
    fun exactKeyframeAndUniformScalePathsRemainPaired() {
        val vm = source("ui/ContextualEditingViewModel.kt")
        assertTrue(vm.contains("exact[KeyframeProperty.SCALE_X]?.let"))
        assertTrue(vm.contains("exact[KeyframeProperty.SCALE_Y]?.let"))
        assertTrue(vm.contains("exact[KeyframeProperty.POSITION_X]?.let"))
        assertTrue(vm.contains("exact[KeyframeProperty.ROTATION]?.let"))
        assertTrue(vm.contains("property == KeyframeProperty.OPACITY && it.timeUs == t"))
        assertTrue(vm.contains("property == KeyframeProperty.AUDIO_GAIN && it.timeUs == t"))
    }
}
''')

# 7) Record the targeted regression layer in the Step 2 test report.
report = ROOT / 'UI_STEP_2_TEST_REPORT.md'
report_text = report.read_text()
anchor = '### Existing regression coverage\n\nThe repository regression suite remains authoritative for core editor/domain behavior including timeline operations, project persistence, media/source handling, render planning, keyframe evaluation/history and native export architecture.\n'
addition = '''### Final interaction hardening regression\n\n`ContextualEditingMathTest` and `Step2LateInteractionContractTest` explicitly lock the late Step 2 fixes that previously depended mostly on broad CI/static review:\n\n- 90°/270° source rotation swaps display dimensions before Crop preset math;\n- center snapping occurs only inside the visual tolerance and preserves values outside it;\n- keyframe diamonds use owner duration rather than the last keyframe as their scale;\n- timeline edge auto-scroll is directional, proportional and bounded;\n- Crop/Transform keep transient pointer state separate from durable commit boundaries;\n- the contextual inspector remains non-modal so the preview stays touchable;\n- Free Crop remains a real action;\n- exact-keyframe updates and paired X/Y uniform Scale paths remain present.\n\n'''
if addition not in report_text:
    if anchor not in report_text:
        raise SystemExit('UI_STEP_2_TEST_REPORT.md anchor missing')
    report.write_text(report_text.replace(anchor, addition + anchor, 1))

print('Step 2 final hardening patch applied')
