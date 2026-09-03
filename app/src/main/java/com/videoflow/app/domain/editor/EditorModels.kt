package com.videoflow.app.domain.editor

import kotlin.math.roundToLong

data class FrameRate(val numerator: Int, val denominator: Int) {
    init {
        require(numerator > 0) { "Frame-rate numerator must be positive" }
        require(denominator > 0) { "Frame-rate denominator must be positive" }
    }

    val fps: Double get() = numerator.toDouble() / denominator.toDouble()

    companion object {
        val FPS_24 = FrameRate(24, 1)
        val FPS_25 = FrameRate(25, 1)
        val FPS_2997 = FrameRate(30_000, 1_001)
        val FPS_30 = FrameRate(30, 1)
        val FPS_5994 = FrameRate(60_000, 1_001)
        val FPS_60 = FrameRate(60, 1)
    }
}

enum class TrackType { VIDEO, AUDIO, OVERLAY }

data class ProjectSettings(
    val projectId: String,
    val width: Int = 1920,
    val height: Int = 1080,
    val frameRate: FrameRate = FrameRate.FPS_30,
    val backgroundArgb: Long = 0xFF000000,
    val createdAt: Long,
    val updatedAt: Long
) {
    init {
        require(width > 0 && height > 0) { "Project dimensions must be positive" }
    }
}

data class TimelineTrack(
    val id: String,
    val projectId: String,
    val type: TrackType,
    val name: String,
    val orderIndex: Int,
    val muted: Boolean = false,
    val solo: Boolean = false,
    val locked: Boolean = false,
    val visible: Boolean = true,
    val gainDb: Float = 0f
)

data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(right > left && bottom > top) { "Crop rectangle must have positive area" }
    }
}

data class ClipTransform(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: CropRect = CropRect()
) {
    init {
        require(x.isFinite() && y.isFinite())
        require(scaleX.isFinite() && scaleY.isFinite() && scaleX > 0f && scaleY > 0f)
        require(rotationDegrees.isFinite())
        require(opacity in 0f..1f)
    }
}

data class TimelineClip(
    val id: String,
    val projectId: String,
    val trackId: String,
    val assetId: String,
    val timelineStartUs: Long,
    val sourceStartUs: Long,
    val sourceEndUs: Long,
    val speed: Double = 1.0,
    val opacity: Float = 1f,
    val enabled: Boolean = true,
    val gainDb: Float = 0f,
    val fadeInUs: Long = 0,
    val fadeOutUs: Long = 0,
    val transform: ClipTransform = ClipTransform()
) {
    init {
        require(timelineStartUs >= 0) { "Timeline start cannot be negative" }
        require(sourceStartUs >= 0) { "Source start cannot be negative" }
        require(sourceEndUs > sourceStartUs) { "Source end must be after source start" }
        require(speed.isFinite() && speed > 0.0) { "Speed must be finite and positive" }
        require(opacity in 0f..1f)
        require(gainDb.isFinite())
        require(fadeInUs >= 0 && fadeOutUs >= 0)
        require(fadeInUs <= timelineDurationUs && fadeOutUs <= timelineDurationUs)
    }

    val sourceDurationUs: Long get() = sourceEndUs - sourceStartUs
    val timelineDurationUs: Long get() = (sourceDurationUs.toDouble() / speed).roundToLong().coerceAtLeast(1)
    val timelineEndUs: Long get() = timelineStartUs + timelineDurationUs
}

data class TextOverlay(
    val id: String,
    val projectId: String,
    val trackId: String,
    val timelineStartUs: Long,
    val timelineEndUs: Long,
    val content: String,
    val fontSizeSp: Float = 32f,
    val fontWeight: Int = 400,
    val italic: Boolean = false,
    val colorArgb: Long = 0xFFFFFFFF,
    val opacity: Float = 1f,
    val alignment: String = "CENTER",
    val transform: ClipTransform = ClipTransform()
) {
    init {
        require(timelineStartUs >= 0 && timelineEndUs > timelineStartUs)
        require(fontSizeSp > 0f && fontSizeSp.isFinite())
        require(opacity in 0f..1f)
    }
}

data class ImageOverlay(
    val id: String,
    val projectId: String,
    val trackId: String,
    val assetId: String,
    val timelineStartUs: Long,
    val timelineEndUs: Long,
    val transform: ClipTransform = ClipTransform()
) {
    init {
        require(timelineStartUs >= 0 && timelineEndUs > timelineStartUs)
    }
}

enum class KeyframeOwnerType { CLIP, TEXT_OVERLAY, IMAGE_OVERLAY }
enum class KeyframeProperty { POSITION_X, POSITION_Y, SCALE_X, SCALE_Y, ROTATION, OPACITY, AUDIO_GAIN }
enum class KeyframeInterpolation { HOLD, LINEAR }

data class Keyframe(
    val id: String,
    val ownerId: String,
    val ownerType: KeyframeOwnerType,
    val property: KeyframeProperty,
    val timeUs: Long,
    val value: Float,
    val interpolation: KeyframeInterpolation = KeyframeInterpolation.LINEAR
) {
    init {
        require(timeUs >= 0)
        require(value.isFinite())
    }
}

enum class ProxyStatus { NONE, QUEUED, GENERATING, READY, FAILED, STALE }
enum class ProxyQuality { PERFORMANCE, BALANCED, HIGH }

data class ProxyMedia(
    val id: String,
    val assetId: String,
    val path: String,
    val width: Int,
    val height: Int,
    val codecMime: String,
    val sourceFingerprint: String?,
    val status: ProxyStatus,
    val quality: ProxyQuality,
    val createdAt: Long,
    val sizeBytes: Long?
)

data class TimelineState(
    val projectId: String,
    val tracks: List<TimelineTrack>,
    val clips: List<TimelineClip>,
    val textOverlays: List<TextOverlay> = emptyList(),
    val imageOverlays: List<ImageOverlay> = emptyList(),
    val keyframes: List<Keyframe> = emptyList()
) {
    val durationUs: Long
        get() = maxOf(
            clips.maxOfOrNull { it.timelineEndUs } ?: 0L,
            textOverlays.maxOfOrNull { it.timelineEndUs } ?: 0L,
            imageOverlays.maxOfOrNull { it.timelineEndUs } ?: 0L
        )
}
