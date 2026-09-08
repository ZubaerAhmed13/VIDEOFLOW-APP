package com.videoflow.app.domain.editor

import kotlin.math.roundToLong

/** Pure Long-us mapping used to keep Trim preview inside the half-open clip interval. */
object TrimPreviewMath {
    fun safeTimelinePreviewUs(
        sourceBoundaryUs: Long,
        currentSourceStartUs: Long,
        timelineStartUs: Long,
        timelineEndUsExclusive: Long,
        speed: Double
    ): Long {
        require(speed.isFinite() && speed > 0.0)
        require(timelineEndUsExclusive > timelineStartUs)
        val safeEnd = timelineEndUsExclusive - 1L
        val offsetUs = ((sourceBoundaryUs - currentSourceStartUs).toDouble() / speed).roundToLong()
        return (timelineStartUs + offsetUs).coerceIn(timelineStartUs, safeEnd)
    }
}
