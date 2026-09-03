package com.videoflow.app.data.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import com.videoflow.app.domain.model.AudioTrackInfo
import com.videoflow.app.domain.model.FingerprintResult
import com.videoflow.app.domain.model.FingerprintStrength
import com.videoflow.app.domain.model.MediaTechnicalMetadata
import com.videoflow.app.domain.model.VideoTrackInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

class UnsupportedMediaException(message: String) : Exception(message)
class CorruptedMediaException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Singleton
class MediaAnalyzer @Inject constructor(@ApplicationContext context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    data class Result(
        val displayName: String,
        val mimeType: String?,
        val sizeBytes: Long?,
        val metadata: MediaTechnicalMetadata
    )

    suspend fun analyze(uri: Uri): Result = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val (name, size) = queryNameSize(uri)
        val mimeType = resolver.getType(uri)
        if (mimeType?.startsWith("image/") == true) {
            return@withContext analyzeImage(uri, name, size, mimeType)
        }
        analyzeAudioVideo(uri, name, size, mimeType)
    }

    private fun analyzeImage(uri: Uri, name: String, size: Long?, mimeType: String): Result {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: throw FileNotFoundException("Unable to open image source")
        } catch (t: Throwable) {
            if (t is FileNotFoundException) throw t
            throw CorruptedMediaException("Android could not read image bounds", t)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw CorruptedMediaException("Image dimensions are unavailable or unsupported")
        }
        return Result(
            displayName = name,
            mimeType = mimeType,
            sizeBytes = size,
            metadata = MediaTechnicalMetadata(
                durationUs = null,
                width = options.outWidth,
                height = options.outHeight,
                rotationDegrees = 0,
                frameRate = null,
                videoCodecMime = null,
                audioCodecMime = null,
                audioSampleRate = null,
                audioChannelCount = null,
                videoTracks = emptyList(),
                audioTracks = emptyList()
            )
        )
    }

    private fun analyzeAudioVideo(
        uri: Uri,
        name: String,
        size: Long?,
        mimeType: String?
    ): Result {
        val extractor = MediaExtractor()
        try {
            val opened = resolver.openFileDescriptor(uri, "r")
                ?: throw FileNotFoundException("Unable to open source")
            opened.use { pfd ->
                try {
                    extractor.setDataSource(pfd.fileDescriptor)
                } catch (t: Throwable) {
                    throw CorruptedMediaException("MediaExtractor could not parse this source", t)
                }
            }

            val videos = mutableListOf<VideoTrackInfo>()
            val audios = mutableListOf<AudioTrackInfo>()
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.string(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    videos += VideoTrackInfo(
                        mime = mime,
                        width = format.int(MediaFormat.KEY_WIDTH),
                        height = format.int(MediaFormat.KEY_HEIGHT),
                        frameRate = format.floatAsDouble(MediaFormat.KEY_FRAME_RATE),
                        bitrate = format.int(MediaFormat.KEY_BIT_RATE),
                        rotationDegrees = normalizeRotation(format.int(MediaFormat.KEY_ROTATION)),
                        profile = format.int(MediaFormat.KEY_PROFILE),
                        level = format.int(MediaFormat.KEY_LEVEL),
                        durationUs = format.long(MediaFormat.KEY_DURATION),
                        colorStandard = format.int(MediaFormat.KEY_COLOR_STANDARD),
                        colorTransfer = format.int(MediaFormat.KEY_COLOR_TRANSFER),
                        colorRange = format.int(MediaFormat.KEY_COLOR_RANGE),
                        hdrStaticInfoPresent = format.containsKey(MediaFormat.KEY_HDR_STATIC_INFO)
                    )
                } else if (mime?.startsWith("audio/") == true) {
                    audios += AudioTrackInfo(
                        mime = mime,
                        sampleRate = format.int(MediaFormat.KEY_SAMPLE_RATE),
                        channelCount = format.int(MediaFormat.KEY_CHANNEL_COUNT),
                        bitrate = format.int(MediaFormat.KEY_BIT_RATE),
                        durationUs = format.long(MediaFormat.KEY_DURATION),
                        profile = format.int(MediaFormat.KEY_PROFILE)
                    )
                }
            }

            if (videos.isEmpty() && audios.isEmpty()) {
                throw UnsupportedMediaException("No supported audio or video tracks were found")
            }

            val primaryVideo = videos.firstOrNull()
            val primaryAudio = audios.firstOrNull()
            val duration = (videos.mapNotNull { it.durationUs } + audios.mapNotNull { it.durationUs }).maxOrNull()
            return Result(
                displayName = name,
                mimeType = mimeType,
                sizeBytes = size,
                metadata = MediaTechnicalMetadata(
                    durationUs = duration,
                    width = primaryVideo?.width,
                    height = primaryVideo?.height,
                    rotationDegrees = primaryVideo?.rotationDegrees,
                    frameRate = primaryVideo?.frameRate,
                    videoCodecMime = primaryVideo?.mime,
                    audioCodecMime = primaryAudio?.mime,
                    audioSampleRate = primaryAudio?.sampleRate,
                    audioChannelCount = primaryAudio?.channelCount,
                    videoTracks = videos,
                    audioTracks = audios
                )
            )
        } finally {
            extractor.release()
        }
    }

    private fun queryNameSize(uri: Uri): Pair<String, Long?> {
        var name = "Media"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return name to size
    }

    private fun normalizeRotation(rotation: Int?): Int? = when (rotation) {
        null -> null
        0, 90, 180, 270 -> rotation
        else -> ((rotation % 360) + 360) % 360
    }
}

