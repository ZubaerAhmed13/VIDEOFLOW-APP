package com.videoflow.app.ui.product

import com.videoflow.app.domain.export.ExportFailureCode
import com.videoflow.app.domain.export.ExportJobStatus
import com.videoflow.app.domain.export.ExportQuality
import com.videoflow.app.domain.export.ExportResolutionPreset
import com.videoflow.app.domain.export.HdrPolicy
import com.videoflow.app.domain.export.VideoCodec

data class ExportFailurePresentation(
    val title: String,
    val message: String,
    val suggestions: List<String> = emptyList()
)

fun sanitizeExportFileName(value: String): String {
    val cleaned = value
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        .replace(Regex("\\s+"), " ")
        .take(96)
        .trim('.', ' ')
        .ifBlank { "VideoFlow Export" }
    return if (cleaned.endsWith(".mp4", ignoreCase = true)) cleaned else "$cleaned.mp4"
}

fun qualityLabel(value: ExportQuality): String = when (value) {
    ExportQuality.SMALL -> "Smaller File"
    ExportQuality.BALANCED -> "Recommended"
    ExportQuality.HIGH -> "High Quality"
    ExportQuality.MAXIMUM -> "Maximum"
}

fun resolutionLabel(value: ExportResolutionPreset): String = when (value) {
    ExportResolutionPreset.SOURCE -> "Match Project"
    ExportResolutionPreset.P480 -> "480p"
    ExportResolutionPreset.P720 -> "720p"
    ExportResolutionPreset.P1080 -> "1080p"
    ExportResolutionPreset.P1440 -> "1440p / QHD"
    ExportResolutionPreset.DCI_2K -> "2K DCI"
    ExportResolutionPreset.UHD_4K -> "4K UHD"
    ExportResolutionPreset.DCI_4K -> "4K DCI"
    ExportResolutionPreset.CUSTOM -> "Custom"
}

fun codecLabel(value: VideoCodec): String = when (value) {
    VideoCodec.H264 -> "H.264 / AVC"
    VideoCodec.HEVC -> "HEVC / H.265"
}

fun codecSupporting(value: VideoCodec): String = when (value) {
    VideoCodec.H264 -> "Best compatibility"
    VideoCodec.HEVC -> "More efficient on supported devices"
}

fun hdrPolicyLabel(value: HdrPolicy): String = when (value) {
    HdrPolicy.PRESERVE_WHEN_COMPATIBLE -> "Preserve compatible colour information"
    HdrPolicy.REQUIRE_PRESERVE -> "Require compatible HDR preservation"
    HdrPolicy.CONVERT_TO_SDR -> "Convert HDR to SDR"
}

fun exportStatusLabel(value: ExportJobStatus): String = when (value) {
    ExportJobStatus.QUEUED -> "Preparing"
    ExportJobStatus.PREPARING -> "Preparing"
    ExportJobStatus.RENDERING -> "Rendering"
    ExportJobStatus.FINALIZING -> "Finalizing"
    ExportJobStatus.VALIDATING -> "Validating"
    ExportJobStatus.COMPLETED -> "Completed"
    ExportJobStatus.FAILED -> "Failed"
    ExportJobStatus.CANCELLED -> "Cancelled"
    ExportJobStatus.INTERRUPTED -> "Interrupted"
}

fun exportFailurePresentation(code: ExportFailureCode?, fallback: String? = null): ExportFailurePresentation = when (code) {
    ExportFailureCode.SOURCE_MISSING -> ExportFailurePresentation(
        "Original file needed",
        "The original media is unavailable. You can keep editing with an available proxy, but the original is required for final export.",
        listOf("Locate the original media and try again")
    )
    ExportFailureCode.SOURCE_CHANGED -> ExportFailurePresentation(
        "Source file changed",
        "VideoFlow detected that a file no longer matches the source used by this project.",
        listOf("Review or relink the original source before exporting")
    )
    ExportFailureCode.PERMISSION_LOST -> ExportFailurePresentation(
        "Permission needed",
        "VideoFlow can no longer access one of the original files.",
        listOf("Locate the original file again through Android's document picker")
    )
    ExportFailureCode.UNSUPPORTED_CODEC -> ExportFailurePresentation(
        "Codec not supported",
        "This codec is not available for the selected export configuration on this device.",
        listOf("Try H.264", "Choose a lower resolution or frame rate")
    )
    ExportFailureCode.UNSUPPORTED_RESOLUTION -> ExportFailurePresentation(
        "Resolution not supported",
        "The device encoder cannot use the selected resolution with the current settings.",
        listOf("Try 1080p", "Use Match Project if it is supported")
    )
    ExportFailureCode.UNSUPPORTED_FRAME_RATE -> ExportFailurePresentation(
        "Frame rate not supported",
        "The selected frame rate is not available with this codec and resolution on this device.",
        listOf("Use Same as Project", "Try 30 fps")
    )
    ExportFailureCode.UNSUPPORTED_HDR -> ExportFailurePresentation(
        "HDR configuration not supported",
        "The selected colour/HDR configuration is not available for this export path.",
        listOf("Use Preserve compatible colour information", "Choose SDR conversion only when you intend to convert")
    )
    ExportFailureCode.ENCODER_INIT_FAILED -> ExportFailurePresentation(
        "Export couldn't start",
        "The device encoder could not start with this configuration.",
        listOf("Try 1080p instead of 4K", "Try H.264 instead of HEVC", "Close other heavy apps and try again")
    )
    ExportFailureCode.DECODER_FAILED -> ExportFailurePresentation(
        "A source could not be decoded",
        "VideoFlow could not decode part of the project during export.",
        listOf("Review the affected source", "Try the export again after reconnecting the original")
    )
    ExportFailureCode.MUXER_FAILED -> ExportFailurePresentation(
        "Video finalization failed",
        "VideoFlow could not finish writing the MP4 container.",
        listOf("Choose the destination again", "Check available storage")
    )
    ExportFailureCode.STORAGE_FULL -> ExportFailurePresentation(
        "Not enough storage",
        "The selected destination does not have enough space to complete this export.",
        listOf("Free storage space", "Choose a smaller quality or resolution")
    )
    ExportFailureCode.DESTINATION_IO -> ExportFailurePresentation(
        "Destination unavailable",
        "VideoFlow could not write to the selected destination.",
        listOf("Choose the destination again", "Use a different document provider")
    )
    ExportFailureCode.VALIDATION_FAILED -> ExportFailurePresentation(
        "Export validation failed",
        "The completed output did not pass VideoFlow's validation checks, so it is not being reported as a successful export.",
        listOf("Try the export again", "Change export settings if the problem repeats")
    )
    ExportFailureCode.CANCELLED -> ExportFailurePresentation("Export cancelled", "The export was cancelled and is not marked as complete.")
    ExportFailureCode.UNKNOWN, null -> ExportFailurePresentation(
        "Export couldn't finish",
        fallback?.takeIf { it.isNotBlank() } ?: "VideoFlow could not complete this export.",
        listOf("Review the settings and try again")
    )
}
