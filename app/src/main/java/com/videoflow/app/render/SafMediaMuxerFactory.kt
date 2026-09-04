@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.videoflow.app.render

import android.content.ContentResolver
import android.media.MediaCodec
import android.media.MediaMuxer
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.MuxerException
import androidx.media3.transformer.FrameworkMuxer
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Media3 muxer factory that writes Transformer output straight to the SAF destination.
 *
 * Transformer exposes a path-shaped factory API, but the factory is free to provide its own Muxer.
 * This implementation deliberately ignores the synthetic path and constructs Android MediaMuxer
 * with the user-selected ParcelFileDescriptor (API 26+). Encoded video/audio therefore never needs
 * a second full-size app-private MP4 before reaching the destination.
 */
class SafMediaMuxerFactory(
    private val contentResolver: ContentResolver,
    private val destinationUri: Uri
) : Muxer.Factory {
    private val delegateCapabilities = FrameworkMuxer.Factory()
    private val created = AtomicBoolean(false)

    override fun create(path: String): Muxer {
        if (!created.compareAndSet(false, true)) {
            throw MuxerException("SAF MP4 muxer factory may create only one output per export.", IllegalStateException("Muxer already created"))
        }
        return try {
            val pfd = contentResolver.openFileDescriptor(destinationUri, "rwt")
                ?: throw IllegalStateException("Destination file descriptor is unavailable")
            SafMediaMuxer(pfd)
        } catch (t: Throwable) {
            throw MuxerException("Could not open SAF destination for MP4 muxing.", t)
        }
    }

    override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
        delegateCapabilities.getSupportedSampleMimeTypes(trackType)

    override fun supportsWritingNegativeTimestampsInEditList(): Boolean = false

    private class SafMediaMuxer(
        private val pfd: android.os.ParcelFileDescriptor
    ) : Muxer {
        private val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        private var started = false
        private var closed = false

        override fun addTrack(format: Format): Int = wrap("add output track") {
            check(!started) { "All tracks must be added before sample writing starts" }
            muxer.addTrack(MediaFormatUtil.createMediaFormatFromFormat(format))
        }

        override fun writeSampleData(trackId: Int, byteBuffer: ByteBuffer, bufferInfo: BufferInfo) {
            wrap("write encoded sample") {
                if (!started) {
                    muxer.start()
                    started = true
                }
                val data = byteBuffer.duplicate()
                val available = data.remaining()
                val sampleSize = bufferInfo.size.coerceAtMost(available).coerceAtLeast(0)
                val offset = data.position()
                data.limit(offset + sampleSize)
                val frameworkInfo = MediaCodec.BufferInfo().apply {
                    set(offset, sampleSize, bufferInfo.presentationTimeUs, toFrameworkFlags(bufferInfo.flags))
                }
                muxer.writeSampleData(trackId, data, frameworkInfo)
            }
        }

        override fun addMetadataEntry(metadataEntry: Metadata.Entry) {
            // Android MediaMuxer does not expose Media3's generic metadata-entry API. The Step 3
            // fidelity contract is carried by encoded track MediaFormat (including colour fields),
            // while nonessential arbitrary container metadata is intentionally not synthesized.
        }

        override fun close() {
            if (closed) return
            closed = true
            var failure: Throwable? = null
            if (started) {
                try {
                    muxer.stop()
                } catch (t: Throwable) {
                    failure = t
                }
            }
            try {
                muxer.release()
            } catch (t: Throwable) {
                if (failure == null) failure = t else failure.addSuppressed(t)
            }
            try {
                pfd.close()
            } catch (t: Throwable) {
                if (failure == null) failure = t else failure.addSuppressed(t)
            }
            if (failure != null) throw MuxerException("Could not finalize SAF MP4 output.", failure)
        }

        private fun toFrameworkFlags(flags: Int): Int {
            var result = 0
            if (flags and C.BUFFER_FLAG_KEY_FRAME != 0) result = result or MediaCodec.BUFFER_FLAG_KEY_FRAME
            if (flags and C.BUFFER_FLAG_END_OF_STREAM != 0) result = result or MediaCodec.BUFFER_FLAG_END_OF_STREAM
            return result
        }

        private inline fun <T> wrap(operation: String, block: () -> T): T = try {
            block()
        } catch (t: Throwable) {
            if (t is MuxerException) throw t
            throw MuxerException("Failed to $operation for SAF MP4 output.", t)
        }
    }
}
