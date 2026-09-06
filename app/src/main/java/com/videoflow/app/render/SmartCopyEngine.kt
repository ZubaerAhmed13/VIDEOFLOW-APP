package com.videoflow.app.render

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import com.videoflow.app.domain.export.FinalRenderPlan
import com.videoflow.app.domain.export.SourcePreservationPolicy

class SmartCopyException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

data class SmartCopyPreflight(
    val eligible: Boolean,
    val reasons: List<String>,
    val videoMime: String? = null,
    val audioMime: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val rotationDegrees: Int? = null,
    val colorStandard: Int? = null,
    val colorTransfer: Int? = null,
    val colorRange: Int? = null
) {
    val summary: String
        get() = if (eligible) "No re-encoding is available for this edit." else reasons.firstOrNull() ?: "Smart Copy is unavailable."
}

data class SmartCopyResult(
    val outputBytes: Long,
    val durationUs: Long,
    val preflight: SmartCopyPreflight
)

/**
 * Packet-copy MP4 path for technically compatible timelines.
 *
 * It deliberately supports a narrow, certifiable subset: one gap-free video track, identity visual
 * transforms, speed 1x, no overlays/keyframes/fades/gain edits, H.264 or HEVC video, and optional AAC
 * audio. Runtime preflight additionally compares codec configuration (CSD), dimensions, colour,
 * rotation and audio sample description and verifies exact trimmed video starts are sync samples.
 */
