package com.videoflow.app.domain.editor

import kotlin.math.roundToLong

/** A bounded layout window, independent of the full project's Long time domain. */
object TimelineViewport {
    const val MAX_WIDTH_DP = 12_000.0
    fun durationUs(scale: Float): Long = (MAX_WIDTH_DP / scale.coerceIn(12f,240f) * 1_000_000.0).roundToLong()
    fun timeAt(originUs: Long, xDp: Double, scale: Float): Long =
        originUs + (xDp / scale.coerceIn(12f,240f) * 1_000_000.0).roundToLong()
    fun positionDp(timeUs: Long, originUs: Long, scale: Float): Double = (timeUs-originUs).toDouble()/1_000_000.0*scale
    fun zoomOrigin(originUs: Long, anchorDp: Double, oldScale: Float, newScale: Float, durationUs: Long): Long =
        (timeAt(originUs,anchorDp,oldScale)-(anchorDp/newScale*1_000_000.0).roundToLong())
            .coerceIn(0L,(durationUs-durationUs(newScale)).coerceAtLeast(0L))
    fun centeredOrigin(timeUs: Long, scale: Float, durationUs: Long): Long =
        (timeUs-durationUs(scale)/2).coerceIn(0L,(durationUs-durationUs(scale)).coerceAtLeast(0L))
    fun intersects(startUs: Long,endUs: Long,visibleStartUs: Long,visibleEndUs: Long): Boolean = endUs>visibleStartUs && startUs<visibleEndUs
}
