package com.videoflow.app.render

import com.videoflow.app.domain.editor.FrameRate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

class FrameCadenceVerifierTest {
    @Test
    fun `2997 cadence is accepted and integer 30 is rejected`() {
        val ntsc = timestamps(FrameRate.FPS_2997, 240)
        val measurement = FrameCadenceVerifier.measure(ntsc)
        assertNotNull(measurement)
        assertTrue(FrameCadenceVerifier.matches(measurement!!.measuredFps, FrameRate.FPS_2997))

        val roundedThirty = timestamps(FrameRate.FPS_30, 240)
        val roundedMeasurement = FrameCadenceVerifier.measure(roundedThirty)!!
        assertFalse(FrameCadenceVerifier.matches(roundedMeasurement.measuredFps, FrameRate.FPS_2997))
    }

    @Test
    fun `5994 cadence is accepted and integer 60 is rejected`() {
        val ntsc = timestamps(FrameRate.FPS_5994, 480)
        val measurement = FrameCadenceVerifier.measure(ntsc)!!
        assertTrue(FrameCadenceVerifier.matches(measurement.measuredFps, FrameRate.FPS_5994))

        val sixty = FrameCadenceVerifier.measure(timestamps(FrameRate.FPS_60, 480))!!
        assertFalse(FrameCadenceVerifier.matches(sixty.measuredFps, FrameRate.FPS_5994))
    }

    @Test
    fun `23976 cadence remains distinct from integer 24`() {
        val target = FrameRate(24_000, 1_001)
        val targetMeasurement = FrameCadenceVerifier.measure(timestamps(target, 240))!!
        assertTrue(FrameCadenceVerifier.matches(targetMeasurement.measuredFps, target))

        val integer = FrameCadenceVerifier.measure(timestamps(FrameRate.FPS_24, 240))!!
        assertFalse(FrameCadenceVerifier.matches(integer.measuredFps, target))
    }

    @Test
    fun `measurement rejects insufficient timestamps`() {
        assertTrue(FrameCadenceVerifier.measure(listOf(0L, 33_366L)) == null)
    }

    private fun timestamps(rate: FrameRate, count: Int): List<Long> =
        (0 until count).map { index ->
            (index.toDouble() * 1_000_000.0 * rate.denominator.toDouble() / rate.numerator.toDouble()).roundToLong()
        }
}
