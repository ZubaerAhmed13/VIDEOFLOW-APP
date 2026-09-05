package com.videoflow.app.render

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.videoflow.app.domain.export.BitrateMode
import com.videoflow.app.domain.export.ExportFailureCode
import com.videoflow.app.domain.export.ExportProblem
import com.videoflow.app.domain.export.ExportWarning
import com.videoflow.app.domain.export.HdrPolicy
import com.videoflow.app.domain.export.ResolvedExportSettings
import com.videoflow.app.domain.export.VideoCodec


data class EncoderCapability(
    val name: String,
    val mimeType: String,
    val hardwareAccelerated: Boolean,
    val width: Int,
    val height: Int,
    val fps: Double,
    val bitrateRange: LongRange?,
    val supportsCq: Boolean,
    val supportsVbr: Boolean,
    val supportsCbr: Boolean,
    val supportsMain10: Boolean
)

data class CapabilityPreflight(
    val selectedEncoder: EncoderCapability?,
    val warnings: List<ExportWarning>,
    val problems: List<ExportProblem>
) {
    val ready: Boolean get() = selectedEncoder != null && problems.isEmpty()
}

interface EncoderCapabilitySource {
    fun encodersFor(mimeType: String, width: Int, height: Int, fps: Double): List<EncoderCapability>
}

class AndroidEncoderCapabilitySource : EncoderCapabilitySource {
    override fun encodersFor(mimeType: String, width: Int, height: Int, fps: Double): List<EncoderCapability> {
        return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .asSequence()
            .filter { it.isEncoder }
            .filter { info -> info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } }
            .mapNotNull { info -> capability(info, mimeType, width, height, fps) }
            .sortedWith(compareByDescending<EncoderCapability> { it.hardwareAccelerated }.thenBy { it.name })
            .toList()
    }

    private fun capability(info: MediaCodecInfo, mimeType: String, width: Int, height: Int, fps: Double): EncoderCapability? {
        val caps = runCatching { info.getCapabilitiesForType(mimeType) }.getOrNull() ?: return null
        val video = caps.videoCapabilities ?: return null
        if (!video.areSizeAndRateSupported(width, height, fps)) return null
        val encoder = caps.encoderCapabilities
        val bitrateRange = video.bitrateRange?.let { it.lower.toLong()..it.upper.toLong() }
        val supportsMain10 = if (mimeType == VideoCodec.HEVC.mimeType) {
            caps.profileLevels.any { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 }
        } else false
        val hardware = if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
        return EncoderCapability(
            name = info.name,
            mimeType = mimeType,
            hardwareAccelerated = hardware,
            width = width,
            height = height,
            fps = fps,
            bitrateRange = bitrateRange,
            supportsCq = encoder?.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ) == true,
            supportsVbr = encoder?.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR) == true,
            supportsCbr = encoder?.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR) == true,
            supportsMain10 = supportsMain10
        )
    }
}

object ExportCapabilityValidator {
    fun validate(
        settings: ResolvedExportSettings,
        sourceHasHdr: Boolean,
        source: EncoderCapabilitySource
    ): CapabilityPreflight {
        val candidates = source.encodersFor(
            settings.videoCodec.mimeType,
            settings.size.width,
            settings.size.height,
            settings.frameRate.fps
        )
        if (candidates.isEmpty()) {
            return CapabilityPreflight(
                selectedEncoder = null,
                warnings = emptyList(),
                problems = listOf(
                    ExportProblem(
                        ExportFailureCode.UNSUPPORTED_RESOLUTION,
                        "No ${settings.videoCodec.name} encoder supports ${settings.size.width}×${settings.size.height} at ${formatFps(settings.frameRate.fps)} fps on this device."
                    )
                )
            )
        }

        val selected = candidates.firstOrNull { it.hardwareAccelerated } ?: candidates.first()
        val warnings = mutableListOf<ExportWarning>()
        val problems = mutableListOf<ExportProblem>()

        if (!selected.hardwareAccelerated) {
            warnings += ExportWarning("SOFTWARE_ENCODER", "The selected system encoder is software-based and long/4K exports may be slow.")
        }
        val range = selected.bitrateRange
        if (range != null && settings.videoBitrate.toLong() !in range) {
            problems += ExportProblem(
                ExportFailureCode.ENCODER_INIT_FAILED,
                "Requested bitrate ${settings.videoBitrate} is outside ${selected.name}'s supported range ${range.first}–${range.last} bit/s."
            )
        }
        if (!supportsBitrateMode(selected, settings.bitrateMode)) {
            warnings += ExportWarning(
                "BITRATE_MODE_FALLBACK",
                "${settings.bitrateMode} is not supported by ${selected.name}; the render backend must choose the best supported mode without changing resolution or frame rate."
            )
        }

        if (sourceHasHdr) {
            when (settings.hdrPolicy) {
                HdrPolicy.CONVERT_TO_SDR -> warnings += ExportWarning("HDR_TO_SDR", "HDR source will be explicitly converted to SDR by user choice.")
                HdrPolicy.REQUIRE_PRESERVE -> {
                    if (settings.videoCodec != VideoCodec.HEVC || !selected.supportsMain10) {
                        problems += ExportProblem(
                            ExportFailureCode.UNSUPPORTED_HDR,
                            "HDR preservation requires a compatible 10-bit HEVC encoder; ${selected.name} cannot satisfy the requested HDR path."
                        )
                    }
                }
                HdrPolicy.PRESERVE_WHEN_COMPATIBLE -> {
                    if (settings.videoCodec != VideoCodec.HEVC || !selected.supportsMain10) {
                        problems += ExportProblem(
                            ExportFailureCode.UNSUPPORTED_HDR,
                            "HDR cannot be silently discarded. Select HEVC on a Main10-capable encoder or explicitly choose SDR conversion."
                        )
                    }
                }
            }
        }

        return CapabilityPreflight(selected, warnings, problems)
    }

    fun bestSupportedBitrateMode(capability: EncoderCapability, requested: BitrateMode): BitrateMode? {
        if (supportsBitrateMode(capability, requested)) return requested
        return when {
            capability.supportsCq -> BitrateMode.CQ
            capability.supportsVbr -> BitrateMode.VBR
            capability.supportsCbr -> BitrateMode.CBR
            else -> null
        }
    }

    private fun supportsBitrateMode(capability: EncoderCapability, mode: BitrateMode): Boolean = when (mode) {
        BitrateMode.AUTO -> capability.supportsCq || capability.supportsVbr || capability.supportsCbr
        BitrateMode.CQ -> capability.supportsCq
        BitrateMode.VBR -> capability.supportsVbr
        BitrateMode.CBR -> capability.supportsCbr
    }

    private fun formatFps(value: Double): String = String.format(java.util.Locale.US, "%.3f", value)
}
