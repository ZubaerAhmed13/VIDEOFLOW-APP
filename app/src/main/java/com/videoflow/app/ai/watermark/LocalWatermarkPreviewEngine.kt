package com.videoflow.app.ai.watermark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import com.videoflow.app.domain.ai.AiModelRole
import com.videoflow.app.domain.ai.NormalizedRoi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bounded single-frame preview path for Watermark Studio.
 *
 * This never replaces the final renderer. It decodes a bounded display frame, runs the checksum-
 * pinned PREVIEW LaMa model locally, and composites only the selected normalized ROI. The saved
 * effect always targets the FINAL model and is re-evaluated against original-resolution pixels by
 * Media3RenderEngine during export.
 */
@Singleton
class LocalWatermarkPreviewEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelPackManager: AiModelPackManager
) {
    data class PreviewResult(
        val bitmap: Bitmap,
        val provider: String
    )

    suspend fun decodeFrame(
        sourceUri: String,
        sourceTimeUs: Long,
        maxDimensionPx: Int = 960
    ): Bitmap = withContext(Dispatchers.IO) {
        require(maxDimensionPx in 128..2048)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(sourceUri))
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?.coerceAtLeast(1) ?: 1280
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?.coerceAtLeast(1) ?: 720
            val scale = min(1.0, maxDimensionPx.toDouble() / max(rawWidth, rawHeight).toDouble())
            val targetWidth = (rawWidth * scale).roundToInt().coerceAtLeast(1)
            val targetHeight = (rawHeight * scale).roundToInt().coerceAtLeast(1)
            val frame = if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(
                    sourceTimeUs.coerceAtLeast(0L),
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    targetWidth,
                    targetHeight
                )
            } else {
                retriever.getFrameAtTime(sourceTimeUs.coerceAtLeast(0L), MediaMetadataRetriever.OPTION_CLOSEST)
                    ?.let { original ->
                        if (original.width == targetWidth && original.height == targetHeight) original
                        else Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true).also { original.recycle() }
                    }
            }
            requireNotNull(frame) { "Could not decode a preview frame from the selected source." }
                .copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            retriever.release()
        }
    }

    suspend fun render(
        sourceUri: String,
        sourceTimeUs: Long,
        roi: NormalizedRoi,
        sourceWidth: Int,
        sourceHeight: Int,
        featherPx: Int
    ): PreviewResult = withContext(Dispatchers.Default) {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(featherPx >= 0)
        val frame = decodeFrame(sourceUri, sourceTimeUs, maxDimensionPx = 960)
        coroutineContext.ensureActive()
        val modelSize = 256
        val modelBitmap = Bitmap.createScaledBitmap(frame, modelSize, modelSize, true)
        val modelPixels = IntArray(modelSize * modelSize)
        modelBitmap.getPixels(modelPixels, 0, modelSize, 0, 0, modelSize, modelSize)
        if (modelBitmap !== frame) modelBitmap.recycle()

        val pixelCount = modelSize * modelSize
        val packed = FloatArray(pixelCount * 4)
        for (y in 0 until modelSize) {
            if ((y and 31) == 0) coroutineContext.ensureActive()
            val ny = (y + 0.5f) / modelSize.toFloat()
            for (x in 0 until modelSize) {
                val nx = (x + 0.5f) / modelSize.toFloat()
                val index = y * modelSize + x
                val inside = nx >= roi.left && nx < roi.right && ny >= roi.top && ny < roi.bottom
                val keep = if (inside) 0f else 1f
                val color = modelPixels[index]
                packed[index] = Color.red(color) / 255f * keep
                packed[pixelCount + index] = Color.green(color) / 255f * keep
                packed[pixelCount * 2 + index] = Color.blue(color) / 255f * keep
                packed[pixelCount * 3 + index] = if (inside) 1f else 0f
            }
        }

        val output = modelPackManager.openSession(AiModelRole.PREVIEW, preferNnapi = true).use { ort ->
            val direct = ByteBuffer.allocateDirect(packed.size * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            direct.put(packed).rewind()
            val environment = OrtEnvironment.getEnvironment("VideoFlowLocalAI")
            OnnxTensor.createTensor(
                environment,
                direct,
                longArrayOf(1, 4, modelSize.toLong(), modelSize.toLong())
            ).use { input ->
                ort.session.run(mapOf(ort.model.inputName to input)).use { result ->
                    val tensor = result.get(ort.model.outputName).orElseGet { result[0] } as? OnnxTensor
                        ?: error("Preview LaMa returned no float output tensor.")
                    FloatArray(pixelCount * 3).also { tensor.floatBuffer.get(it) } to ort.provider
                }
            }
        }
        coroutineContext.ensureActive()

        val generated = output.first
        val provider = output.second
        val mutable = frame.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(mutable.width * mutable.height)
        mutable.getPixels(pixels, 0, mutable.width, 0, 0, mutable.width, mutable.height)
        val left = (roi.left * mutable.width).toInt().coerceIn(0, mutable.width - 1)
        val top = (roi.top * mutable.height).toInt().coerceIn(0, mutable.height - 1)
        val right = (roi.right * mutable.width).toInt().coerceIn(left + 1, mutable.width)
        val bottom = (roi.bottom * mutable.height).toInt().coerceIn(top + 1, mutable.height)
        val featherScale = ((mutable.width.toFloat() / sourceWidth) + (mutable.height.toFloat() / sourceHeight)) / 2f
        val previewFeather = (featherPx * featherScale).coerceAtLeast(if (featherPx > 0) 1f else 0f)

        for (y in top until bottom) {
            if ((y and 31) == 0) coroutineContext.ensureActive()
            val my = ((y + 0.5f) / mutable.height * modelSize).toInt().coerceIn(0, modelSize - 1)
            for (x in left until right) {
                val mx = ((x + 0.5f) / mutable.width * modelSize).toInt().coerceIn(0, modelSize - 1)
                val modelIndex = my * modelSize + mx
                val rawR = generated[modelIndex]
                val rawG = generated[pixelCount + modelIndex]
                val rawB = generated[pixelCount * 2 + modelIndex]
                val outputScale = if (max(rawR, max(rawG, rawB)) <= 1.5f) 255f else 1f
                val gr = (rawR * outputScale).roundToInt().coerceIn(0, 255)
                val gg = (rawG * outputScale).roundToInt().coerceIn(0, 255)
                val gb = (rawB * outputScale).roundToInt().coerceIn(0, 255)

                val distance = min(min(x - left, right - 1 - x), min(y - top, bottom - 1 - y)).toFloat().coerceAtLeast(0f)
                val blend = if (previewFeather <= 0f) 1f else (distance / previewFeather).coerceIn(0f, 1f)
                val index = y * mutable.width + x
                val original = pixels[index]
                val r = (Color.red(original) * (1f - blend) + gr * blend).roundToInt().coerceIn(0, 255)
                val g = (Color.green(original) * (1f - blend) + gg * blend).roundToInt().coerceIn(0, 255)
                val b = (Color.blue(original) * (1f - blend) + gb * blend).roundToInt().coerceIn(0, 255)
                pixels[index] = Color.argb(Color.alpha(original), r, g, b)
            }
        }
        mutable.setPixels(pixels, 0, mutable.width, 0, 0, mutable.width, mutable.height)
        frame.recycle()
        PreviewResult(mutable, provider)
    }
}
