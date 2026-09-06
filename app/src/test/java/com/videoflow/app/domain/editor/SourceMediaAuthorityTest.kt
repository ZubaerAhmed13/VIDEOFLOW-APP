package com.videoflow.app.domain.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SourceMediaAuthorityTest {
    @Test
    fun preserves4kCanvasInsteadOfSilentlyReducingTo1080p() {
        assertEquals(3840 to 2160, SourceMediaAuthority.canvas(3840, 2160))
    }

    @Test
    fun preservesLargeEvenCanvasAndNormalizesOddDimensionsOnly() {
        assertEquals(7680 to 4320, SourceMediaAuthority.canvas(7680, 4320))
        assertEquals(3838 to 2158, SourceMediaAuthority.canvas(3839, 2159))
    }

    @Test
    fun preservesBroadcastFractionalRatesAsRationalValues() {
        assertEquals(FrameRate.FPS_2997, SourceMediaAuthority.frameRate(29.97002997))
        assertEquals(FrameRate.FPS_5994, SourceMediaAuthority.frameRate(59.94005994))
        assertEquals(FrameRate(24_000, 1_001), SourceMediaAuthority.frameRate(23.976023976))
    }

    @Test
    fun doesNotCollapseValidNonPresetRateTo30fps() {
        assertEquals(FrameRate(48, 1), SourceMediaAuthority.frameRate(48.0))
        val rate = SourceMediaAuthority.frameRate(47.952)
        assertNotNull(rate)
        assertEquals(47.952, rate.fps, 0.001)
    }
}