@Singleton
class UriFingerprintService @Inject constructor(@ApplicationContext context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val engine = FingerprintEngine()

    suspend fun fingerprint(
        uri: Uri,
        sizeHint: Long?,
        durationUs: Long?,
        width: Int?,
        height: Int?
    ): FingerprintResult = withContext(Dispatchers.IO) {
        val job: Job? = currentCoroutineContext()[Job]
        try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val stableSize = when {
                    pfd.statSize >= 0L -> pfd.statSize
                    sizeHint != null && sizeHint >= 0L -> sizeHint
                    else -> -1L
                }
                if (stableSize < 0L) {
                    return@withContext weakFirst(uri, sizeHint, durationUs, width, height, "Provider did not expose a stable size", job)
                }

                FileInputStream(pfd.fileDescriptor).use { input ->
                    val channel = input.channel
                    val reader = object : RandomAccessReader {
                        override val size: Long = stableSize
                        override fun readAt(offset: Long, buffer: ByteArray, length: Int): Int {
                            require(offset >= 0L)
                            channel.position(offset)
                            return channel.read(ByteBuffer.wrap(buffer, 0, length))
                        }
                        override fun close() = Unit
                    }
                    return@withContext try {
                        engine.fingerprint(reader, durationUs, width, height) { job?.ensureActive() }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        weakFirst(uri, stableSize, durationUs, width, height, "Provider does not support reliable random seek", job)
                    }
                }
            } ?: FingerprintResult(
                sha256 = null,
                strength = FingerprintStrength.UNAVAILABLE,
                sampledBytes = 0L,
                note = "Unable to open source"
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            FingerprintResult(
                sha256 = null,
                strength = FingerprintStrength.UNAVAILABLE,
                sampledBytes = 0L,
                note = t.message
            )
        }
    }

    private fun weakFirst(
        uri: Uri,
        sizeHint: Long?,
        durationUs: Long?,
        width: Int?,
        height: Int?,
        note: String,
        job: Job?
    ): FingerprintResult {
        val digest = MessageDigest.getInstance("SHA-256")
        fun meta(value: String) {
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        meta("VideoFlowSampleSHA256-v1-weak")
        meta(sizeHint?.toString() ?: "?")
        meta(durationUs?.toString() ?: "?")
        meta(width?.toString() ?: "?")
        meta(height?.toString() ?: "?")

        var total = 0L
        val buffer = ByteArray(256 * 1024)
        resolver.openInputStream(uri)?.use { input ->
            while (total < 4L * 1024L * 1024L) {
                job?.ensureActive()
                val wanted = minOf(buffer.size.toLong(), 4L * 1024L * 1024L - total).toInt()
                val read = input.read(buffer, 0, wanted)
                if (read <= 0) break
                digest.update(buffer, 0, read)
                total += read.toLong()
            }
        }
        return FingerprintResult(
            sha256 = digest.digest().joinToString(separator = "") { "%02x".format(it) },
            strength = FingerprintStrength.WEAK_FIRST_REGION_ONLY,
            sampledBytes = total,
            note = note
        )
    }
}

private fun MediaFormat.int(key: String): Int? =
    if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

private fun MediaFormat.long(key: String): Long? =
    if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null

private fun MediaFormat.floatAsDouble(key: String): Double? {
    if (!containsKey(key)) return null
    return runCatching { getFloat(key).toDouble() }.getOrNull()
        ?: runCatching { getInteger(key).toDouble() }.getOrNull()
        ?: runCatching { getLong(key).toDouble() }.getOrNull()
}

private fun MediaFormat.string(key: String): String? =
    if (containsKey(key)) runCatching { getString(key) }.getOrNull() else null
