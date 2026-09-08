package com.videoflow.app.domain.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class TrimPreviewMathTest {
    @Test fun endBoundaryNeverReturnsExclusiveClipEnd() {
        assertEquals(
            9_999_999L,
            TrimPreviewMath.safeTimelinePreviewUs(
                sourceBoundaryUs = 10_000_000L,
                currentSourceStartUs = 0L,
                timelineStartUs = 0L,
                timelineEndUsExclusive = 10_000_000L,
                speed = 1.0
            )
        )
    }

    @Test fun largeHourScaleValuesRemainLongSafe() {
        val start = 36_000_000_000L
        val end = start + 3_600_000_000L
        assertEquals(
            end - 1L,
            TrimPreviewMath.safeTimelinePreviewUs(
                sourceBoundaryUs = 3_600_000_000L,
                currentSourceStartUs = 0L,
                timelineStartUs = start,
                timelineEndUsExclusive = end,
                speed = 1.0
            )
        )
    }
}
