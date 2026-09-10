package com.videoflow.app.ui.editor

import com.videoflow.app.domain.editor.CropRect
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineEngine
import com.videoflow.app.render.effects.normalizedCropToMedia3Bounds
import com.videoflow.app.util.formatHumanDurationUs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UXStep2TrimSpeedCropCoreTest {
    private fun clip() = TimelineClip(id="clip", projectId="project", trackId="track", assetId="asset", timelineStartUs=7_000_000L, sourceStartUs=0L, sourceEndUs=120_000_000L)

    @Test fun focusedTrim_changesSourceRangeButKeepsTimelineAnchor() {
        val before = clip().copy(fadeInUs = 110_000_000L, fadeOutUs = 115_000_000L)
        val after = TimelineEngine.trimSourceRangeKeepingTimelineAnchor(before, 12_000_000L, 120_000_000L, 120_000_000L)
        assertEquals(7_000_000L, after.timelineStartUs)
        assertEquals(12_000_000L, after.sourceStartUs)
        assertEquals(120_000_000L, after.sourceEndUs)
        assertEquals(108_000_000L, after.timelineDurationUs)
        assertTrue(after.fadeInUs <= after.timelineDurationUs)
        assertTrue(after.fadeOutUs <= after.timelineDurationUs)
    }

    @Test fun directTimelineTrimStart_keepsItsExistingEdgeTrimSemantics() {
        val before = clip()
        assertTrue(TimelineEngine.trimStart(before, 12_000_000L).timelineStartUs > before.timelineStartUs)
    }

    @Test fun humanDuration_isReadableAndSpeedMathStaysLongBased() {
        assertEquals("12.6 sec", formatHumanDurationUs(12_600_000L))
        assertEquals("1 min 39 sec", formatHumanDurationUs(99_000_000L))
        assertEquals(49_500_000L, speedAdjustedDurationUs(99_000_000L, 2.0))
        assertEquals(198_000_000L, speedAdjustedDurationUs(99_000_000L, 0.5))
    }

    @Test fun cropGeometry_usesSharedSourceSpaceMedia3Bounds() {
        val bounds = normalizedCropToMedia3Bounds(CropRect(0.25f, 0.10f, 0.75f, 0.90f))
        requireNotNull(bounds)
        assertEquals(-0.50f, bounds.left, 0.001f)
        assertEquals(0.50f, bounds.right, 0.001f)
        assertEquals(-0.80f, bounds.bottom, 0.001f)
        assertEquals(0.80f, bounds.top, 0.001f)
        assertEquals(null, normalizedCropToMedia3Bounds(CropRect()))
    }

    @Test fun rotationAwareDisplayDimensions_areCorrect() {
        assertEquals(1920 to 1080, displayDimensionsForRotation(1920,1080,0))
        assertEquals(1080 to 1920, displayDimensionsForRotation(1920,1080,90))
        assertEquals(1080 to 1920, displayDimensionsForRotation(1920,1080,270))
    }
}
