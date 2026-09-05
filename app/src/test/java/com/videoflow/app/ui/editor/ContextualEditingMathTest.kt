package com.videoflow.app.ui.editor

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
