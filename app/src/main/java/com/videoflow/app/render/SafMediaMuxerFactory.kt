@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.videoflow.app.render

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.container.Mp4OrientationData
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Mp4Muxer
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.MuxerException
import androidx.media3.muxer.MuxerUtil
import androidx.media3.muxer.SeekableMuxerOutput
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Writes encoded Media3 output directly to the selected seekable SAF descriptor.
 * Unbatched samples avoid retaining encoded payloads or creating a second full-size MP4.
 * Media3's MP4 edit lists retain negative AAC priming timestamps supplied by Transformer.
 */
class SafMediaMuxerFactory(
    private val contentResolver: ContentResolver,
    private val destinationUri: Uri
) : Muxer.Factory {
    private val created = AtomicBoolean(false)

    override fun create(path: String): Muxer {
        if (!created.compareAndSet(false, true)) {
            throw MuxerException("SAF MP4 muxer factory may create only one output per export.", IllegalStateException("Muxer already created"))
        }
        return try {
            val pfd = contentResolver.openFileDescriptor(destinationUri, "rwt")
                ?: throw IllegalStateException("Destination file descriptor is unavailable")
            val stream = ParcelFileDescriptor.AutoCloseOutputStream(pfd)
            try {
                val muxer = Mp4Muxer.Builder(SeekableMuxerOutput.of(stream))
                    .setSampleBatchingEnabled(false)
                    .setAttemptStreamableOutputEnabled(false)
                    .build()
                SafMediaMuxer(muxer, stream)
            } catch (failure: Throwable) {
                try { stream.close() } catch (closeFailure: Throwable) { failure.addSuppressed(closeFailure) }
                throw failure
            }
        } catch (failure: Throwable) {
            throw MuxerException("Could not open SAF destination for MP4 muxing.", failure)
        }
    }

    override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> = when (trackType) {
        C.TRACK_TYPE_VIDEO -> Mp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES
        C.TRACK_TYPE_AUDIO -> Mp4Muxer.SUPPORTED_AUDIO_SAMPLE_MIME_TYPES
        else -> ImmutableList.of()
    }

    override fun supportsWritingNegativeTimestampsInEditList(): Boolean = true

    private class SafMediaMuxer(
        private val muxer: Mp4Muxer,
        private val stream: ParcelFileDescriptor.AutoCloseOutputStream
    ) : Muxer {
        private var closed = false

        override fun addTrack(format: Format): Int {
            check(!closed) { "Muxer is closed" }
            val isVideo = MimeTypes.isVideo(format.sampleMimeType)
            val trackId = muxer.addTrack(if (isVideo) 0 else 1, format)
            if (isVideo) muxer.addMetadataEntry(Mp4OrientationData(format.rotationDegrees))
            return trackId
        }

        override fun writeSampleData(trackId: Int, byteBuffer: ByteBuffer, bufferInfo: BufferInfo) {
            check(!closed) { "Muxer is closed" }
            // Preserve signed timestamps; the MP4 edit list compensates for encoder priming.
            muxer.writeSampleData(trackId, byteBuffer, bufferInfo)
        }

        override fun addMetadataEntry(metadataEntry: Metadata.Entry) {
            if (MuxerUtil.isMetadataSupported(metadataEntry)) muxer.addMetadataEntry(metadataEntry)
        }

        override fun close() {
            if (closed) return
            closed = true
            var failure: Throwable? = null
            try { muxer.close() } catch (error: Throwable) { failure = error }
            // Mp4Muxer normally closes this stream; also close on failed finalization.
            try { stream.close() } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
            if (failure != null) throw MuxerException("Could not finalize SAF MP4 output.", failure)
        }
    }
}
