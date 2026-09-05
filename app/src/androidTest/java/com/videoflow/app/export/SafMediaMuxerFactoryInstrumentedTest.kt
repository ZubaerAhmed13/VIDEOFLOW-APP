@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.videoflow.app.export

import android.content.ContentValues
import android.media.MediaExtractor
import android.provider.MediaStore
import androidx.media3.common.C
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.muxer.BufferInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.render.SafMediaMuxerFactory
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafMediaMuxerFactoryInstrumentedTest {
    @Test
    fun remuxesFixtureDirectlyIntoMediaStoreContentUri() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolver = context.contentResolver
        val source = File(context.cacheDir, "step3-saf-source.mp4")
        context.assets.open("sample_av.mp4").use { input -> source.outputStream().use(input::copyTo) }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "videoflow-step3-saf-${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoFlowCertification")
        }
        val outputUri = requireNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
        try {
            val input = MediaExtractor()
            val muxer = SafMediaMuxerFactory(resolver, outputUri).create("ignored-by-saf-factory.mp4")
            try {
                input.setDataSource(source.absolutePath)
                val outputTrackIds = IntArray(input.trackCount)
                for (track in 0 until input.trackCount) {
                    outputTrackIds[track] = muxer.addTrack(
                        MediaFormatUtil.createFormatFromMediaFormat(input.getTrackFormat(track))
                    )
                    input.selectTrack(track)
                }

                val buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024)
                while (true) {
                    val track = input.sampleTrackIndex
                    if (track < 0) break
                    buffer.clear()
                    val size = input.readSampleData(buffer, 0)
                    if (size < 0) break
                    buffer.position(0)
                    buffer.limit(size)
                    val flags = if (input.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) C.BUFFER_FLAG_KEY_FRAME else 0
                    muxer.writeSampleData(
                        outputTrackIds[track],
                        buffer,
                        BufferInfo(input.sampleTime, size, flags)
                    )
                    if (!input.advance()) break
                }
            } finally {
                runCatching { muxer.close() }.getOrThrow()
                input.release()
                source.delete()
            }

            // A freshly finalized MediaStore row can expose stale SIZE metadata briefly on some
            // providers/emulators. Read the file descriptor itself: this measures the bytes the
            // direct-SAF muxer actually committed, independent of MediaStore indexing latency.
            val directSize = resolver.openFileDescriptor(outputUri, "r")!!.use { pfd -> pfd.statSize }
            assertTrue("Direct SAF MP4 should contain encoded media", directSize > 1_024L)

            val verify = MediaExtractor()
            try {
                resolver.openFileDescriptor(outputUri, "r")!!.use { verify.setDataSource(it.fileDescriptor) }
                assertEquals(2, verify.trackCount)
                val mimes = (0 until verify.trackCount).map {
                    verify.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                }
                assertTrue(mimes.any { it.startsWith("video/") })
                assertTrue(mimes.any { it.startsWith("audio/") })
            } finally {
                verify.release()
            }
        } finally {
            resolver.delete(outputUri, null, null)
            source.delete()
        }
    }
}
