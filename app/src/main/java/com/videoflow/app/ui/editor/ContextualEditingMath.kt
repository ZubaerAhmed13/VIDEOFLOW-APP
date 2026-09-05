package com.videoflow.app.ui.editor

import kotlin.math.abs

/** Small pure helpers shared by direct-manipulation UI and its JVM regression tests. */
internal fun displayDimensionsForRotation(
    encodedWidth: Int,
    encodedHeight: Int,
    rotationDegrees: Int?
): Pair<Int, Int> {
    require(encodedWidth > 0 && encodedHeight > 0)
    val normalized = (((rotationDegrees ?: 0) % 360) + 360) % 360
    return if (normalized == 90 || normalized == 270) {
        encodedHeight to encodedWidth
    } else {
        encodedWidth to encodedHeight
    }
}

internal fun snapNormalizedToCenter(
    value: Float,
    center: Float = 0.5f,
    threshold: Float = 0.018f
): Float {
    val bounded = value.coerceIn(0f, 1f)
    return if (abs(bounded - center) < threshold) center else bounded
}

internal fun keyframeMarkerFraction(timeUs: Long, ownerDurationUs: Long): Float {
    if (ownerDurationUs <= 0L) return 0f
    return (timeUs.toDouble() / ownerDurationUs.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

internal fun timelineAutoScrollDelta(
    visibleXPx: Float,
    viewportWidthPx: Float,
    edgePx: Float,
    maxStepPx: Float
): Float {
    if (viewportWidthPx <= 0f || edgePx <= 0f || maxStepPx <= 0f) return 0f
    return when {
        visibleXPx < edgePx -> -maxStepPx * ((edgePx - visibleXPx) / edgePx).coerceIn(0f, 1f)
        visibleXPx > viewportWidthPx - edgePx ->
            maxStepPx * ((visibleXPx - (viewportWidthPx - edgePx)) / edgePx).coerceIn(0f, 1f)
        else -> 0f
    }
}
