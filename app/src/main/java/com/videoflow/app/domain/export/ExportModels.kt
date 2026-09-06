package com.videoflow.app.domain.export

import com.videoflow.app.domain.editor.FrameRate
import java.math.BigInteger
import kotlin.math.roundToLong

data class ExportSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0)
        require(width % 2 == 0 && height % 2 == 0) { "Video encoder dimensions must be even" }
    }

    val pixels: Long get() = width.toLong() * height.toLong()
}

enum class ExportResolutionPreset {
    SOURCE,
    P480,
    P720,
    P1080,
    P1440,
    DCI_2K,
    UHD_4K,
    DCI_4K,
    CUSTOM
}

enum class VideoCodec(val mimeType: String) {
    H264("video/avc"),
    HEVC("video/hevc")
}

enum class ExportQuality { SMALL, BALANCED, HIGH, MAXIMUM }
enum class BitrateMode { AUTO, CQ, VBR, CBR }
enum class AudioCodec(val mimeType: String) { AAC_LC("audio/mp4a-latm") }
enum class HdrPolicy { PRESERVE_WHEN_COMPATIBLE, REQUIRE_PRESERVE, CONVERT_TO_SDR }

/**
 * User-facing export intent. SMART_COPY is a genuine packet-copy path and must never be treated as
 * a synonym for high-quality rendering.
 */
enum class ExportMode {
    RECOMMENDED,
    MATCH_SOURCE,
    SMART_COPY,
    SMALLER_FILE,
    HIGH_QUALITY
}

enum class ExportJobStatus {
    QUEUED,
    PREPARING,
    RENDERING,
    FINALIZING,
    VALIDATING,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED
}

enum class ExportFailureCode {
    SOURCE_MISSING,
    SOURCE_CHANGED,
    PERMISSION_LOST,
    UNSUPPORTED_CODEC,
    UNSUPPORTED_RESOLUTION,
    UNSUPPORTED_FRAME_RATE,
    UNSUPPORTED_HDR,
    ENCODER_INIT_FAILED,
    DECODER_FAILED,
    MUXER_FAILED,
    STORAGE_FULL,
    DESTINATION_IO,
    CANCELLED,
    VALIDATION_FAILED,
    UNKNOWN
}

data class ExportSettings(
    val resolutionPreset: ExportResolutionPreset = ExportResolutionPreset.SOURCE,
    val customWidth: Int? = null,
    val customHeight: Int? = null,
    val frameRate: FrameRate? = null,
    val videoCodec: VideoCodec = VideoCodec.H264,
    val quality: ExportQuality = ExportQuality.HIGH,
    val bitrateMode: BitrateMode = BitrateMode.AUTO,
    val videoBitrateOverride: Int? = null,
    val audioCodec: AudioCodec = AudioCodec.AAC_LC,
    val audioBitrate: Int = 256_000,
    val audioSampleRate: Int = 48_000,
    val audioChannels: Int = 2,
    val hdrPolicy: HdrPolicy = HdrPolicy.PRESERVE_WHEN_COMPATIBLE,
    val mode: ExportMode = ExportMode.RECOMMENDED
) {
    init {
        require(customWidth == null || customWidth > 0)
        require(customHeight == null || customHeight > 0)
        require(videoBitrateOverride == null || videoBitrateOverride > 0)
        require(audioBitrate in 64_000..512_000)
        require(audioSampleRate in 8_000..192_000)
        require(audioChannels in 1..2)
        if (resolutionPreset == ExportResolutionPreset.CUSTOM) {
            require(customWidth != null && customHeight != null) { "Custom resolution needs width and height" }
        }
    }
}

data class ResolvedExportSettings(
    val size: ExportSize,
    val frameRate: FrameRate,
    val videoCodec: VideoCodec,
    val quality: ExportQuality,
    val bitrateMode: BitrateMode,
    val videoBitrate: Int,
    val audioCodec: AudioCodec,
    val audioBitrate: Int,
    val audioSampleRate: Int,
    val audioChannels: Int,
    val hdrPolicy: HdrPolicy,
    val isUpscale: Boolean
)

data class ExportEstimate(
    val payloadBytes: Long,
    val safetyMarginBytes: Long,
    val requiredBytes: Long,
    val safetyMarginFraction: Double
)

data class ExportWarning(val code: String, val message: String)
data class ExportProblem(val code: ExportFailureCode, val message: String)

data class ExportPreflight(
    val resolved: ResolvedExportSettings?,
    val estimate: ExportEstimate?,
    val warnings: List<ExportWarning>,
    val problems: List<ExportProblem>
) {
    val ready: Boolean get() = resolved != null && problems.isEmpty()
}

data class ExportJob(
    val id: String,
    val projectId: String,
    val destinationUri: String,
    val displayName: String,
    val settingsJson: String,
    val status: ExportJobStatus,
    val progress: Float,
    val createdAt: Long,
    val startedAt: Long?,
    val completedAt: Long?,
    val failureCode: ExportFailureCode?,
    val failureMessage: String?
) {
    init { require(progress in 0f..1f) }
}

