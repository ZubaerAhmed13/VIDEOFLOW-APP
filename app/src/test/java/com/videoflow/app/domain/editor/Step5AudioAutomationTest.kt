package com.videoflow.app.domain.editor

import org.junit.Assert.*
import org.junit.Test

class Step5AudioAutomationTest {
    private fun frame(n: Int, time: Long, value: Float, mode: KeyframeInterpolation = KeyframeInterpolation.LINEAR) =
        Keyframe("$n", "clip", KeyframeOwnerType.CLIP, KeyframeProperty.AUDIO_GAIN, time, value, mode)

    @Test fun longTimelineDenseAutomationUsesBoundedLookupWithoutSortingPerSample() {
        val start = 3_600_000_000L
        val frames = List(2000) { frame(it, start + it * 1_000_000L, it.toFloat()) }
        var reads = 0
        val measured = object : AbstractList<Keyframe>() {
            override val size = frames.size
            override fun get(index: Int): Keyframe { reads++; return frames[index] }
        }
        repeat(1000) { n ->
            assertEquals(n + .5f, KeyframeEvaluator.evaluateSorted(-6f, start + n * 1_000_000L + 500_000L, measured), .0001f)
        }
        assertTrue("Audio automation scanned or copied the dense list: $reads reads", reads < 20_000)
    }

    @Test fun sortedAutomationPreservesBaseHoldDuplicateAndEndpointRules() {
        val frames = listOf(frame(0, 100, -6f, KeyframeInterpolation.HOLD), frame(1, 200, -3f),
            frame(2, 200, -2f), frame(3, 300, 0f))
        assertEquals(-9f, KeyframeEvaluator.evaluateSorted(-9f, 99, frames), 0f)
        assertEquals(-6f, KeyframeEvaluator.evaluateSorted(-9f, 150, frames), 0f)
        assertEquals(-3f, KeyframeEvaluator.evaluateSorted(-9f, 200, frames), 0f)
        assertEquals(-1f, KeyframeEvaluator.evaluateSorted(-9f, 250, frames), 0f)
        assertEquals(0f, KeyframeEvaluator.evaluateSorted(-9f, 400, frames), 0f)
    }
}
