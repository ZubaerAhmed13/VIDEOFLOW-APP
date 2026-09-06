@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.videoflow.app.ai.watermark

import android.opengl.GLES20
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlRect
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.ByteBufferGlEffect
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.videoflow.app.domain.ai.AiModelRole
import com.videoflow.app.domain.ai.AiTile
import com.videoflow.app.domain.ai.AiWatermarkEffect
import com.videoflow.app.domain.ai.AiWatermarkMath
import com.videoflow.app.domain.ai.PixelRect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/** Shared final-quality LaMa session for one export; inference is bounded and serialized. */
class SharedLamaRenderRuntime private constructor(
    private val ort: AiOrtSession,
    private val executor: ListeningExecutorService
) : AutoCloseable {
    private val cancelled = AtomicBoolean(false)
    private val inferenceLock = Any()
    private val environment = OrtEnvironment.getEnvironment("VideoFlowLocalAI")

    val provider: String get() = ort.provider

    fun submit(block: () -> LamaPatch?): ListenableFuture<LamaPatch?> = executor.submit<LamaPatch?> {
        if (cancelled.get()) return@submit null
        block()
    }

    fun inferPacked(packed: FloatArray): FloatArray {
        if (cancelled.get()) throw InterruptedException("AI export cancelled")
        val size = ort.model.spec.inferenceSize
        require(packed.size == 4 * size * size)
        val direct = ByteBuffer.allocateDirect(packed.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        direct.put(packed).rewind()
        synchronized(inferenceLock) {
            if (cancelled.get()) throw InterruptedException("AI export cancelled")
            OnnxTensor.createTensor(environment, direct, longArrayOf(1, 4, size.toLong(), size.toLong())).use { input ->
                ort.session.run(mapOf(ort.model.inputName to input)).use { result ->
                    val tensor = result.get(ort.model.outputName).orElseGet { result[0] } as? OnnxTensor
                        ?: error("LaMa returned no float output tensor.")
                    val values = FloatArray(3 * size * size)
                    tensor.floatBuffer.get(values)
                    return values
                }
            }
        }
    }

    fun cancel() {
        cancelled.set(true)
        executor.shutdownNow()
    }

    override fun close() {
        cancelled.set(true)
        executor.shutdownNow()
        ort.close()
    }

    companion object {
        suspend fun create(manager: AiModelPackManager): SharedLamaRenderRuntime {
            // CPU is the correctness baseline. NNAPI is attempted first and manager falls back to CPU.
            val session = manager.openSession(AiModelRole.FINAL, preferNnapi = true)
            val executor = MoreExecutors.listeningDecorator(
                Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "VideoFlow-LaMa-Final").apply { priority = Thread.NORM_PRIORITY - 1 } }
            )
            return SharedLamaRenderRuntime(session, executor)
        }
    }
}

data class LamaPatch(
    val glRect: PixelRect,
    val rgbaGlOrder: ByteBuffer
)

/** Creates one Media3 ByteBuffer effect per bounded tile of a logical AI Watermark region. */
object OnnxWatermarkEffectFactory {
    fun createEffects(
        effect: AiWatermarkEffect,
        sourceWidth: Int,
        sourceHeight: Int,
        runtime: SharedLamaRenderRuntime
    ): List<ByteBufferGlEffect<LamaPatch?>> {
        require(sourceWidth > 0 && sourceHeight > 0)
        val baseTarget = AiWatermarkMath.toPixelRect(effect.roi, sourceWidth, sourceHeight)
        val count = AiWatermarkMath.planTiles(
            target = baseTarget,
            frameWidth = sourceWidth,
            frameHeight = sourceHeight,
            modelSize = effectModelSize(effect),
            contextPx = effect.contextPaddingPx.coerceAtMost(effectModelSize(effect) / 2 - 1)
        ).size
        return (0 until count).map { index ->
            ByteBufferGlEffect(LamaTileProcessor(effect, index, sourceWidth, sourceHeight, runtime))
        }
    }

