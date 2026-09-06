package com.videoflow.app.domain.export

import com.videoflow.app.domain.editor.ClipTransform
import com.videoflow.app.domain.editor.FrameRate
import com.videoflow.app.domain.editor.TrackType
import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.max

/** Pure policy layer for honest source-preserving export decisions. */
data class SourceMatchProfile(
    val width: Int?,
    val height: Int?,
    val frameRate: FrameRate,
    val videoCodec: VideoCodec?,
    val videoBitrate: Int?,
    val audioSampleRate: Int?,
    val audioChannels: Int?,
    val colorStandard: Int?,
    val colorTransfer: Int?,
    val colorRange: Int?,
    val hdrPresent: Boolean,
    val homogeneous: Boolean
)

data class SourcePreservationAnalysis(
    val smartCopyCandidate: Boolean,
    val smartCopyReasons: List<String>,
    val requiresRuntimeSyncAndCodecConfigCheck: Boolean,
    val profile: SourceMatchProfile,
    val matchSourceWarnings: List<String>
)

object SourcePreservationPolicy {
    fun analyze(plan: FinalRenderPlan): SourcePreservationAnalysis {
        val clips = plan.editorPlan.clips.filter { it.enabled }.sortedBy { it.timelineStartUs }
        val reasons = mutableListOf<String>()
        if (clips.isEmpty()) reasons += "The project has no enabled video clips."
        if (clips.firstOrNull()?.timelineStartUs != 0L) reasons += "Smart Copy requires a gap-free timeline beginning at 00:00:00.000."
        if (clips.map { it.trackId }.distinct().size > 1) reasons += "Smart Copy currently requires one sequential video track."
        if (plan.editorPlan.textOverlays.isNotEmpty()) reasons += "Text overlays require rendering."
        if (plan.editorPlan.imageOverlays.isNotEmpty()) reasons += "Image overlays require rendering."
        if (plan.editorPlan.keyframes.isNotEmpty()) reasons += "Keyframes require rendering."

        clips.forEach { clip ->
            val track = plan.editorPlan.tracks.firstOrNull { it.id == clip.trackId }
            if (track?.type != TrackType.VIDEO) reasons += "Smart Copy currently supports video clips only."
            if (track?.muted == true || track?.gainDb?.let { abs(it) > 0.0001f } == true) reasons += "Track audio changes require rendering."
            if (abs(clip.speed - 1.0) > 0.000001) reasons += "Speed changes require rendering."
            if (abs(clip.opacity - 1f) > 0.0001f) reasons += "Opacity changes require rendering."
            if (abs(clip.gainDb) > 0.0001f) reasons += "Clip volume changes require rendering."
            if (clip.fadeInUs != 0L || clip.fadeOutUs != 0L) reasons += "Audio fades require rendering."
            if (clip.transform != ClipTransform()) reasons += "Crop or transform changes require rendering."
        }
        clips.zipWithNext().forEach { (left, right) ->
            if (left.timelineEndUs != right.timelineStartUs) reasons += "Smart Copy requires clips to be contiguous without gaps or overlaps."
        }

        val sources = clips.mapNotNull { plan.originalSources[it.assetId] }
        if (sources.size != clips.size) reasons += "Original source mapping is incomplete."
        if (sources.any { it.mimeType != null && !it.mimeType.equals("video/mp4", ignoreCase = true) }) {
            reasons += "Smart Copy currently writes MP4 and requires MP4-compatible source tracks."
        }
        if (sources.any { it.videoCodecMime !in setOf("video/avc", "video/hevc") }) {
            reasons += "Smart Copy currently supports H.264 or HEVC video."
        }
        if (sources.any { it.audioCodecMime != null && it.audioCodecMime != "audio/mp4a-latm" }) {
            reasons += "Smart Copy currently supports AAC audio when audio is present."
        }

        fun <T> homogeneous(selector: (OriginalRenderSource) -> T?): Boolean = sources.map(selector).distinct().size <= 1
        val staticCompatibility = listOf(
            homogeneous { it.videoCodecMime },
            homogeneous { it.width },
            homogeneous { it.height },
            homogeneous { it.rotationDegrees },
            homogeneous { it.audioCodecMime },
            homogeneous { it.audioSampleRate },
            homogeneous { it.audioChannelCount },
            homogeneous { it.colorStandard },
            homogeneous { it.colorTransfer },
            homogeneous { it.colorRange },
            homogeneous { it.hdrStaticInfoPresent }
        ).all { it }
        if (!staticCompatibility) reasons += "Selected clips do not share compatible encoded source characteristics."

        val first = sources.firstOrNull()
        val homogeneousSource = sources.isNotEmpty() && staticCompatibility
        val codec = when (first?.videoCodecMime) {
            VideoCodec.H264.mimeType -> VideoCodec.H264
            VideoCodec.HEVC.mimeType -> VideoCodec.HEVC
            else -> null
        }
        val profile = SourceMatchProfile(
            width = if (homogeneousSource) first?.width else plan.editorPlan.width,
            height = if (homogeneousSource) first?.height else plan.editorPlan.height,
            frameRate = plan.editorPlan.frameRate,
            videoCodec = if (homogeneousSource) codec else null,
            videoBitrate = if (homogeneousSource) first?.videoBitrate else null,
            audioSampleRate = if (homogeneousSource) first?.audioSampleRate else null,
            audioChannels = if (homogeneousSource) first?.audioChannelCount else null,
            colorStandard = if (homogeneousSource) first?.colorStandard else null,
            colorTransfer = if (homogeneousSource) first?.colorTransfer else null,
            colorRange = if (homogeneousSource) first?.colorRange else null,
            hdrPresent = sources.any { it.hdrStaticInfoPresent },
            homogeneous = homogeneousSource
        )
        val warnings = buildList {
            if (!homogeneousSource && sources.size > 1) add("This project contains multiple source formats. Match Source will preserve the project canvas and rational frame rate while rendering each source appropriately.")
            if (profile.videoCodec == null && sources.isNotEmpty()) add("The source codec cannot be selected directly by Match Source; choose a supported output codec explicitly.")
            if (profile.hdrPresent) add("HDR preservation remains capability-gated. VideoFlow will not silently claim HDR preservation if the device/renderer cannot provide it.")
        }
        return SourcePreservationAnalysis(
            smartCopyCandidate = reasons.isEmpty(),
            smartCopyReasons = reasons.distinct(),
            requiresRuntimeSyncAndCodecConfigCheck = reasons.isEmpty(),
            profile = profile,
            matchSourceWarnings = warnings
        )
    }

