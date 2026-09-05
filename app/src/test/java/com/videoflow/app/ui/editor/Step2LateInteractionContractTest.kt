package com.videoflow.app.ui.editor

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
        val next = screen.indexOf("\n    fun ", start + 5).let { if (it < 0) screen.length else it }
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
