package com.videoflow.app.domain.ai

import org.junit.Assert.*
import org.junit.Test

class AiLongJobPlanTest {
    @Test fun thirtyMinuteProductionScheduleHasExactContinuousBoundaries() {
        val plan=AiLongJobPlan(1_800_000_000L)
        assertEquals(30L,plan.count)
        var cursor=0L
        for(segment in plan.segments()) {
            assertEquals(cursor,segment.startUs)
            assertEquals(60_000_000L,segment.durationUs)
            cursor=segment.endUs
        }
        assertEquals(plan.durationUs,cursor)
        assertEquals(1f,plan.progress(cursor),0f)
        assertEquals(.5f,plan.progress(900_000_000L),0f)
        assertEquals(900_000_000L,plan.segments(15).first().startUs)
        assertFalse(plan.segments(plan.count).iterator().hasNext())
    }
    @Test fun longAndRaggedSchedulesNeverUseIntTimeOffsets() {
        val plan=AiLongJobPlan(36_000_123_456L)
        assertEquals(601L,plan.count)
        assertEquals(123_456L,plan.segment(600).durationUs)
        assertTrue(plan.storageBytes(100_000_000L)>3L*1024*1024*1024)
        assertEquals(plan.durationUs,plan.segment(600).endUs)
        assertEquals(3,AiResourceProfile().maximumQueuedTasks)
    }
    @Test fun hugePlanCanSeekWithoutAllocatingAllSegments() {
        val plan=AiLongJobPlan(Long.MAX_VALUE,1_000_000L)
        assertEquals(Long.MAX_VALUE,plan.segment(plan.count-1).endUs)
        assertEquals(3,plan.segments().take(3).count())
    }
    @Test(expected=ArithmeticException::class) fun impossibleStorageEstimateFailsSafely() {
        AiLongJobPlan(Long.MAX_VALUE).storageBytes(Long.MAX_VALUE/2L)
    }
}
