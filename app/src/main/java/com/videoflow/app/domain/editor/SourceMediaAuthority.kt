package com.videoflow.app.domain.editor

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Converts source metadata into project authority without imposing a preview-resolution cap.
 * Device encoder capability is checked later by export preflight; project/source fidelity should
 * not silently become 1080p merely because a source is 4K or larger.
 */
object SourceMediaAuthority {
    fun canvas(width: Int?, height: Int?): Pair<Int, Int>? {
        if (width == null || height == null || width <= 0 || height <= 0) return null
        return evenDimension(width) to evenDimension(height)
    }

    fun frameRate(value: Double?): FrameRate {
        if (value == null || !value.isFinite() || value <= 0.0) return FrameRate.FPS_30

        val known = listOf(
            23.976023976 to FrameRate(24_000, 1_001),
            24.0 to FrameRate.FPS_24,
            25.0 to FrameRate.FPS_25,
            29.970029970 to FrameRate.FPS_2997,
            30.0 to FrameRate.FPS_30,
            50.0 to FrameRate(50, 1),
            59.940059940 to FrameRate.FPS_5994,
            60.0 to FrameRate.FPS_60
        )
        known.minByOrNull { abs(it.first - value) }
            ?.takeIf { abs(it.first - value) <= 0.02 }
            ?.let { return it.second }

        val nearestInteger = value.roundToInt()
        if (nearestInteger > 0 && abs(value - nearestInteger.toDouble()) <= 0.005) {
            return FrameRate(nearestInteger, 1)
        }

        var bestNumerator = value.roundToInt().coerceAtLeast(1)
        var bestDenominator = 1
        var bestError = abs(value - bestNumerator.toDouble())
        for (denominator in 2..1_001) {
            val numerator = (value * denominator).roundToInt().coerceAtLeast(1)
            val error = abs(value - numerator.toDouble() / denominator.toDouble())
            if (error < bestError) {
                bestError = error
                bestNumerator = numerator
                bestDenominator = denominator
            }
        }
        val divisor = gcd(bestNumerator, bestDenominator)
        return FrameRate(bestNumerator / divisor, bestDenominator / divisor)
    }

    private fun evenDimension(value: Int): Int = when {
        value < 2 -> 2
        value % 2 == 0 -> value
        else -> value - 1
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) kotlin.math.abs(a) else gcd(b, a % b)
}