    private fun effectModelSize(effect: AiWatermarkEffect): Int =
        com.videoflow.app.domain.ai.AiModelCatalog.byId(effect.modelId)?.inferenceSize
            ?: com.videoflow.app.domain.ai.AiModelCatalog.FINAL_512.inferenceSize
}

private class LamaTileProcessor(
    private val effect: AiWatermarkEffect,
    private val tileIndex: Int,
    private val expectedWidth: Int,
    private val expectedHeight: Int,
    private val runtime: SharedLamaRenderRuntime
) : ByteBufferGlEffect.Processor<LamaPatch?> {
    private var frameWidth = expectedWidth
    private var frameHeight = expectedHeight
    private var configuredReadWidth = min(512, expectedWidth)
    private var configuredReadHeight = min(512, expectedHeight)
    private var previousCore: ByteArray? = null

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        frameWidth = inputWidth
        frameHeight = inputHeight
        val tile = tileAt(effect.clipLocalStartUs)
        configuredReadWidth = tile.read.width
        configuredReadHeight = tile.read.height
        return Size(configuredReadWidth, configuredReadHeight)
    }

    override fun getScaledRegion(presentationTimeUs: Long): GlRect {
        val tile = tileAt(presentationTimeUs)
        val gl = AiWatermarkMath.toOpenGlRect(tile.read, frameHeight)
        return GlRect(gl.left, gl.top, gl.right, gl.bottom).normalizeGlRect()
    }

    override fun processImage(
        image: ByteBufferGlEffect.Image,
        presentationTimeUs: Long
    ): ListenableFuture<LamaPatch?> {
        if (!effect.activeAt(presentationTimeUs)) return runtime.submit { null }
        val tile = tileAt(presentationTimeUs)
        return runtime.submit {
            if (image.width != tile.read.width || image.height != tile.read.height) {
                throw IllegalStateException(
                    "AI ROI readback changed size (${image.width}x${image.height} vs ${tile.read.width}x${tile.read.height}); motion anchors must preserve ROI size."
                )
            }
            inpaint(image.pixelBuffer, image.width, image.height, tile)
        }
    }

    override fun finishProcessingAndBlend(
        outputFrame: GlTextureInfo,
        presentationTimeUs: Long,
        result: LamaPatch?
    ) {
        if (result == null) return
        val rect = result.glRect
        result.rgbaGlOrder.rewind()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, outputFrame.texId)
        GLES20.glTexSubImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            rect.left,
            rect.top,
            rect.width,
            rect.height,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            result.rgbaGlOrder
        )
        GlUtil.checkGlError()
    }

    override fun release() {
        previousCore = null
    }

    private fun tileAt(timeUs: Long): AiTile {
        val target = AiWatermarkMath.toPixelRect(effect.roiAt(timeUs), frameWidth, frameHeight)
        val tiles = AiWatermarkMath.planTiles(
            target,
            frameWidth,
            frameHeight,
            modelSize = 512,
            contextPx = effect.contextPaddingPx.coerceAtMost(255)
        )
        return tiles[tileIndex.coerceAtMost(tiles.lastIndex)]
    }

    private fun inpaint(inputGl: ByteBuffer, readWidth: Int, readHeight: Int, tile: AiTile): LamaPatch {
        val modelSize = 512
        val pixels = modelSize * modelSize
        val packed = FloatArray(4 * pixels)
        val coreLocalLeft = tile.core.left - tile.read.left
        val coreLocalTop = tile.core.top - tile.read.top
        val coreLocalRight = coreLocalLeft + tile.core.width
        val coreLocalBottom = coreLocalTop + tile.core.height
        val input = inputGl.duplicate().order(ByteOrder.nativeOrder())

        fun rgbaAtTopLeft(x: Int, y: Int, channel: Int): Int {
            val sx = x.coerceIn(0, readWidth - 1)
            val sy = y.coerceIn(0, readHeight - 1)
            val glRow = readHeight - 1 - sy
            return input.get((glRow * readWidth + sx) * 4 + channel).toInt() and 0xFF
        }

        for (y in 0 until modelSize) {
            val sy = y.coerceAtMost(readHeight - 1)
            for (x in 0 until modelSize) {
                val sx = x.coerceAtMost(readWidth - 1)
                val i = y * modelSize + x
                val hole = if (sx in coreLocalLeft until coreLocalRight && sy in coreLocalTop until coreLocalBottom) 1f else 0f
                packed[i] = (rgbaAtTopLeft(sx, sy, 0) / 255f) * (1f - hole)
                packed[pixels + i] = (rgbaAtTopLeft(sx, sy, 1) / 255f) * (1f - hole)
                packed[pixels * 2 + i] = (rgbaAtTopLeft(sx, sy, 2) / 255f) * (1f - hole)
                packed[pixels * 3 + i] = hole
            }
        }

        val output = runtime.inferPacked(packed)
        val coreTopLeft = ByteArray(tile.core.width * tile.core.height * 4)
        val previous = previousCore?.takeIf { it.size == coreTopLeft.size }
        val stability = effect.temporalStability
        for (cy in 0 until tile.core.height) {
            val srcY = coreLocalTop + cy
            for (cx in 0 until tile.core.width) {
                val srcX = coreLocalLeft + cx
                val modelIndex = srcY.coerceIn(0, modelSize - 1) * modelSize + srcX.coerceIn(0, modelSize - 1)
                val outIndex = (cy * tile.core.width + cx) * 4
                val rRaw = output[modelIndex]
                val gRaw = output[pixels + modelIndex]
                val bRaw = output[pixels * 2 + modelIndex]
                val scale = if (max(rRaw, max(gRaw, bRaw)) <= 1.5f) 255f else 1f
                val generated = intArrayOf(
                    (rRaw * scale).toInt().coerceIn(0, 255),
                    (gRaw * scale).toInt().coerceIn(0, 255),
                    (bRaw * scale).toInt().coerceIn(0, 255)
                )
                val globalX = tile.core.left + cx
                val globalY = tile.core.top + cy
                val logical = AiWatermarkMath.toPixelRect(effect.roi, frameWidth, frameHeight)
                val edgeDistance = min(
                    min(globalX - logical.left, logical.right - 1 - globalX),
                    min(globalY - logical.top, logical.bottom - 1 - globalY)
                ).coerceAtLeast(0)
                val feather = if (effect.featherPx <= 0) 1f else (edgeDistance.toFloat() / effect.featherPx).coerceIn(0f, 1f)
                for (channel in 0..2) {
                    val original = rgbaAtTopLeft(srcX, srcY, channel)
                    var ai = generated[channel].toFloat()
                    if (previous != null && stability > 0f) {
                        val old = previous[outIndex + channel].toInt() and 0xFF
                        ai = ai * (1f - stability) + old * stability
                    }
                    coreTopLeft[outIndex + channel] = (original * (1f - feather) + ai * feather)
                        .toInt().coerceIn(0, 255).toByte()
                }
                coreTopLeft[outIndex + 3] = 0xFF.toByte()
            }
        }
        previousCore = coreTopLeft.copyOf()

        val glBuffer = ByteBuffer.allocateDirect(coreTopLeft.size).order(ByteOrder.nativeOrder())
        for (glY in 0 until tile.core.height) {
            val topY = tile.core.height - 1 - glY
            val offset = topY * tile.core.width * 4
            glBuffer.put(coreTopLeft, offset, tile.core.width * 4)
        }
        glBuffer.flip()
        val glCoreTopLeft = AiWatermarkMath.toOpenGlRect(tile.core, frameHeight)
        // PixelRect uses top/bottom names, but after toOpenGlRect its top field is the GL bottom edge.
        return LamaPatch(
            glRect = PixelRect(glCoreTopLeft.left, glCoreTopLeft.top, glCoreTopLeft.right, glCoreTopLeft.bottom),
            rgbaGlOrder = glBuffer
        )
    }

    /** GlRect constructor is left,bottom,right,top while PixelRect is left,top,right,bottom. */
    private fun GlRect.normalizeGlRect(): GlRect = GlRect(left, bottom, right, top)
}
