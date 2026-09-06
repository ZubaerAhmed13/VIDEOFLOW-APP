package com.videoflow.app.domain.editor

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Parser/formatter used by the precise Trim UI.
 *
 * Accepted forms:
 *  - 5              -> 5 seconds
 *  - 5.5            -> 5.5 seconds
 *  - 00:05          -> 5 seconds
 *  - 01:20          -> 1 minute 20 seconds
 *  - 00:01:20.500   -> 80.5 seconds
 *
 * Internal authority remains microseconds. Display is normalized to HH:MM:SS.mmm.
 */
object TrimTimecode {
    const val DEFAULT_MIN_DURATION_US: Long = 100_000L
    private val million = BigDecimal("1000000")
    private val oneMillionInteger = BigInteger.valueOf(1_000_000L)

    fun parseToUs(raw: String): Result<Long> = runCatching {
        val value = raw.trim()
        require(value.isNotEmpty()) { "Enter a time." }
        require(!value.startsWith('-')) { "Time cannot be negative." }
        val parts = value.split(':')
        require(parts.size in 1..3) { "Use seconds, MM:SS, or HH:MM:SS.mmm." }

        fun whole(text: String, label: String): Long {
            require(text.isNotEmpty() && text.all(Char::isDigit)) { "$label must be a whole number." }
            return text.toLongOrNull() ?: error("$label is too large.")
        }

        fun seconds(text: String): BigDecimal {
            require(text.isNotEmpty()) { "Seconds are required." }
            val parsed = text.toBigDecimalOrNull() ?: error("Seconds are not valid.")
            require(parsed.signum() >= 0) { "Time cannot be negative." }
            return parsed
        }

        val totalSeconds = when (parts.size) {
            1 -> seconds(parts[0])
            2 -> {
                val minutes = whole(parts[0], "Minutes")
                val second = seconds(parts[1])
                require(second < BigDecimal("60")) { "Seconds must be below 60." }
                BigDecimal.valueOf(minutes).multiply(BigDecimal("60")).add(second)
            }
            else -> {
                val hours = whole(parts[0], "Hours")
                val minutes = whole(parts[1], "Minutes")
                val second = seconds(parts[2])
                require(minutes < 60L) { "Minutes must be below 60." }
                require(second < BigDecimal("60")) { "Seconds must be below 60." }
                BigDecimal.valueOf(hours).multiply(BigDecimal("3600"))
                    .add(BigDecimal.valueOf(minutes).multiply(BigDecimal("60")))
                    .add(second)
            }
        }
        require(totalSeconds.signum() >= 0) { "Time cannot be negative." }
        totalSeconds.multiply(million).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    fun formatUs(timeUs: Long): String {
        require(timeUs >= 0) { "Time cannot be negative." }
        val totalMs = (timeUs + 500L) / 1_000L
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs / 60_000L) % 60L
        val seconds = (totalMs / 1_000L) % 60L
        val millis = totalMs % 1_000L
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    }

    fun validationMessage(
        startUs: Long,
        endUs: Long,
        sourceDurationUs: Long,
        minimumDurationUs: Long = DEFAULT_MIN_DURATION_US
    ): String? = when {
        startUs < 0L -> "Start time cannot be negative."
        endUs < 0L -> "End time cannot be negative."
        sourceDurationUs <= 0L -> "Source duration is unavailable."
        startUs >= sourceDurationUs -> "Start time must be before the end of the source."
        endUs > sourceDurationUs -> "Maximum end time is ${formatUs(sourceDurationUs)}."
        startUs >= endUs -> "Start time must be before End time."
        endUs - startUs < minimumDurationUs -> "Trim duration must be at least ${formatUs(minimumDurationUs)}."
        else -> null
    }

    /**
     * CFR helper using the exact rational frame rate. Do not use this for known VFR media; VFR
     * boundaries must be resolved against actual sample timestamps by the media/render path.
     */
    fun snapCfrToNearestFrame(timeUs: Long, frameRate: FrameRate, sourceDurationUs: Long): Long {
        require(timeUs >= 0L)
        require(sourceDurationUs >= 0L)
        val numerator = BigInteger.valueOf(timeUs)
            .multiply(BigInteger.valueOf(frameRate.numerator.toLong()))
        val denominator = BigInteger.valueOf(frameRate.denominator.toLong()).multiply(oneMillionInteger)
        val quotient = numerator.divide(denominator)
        val remainder = numerator.remainder(denominator)
        val frameIndex = if (remainder.shiftLeft(1) >= denominator) quotient + BigInteger.ONE else quotient
        val timestamp = frameIndex
            .multiply(BigInteger.valueOf(frameRate.denominator.toLong()))
            .multiply(oneMillionInteger)
            .divide(BigInteger.valueOf(frameRate.numerator.toLong()))
            .longValueExact()
        return timestamp.coerceIn(0L, sourceDurationUs)
    }
}
