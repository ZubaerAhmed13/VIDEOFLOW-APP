package com.videoflow.app.render.effects

import androidx.media3.common.Effect
import androidx.media3.effect.Crop
import com.videoflow.app.domain.editor.CropRect

/** Media3 NDC crop bounds shared by editor preview and final render. */
internal data class Media3CropBounds(
    val left: Float,
    val right: Float,
    val bottom: Float,
    val top: Float
)

/**
 * Converts VideoFlow's normalized top-left-origin crop rectangle into Media3 Crop bounds.
 * A full-source crop deliberately returns null so no redundant GL stage is installed.
 */
internal fun normalizedCropToMedia3Bounds(crop: CropRect): Media3CropBounds? {
    if (crop.left <= 0f && crop.top <= 0f && crop.right >= 1f && crop.bottom >= 1f) return null
    return Media3CropBounds(
        left = crop.left * 2f - 1f,
        right = crop.right * 2f - 1f,
        bottom = 1f - crop.bottom * 2f,
        top = 1f - crop.top * 2f
    )
}

internal fun media3CropEffectOrNull(crop: CropRect): Effect? =
    normalizedCropToMedia3Bounds(crop)?.let { bounds ->
        Crop(bounds.left, bounds.right, bounds.bottom, bounds.top)
    }