    fun settingsForMode(plan: FinalRenderPlan, current: ExportSettings, mode: ExportMode): ExportSettings = when (mode) {
        ExportMode.RECOMMENDED -> current.copy(mode = mode)
        ExportMode.SMALLER_FILE -> current.copy(
            mode = mode,
            quality = ExportQuality.SMALL,
            bitrateMode = BitrateMode.AUTO,
            videoBitrateOverride = null
        )
        ExportMode.HIGH_QUALITY -> current.copy(
            mode = mode,
            quality = ExportQuality.MAXIMUM,
            bitrateMode = BitrateMode.AUTO,
            videoBitrateOverride = null
        )
        ExportMode.MATCH_SOURCE, ExportMode.SMART_COPY -> sourceFidelitySettings(plan, current, mode)
    }

    private fun sourceFidelitySettings(plan: FinalRenderPlan, current: ExportSettings, mode: ExportMode): ExportSettings {
        val analysis = analyze(plan)
        val profile = analysis.profile
        val width = profile.width?.takeIf { it > 0 }?.let(::even)
        val height = profile.height?.takeIf { it > 0 }?.let(::even)
        val codec = profile.videoCodec ?: current.videoCodec
        val size = if (width != null && height != null) ExportSize(width, height)
            else ExportSize(even(plan.editorPlan.width), even(plan.editorPlan.height))
        val sourceAwareFloor = ExportMath.selectVideoBitrate(size, profile.frameRate, codec, ExportQuality.HIGH)
        val fidelityBitrate = max(profile.videoBitrate ?: 0, sourceAwareFloor).coerceIn(1_000_000, 120_000_000)
        return current.copy(
            mode = mode,
            resolutionPreset = if (width != null && height != null) ExportResolutionPreset.CUSTOM else ExportResolutionPreset.SOURCE,
            customWidth = width,
            customHeight = height,
            frameRate = profile.frameRate,
            videoCodec = codec,
            quality = ExportQuality.MAXIMUM,
            bitrateMode = BitrateMode.VBR,
            videoBitrateOverride = fidelityBitrate,
            audioSampleRate = profile.audioSampleRate?.coerceIn(8_000, 192_000) ?: current.audioSampleRate,
            audioChannels = profile.audioChannels?.coerceIn(1, 2) ?: current.audioChannels,
            hdrPolicy = HdrPolicy.PRESERVE_WHEN_COMPATIBLE
        )
    }

    /** Approximation from retained source bytes; container rewrite overhead is intentionally not called exact. */
    fun estimateSmartCopyPayloadBytes(plan: FinalRenderPlan): Long? {
        val pieces = plan.editorPlan.clips.filter { it.enabled }.mapNotNull { clip ->
            val source = plan.originalSources[clip.assetId] ?: return@mapNotNull null
            val bytes = source.sizeBytes?.takeIf { it > 0L } ?: return@mapNotNull null
            val duration = source.durationUs?.takeIf { it > 0L } ?: return@mapNotNull null
            BigInteger.valueOf(bytes)
                .multiply(BigInteger.valueOf(clip.sourceDurationUs))
                .divide(BigInteger.valueOf(duration))
        }
        if (pieces.isEmpty()) return null
        return pieces.fold(BigInteger.ZERO, BigInteger::add).longValueExact()
    }

    private fun even(value: Int): Int = if (value % 2 == 0) value else value - 1
}
