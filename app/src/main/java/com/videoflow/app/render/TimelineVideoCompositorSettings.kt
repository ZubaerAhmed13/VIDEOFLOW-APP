package com.videoflow.app.render

import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import com.videoflow.app.domain.export.EvaluatedTransform
import com.videoflow.app.domain.export.ExportSize
import com.videoflow.app.domain.export.FinalRenderEvaluator
import com.videoflow.app.domain.export.FinalRenderPlan

enum class RenderLayerKind { BACKGROUND, VIDEO_CLIP, IMAGE_OVERLAY, TEXT_OVERLAY }

data class RenderVisualLayer(
    val kind: RenderLayerKind,
    val ownerId: String,
    val baseScaleX: Float = 1f,
    val baseScaleY: Float = 1f
)

/**
 * Maps Step 2's deterministic timeline/keyframe semantics onto Media3's GPU compositor.
 * The layer list order must exactly match the order of VIDEO sequences in the Composition.
 */
@UnstableApi
class TimelineVideoCompositorSettings(
    private val plan: FinalRenderPlan,
    private val outputSize: ExportSize,
    private val layers: List<RenderVisualLayer>
) : VideoCompositorSettings {

    override fun getOutputSize(inputSizes: MutableList<Size>): Size =
        Size(outputSize.width, outputSize.height)

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        val layer = layers.getOrNull(inputId) ?: return hidden()
        if (layer.kind == RenderLayerKind.BACKGROUND) return StaticOverlaySettings.Builder().build()

        val state = FinalRenderEvaluator.evaluate(plan, presentationTimeUs.coerceAtLeast(0L))
        val transform = when (layer.kind) {
            RenderLayerKind.BACKGROUND -> null
            RenderLayerKind.VIDEO_CLIP -> state.video.firstOrNull { it.clip.id == layer.ownerId }?.transform
            RenderLayerKind.IMAGE_OVERLAY -> state.images.firstOrNull { it.overlay.id == layer.ownerId }?.transform
            RenderLayerKind.TEXT_OVERLAY -> state.text.firstOrNull { it.overlay.id == layer.ownerId }?.transform
        } ?: return hidden()

        return fromTransform(transform, layer)
    }

    private fun fromTransform(transform: EvaluatedTransform, layer: RenderVisualLayer): OverlaySettings {
        val xNdc = (transform.x * 2f - 1f).coerceIn(-1f, 1f)
        val yNdc = (1f - transform.y * 2f).coerceIn(-1f, 1f)
        val scaleX = layer.baseScaleX * transform.scaleX * if (transform.flipHorizontal) -1f else 1f
        val scaleY = layer.baseScaleY * transform.scaleY * if (transform.flipVertical) -1f else 1f
        return StaticOverlaySettings.Builder()
            .setAlphaScale(transform.opacity.coerceIn(0f, 1f))
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(xNdc, yNdc)
            // Step 2's Compose rotation is clockwise-positive; Media3 compositor is CCW-positive.
            .setRotationDegrees(-transform.rotationDegrees)
            .setScale(scaleX, scaleY)
            .build()
    }

    private fun hidden(): OverlaySettings = StaticOverlaySettings.Builder()
        .setAlphaScale(0f)
        .build()
}
