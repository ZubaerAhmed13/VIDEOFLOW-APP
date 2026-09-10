package com.videoflow.app.ui.editor

import com.videoflow.app.domain.editor.CropRect
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineEngine
import com.videoflow.app.util.formatHumanDurationUs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UXStep2TrimSpeedCropCoreTest {
    private fun clip() = TimelineClip(id="clip", projectId="project", trackId="track", assetId="asset", timelineStartUs=7_000_000L, sourceStartUs=0L, sourceEndUs=120_000_000L)

    @Test fun focusedTrim_changesSourceRangeButKeepsTimelineAnchor() {
        val before = clip()
        val after = TimelineEngine.trimSourceRangeKeepingTimelineAnchor(before, 12_000_000L, 120_000_000L, 120_000_000L)
        assertEquals(7_000_000L, after.timelineStartUs)
        assertEquals(12_000_000L, after.sourceStartUs)
        assertEquals(120_000_000L, after.sourceEndUs)
        assertEquals(108_000_000L, after.timelineDurationUs)
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

    @Test fun cropGeometry_usesOneUniformScale() {
        val full = uniformCropPreviewGeometry(1920,1080,393f,221f,CropRect(0f,0f,1f,1f))
        val portrait = uniformCropPreviewGeometry(1920,1080,393f,221f,CropRect(0.342f,0f,0.658f,1f))
        assertEquals(1f, full.scale, 0.001f)
        assertTrue(portrait.scale > 1f)
        assertTrue(portrait.contentWidthFraction > 0f && portrait.contentHeightFraction > 0f)
    }

    @Test fun rotationAwareDisplayDimensions_areCorrect() {
        assertEquals(1920 to 1080, displayDimensionsForRotation(1920,1080,0))
        assertEquals(1080 to 1920, displayDimensionsForRotation(1920,1080,90))
        assertEquals(1080 to 1920, displayDimensionsForRotation(1920,1080,270))
    }
}