@Singleton
class SmartCopyEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    fun preflight(plan: FinalRenderPlan): SmartCopyPreflight {
        val policy = SourcePreservationPolicy.analyze(plan)
        if (!policy.smartCopyCandidate) return SmartCopyPreflight(false, policy.smartCopyReasons)
        return runCatching { runtimePreflight(plan) }
            .getOrElse { error ->
                SmartCopyPreflight(false, listOf(error.message ?: "Encoded stream compatibility could not be verified."))
            }
    }

    fun copy(plan: FinalRenderPlan, destination: Uri): SmartCopyResult {
        cancelled.set(false)
        val check = preflight(plan)
        if (!check.eligible) throw SmartCopyException("Smart Copy is unavailable: ${check.reasons.joinToString(" ")}")
        val clips = plan.editorPlan.clips.filter { it.enabled }.sortedBy { it.timelineStartUs }
        val firstSource = plan.originalSources.getValue(clips.first().assetId)
        val firstProbe = probe(Uri.parse(firstSource.sourceUri))
        val resolver = context.contentResolver
        val pfd = resolver.openFileDescriptor(destination, "rwt")
            ?: throw SmartCopyException("The selected save destination could not be opened.")
        var muxer: MediaMuxer? = null
        try {
            muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            firstProbe.videoFormat?.getIntegerOrNull(MediaFormat.KEY_ROTATION)?.takeIf { it != 0 }?.let(muxer::setOrientationHint)
            val outputVideoTrack = firstProbe.videoFormat?.let(muxer::addTrack)
            val outputAudioTrack = firstProbe.audioFormat?.let(muxer::addTrack)
            if (outputVideoTrack == null) throw SmartCopyException("A readable video track is required for Smart Copy.")
            muxer.start()

            val maxInput = maxOf(
                firstProbe.videoFormat?.getIntegerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE) ?: 0,
                firstProbe.audioFormat?.getIntegerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE) ?: 0,
                1_048_576
            ).coerceAtMost(64 * 1024 * 1024)
            val buffer = ByteBuffer.allocateDirect(maxInput)
            val info = MediaCodec.BufferInfo()

            clips.forEach { clip ->
                if (cancelled.get()) throw SmartCopyException("Smart Copy was cancelled.")
                val source = plan.originalSources.getValue(clip.assetId)
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(context, Uri.parse(source.sourceUri), null)
                    val videoTrack = findTrack(extractor, "video/")
                    val audioTrack = findTrack(extractor, "audio/")
                    extractor.selectTrack(videoTrack)
                    if (audioTrack >= 0) extractor.selectTrack(audioTrack)
                    extractor.seekTo(clip.sourceStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                    while (true) {
                        if (cancelled.get()) throw SmartCopyException("Smart Copy was cancelled.")
                        val sampleTime = extractor.sampleTime
                        if (sampleTime < 0L || sampleTime >= clip.sourceEndUs) break
                        val sourceTrack = extractor.sampleTrackIndex
                        if (sourceTrack < 0) break
                        if (sampleTime >= clip.sourceStartUs) {
                            buffer.clear()
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) break
                            val outputTrack = when (sourceTrack) {
                                videoTrack -> outputVideoTrack
                                audioTrack -> outputAudioTrack
                                else -> null
                            }
                            if (outputTrack != null) {
                                val presentationUs = clip.timelineStartUs + (sampleTime - clip.sourceStartUs).coerceAtLeast(0L)
                                val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                                } else 0
                                info.set(0, size, presentationUs, flags)
                                muxer.writeSampleData(outputTrack, buffer, info)
                            }
                        }
                        if (!extractor.advance()) break
                    }
                } finally {
                    extractor.release()
                }
            }
            muxer.stop()
            muxer.release()
            muxer = null
            val bytes = pfd.statSize.takeIf { it >= 0L } ?: -1L
            return SmartCopyResult(bytes, plan.durationUs, check)
        } catch (t: Throwable) {
            if (t is SmartCopyException) throw t
            throw SmartCopyException("Smart Copy could not safely write the MP4 output: ${t.message ?: t::class.java.simpleName}", t)
        } finally {
            runCatching { muxer?.release() }
            runCatching { pfd.close() }
        }
    }

    private fun runtimePreflight(plan: FinalRenderPlan): SmartCopyPreflight {
        val clips = plan.editorPlan.clips.filter { it.enabled }.sortedBy { it.timelineStartUs }
        require(clips.isNotEmpty()) { "The project has no enabled video clips." }
        val probesByUri = linkedMapOf<String, SourceProbe>()
        clips.forEach { clip ->
            val source = plan.originalSources[clip.assetId] ?: error("Original source mapping is incomplete.")
            val probe = probesByUri.getOrPut(source.sourceUri) { probe(Uri.parse(source.sourceUri)) }
            require(probe.videoTrack >= 0 && probe.videoFormat != null) { "${source.displayName} has no readable video track." }
            require(exactSyncStart(Uri.parse(source.sourceUri), clip.sourceStartUs)) {
                "Exact trim start ${clip.sourceStartUs} us is not a sync-sample boundary. Use Match Source for an exact rendered cut, or choose a keyframe-aligned Smart Copy cut."
            }
        }
        val probes = clips.map { clip -> probesByUri.getValue(plan.originalSources.getValue(clip.assetId).sourceUri) }
        val first = probes.first()
        probes.drop(1).forEachIndexed { index, probe ->
            require(probe.videoSignature == first.videoSignature) {
                "Clip ${index + 2} has a different encoded video sample description (codec/configuration, dimensions, FPS, colour, rotation or parameter sets)."
            }
            require(probe.audioSignature == first.audioSignature) {
                "Clip ${index + 2} has a different audio sample description (codec, sample rate, channels or codec configuration)."
            }
        }
        val video = first.videoFormat!!
        return SmartCopyPreflight(
            eligible = true,
            reasons = emptyList(),
            videoMime = video.getStringOrNull(MediaFormat.KEY_MIME),
            audioMime = first.audioFormat?.getStringOrNull(MediaFormat.KEY_MIME),
            width = video.getIntegerOrNull(MediaFormat.KEY_WIDTH),
            height = video.getIntegerOrNull(MediaFormat.KEY_HEIGHT),
            rotationDegrees = video.getIntegerOrNull(MediaFormat.KEY_ROTATION),
            colorStandard = video.getIntegerOrNull(MediaFormat.KEY_COLOR_STANDARD),
            colorTransfer = video.getIntegerOrNull(MediaFormat.KEY_COLOR_TRANSFER),
            colorRange = video.getIntegerOrNull(MediaFormat.KEY_COLOR_RANGE)
        )
    }

    private fun exactSyncStart(uri: Uri, startUs: Long): Boolean {
        if (startUs == 0L) return true
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val videoTrack = findTrack(extractor, "video/")
            if (videoTrack < 0) return false
            extractor.selectTrack(videoTrack)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            while (extractor.sampleTime >= 0L && extractor.sampleTime < startUs) {
                if (!extractor.advance()) return false
            }
            extractor.sampleTime == startUs && extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
        } finally {
            extractor.release()
        }
    }

    private fun probe(uri: Uri): SourceProbe {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val videoTrack = findTrack(extractor, "video/")
            val audioTrack = findTrack(extractor, "audio/")
            val videoFormat = videoTrack.takeIf { it >= 0 }?.let(extractor::getTrackFormat)
            val audioFormat = audioTrack.takeIf { it >= 0 }?.let(extractor::getTrackFormat)
            return SourceProbe(
                videoTrack = videoTrack,
                audioTrack = audioTrack,
                videoFormat = videoFormat,
                audioFormat = audioFormat,
                videoSignature = videoFormat?.signature(video = true),
                audioSignature = audioFormat?.signature(video = false)
            )
        } finally {
            extractor.release()
        }
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getStringOrNull(MediaFormat.KEY_MIME)
            if (mime?.startsWith(prefix) == true) return index
        }
        return -1
    }

    private data class SourceProbe(
        val videoTrack: Int,
        val audioTrack: Int,
        val videoFormat: MediaFormat?,
        val audioFormat: MediaFormat?,
        val videoSignature: TrackSignature?,
        val audioSignature: TrackSignature?
    )

    private data class TrackSignature(
        val mime: String?,
        val width: Int?,
        val height: Int?,
        val frameRate: String?,
        val profile: Int?,
        val level: Int?,
        val sampleRate: Int?,
        val channels: Int?,
        val rotation: Int?,
        val colorStandard: Int?,
        val colorTransfer: Int?,
        val colorRange: Int?,
        val csd0: String?,
        val csd1: String?,
        val csd2: String?
    )

    private fun MediaFormat.signature(video: Boolean) = TrackSignature(
        mime = getStringOrNull(MediaFormat.KEY_MIME),
        width = if (video) getIntegerOrNull(MediaFormat.KEY_WIDTH) else null,
        height = if (video) getIntegerOrNull(MediaFormat.KEY_HEIGHT) else null,
        frameRate = if (video) valueString(MediaFormat.KEY_FRAME_RATE) else null,
        profile = getIntegerOrNull(MediaFormat.KEY_PROFILE),
        level = getIntegerOrNull(MediaFormat.KEY_LEVEL),
        sampleRate = if (!video) getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) else null,
        channels = if (!video) getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) else null,
        rotation = if (video) getIntegerOrNull(MediaFormat.KEY_ROTATION) else null,
        colorStandard = if (video) getIntegerOrNull(MediaFormat.KEY_COLOR_STANDARD) else null,
        colorTransfer = if (video) getIntegerOrNull(MediaFormat.KEY_COLOR_TRANSFER) else null,
        colorRange = if (video) getIntegerOrNull(MediaFormat.KEY_COLOR_RANGE) else null,
        csd0 = csd("csd-0"),
        csd1 = csd("csd-1"),
        csd2 = csd("csd-2")
    )

    private fun MediaFormat.csd(key: String): String? = if (!containsKey(key)) null else {
        getByteBuffer(key)?.duplicate()?.let { duplicate ->
            val bytes = ByteArray(duplicate.remaining())
            duplicate.get(bytes)
            Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun MediaFormat.valueString(key: String): String? {
        if (!containsKey(key)) return null
        return runCatching { getInteger(key).toString() }
            .getOrElse { runCatching { getFloat(key).toString() }.getOrNull() }
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? =
        if (!containsKey(key)) null else runCatching { getInteger(key) }.getOrNull()

    private fun MediaFormat.getStringOrNull(key: String): String? =
        if (!containsKey(key)) null else runCatching { getString(key) }.getOrNull()
}
