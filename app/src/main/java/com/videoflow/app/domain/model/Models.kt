package com.videoflow.app.domain.model

enum class SourceStatus {
    AVAILABLE,
    MISSING,
    PERMISSION_LOST,
    CHANGED,
    UNSUPPORTED,
    CORRUPTED,
    UNKNOWN
}

enum class FingerprintStrength {
    STRONG_THREE_REGION,
    FULL_SMALL_FILE,
    WEAK_FIRST_REGION_ONLY,
    UNAVAILABLE
}

enum class ImportState {
    Idle,
    Selecting,
    Opening,
    ReadingMetadata,
    Fingerprinting,
    Saving,
    Ready,
    Error,
    Cancelled
}

data class VideoTrackInfo(
    val mime: String?,
    val width: Int?,
    val height: Int?,
    val frameRate: Double?,
    val bitrate: Int?,
    val rotationDegrees: Int?,
    val profile: Int?,
    val level: Int?,
    val durationUs: Long?,
    val colorStandard: Int?,
    val colorTransfer: Int?,
    val colorRange: Int?,
    val hdrStaticInfoPresent: Boolean
)

data class AudioTrackInfo(
    val mime: String?,
    val sampleRate: Int?,
    val channelCount: Int?,
    val bitrate: Int?,
    val durationUs: Long?,
    val profile: Int?
)

data class MediaTechnicalMetadata(
    val durationUs: Long?,
    val width: Int?,
    val height: Int?,
    val rotationDegrees: Int?,
    val frameRate: Double?,
    val videoCodecMime: String?,
    val audioCodecMime: String?,
    val audioSampleRate: Int?,
    val audioChannelCount: Int?,
    val videoTracks: List<VideoTrackInfo>,
    val audioTracks: List<AudioTrackInfo>
)

data class FingerprintResult(
    val algorithm: String = "VideoFlowSampleSHA256-v1",
    val sha256: String?,
    val strength: FingerprintStrength,
    val sampledBytes: Long,
    val note: String? = null
)

data class VideoFlowProject(
    val id: String,
    val name: String,
    val projectFormatVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
    val mediaAssets: List<MediaAsset>
)

data class MediaAsset(
    val id: String,
    val projectId: String,
    val sourceUri: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val durationUs: Long?,
    val width: Int?,
    val height: Int?,
    val rotationDegrees: Int?,
    val frameRate: Double?,
    val videoCodecMime: String?,
    val audioCodecMime: String?,
    val audioSampleRate: Int?,
    val audioChannelCount: Int?,
    val videoTrackCount: Int,
    val audioTrackCount: Int,
    val videoBitrate: Int?,
    val videoProfile: Int?,
    val videoLevel: Int?,
    val colorStandard: Int?,
    val colorTransfer: Int?,
    val colorRange: Int?,
    val hdrStaticInfoPresent: Boolean,
    val fingerprintSha256: String?,
    val fingerprintAlgorithm: String?,
    val fingerprintStrength: FingerprintStrength,
    val fingerprintSampledBytes: Long,
    val fingerprintNote: String?,
    val permissionPersisted: Boolean,
    val sourceStatus: SourceStatus,
    val createdAt: Long
)
