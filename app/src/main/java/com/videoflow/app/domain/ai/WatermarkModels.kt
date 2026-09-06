package com.videoflow.app.domain.ai

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Normalized Android/image-space rectangle: origin is top-left, all edges are in [0,1]. */
data class NormalizedRoi(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() })
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(right > left && bottom > top)
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun translated(centerX: Float, centerY: Float): NormalizedRoi {
        val halfW = width / 2f
        val halfH = height / 2f
        val cx = centerX.coerceIn(halfW, 1f - halfW)
        val cy = centerY.coerceIn(halfH, 1f - halfH)
        return NormalizedRoi(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
    }

    fun expandedPixels(pixels: Int, frameWidth: Int, frameHeight: Int): NormalizedRoi {
        require(pixels >= 0 && frameWidth > 0 && frameHeight > 0)
        val dx = pixels.toFloat() / frameWidth
        val dy = pixels.toFloat() / frameHeight
        return NormalizedRoi(
            (left - dx).coerceAtLeast(0f),
            (top - dy).coerceAtLeast(0f),
            (right + dx).coerceAtMost(1f),
            (bottom + dy).coerceAtMost(1f)
        )
    }
}

data class RoiMotionAnchor(
    val clipLocalTimeUs: Long,
    val centerX: Float,
    val centerY: Float,
    val confidence: Float = 1f
) {
    init {
        require(clipLocalTimeUs >= 0L)
        require(centerX in 0f..1f && centerY in 0f..1f)
        require(confidence in 0f..1f)
    }
}

data class AiWatermarkEffect(
    val id: String,
    val projectId: String,
    val clipId: String,
    val clipLocalStartUs: Long,
    val clipLocalEndUs: Long,
    val roi: NormalizedRoi,
    val motionAnchors: List<RoiMotionAnchor> = emptyList(),
    val contextPaddingPx: Int = 48,
    val featherPx: Int = 8,
    val temporalStability: Float = 0.12f,
    val modelId: String = AiModelCatalog.FINAL_512.id,
    val enabled: Boolean = true
) {
    init {
        require(id.isNotBlank() && projectId.isNotBlank() && clipId.isNotBlank())
        require(clipLocalStartUs >= 0L && clipLocalEndUs > clipLocalStartUs)
        require(contextPaddingPx in 0..256)
        require(featherPx in 0..128)
        require(temporalStability in 0f..0.5f)
        require(modelId.isNotBlank())
    }

    fun activeAt(clipLocalTimeUs: Long): Boolean = enabled && clipLocalTimeUs in clipLocalStartUs until clipLocalEndUs

    fun roiAt(clipLocalTimeUs: Long): NormalizedRoi = AiWatermarkMath.roiAt(this, clipLocalTimeUs)
}

enum class AiModelRole { FINAL, PREVIEW }

data class AiModelSpec(
    val id: String,
    val role: AiModelRole,
    val assetPath: String,
    val fileName: String,
    val sha256: String,
    val expectedBytes: Long,
    val inferenceSize: Int,
    val license: String,
    val sourceLabel: String
)

object AiModelCatalog {
    /** Same checksum-pinned production model as the approved VideoFlow web reconstruction pack. */
    val FINAL_512 = AiModelSpec(
        id = "lama-512-int8-v1",
        role = AiModelRole.FINAL,
        assetPath = "models/lama-512-int8.onnx",
        fileName = "lama-512-int8.onnx",
        sha256 = "cab19978adc306622fe37ef60d4a52103b99c98141d499c2a2366a7ed1255dbe",
        expectedBytes = 62_074_990L,
        inferenceSize = 512,
        license = "Apache-2.0",
        sourceLabel = "g-ronimo/LaMa fixed 512 INT8"
    )

    val PREVIEW_DYNAMIC = AiModelSpec(
        id = "lama-dynamic-int8-v1",
        role = AiModelRole.PREVIEW,
        assetPath = "models/lama-dynamic-int8.onnx",
        fileName = "lama-dynamic-int8.onnx",
        sha256 = "1941214c210399eb815eb2d32570ba91d5e6c4ac3de4c939bd3fb09300454972",
        expectedBytes = 61_512_617L,
        inferenceSize = 256,
        license = "Apache-2.0",
        sourceLabel = "g-ronimo/LaMa dynamic INT8"
    )

    val all: List<AiModelSpec> = listOf(FINAL_512, PREVIEW_DYNAMIC)
    fun byId(id: String): AiModelSpec? = all.firstOrNull { it.id == id }
}

data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init { require(left >= 0 && top >= 0 && right > left && bottom > top) }
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class AiTile(
    /** Pixels actually replaced by this tile. Core tiles never overlap. */
    val core: PixelRect,
    /** Context read for LaMa. This may overlap adjacent tiles and is never larger than 512x512. */
    val read: PixelRect
)