data class ExportReport(
    val id: String,
    val jobId: String,
    val outputWidth: Int,
    val outputHeight: Int,
    val frameRateNumerator: Int,
    val frameRateDenominator: Int,
    val videoCodecMime: String,
    val encoderName: String?,
    val videoBitrate: Int,
    val audioCodecMime: String?,
    val audioBitrate: Int?,
    val colorStandard: Int?,
    val colorRange: Int?,
    val colorTransfer: Int?,
    val hdrPreserved: Boolean,
    val durationUs: Long,
    val fileSizeBytes: Long,
    val renderDurationMs: Long,
    val validationPassed: Boolean,
    val createdAt: Long
)

object ExportMath {
    private val oneMillion = BigInteger.valueOf(1_000_000L)

    fun resolveSize(preset: ExportResolutionPreset, project: ExportSize, customWidth: Int? = null, customHeight: Int? = null): ExportSize =
        when (preset) {
            ExportResolutionPreset.SOURCE -> project.even()
            ExportResolutionPreset.P480 -> ExportSize(854, 480)
            ExportResolutionPreset.P720 -> ExportSize(1280, 720)
            ExportResolutionPreset.P1080 -> ExportSize(1920, 1080)
            ExportResolutionPreset.P1440 -> ExportSize(2560, 1440)
            ExportResolutionPreset.DCI_2K -> ExportSize(2048, 1080)
            ExportResolutionPreset.UHD_4K -> ExportSize(3840, 2160)
            ExportResolutionPreset.DCI_4K -> ExportSize(4096, 2160)
            ExportResolutionPreset.CUSTOM -> ExportSize(
                requireNotNull(customWidth) { "Custom width required" }.evenDimension(),
                requireNotNull(customHeight) { "Custom height required" }.evenDimension()
            )
        }

    fun frameTimestampUs(frameIndex: Long, frameRate: FrameRate): Long {
        require(frameIndex >= 0)
        val numerator = BigInteger.valueOf(frameIndex)
            .multiply(BigInteger.valueOf(frameRate.denominator.toLong()))
            .multiply(oneMillion)
        return numerator.divide(BigInteger.valueOf(frameRate.numerator.toLong())).longValueExact()
    }

    fun frameCountForDuration(durationUs: Long, frameRate: FrameRate): Long {
        require(durationUs >= 0)
        if (durationUs == 0L) return 0L
        val numerator = BigInteger.valueOf(durationUs)
            .multiply(BigInteger.valueOf(frameRate.numerator.toLong()))
        val denominator = BigInteger.valueOf(frameRate.denominator.toLong()).multiply(oneMillion)
        return numerator.add(denominator).subtract(BigInteger.ONE).divide(denominator).longValueExact()
    }

    fun selectVideoBitrate(size: ExportSize, frameRate: FrameRate, codec: VideoCodec, quality: ExportQuality): Int {
        val bpp = when (quality) {
            ExportQuality.SMALL -> 0.10
            ExportQuality.BALANCED -> 0.16
            ExportQuality.HIGH -> 0.22
            ExportQuality.MAXIMUM -> 0.28
        }
        val codecFactor = if (codec == VideoCodec.HEVC) 0.65 else 1.0
        val calculated = size.pixels.toDouble() * frameRate.fps * bpp * codecFactor
        return calculated.roundToLong().coerceIn(1_000_000L, 120_000_000L).toInt()
    }

    fun estimateOutputSize(durationUs: Long, videoBitrate: Int, audioBitrate: Int, safetyMarginFraction: Double = 0.15): ExportEstimate {
        require(durationUs >= 0)
        require(videoBitrate > 0 && audioBitrate >= 0)
        require(safetyMarginFraction in 0.0..1.0)
        val totalBitsPerSecond = BigInteger.valueOf(videoBitrate.toLong() + audioBitrate.toLong())
        val payload = totalBitsPerSecond
            .multiply(BigInteger.valueOf(durationUs))
            .divide(BigInteger.valueOf(8_000_000L))
            .longValueExact()
        val margin = (payload.toDouble() * safetyMarginFraction).roundToLong()
        return ExportEstimate(payload, margin, Math.addExact(payload, margin), safetyMarginFraction)
    }

    fun resolve(projectSize: ExportSize, projectFrameRate: FrameRate, settings: ExportSettings): ResolvedExportSettings {
        val size = resolveSize(settings.resolutionPreset, projectSize, settings.customWidth, settings.customHeight)
        val fps = settings.frameRate ?: projectFrameRate
        val bitrate = settings.videoBitrateOverride
            ?: selectVideoBitrate(size, fps, settings.videoCodec, settings.quality)
        return ResolvedExportSettings(
            size = size,
            frameRate = fps,
            videoCodec = settings.videoCodec,
            quality = settings.quality,
            bitrateMode = settings.bitrateMode,
            videoBitrate = bitrate,
            audioCodec = settings.audioCodec,
            audioBitrate = settings.audioBitrate,
            audioSampleRate = settings.audioSampleRate,
            audioChannels = settings.audioChannels,
            hdrPolicy = settings.hdrPolicy,
            isUpscale = size.width > projectSize.width || size.height > projectSize.height
        )
    }

    private fun ExportSize.even(): ExportSize = ExportSize(width.evenDimension(), height.evenDimension())
    private fun Int.evenDimension(): Int = if (this % 2 == 0) this else this - 1
}
