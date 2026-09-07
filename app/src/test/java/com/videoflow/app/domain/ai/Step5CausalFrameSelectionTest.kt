package com.videoflow.app.domain.ai

import com.videoflow.app.render.CausalFrameSelection
import org.junit.Assert.*
import org.junit.Test

class Step5CausalFrameSelectionTest {
    @Test fun slowSpeedFlashNeverAppearsBeforeItsAuthoredTimestamp() {
        val sourceFrames=listOf(733_333L,800_000L,866_667L)
        fun selected(output: Long)=sourceFrames.minBy { CausalFrameSelection.distanceUs(it-output) }
        assertEquals(733_333L,selected(766_666L))
        assertEquals(800_000L,selected(800_000L))
        assertEquals(800_000L,selected(833_333L))
    }
    @Test fun variableCadenceAndLateStartsRetainTheMostRecentValidFrame() {
        val frames=listOf(3_600_000_000L,3_600_040_000L,3_600_150_000L)
        for(output in 3_600_000_000L..3_600_200_000L step 1_000L) {
            val selected=frames.minBy { CausalFrameSelection.distanceUs(it-output) }
            assertEquals(frames.last { it<=output },selected)
        }
        assertEquals(frames.first(),frames.minBy { CausalFrameSelection.distanceUs(it-2_000_000_000L) })
        assertTrue(CausalFrameSelection.distanceUs(Long.MIN_VALUE)>0)
    }
}