object AiWatermarkMath {
    const val FINAL_TILE_SIZE = 512

    fun roiAt(effect: AiWatermarkEffect, clipLocalTimeUs: Long): NormalizedRoi {
        val anchors = effect.motionAnchors.sortedBy { it.clipLocalTimeUs }
        if (anchors.isEmpty()) return effect.roi
        if (clipLocalTimeUs <= anchors.first().clipLocalTimeUs) {
            return effect.roi.translated(anchors.first().centerX, anchors.first().centerY)
        }
        if (clipLocalTimeUs >= anchors.last().clipLocalTimeUs) {
            return effect.roi.translated(anchors.last().centerX, anchors.last().centerY)
        }
        val rightIndex = anchors.indexOfFirst { it.clipLocalTimeUs >= clipLocalTimeUs }
        val right = anchors[rightIndex]
        val left = anchors[rightIndex - 1]
        val span = (right.clipLocalTimeUs - left.clipLocalTimeUs).coerceAtLeast(1L)
        val t = ((clipLocalTimeUs - left.clipLocalTimeUs).toDouble() / span).coerceIn(0.0, 1.0).toFloat()
        val cx = left.centerX + (right.centerX - left.centerX) * t
        val cy = left.centerY + (right.centerY - left.centerY) * t
        return effect.roi.translated(cx, cy)
    }

    fun toPixelRect(roi: NormalizedRoi, frameWidth: Int, frameHeight: Int): PixelRect {
        require(frameWidth > 0 && frameHeight > 0)
        val left = floor(roi.left * frameWidth).toInt().coerceIn(0, frameWidth - 1)
        val top = floor(roi.top * frameHeight).toInt().coerceIn(0, frameHeight - 1)
        val right = ceil(roi.right * frameWidth).toInt().coerceIn(left + 1, frameWidth)
        val bottom = ceil(roi.bottom * frameHeight).toInt().coerceIn(top + 1, frameHeight)
        return PixelRect(left, top, right, bottom)
    }

    /** Converts top-left image coordinates to bottom-left OpenGL pixel coordinates. */
    fun toOpenGlRect(rect: PixelRect, frameHeight: Int): PixelRect {
        require(frameHeight >= rect.bottom)
        return PixelRect(rect.left, frameHeight - rect.bottom, rect.right, frameHeight - rect.top)
    }

    /**
     * Splits an arbitrarily large ROI into non-overlapping replacement cores with bounded context.
     * Each read rectangle is <= modelSize in both axes, so 4K/8K frames never require whole-frame
     * CPU readback just because AI is active.
     */
    fun planTiles(
        target: PixelRect,
        frameWidth: Int,
        frameHeight: Int,
        modelSize: Int = FINAL_TILE_SIZE,
        contextPx: Int = 48
    ): List<AiTile> {
        require(frameWidth > 0 && frameHeight > 0)
        require(modelSize >= 64)
        require(contextPx >= 0 && contextPx * 2 < modelSize)
        val coreMax = modelSize - contextPx * 2
        val result = mutableListOf<AiTile>()
        var y = target.top
        while (y < target.bottom) {
            val coreBottom = min(target.bottom, y + coreMax)
            var x = target.left
            while (x < target.right) {
                val coreRight = min(target.right, x + coreMax)
                val core = PixelRect(x, y, coreRight, coreBottom)
                var readLeft = max(0, core.left - contextPx)
                var readTop = max(0, core.top - contextPx)
                var readRight = min(frameWidth, core.right + contextPx)
                var readBottom = min(frameHeight, core.bottom + contextPx)

                // At frame edges, grow the opposite side when possible so the model retains context.
                val wantW = min(modelSize, frameWidth)
                val wantH = min(modelSize, frameHeight)
                if (readRight - readLeft < wantW) {
                    val missing = wantW - (readRight - readLeft)
                    val takeLeft = min(readLeft, missing)
                    readLeft -= takeLeft
                    readRight = min(frameWidth, readRight + (missing - takeLeft))
                }
                if (readBottom - readTop < wantH) {
                    val missing = wantH - (readBottom - readTop)
                    val takeTop = min(readTop, missing)
                    readTop -= takeTop
                    readBottom = min(frameHeight, readBottom + (missing - takeTop))
                }
                val read = PixelRect(readLeft, readTop, readRight, readBottom)
                require(read.width <= modelSize && read.height <= modelSize)
                result += AiTile(core, read)
                x = coreRight
            }
            y = coreBottom
        }
        return result
    }

    fun estimatedWorkingSetBytes(tileSize: Int = FINAL_TILE_SIZE): Long {
        require(tileSize > 0)
        // RGBA input + RGB float input + mask float + RGB float output + RGBA output.
        val pixels = tileSize.toLong() * tileSize
        return pixels * (4L + 12L + 4L + 12L + 4L)
    }
}
