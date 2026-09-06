package com.videoflow.app.ai.watermark

import android.graphics.Bitmap
import android.graphics.Color
import com.videoflow.app.domain.ai.NormalizedRoi
import com.videoflow.app.domain.ai.RoiMotionAnchor
import com.videoflow.app.domain.editor.TimelineClip
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Lightweight, fully local block tracker used to seed Watermark Studio motion anchors.
 * It samples a bounded number of small decoded frames and searches a bounded neighborhood around
 * the previous ROI centre. It never scans a whole source file or holds more than two small frames.
 */
@Singleton
class LocalRoiTracker @Inject constructor(
    private val previewEngine: LocalWatermarkPreviewEngine
) {
    data class TrackingResult(
        val anchors: List<RoiMotionAnchor>,
        val averageConfidence: Float
    )

    suspend fun track(
        sourceUri: String,
        clip: TimelineClip,
        roi: NormalizedRoi,
        clipLocalStartUs: Long,
        clipLocalEndUs: Long,
        onProgress: (Float) -> Unit = {}
    ): TrackingResult = withContext(Dispatchers.Default) {
        require(clipLocalStartUs >= 0L)
        require(clipLocalEndUs > clipLocalStartUs)
        require(clipLocalEndUs <= clip.timelineDurationUs)

        val durationUs = clipLocalEndUs - clipLocalStartUs
        val sampleCount = min(16, max(2, ceil(durationUs / 500_000.0).toInt() + 1))
        val times = (0 until sampleCount).map { index ->
            if (index == sampleCount - 1) clipLocalEndUs - 1L
            else clipLocalStartUs + (durationUs * index.toLong() / (sampleCount - 1).toLong())
        }.distinct()

        var reference: IntArray? = null
        var previousCenterX = (roi.left + roi.right) / 2f
        var previousCenterY = (roi.top + roi.bottom) / 2f
        val anchors = mutableListOf<RoiMotionAnchor>()
        var confidenceSum = 0f

        times.forEachIndexed { index, clipLocalUs ->
            coroutineContext.ensureActive()
            val sourceUs = clip.sourceStartUs + (clipLocalUs.toDouble() * clip.speed).roundToLong()
            val boundedSourceUs = sourceUs.coerceIn(clip.sourceStartUs, clip.sourceEndUs - 1L)
            val bitmap = previewEngine.decodeFrame(sourceUri, boundedSourceUs, maxDimensionPx = 360)
            try {
                val gray = GrayFrame.from(bitmap)
                // Keep one-pixel headroom on both sides so center clamping remains valid even for
                // a user-selected edge-to-edge ROI.
                val maxPatchWidth = (gray.width - 2).coerceAtLeast(12)
                val maxPatchHeight = (gray.height - 2).coerceAtLeast(12)
                val roiWidthPx = (roi.width * gray.width).roundToInt().coerceIn(12, maxPatchWidth)
                val roiHeightPx = (roi.height * gray.height).roundToInt().coerceIn(12, maxPatchHeight)
                val referenceSamples = reference ?: samplePatch(
                    gray,
                    previousCenterX,
                    previousCenterY,
                    roiWidthPx,
                    roiHeightPx
                ).also { reference = it }

                val match = if (index == 0) {
                    Match(previousCenterX, previousCenterY, 1f)
                } else {
                    findBestMatch(
                        frame = gray,
                        reference = referenceSamples,
                        previousCenterX = previousCenterX,
                        previousCenterY = previousCenterY,
                        roiWidthPx = roiWidthPx,
                        roiHeightPx = roiHeightPx,
                        roi = roi
                    )
                }
                previousCenterX = match.centerX
                previousCenterY = match.centerY
                confidenceSum += match.confidence
                anchors += RoiMotionAnchor(
                    clipLocalTimeUs = clipLocalUs,
                    centerX = match.centerX,
                    centerY = match.centerY,
                    confidence = match.confidence
                )

                // Slow template adaptation reduces drift when lighting changes without allowing a
                // single bad frame to completely replace the reference patch.
                if (index > 0 && match.confidence >= 0.35f) {
                    val current = samplePatch(gray, match.centerX, match.centerY, roiWidthPx, roiHeightPx)
                    reference = IntArray(referenceSamples.size) { i ->
                        (referenceSamples[i] * 0.75f + current[i] * 0.25f).roundToInt()
                    }
                }
            } finally {
                bitmap.recycle()
            }
            onProgress((index + 1).toFloat() / times.size.toFloat())
        }

        TrackingResult(
            anchors = anchors,
            averageConfidence = if (anchors.isEmpty()) 0f else confidenceSum / anchors.size.toFloat()
        )
    }

    private data class Match(val centerX: Float, val centerY: Float, val confidence: Float)

    private fun findBestMatch(
        frame: GrayFrame,
        reference: IntArray,
        previousCenterX: Float,
        previousCenterY: Float,
        roiWidthPx: Int,
        roiHeightPx: Int,
        roi: NormalizedRoi
    ): Match {
        val previousX = (previousCenterX * frame.width).roundToInt()
        val previousY = (previousCenterY * frame.height).roundToInt()
        val radiusX = max(12, (frame.width * 0.10f).roundToInt())
        val radiusY = max(12, (frame.height * 0.10f).roundToInt())
        val step = max(2, min(roiWidthPx, roiHeightPx) / 12)
        val halfW = roiWidthPx / 2
        val halfH = roiHeightPx / 2
        val minCenterX = halfW.coerceAtLeast(0)
        val maxCenterX = (frame.width - 1 - halfW).coerceAtLeast(minCenterX)
        val minCenterY = halfH.coerceAtLeast(0)
        val maxCenterY = (frame.height - 1 - halfH).coerceAtLeast(minCenterY)
        var bestError = Float.MAX_VALUE
        var bestX = previousX.coerceIn(minCenterX, maxCenterX)
        var bestY = previousY.coerceIn(minCenterY, maxCenterY)

        var y = (previousY - radiusY).coerceAtLeast(minCenterY)
        val maxY = (previousY + radiusY).coerceAtMost(maxCenterY)
        while (y <= maxY) {
            var x = (previousX - radiusX).coerceAtLeast(minCenterX)
            val maxX = (previousX + radiusX).coerceAtMost(maxCenterX)
            while (x <= maxX) {
                val candidate = samplePatchPixels(frame, x, y, roiWidthPx, roiHeightPx)
                var error = 0f
                for (i in reference.indices) error += kotlin.math.abs(reference[i] - candidate[i]).toFloat()
                error /= reference.size.toFloat()
                if (error < bestError) {
                    bestError = error
                    bestX = x
                    bestY = y
                }
                x += step
            }
            y += step
        }

        val confidence = (1f - bestError / 255f).coerceIn(0f, 1f)
        return Match(
            centerX = (bestX.toFloat() / frame.width).coerceIn(roi.width / 2f, 1f - roi.width / 2f),
            centerY = (bestY.toFloat() / frame.height).coerceIn(roi.height / 2f, 1f - roi.height / 2f),
            confidence = confidence
        )
    }

    private fun samplePatch(
        frame: GrayFrame,
        centerX: Float,
        centerY: Float,
        roiWidthPx: Int,
        roiHeightPx: Int
    ): IntArray = samplePatchPixels(
        frame,
        (centerX * frame.width).roundToInt(),
        (centerY * frame.height).roundToInt(),
        roiWidthPx,
        roiHeightPx
    )

    /** 9x9 normalized luminance grid: bounded and resolution-independent. */
    private fun samplePatchPixels(
        frame: GrayFrame,
        centerX: Int,
        centerY: Int,
        roiWidthPx: Int,
        roiHeightPx: Int
    ): IntArray {
        val result = IntArray(81)
        var out = 0
        for (gy in 0 until 9) {
            val fy = if (gy == 8) 1f else gy / 8f
            val y = (centerY - roiHeightPx / 2f + fy * (roiHeightPx - 1)).roundToInt()
                .coerceIn(0, frame.height - 1)
            for (gx in 0 until 9) {
                val fx = if (gx == 8) 1f else gx / 8f
                val x = (centerX - roiWidthPx / 2f + fx * (roiWidthPx - 1)).roundToInt()
                    .coerceIn(0, frame.width - 1)
                result[out++] = frame.luma[y * frame.width + x]
            }
        }
        return result
    }

    private data class GrayFrame(val width: Int, val height: Int, val luma: IntArray) {
        companion object {
            fun from(bitmap: Bitmap): GrayFrame {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val luma = IntArray(pixels.size)
                for (i in pixels.indices) {
                    val color = pixels[i]
                    luma[i] = ((Color.red(color) * 77 + Color.green(color) * 150 + Color.blue(color) * 29) shr 8)
                }
                return GrayFrame(bitmap.width, bitmap.height, luma)
            }
        }
    }
}
