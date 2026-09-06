@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.videoflow.app.export

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.ai.watermark.AiModelPackManager
import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.domain.editor.FrameRate
import com.videoflow.app.domain.editor.RenderPlan
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineTrack
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.domain.export.AudioCodec
import com.videoflow.app.domain.export.BitrateMode
import com.videoflow.app.domain.export.ExportQuality
import com.videoflow.app.domain.export.ExportSize
import com.videoflow.app.domain.export.FinalRenderPlan
import com.videoflow.app.domain.export.HdrPolicy
import com.videoflow.app.domain.export.OriginalRenderSource
import com.videoflow.app.domain.export.ResolvedExportSettings
import com.videoflow.app.domain.export.VideoCodec
import com.videoflow.app.render.Media3RenderEngine
import com.videoflow.app.render.OutputDestination
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeRenderEngineInstrumentedTest {
    @Test
    fun rendersOriginalContentUriThroughCompositionDirectlyToSaf() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolver = context.contentResolver
        val sourceUri = createVideoRow(context, "videoflow-step3-render-source-${System.currentTimeMillis()}.mp4")
        val outputUri = createVideoRow(context, "videoflow-step3-render-output-${System.currentTimeMillis()}.mp4")
        try {
            resolver.openOutputStream(sourceUri, "w")!!.use { output ->
                context.assets.open("sample_av.mp4").use { it.copyTo(output, 64 * 1024) }
            }

            val track = TimelineTrack(
                id = "video-track",
                projectId = "render-test",
                type = TrackType.VIDEO,
                name = "Video",
                orderIndex = 0
            )
            val clip = TimelineClip(
                id = "clip",
                projectId = "render-test",
                trackId = track.id,
                assetId = "source",
                timelineStartUs = 0L,
                sourceStartUs = 0L,
                sourceEndUs = 3_000_000L
            )
            val editorPlan = RenderPlan(
                projectId = "render-test",
                width = 320,
                height = 240,
                frameRate = FrameRate.FPS_30,
                tracks = listOf(track),
                clips = listOf(clip),
                textOverlays = emptyList(),
                imageOverlays = emptyList(),
                keyframes = emptyList(),
                backgroundArgb = 0xFF203040L
            )
            val original = OriginalRenderSource(
                assetId = "source",
                sourceUri = sourceUri.toString(),
                displayName = "sample_av.mp4",
                mimeType = "video/mp4",
                sizeBytes = null,
                durationUs = 3_000_000L,
                width = 320,
                height = 240,
                rotationDegrees = 0,
                frameRate = 30.0,
                videoCodecMime = "video/avc",
                audioCodecMime = "audio/mp4a-latm",
                audioSampleRate = 48_000,
                audioChannelCount = 1,
                videoBitrate = null,
                colorStandard = null,
                colorTransfer = null,
                colorRange = null,
                hdrStaticInfoPresent = false,
                fingerprintSha256 = "instrumentation-fixture"
            )
            val plan = FinalRenderPlan(editorPlan, mapOf("source" to original), 3_000_000L)
            val settings = ResolvedExportSettings(
                size = ExportSize(320, 240),
                frameRate = FrameRate.FPS_30,
                videoCodec = VideoCodec.H264,
                quality = ExportQuality.BALANCED,
                bitrateMode = BitrateMode.AUTO,
                videoBitrate = 2_000_000,
                audioCodec = AudioCodec.AAC_LC,
                audioBitrate = 128_000,
                audioSampleRate = 48_000,
                audioChannels = 2,
                hdrPolicy = HdrPolicy.PRESERVE_WHEN_COMPATIBLE,
                isUpscale = false
            )

            // Step 4 extends the native renderer with local-AI dependencies. This fixture has no
            // AI sidecar edits, so the model pack is not opened during this render; nevertheless
            // the test deliberately uses the real local repository/manager to exercise the exact
            // production constructor and keep the pre-AI native-render regression intact.
            val engine = Media3RenderEngine(
                context = context,
                aiRepository = AiWatermarkRepository(context),
                aiModelPackManager = AiModelPackManager(context)
            )
            val prepared = engine.prepare(plan, OutputDestination(outputUri, "render-test.mp4"), settings)
            assertTrue("Preflight problems: ${prepared.problems}", prepared.ready)
            assertEquals(false, prepared.preparation!!.usesTemporaryLocalOutput)

            val result = engine.render(prepared.preparation!!).getOrThrow()
            assertTrue("Validation problems: ${result.validation.problems}", result.validation.passed)
            assertTrue(result.outputBytes > 1_024L)
            assertEquals(320, result.validation.video?.width)
            assertEquals(240, result.validation.video?.height)
            assertEquals("video/avc", result.validation.video?.mimeType)
            assertEquals("audio/mp4a-latm", result.validation.audio?.mimeType)
            assertTrue(result.validation.video?.measuredFrameRate?.let { kotlin.math.abs(it - 30.0) < 0.1 } == true)
        } finally {
            resolver.delete(sourceUri, null, null)
            resolver.delete(outputUri, null, null)
        }
    }

    private fun createVideoRow(context: android.content.Context, displayName: String): android.net.Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoFlowCertification")
        }
        return requireNotNull(context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
    }
}
