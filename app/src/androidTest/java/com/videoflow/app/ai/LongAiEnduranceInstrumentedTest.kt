@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.videoflow.app.ai

import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.videoflow.app.ai.watermark.AiModelPackManager
import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.domain.ai.AiModelCatalog
import com.videoflow.app.domain.ai.AiWatermarkEffect
import com.videoflow.app.domain.ai.NormalizedRoi
import com.videoflow.app.domain.ai.RoiMotionAnchor
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
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Decisive Step-4 export gate: a real enabled AI sidecar edit opens the FINAL LaMa runtime inside
 * Media3RenderEngine, processes original-source pixels, writes through the production SAF muxer,
 * and produces an output that passes the production OutputValidator.
 */
@RunWith(AndroidJUnit4::class)
class LongAiEnduranceInstrumentedTest {
    @Test
    fun runConfiguredOriginalSourceThroughFinalAiPipeline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val resolver = context.contentResolver
        val args = InstrumentationRegistry.getArguments()
        val configuredUri = requireNotNull(args.getString("vfSourceUri")) { "Supply vfSourceUri from an original source accessible to VideoFlow." }
        val sourceInfo = com.videoflow.app.data.media.MediaAnalyzer(context).analyze(android.net.Uri.parse(configuredUri))
        val metadata = sourceInfo.metadata
        val videoInfo = metadata.videoTracks.firstOrNull()
        val rotated = metadata.rotationDegrees == 90 || metadata.rotationDegrees == 270
        val startUs = args.getString("vfStartUs")?.toLong() ?: 0L
        val endUs = args.getString("vfEndUs")?.toLong() ?: requireNotNull(metadata.durationUs)
        require(startUs >= 0L && endUs > startUs && endUs <= requireNotNull(metadata.durationUs))
        val durationUs = endUs-startUs
        val outputWidth = requireNotNull(if(rotated) metadata.height else metadata.width)
        val outputHeight = requireNotNull(if(rotated) metadata.width else metadata.height)
        val cadence = com.videoflow.app.domain.editor.SourceMediaAuthority.frameRate(metadata.frameRate)
        val roiValues = (args.getString("vfRoi") ?: "0.70,0.05,0.90,0.15").split(',').map { it.toFloat() }
        require(roiValues.size == 4)
        val configuredRoi = NormalizedRoi(roiValues[0],roiValues[1],roiValues[2],roiValues[3])
        val suffix = System.currentTimeMillis().toString()
        val projectId = "endurance-export-$suffix"
        val sourceUri = android.net.Uri.parse(configuredUri)
        val outputUri = createVideoRow(context, "videoflow-step4-final-output-$suffix.mp4")
        val aiRepository = AiWatermarkRepository(context)
        val keepOutput = args.getString("vfKeepOutput")?.toBooleanStrict() ?: true
        var succeeded = false
        val started = android.os.SystemClock.elapsedRealtime()
        var peakPssKb = 0L
        var lastSampleMs = -10_000L
        val diagnostics = java.io.File(context.getExternalFilesDir(null),"ai-endurance/$projectId.jsonl")
        diagnostics.parentFile!!.mkdirs()
        fun sample(progress: Float) {
            val elapsed = android.os.SystemClock.elapsedRealtime()-started
            if(elapsed-lastSampleMs < 5_000L && progress < 1f) return
            lastSampleMs=elapsed
            val pss=android.os.Debug.getPss();peakPssKb=maxOf(peakPssKb,pss)
            val thermal=if(android.os.Build.VERSION.SDK_INT >= 29) context.getSystemService(android.os.PowerManager::class.java).currentThermalStatus else -1
            diagnostics.appendText(org.json.JSONObject().put("elapsedMs",elapsed).put("progress",progress.toDouble())
                .put("pssKb",pss).put("sampledPeakPssKb",peakPssKb).put("thermalStatus",thermal)
                .put("tileSize",512).put("inferenceWorkers",1).put("queueLimit",2).toString()+"\n")
        }
        try {
            val track = TimelineTrack(
                id = "video-track",
                projectId = projectId,
                type = TrackType.VIDEO,
                name = "Video",
                orderIndex = 0
            )
            val clip = TimelineClip(
                id = "ai-final-clip",
                projectId = projectId,
                trackId = track.id,
                assetId = "source",
                timelineStartUs = 0L,
                sourceStartUs = startUs,
                sourceEndUs = endUs
            )
            val plan = FinalRenderPlan(
                editorPlan = RenderPlan(
                    projectId = projectId,
                    width = outputWidth,
                    height = outputHeight,
                    frameRate = cadence,
                    tracks = listOf(track),
                    clips = listOf(clip),
                    textOverlays = emptyList(),
                    imageOverlays = emptyList(),
                    keyframes = emptyList(),
                    backgroundArgb = 0xFF203040L
                ),
                originalSources = mapOf(
                    "source" to OriginalRenderSource(
                        assetId = "source",
                        sourceUri = sourceUri.toString(),
                        displayName = sourceInfo.displayName,
                        mimeType = sourceInfo.mimeType,
                        sizeBytes = sourceInfo.sizeBytes,
                        durationUs = metadata.durationUs,
                        width = metadata.width,
                        height = metadata.height,
                        rotationDegrees = metadata.rotationDegrees,
                        frameRate = metadata.frameRate,
                        videoCodecMime = metadata.videoCodecMime,
                        audioCodecMime = metadata.audioCodecMime,
                        audioSampleRate = metadata.audioSampleRate ?: 48_000,
                        audioChannelCount = metadata.audioChannelCount,
                        videoBitrate = videoInfo?.bitrate,
                        colorStandard = videoInfo?.colorStandard,
                        colorTransfer = videoInfo?.colorTransfer,
                        colorRange = videoInfo?.colorRange,
                        hdrStaticInfoPresent = videoInfo?.hdrStaticInfoPresent ?: false,
                        fingerprintSha256 = null
                    )
                ),
                durationUs = durationUs
            )
            val effect = AiWatermarkEffect(
                id = "final-ai-effect",
                projectId = projectId,
                clipId = clip.id,
                // Every frame in the configured duration uses the production FINAL model.
                clipLocalStartUs = 0L,
                clipLocalEndUs = durationUs,
                roi = configuredRoi,
                motionAnchors = emptyList(),
                contextPaddingPx = 32,
                featherPx = 6,
                temporalStability = .12f,
                modelId = AiModelCatalog.FINAL_512.id,
                enabled = true
            )
            aiRepository.upsert(effect)
            val settings = ResolvedExportSettings(
                size = ExportSize(outputWidth, outputHeight),
                frameRate = cadence,
                videoCodec = VideoCodec.H264,
                quality = ExportQuality.BALANCED,
                bitrateMode = BitrateMode.AUTO,
                videoBitrate = (outputWidth.toLong()*outputHeight*cadence.fps*.12).toLong().coerceIn(2_000_000,100_000_000).toInt(),
                audioCodec = AudioCodec.AAC_LC,
                audioBitrate = 128_000,
                audioSampleRate = metadata.audioSampleRate ?: 48_000,
                audioChannels = metadata.audioChannelCount ?: 2,
                hdrPolicy = HdrPolicy.PRESERVE_WHEN_COMPATIBLE,
                isUpscale = false
            )
            val manager = AiModelPackManager(context)
            manager.ensurePackInstalled()
            val engine = com.videoflow.app.render.SegmentedAiRenderEngine(context,Media3RenderEngine(context,aiRepository,manager),aiRepository).apply { checkpointIntervalUs = args.getString("vfCheckpointUs")?.toLong() ?: 60_000_000L }

            val prepared = engine.prepare(
                plan,
                OutputDestination(outputUri, "step4-final-ai.mp4"),
                settings
            )
            assertTrue("Final AI export preflight problems: ${prepared.problems}", prepared.ready)
            assertTrue(prepared.warnings.any { it.code == "LOCAL_AI_RENDER_REQUIRED" })

            val result = engine.render(requireNotNull(prepared.preparation), com.videoflow.app.render.RenderProgressListener(::sample)).getOrThrow()
            assertTrue("Final AI export validation problems: ${result.validation.problems}", result.validation.passed)
            assertTrue(result.outputBytes > 1_024L)
            assertEquals(outputWidth, result.validation.video?.width)
            assertEquals(outputHeight, result.validation.video?.height)
            assertEquals("video/avc", result.validation.video?.mimeType)
            if (metadata.audioCodecMime != null) assertEquals("audio/mp4a-latm", result.validation.audio?.mimeType)
            assertTrue(result.validation.video?.measuredFrameRate?.let { kotlin.math.abs(it - cadence.fps) < 0.5 } == true)

            val sha256 = resolver.openInputStream(outputUri)!!.use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            assertEquals(64, sha256.length)
            succeeded=true
            sample(1f)
            val marker =
                "LONG_AI_ENDURANCE_EXPORT_CERTIFIED project=$projectId bytes=${result.outputBytes} " +
                    "sha256=$sha256 model=${AiModelCatalog.FINAL_512.id} validation=true " +
                    "output=$outputUri diagnostics=${diagnostics.absolutePath} sampledPeakPssKb=$peakPssKb elapsedMs=${android.os.SystemClock.elapsedRealtime()-started}"
            diagnostics.appendText(org.json.JSONObject().put("completed",true).put("outputUri",outputUri.toString())
                .put("sha256",sha256).put("durationUs",durationUs).put("width",outputWidth).put("height",outputHeight).toString()+"\n")

            // System.out from instrumentation tests is not guaranteed to be forwarded by
            // `adb shell am instrument`. Send the marker through Instrumentation's status stream
            // so CI can cryptographically bind the visible certification evidence to this PASS.
            InstrumentationRegistry.getInstrumentation().sendStatus(
                0,
                Bundle().apply { putString("stream", "$marker\n") }
            )
        } finally {
            aiRepository.visualEdits.delete(projectId)
            aiRepository.replaceProjectEffects(projectId, emptyList())
            if(!succeeded || !keepOutput) resolver.delete(outputUri, null, null)
        }
    }

    private fun createVideoRow(context: Context, displayName: String): android.net.Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoFlowCertification")
        }
        return requireNotNull(context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
    }
}
