package com.videoflow.app.render

import com.videoflow.app.domain.editor.FrameRate
import kotlin.math.abs

/**
 * Validates encoded video cadence from actual MP4 presentation timestamps rather than trusting a
 * container metadata label. This is what distinguishes 29.97 from 30 and 59.94 from 60.
 */
object FrameCadenceVerifier {
    data class Measurement(
        val sampleCount: Int,
        val measuredFps: Double,
        val firstTimestampUs: Long,
        val lastTimestampUs: Long
    )

    fun measure(sampleTimesUs: List<Long>): Measurement? {
        val monotonic = sampleTimesUs.asSequence()
            .filter { it >= 0L }
            .distinct()
            .sorted()
            .toList()
        if (monotonic.size < 3) return null
        val spanUs = monotonic.last() - monotonic.first()
        if (spanUs <= 0L) return null
        val measured = (monotonic.size - 1).toDouble() * 1_000_000.0 / spanUs.toDouble()
        return Measurement(monotonic.size, measured, monotonic.first(), monotonic.last())
    }

    fun toleranceFps(target: FrameRate): Double = maxOf(0.01, target.fps * 0.0005)

    fun matches(measuredFps: Double, target: FrameRate): Boolean =
        measuredFps.isFinite() && abs(measuredFps - target.fps) <= toleranceFps(target)

    fun mismatchMessage(measuredFps: Double, target: FrameRate): String =
        "Encoded cadence %.5f fps does not match requested %.5f fps (tolerance %.5f).".format(
            java.util.Locale.US,
            measuredFps,
            target.fps,
            toleranceFps(target)
        )
}
