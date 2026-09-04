@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.videoflow.app.render

import android.content.ContentResolver
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.C
import com.videoflow.app.domain.export.HdrPolicy
import com.videoflow.app.domain.export.ResolvedExportSettings
import java.io.File
import kotlin.math.abs


data class OutputTrackInfo(
    val mimeType: String?,
    val durationUs: Long?,
    val width: Int?,
    val height: Int?,
    val frameRate: Float?,
    val rotationDegrees: Int?,
    val colorStandard: Int?,
    val colorRange: Int?,
    val colorTransfer: Int?,
    val measuredFrameRate: Double? = null
)

data class OutputColourExpectation(
    val colorStandard: Int? = null,
    val colorRange: Int? = null,
    val colorTransfer: Int? = null
) {
    val hasAny: Boolean get() = colorStandard != null || colorRange != null || colorTransfer != null
}

data class OutputValidationResult(
    val passed: Boolean,
    val fileSizeBytes: Long,
    val durationUs: Long?,
    val video: OutputTrackInfo?,
    val audio: OutputTrackInfo?,
    val problems: List<String>
)

class OutputValidator(private val contentResolver: ContentResolver) {
    fun validateUri(
        uri: Uri,
        expected: ResolvedExportSettings,
        expectedDurationUs: Long,
        expectAudio: Boolean,
        expectedHdr: Boolean? = null,
        expectedColour: OutputColourExpectation? = null
    ): OutputValidationResult {
        val problems = mutableListOf<String>()
        val length = querySize(uri)
        if (length <= 1_024L) problems += "Output is missing or unreasonably small ($length bytes)."
        val extractor = MediaExtractor()
        var video: OutputTrackInfo? = null
        var audio: OutputTrackInfo? = null
        var videoTrackIndex: Int? = null
        try {
            val pfd = contentResolver.openFileDescriptor(uri, "r")
            if (pfd == null) {
                problems += "Destination cannot be reopened for validation."
                return OutputValidationResult(false, length, null, null, null, problems)
            }
            pfd.use { extractor.setDataSource(it.fileDescriptor) }
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val info = format.toInfo()
                val mime = info.mimeType.orEmpty()
                if (mime.startsWith("video/") && video == null) {
                    video = info
                    videoTrackIndex = i
                }
                if (mime.startsWith("audio/") && audio == null) audio = info
            }
            videoTrackIndex?.let { index ->
                val measured = measureVideoCadence(extractor, index)
                if (measured != null) video = video?.copy(measuredFrameRate = measured.measuredFps)
            }
        } catch (t: Throwable) {
            problems += "Output container could not be read: ${t.message ?: t::class.java.simpleName}."
        } finally {
            extractor.release()
        }
        validateTracks(video, audio, expected, expectedDurationUs, expectAudio, expectedHdr, expectedColour, problems)
        val duration = listOfNotNull(video?.durationUs, audio?.durationUs).maxOrNull()
        return OutputValidationResult(problems.isEmpty(), length, duration, video, audio, problems)
    }

    fun validateFile(
        file: File,
        expected: ResolvedExportSettings,
        expectedDurationUs: Long,
        expectAudio: Boolean,
        expectedHdr: Boolean? = null,
        expectedColour: OutputColourExpectation? = null
    ): OutputValidationResult {
        val problems = mutableListOf<String>()
        val length = file.takeIf { it.isFile }?.length() ?: 0L
        if (length <= 1_024L) problems += "Output is missing or unreasonably small ($length bytes)."
        val extractor = MediaExtractor()
        var video: OutputTrackInfo? = null
        var audio: OutputTrackInfo? = null
        var videoTrackIndex: Int? = null
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val info = extractor.getTrackFormat(i).toInfo()
                val mime = info.mimeType.orEmpty()
                if (mime.startsWith("video/") && video == null) {
                    video = info
                    videoTrackIndex = i
                }
                if (mime.startsWith("audio/") && audio == null) audio = info
            }
            videoTrackIndex?.let { index ->
                val measured = measureVideoCadence(extractor, index)
                if (measured != null) video = video?.copy(measuredFrameRate = measured.measuredFps)
            }
        } catch (t: Throwable) {
            problems += "Output container could not be read: ${t.message ?: t::class.java.simpleName}."
        } finally {
            extractor.release()
        }
        validateTracks(video, audio, expected, expectedDurationUs, expectAudio, expectedHdr, expectedColour, problems)
        val duration = listOfNotNull(video?.durationUs, audio?.durationUs).maxOrNull()
        return OutputValidationResult(problems.isEmpty(), length, duration, video, audio, problems)
    }

    private fun validateTracks(
        video: OutputTrackInfo?,
        audio: OutputTrackInfo?,
        expected: ResolvedExportSettings,
        expectedDurationUs: Long,
        expectAudio: Boolean,
        expectedHdr: Boolean?,
        expectedColour: OutputColourExpectation?,
        problems: MutableList<String>
    ) {
        if (video == null) problems += "Output has no readable video track."
        video?.let { info ->
            if (info.width != expected.size.width || info.height != expected.size.height) {
                problems += "Output resolution ${info.width}×${info.height} does not match requested ${expected.size.width}×${expected.size.height}."
            }
            if (info.mimeType != expected.videoCodec.mimeType) {
                problems += "Output video MIME ${info.mimeType} does not match ${expected.videoCodec.mimeType}."
            }
            val measured = info.measuredFrameRate
            if (measured == null) {
                val minimumCadenceWindowUs = (2.0 * 1_000_000.0 / expected.frameRate.fps).toLong()
                if (expectedDurationUs > minimumCadenceWindowUs) {
                    problems += "Encoded video cadence could not be measured from sample timestamps."
                }
            } else if (!FrameCadenceVerifier.matches(measured, expected.frameRate)) {
                problems += FrameCadenceVerifier.mismatchMessage(measured, expected.frameRate)
            }
            validateHdr(info, expected, expectedHdr, problems)
            validateColour(info, expectedColour, problems)
        }
        if (expectAudio && audio == null) problems += "Output is missing expected audio."
        audio?.let {
            if (it.mimeType != expected.audioCodec.mimeType) {
                problems += "Output audio MIME ${it.mimeType} does not match ${expected.audioCodec.mimeType}."
            }
        }
        val duration = listOfNotNull(video?.durationUs, audio?.durationUs).maxOrNull()
        if (duration == null) {
            problems += "Output duration is unavailable."
        } else {
            val frameToleranceUs = ((1_000_000.0 / expected.frameRate.fps) * 2.0).toLong().coerceAtLeast(50_000L)
            if (abs(duration - expectedDurationUs) > frameToleranceUs) {
                problems += "Output duration $duration us differs from RenderPlan $expectedDurationUs us by more than $frameToleranceUs us."
            }
        }
    }

    private fun validateHdr(
        video: OutputTrackInfo,
        expected: ResolvedExportSettings,
        expectedHdr: Boolean?,
        problems: MutableList<String>
    ) {
        val outputIsHdr = video.colorTransfer == C.COLOR_TRANSFER_HLG || video.colorTransfer == C.COLOR_TRANSFER_ST2084
        if (expected.hdrPolicy == HdrPolicy.CONVERT_TO_SDR && outputIsHdr) {
            problems += "Output remained HDR even though explicit SDR conversion was requested."
        }
        if (expectedHdr == true && expected.hdrPolicy != HdrPolicy.CONVERT_TO_SDR && !outputIsHdr) {
            problems += "HDR source was not preserved in the encoded output. Silent HDR-to-SDR conversion is not allowed."
        }
        if (expectedHdr == false && outputIsHdr) {
            problems += "SDR render unexpectedly produced HDR transfer characteristics."
        }
    }

    private fun validateColour(
        actual: OutputTrackInfo,
        expected: OutputColourExpectation?,
        problems: MutableList<String>
    ) {
        if (expected == null || !expected.hasAny) return
        expected.colorStandard?.let { value ->
            if (actual.colorStandard != value) {
                problems += "Output colour standard ${actual.colorStandard} does not preserve homogeneous source value $value."
            }
        }
        expected.colorRange?.let { value ->
            if (actual.colorRange != value) {
                problems += "Output colour range ${actual.colorRange} does not preserve homogeneous source value $value."
            }
        }
        expected.colorTransfer?.let { value ->
            if (actual.colorTransfer != value) {
                problems += "Output colour transfer ${actual.colorTransfer} does not preserve homogeneous source value $value."
            }
        }
    }

    private fun measureVideoCadence(extractor: MediaExtractor, trackIndex: Int): FrameCadenceVerifier.Measurement? {
        val samples = ArrayList<Long>(MAX_CADENCE_SAMPLES)
        return runCatching {
            extractor.selectTrack(trackIndex)
            while (samples.size < MAX_CADENCE_SAMPLES) {
                val timeUs = extractor.sampleTime
                if (timeUs < 0L) break
                samples += timeUs
                if (!extractor.advance()) break
            }
            extractor.unselectTrack(trackIndex)
            FrameCadenceVerifier.measure(samples)
        }.getOrNull()
    }

    private fun querySize(uri: Uri): Long = runCatching {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    private fun MediaFormat.toInfo() = OutputTrackInfo(
        mimeType = stringOrNull(MediaFormat.KEY_MIME),
        durationUs = longOrNull(MediaFormat.KEY_DURATION),
        width = intOrNull(MediaFormat.KEY_WIDTH),
        height = intOrNull(MediaFormat.KEY_HEIGHT),
        frameRate = floatOrIntOrNull(MediaFormat.KEY_FRAME_RATE),
        rotationDegrees = intOrNull(MediaFormat.KEY_ROTATION),
        colorStandard = intOrNull(MediaFormat.KEY_COLOR_STANDARD),
        colorRange = intOrNull(MediaFormat.KEY_COLOR_RANGE),
        colorTransfer = intOrNull(MediaFormat.KEY_COLOR_TRANSFER)
    )

    private fun MediaFormat.stringOrNull(key: String): String? = if (containsKey(key)) getString(key) else null
    private fun MediaFormat.longOrNull(key: String): Long? = if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null
    private fun MediaFormat.intOrNull(key: String): Int? = if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
    private fun MediaFormat.floatOrIntOrNull(key: String): Float? = if (!containsKey(key)) null else {
        runCatching { getFloat(key) }.getOrElse { runCatching { getInteger(key).toFloat() }.getOrNull() }
    }

    private companion object {
        const val MAX_CADENCE_SAMPLES = 240
    }
}
