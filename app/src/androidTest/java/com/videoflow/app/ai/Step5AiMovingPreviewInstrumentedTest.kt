@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.videoflow.app.ai

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.videoflow.app.ai.watermark.AiModelPackManager
import com.videoflow.app.ai.watermark.AiPreviewCacheStore
import com.videoflow.app.ai.watermark.AiProcessedPreviewManager
import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.data.diagnostics.LocalDiagnosticLog
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.data.media.MediaAnalyzer
import com.videoflow.app.data.media.UriFingerprintService
import com.videoflow.app.data.project.AddMediaResult
import com.videoflow.app.data.project.ProjectRepository
import com.videoflow.app.domain.ai.AiModelCatalog
import com.videoflow.app.domain.ai.AiWatermarkEffect
import com.videoflow.app.domain.ai.NormalizedRoi
import com.videoflow.app.domain.ai.RoiMotionAnchor
import com.videoflow.app.domain.editor.AiPreviewPlaybackResolver
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Decisive moving-preview gate: real LaMa media, stitched ranges, tracking and audio. */
@RunWith(AndroidJUnit4::class)
class Step5AiMovingPreviewInstrumentedTest {
    @Test
    fun processedPreview_isRealMovingMediaWithAudioAndInvalidatesOnEdit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val resolver = context.contentResolver
        val suffix = System.currentTimeMillis().toString()
        val sourceUri = createVideoRow(context, "videoflow-step5-preview-$suffix.mp4")
        val db = Room.inMemoryDatabaseBuilder(context, VideoFlowDatabase::class.java).allowMainThreadQueries().build()
        val aiRepository = AiWatermarkRepository(context)
        var cleanupProjectId: String? = null
        try {
            resolver.openOutputStream(sourceUri, "w")!!.use { output ->
                testContext.assets.open("sample_av.mp4").use { it.copyTo(output, 64 * 1024) }
            }
            val projects = ProjectRepository(
                db,
                context,
                MediaAnalyzer(context),
                UriFingerprintService(context),
                LocalDiagnosticLog()
            )
            val editor = EditorRepository(db)
            val projectId = projects.createProject("Step 5 real moving preview")
            cleanupProjectId = projectId
            val asset = (projects.addMedia(projectId, sourceUri) as AddMediaResult.Added).asset
            val clip = editor.addClip(projectId, asset.id, 0L)

            val startRoi = NormalizedRoi(.62f, .62f, .94f, .92f)
            val endRoi = NormalizedRoi(.06f, .08f, .38f, .38f)
            val moving = AiWatermarkEffect(
                id = "moving-$suffix",
                projectId = projectId,
                clipId = clip.id,
                clipLocalStartUs = 0L,
                clipLocalEndUs = 180_000L,
                roi = startRoi,
                motionAnchors = listOf(
                    RoiMotionAnchor(0L, .78f, .77f, width = startRoi.width, height = startRoi.height),
                    RoiMotionAnchor(160_000L, .22f, .23f, width = endRoi.width, height = endRoi.height)
                ),
                contextPaddingPx = 24,
                featherPx = 6,
                temporalStability = 0f,
                modelId = AiModelCatalog.FINAL_512.id
            )
            val second = AiWatermarkEffect(
                id = "second-$suffix",
                projectId = projectId,
                clipId = clip.id,
                clipLocalStartUs = 400_000L,
                clipLocalEndUs = 500_000L,
                roi = NormalizedRoi(.42f, .35f, .62f, .58f),
                contextPaddingPx = 24,
                featherPx = 6,
                temporalStability = 0f,
                modelId = AiModelCatalog.FINAL_512.id
            )
            aiRepository.replaceProjectEffects(projectId, listOf(moving, second))

            val models = AiModelPackManager(context)
            models.ensurePackInstalled()
            val manager = AiProcessedPreviewManager(context, editor, projects, aiRepository, models)
            val progress = mutableListOf<Float>()
            val ready = manager.prepareClip(projectId, clip.id) { progress += it }

            assertEquals(2, ready.size)
            assertTrue(progress.isNotEmpty() && progress.last() == 1f)
            assertTrue(ready.all { File(it.path).isFile && File(it.path).length() > 1_024L })
            ready.forEach { segment ->
                val tracks = mediaTracks(segment.path)
                assertTrue("processed preview missing video", tracks.first)
                assertTrue("processed preview must retain audio", tracks.second)
            }

            val project = requireNotNull(projects.getProject(projectId))
            val loadedEditor = editor.load(projectId)
            val cached = AiPreviewCacheStore(context).resolveReadySegments(project, loadedEditor, clip)
            assertEquals(2, cached.size)
            val stitched = AiPreviewPlaybackResolver.build(asset.sourceUri, clip, cached)
            assertEquals(listOf(true, false, true, false), stitched.map { it.aiProcessed })
            stitched.zipWithNext().forEach { (left, right) -> assertEquals(left.sourceOffsetEndMs, right.sourceOffsetStartMs) }

            val firstSegment = ready.first()
            val sourceEarly = sourceFrame(context, sourceUri, 35_000L)
            val outputEarly = fileFrame(firstSegment.path, 35_000L)
            val sourceLate = sourceFrame(context, sourceUri, 145_000L)
            val outputLate = fileFrame(firstSegment.path, 145_000L)
            try {
                val earlyStartDiff = roiDifference(sourceEarly, outputEarly, startRoi)
                val earlyEndDiff = roiDifference(sourceEarly, outputEarly, endRoi)
                val lateStartDiff = roiDifference(sourceLate, outputLate, startRoi)
                val lateEndDiff = roiDifference(sourceLate, outputLate, endRoi)
                assertTrue("AI change not visible near starting tracked ROI: $earlyStartDiff/$earlyEndDiff", earlyStartDiff > earlyEndDiff)
                assertTrue("AI change did not follow moving ROI: $lateEndDiff/$lateStartDiff", lateEndDiff > lateStartDiff)
                assertFalse("moving ROI must change position", moving.roiAt(20_000L) == moving.roiAt(145_000L))
            } finally {
                sourceEarly.recycle(); outputEarly.recycle(); sourceLate.recycle(); outputLate.recycle()
            }

            // An edit makes every old segment stale before any regeneration happens.
            aiRepository.upsert(moving.copy(roi = NormalizedRoi(.50f, .50f, .82f, .80f)))
            val stale = AiPreviewCacheStore(context).resolveReadySegments(
                requireNotNull(projects.getProject(projectId)),
                editor.load(projectId),
                clip
            )
            assertTrue(stale.isEmpty())

            InstrumentationRegistry.getInstrumentation().sendStatus(
                0,
                Bundle().apply {
                    putString("stream", "STEP5_AI_MOVING_PREVIEW_CERTIFIED ranges=${ready.size} audio=true tracking=true invalidation=true\n")
                }
            )
            manager.invalidateProject(projectId)
        } finally {
            cleanupProjectId?.let { aiRepository.deleteProjectState(it) }
            db.close()
            resolver.delete(sourceUri, null, null)
        }
    }

    private fun mediaTracks(path: String): Pair<Boolean, Boolean> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            var video = false
            var audio = false
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                video = video || mime.startsWith("video/")
                audio = audio || mime.startsWith("audio/")
            }
            video to audio
        } finally { extractor.release() }
    }

    private fun sourceFrame(context: Context, uri: Uri, timeUs: Long): Bitmap =
        MediaMetadataRetriever().let { retriever ->
            try {
                retriever.setDataSource(context, uri)
                requireNotNull(retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST))
            } finally { retriever.release() }
        }

    private fun fileFrame(path: String, timeUs: Long): Bitmap =
        MediaMetadataRetriever().let { retriever ->
            try {
                retriever.setDataSource(path)
                requireNotNull(retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST))
            } finally { retriever.release() }
        }

    private fun roiDifference(source: Bitmap, output: Bitmap, roi: NormalizedRoi): Double {
        val width = minOf(source.width, output.width)
        val height = minOf(source.height, output.height)
        val left = (roi.left * width).toInt().coerceIn(0, width - 1)
        val right = (roi.right * width).toInt().coerceIn(left + 1, width)
        val top = (roi.top * height).toInt().coerceIn(0, height - 1)
        val bottom = (roi.bottom * height).toInt().coerceIn(top + 1, height)
        var sum = 0L
        var count = 0L
        for (y in top until bottom step 2) for (x in left until right step 2) {
            val a = source.getPixel(x, y); val b = output.getPixel(x, y)
            sum += abs(android.graphics.Color.red(a) - android.graphics.Color.red(b))
            sum += abs(android.graphics.Color.green(a) - android.graphics.Color.green(b))
            sum += abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
            count += 3
        }
        return if (count == 0L) 0.0 else sum.toDouble() / count
    }

    private fun createVideoRow(context: Context, displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoFlowCertification")
        }
        return requireNotNull(context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
    }
}
