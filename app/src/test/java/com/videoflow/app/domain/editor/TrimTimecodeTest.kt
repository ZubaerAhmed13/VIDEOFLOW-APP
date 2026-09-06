package com.videoflow.app.domain.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimTimecodeTest {
    @Test fun friendlyInputsNormalizeToMicroseconds() {
        assertEquals(5_000_000L, TrimTimecode.parseToUs("5").getOrThrow())
        assertEquals(5_500_000L, TrimTimecode.parseToUs("5.5").getOrThrow())
        assertEquals(5_000_000L, TrimTimecode.parseToUs("00:05").getOrThrow())
        assertEquals(80_000_000L, TrimTimecode.parseToUs("01:20").getOrThrow())
        assertEquals(80_500_000L, TrimTimecode.parseToUs("00:01:20.500").getOrThrow())
        assertEquals(3_723_250_000L, TrimTimecode.parseToUs("1:02:03.250").getOrThrow())
    }

    @Test fun invalidInputsAreRejectedWithoutOverflow() {
        assertTrue(TrimTimecode.parseToUs("").isFailure)
        assertTrue(TrimTimecode.parseToUs("letters").isFailure)
        assertTrue(TrimTimecode.parseToUs("-1").isFailure)
        assertTrue(TrimTimecode.parseToUs("00:99").isFailure)
        assertTrue(TrimTimecode.parseToUs("999999999999999999999999").isFailure)
    }

    @Test fun displayIsAlwaysMillisecondTimecode() {
        assertEquals("00:00:03.500", TrimTimecode.formatUs(3_500_000L))
        assertEquals("01:14:32.125", TrimTimecode.formatUs(4_472_125_000L))
    }

    @Test fun validationCoversBoundsAndOrdering() {
        assertNull(TrimTimecode.validationMessage(3_000_000L, 15_000_000L, 25_000_000L))
        assertEquals("Start time must be before End time.", TrimTimecode.validationMessage(5_000_000L, 5_000_000L, 25_000_000L))
        assertEquals("Maximum end time is 00:00:25.000.", TrimTimecode.validationMessage(0L, 26_000_000L, 25_000_000L))
    }

    @Test fun cfrSnapUsesRationalFpsNotRoundedThirty() {
        assertEquals(1_001_000L, TrimTimecode.snapCfrToNearestFrame(1_000_000L, FrameRate.FPS_2997, 10_000_000L))
        assertEquals(1_001_000L, TrimTimecode.snapCfrToNearestFrame(1_000_000L, FrameRate.FPS_5994, 10_000_000L))
    }
}
