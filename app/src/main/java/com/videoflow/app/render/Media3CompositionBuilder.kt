package com.videoflow.app.render

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.videoflow.app.ai.watermark.OnnxWatermarkEffectFactory
import com.videoflow.app.ai.watermark.SharedLamaRenderRuntime
import com.videoflow.app.domain.ai.AiWatermarkEffect
import com.videoflow.app.domain.editor.ImageOverlay
import com.videoflow.app.domain.editor.KeyframeProperty
import com.videoflow.app.domain.editor.TextOverlay
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineTrack
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.domain.export.ExportSize
import com.videoflow.app.domain.export.FinalRenderPlan
import com.videoflow.app.domain.export.HdrPolicy
import com.videoflow.app.domain.export.OriginalRenderSource
import com.videoflow.app.domain.export.ResolvedExportSettings
import java.io.File

@UnstableApi
data class Media3CompositionBundle(
    val composition: Composition,
    val visualLayers: List<RenderVisualLayer>
)

/** Builds a Media3 Composition directly from the immutable editor RenderPlan. */
@UnstableApi
class Media3CompositionBuilder(
    private val rasterAssets: RenderRasterAssets
) {
    fun build(
        plan: FinalRenderPlan,
        settings: ResolvedExportSettings,
        aiEffects: List<AiWatermarkEffect> = emptyList(),
        aiRuntime: SharedLamaRenderRuntime? = null
    ): Media3CompositionBundle {
        require(plan.durationUs > 0) { "Cannot export an empty timeline" }
        if (aiEffects.isNotEmpty()) requireNotNull(aiRuntime) { "Local AI effects require an active LaMa render runtime." }
        val outputSize = settings.size
        val tracksById = plan.editorPlan.tracks.associateBy { it.id }
        val visualItems = mutableListOf<VisualItem>()
        val aiByClip = aiEffects.filter { it.enabled }.groupBy { it.clipId }

        plan.editorPlan.clips
            .filter { it.enabled }
            .forEach { clip ->
                val track = tracksById[clip.trackId] ?: return@forEach
                if (track.type == TrackType.VIDEO && track.visible) {
                    visualItems += VisualItem.Video(track.orderIndex, clip)
                }
            }
        plan.editorPlan.imageOverlays.forEach { overlay ->
            val track = tracksById[overlay.trackId] ?: return@forEach
            if (track.visible) visualItems += VisualItem.Image(track.orderIndex, overlay)
        }
        plan.editorPlan.textOverlays.forEach { overlay ->
            val track = tracksById[overlay.trackId] ?: return@forEach
            if (track.visible) visualItems += VisualItem.Text(track.orderIndex, overlay)
        }

        visualItems.sortWith(compareBy<VisualItem> { it.trackOrder }.thenBy { it.timelineStartUs }.thenBy { it.ownerId })

        val videoSequences = mutableListOf<EditedMediaItemSequence>()
        val layers = mutableListOf<RenderVisualLayer>()

        val background = rasterAssets.createBackground(plan.editorPlan.backgroundArgb)
        videoSequences += singleImageSequence(background, 0L, plan.durationUs, settings)
        layers += RenderVisualLayer(RenderLayerKind.BACKGROUND, "__background__")

        visualItems.forEach { item ->
            when (item) {
                is VisualItem.Video -> {
                    val source = plan.originalSources.getValue(item.clip.assetId)
                    videoSequences += singleVideoSequence(
                        clip = item.clip,
                        source = source,
                        settings = settings,
                        aiEffects = aiByClip[item.clip.id].orEmpty(),
                        aiRuntime = aiRuntime
                    )
                    val croppedWidth = (source.width ?: plan.editorPlan.width) * item.clip.transform.crop.run { right - left }
                    val croppedHeight = (source.height ?: plan.editorPlan.height) * item.clip.transform.crop.run { bottom - top }
                    val (sx, sy) = aspectFitScale(croppedWidth, croppedHeight, outputSize)
                    layers += RenderVisualLayer(RenderLayerKind.VIDEO_CLIP, item.clip.id, sx, sy)
                }
                is VisualItem.Image -> {
                    val source = plan.originalSources.getValue(item.overlay.assetId)
                    videoSequences += singleSourceImageSequence(
                        source.sourceUri,
                        item.overlay.timelineStartUs,
                        item.overlay.timelineEndUs - item.overlay.timelineStartUs,
                        settings
                    )
                    val width = (source.width ?: outputSize.width).toFloat()
                    val height = (source.height ?: outputSize.height).toFloat()
                    val (sx, sy) = aspectFitScale(width, height, outputSize)
                    layers += RenderVisualLayer(RenderLayerKind.IMAGE_OVERLAY, item.overlay.id, sx, sy)
                }
                is VisualItem.Text -> {
                    val textFile = rasterAssets.createText(
                        item.overlay,
                        ExportSize(plan.editorPlan.width, plan.editorPlan.height),
                        outputSize
                    )
                    val bounds = imageBounds(textFile)
                    videoSequences += singleImageSequence(
                        textFile,
                        item.overlay.timelineStartUs,
                        item.overlay.timelineEndUs - item.overlay.timelineStartUs,
                        settings
                    )
                    layers += RenderVisualLayer(
                        RenderLayerKind.TEXT_OVERLAY,
                        item.overlay.id,
                        (bounds.first.toFloat() / outputSize.width).coerceAtLeast(1f / outputSize.width),
                        (bounds.second.toFloat() / outputSize.height).coerceAtLeast(1f / outputSize.height)
                    )
                }
            }
        }

        val audioSequences = buildAudioSequences(plan, tracksById, settings)
        val allSequences = videoSequences + audioSequences
        val compositor = TimelineVideoCompositorSettings(plan, outputSize, layers)
        val hdrMode = when (settings.hdrPolicy) {
            HdrPolicy.CONVERT_TO_SDR -> Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
            HdrPolicy.PRESERVE_WHEN_COMPATIBLE, HdrPolicy.REQUIRE_PRESERVE -> Composition.HDR_MODE_KEEP_HDR
        }
        val composition = Composition.Builder(allSequences)
            .setVideoCompositorSettings(compositor)
            .setHdrMode(hdrMode)
            .build()
        return Media3CompositionBundle(composition, layers)
    }

    private fun buildAudioSequences(
        plan: FinalRenderPlan,
        tracksById: Map<String, TimelineTrack>,
        settings: ResolvedExportSettings
    ): List<EditedMediaItemSequence> {
        val audioCapableTracks = tracksById.values.filter { it.type == TrackType.AUDIO || it.type == TrackType.VIDEO }
        val soloTracks = audioCapableTracks.filter { it.solo }
        val audibleTrackIds = (if (soloTracks.isNotEmpty()) soloTracks else audioCapableTracks)
            .filterNot { it.muted }
            .map { it.id }
            .toSet()
        val keyframesByOwner = plan.editorPlan.keyframes.groupBy { it.ownerId }

        return plan.editorPlan.clips
            .asSequence()
            .filter { it.enabled && it.trackId in audibleTrackIds }
            .filter { plan.originalSources[it.assetId]?.audioCodecMime != null }
            .sortedWith(compareBy<TimelineClip> { tracksById[it.trackId]?.orderIndex ?: Int.MAX_VALUE }.thenBy { it.timelineStartUs }.thenBy { it.id })
            .map { clip ->
                val track = tracksById.getValue(clip.trackId)
                val media = clippedMediaItem(
                    plan.originalSources.getValue(clip.assetId).sourceUri,
                    clip.sourceStartUs,
                    clip.sourceEndUs
                )
                val gainProcessor = AutomationGainAudioProcessor(
                    baseGainDb = clip.gainDb + track.gainDb,
                    fadeInUs = clip.fadeInUs,
                    fadeOutUs = clip.fadeOutUs,
                    timelineDurationUs = clip.timelineDurationUs,
                    speed = clip.speed,
                    gainKeyframes = keyframesByOwner[clip.id].orEmpty().filter { it.property == KeyframeProperty.AUDIO_GAIN },
                    outputChannelCount = settings.audioChannels
                )
                val item = EditedMediaItem.Builder(media)
                    .setRemoveVideo(true)
                    .setSpeed(ConstantSpeedProvider(clip.speed.toFloat()))
                    .setEffects(Effects(listOf(gainProcessor), emptyList()))
                    .build()
                EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO)).apply {
                    if (clip.timelineStartUs > 0) addGap(clip.timelineStartUs)
                    addItem(item)
                }.build()
            }
            .toList()
    }

    private fun singleVideoSequence(
        clip: TimelineClip,
        source: OriginalRenderSource,
        settings: ResolvedExportSettings,
        aiEffects: List<AiWatermarkEffect>,
        aiRuntime: SharedLamaRenderRuntime?
    ): EditedMediaItemSequence {
        val effects = mutableListOf<Effect>()

        // AI reconstruction runs in original source pixel coordinates before crop/transform. This
        // is the critical proxy->original fidelity rule: proxy editing never reduces final AI ROI resolution.
        if (aiEffects.isNotEmpty()) {
            val runtime = requireNotNull(aiRuntime)
            val sourceWidth = source.width?.takeIf { it > 0 }
                ?: error("AI reconstruction requires known original source width for clip ${clip.id}.")
            val sourceHeight = source.height?.takeIf { it > 0 }
                ?: error("AI reconstruction requires known original source height for clip ${clip.id}.")
            aiEffects.sortedWith(compareBy<AiWatermarkEffect> { it.clipLocalStartUs }.thenBy { it.id }).forEach { ai ->
                effects += OnnxWatermarkEffectFactory.createEffects(ai, sourceWidth, sourceHeight, runtime)
            }
        }

        val crop = clip.transform.crop
        if (crop.left > 0f || crop.top > 0f || crop.right < 1f || crop.bottom < 1f) {
            effects += Crop(
                crop.left * 2f - 1f,
                crop.right * 2f - 1f,
                1f - crop.bottom * 2f,
                1f - crop.top * 2f
            )
        }
        val item = EditedMediaItem.Builder(clippedMediaItem(source.sourceUri, clip.sourceStartUs, clip.sourceEndUs))
            .setRemoveAudio(true)
            .setSpeed(ConstantSpeedProvider(clip.speed.toFloat()))
            .setFrameRate(kotlin.math.ceil(settings.frameRate.fps).toInt().coerceAtLeast(1))
            .setEffects(Effects(emptyList(), effects))
            .build()
        return EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).apply {
            if (clip.timelineStartUs > 0) addGap(clip.timelineStartUs)
            addItem(item)
        }.build()
    }

    private fun singleSourceImageSequence(
        sourceUri: String,
        startUs: Long,
        durationUs: Long,
        settings: ResolvedExportSettings
    ): EditedMediaItemSequence {
        val durationMs = ((durationUs.coerceAtLeast(1L) + 999L) / 1_000L).coerceAtLeast(1L)
        val media = MediaItem.Builder()
            .setUri(Uri.parse(sourceUri))
            .setImageDurationMs(durationMs)
            .build()
        val item = EditedMediaItem.Builder(media)
            .setDurationUs(durationUs.coerceAtLeast(1L))
            .setFrameRate(settings.frameRate.fps.toInt().coerceAtLeast(1))
            .setRemoveAudio(true)
            .build()
        return EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).apply {
            if (startUs > 0) addGap(startUs)
            addItem(item)
        }.build()
    }

    private fun singleImageSequence(
        file: File,
        startUs: Long,
        durationUs: Long,
        settings: ResolvedExportSettings
    ): EditedMediaItemSequence = singleSourceImageSequence(
        Uri.fromFile(file).toString(),
        startUs,
        durationUs,
        settings
    )

    private fun clippedMediaItem(sourceUri: String, startUs: Long, endUs: Long): MediaItem =
        MediaItem.Builder()
            .setUri(Uri.parse(sourceUri))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionUs(startUs)
                    .setEndPositionUs(endUs)
                    .build()
            )
            .build()

    private fun imageBounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth.coerceAtLeast(1) to options.outHeight.coerceAtLeast(1)
    }

    private fun aspectFitScale(
        sourceWidth: Number,
        sourceHeight: Number,
        output: ExportSize
    ): Pair<Float, Float> {
        val sw = sourceWidth.toFloat().coerceAtLeast(1f)
        val sh = sourceHeight.toFloat().coerceAtLeast(1f)
        val factor = minOf(output.width / sw, output.height / sh)
        return ((sw * factor) / output.width).coerceAtMost(1f) to
            ((sh * factor) / output.height).coerceAtMost(1f)
    }

    private sealed interface VisualItem {
        val trackOrder: Int
        val timelineStartUs: Long
        val ownerId: String

        data class Video(override val trackOrder: Int, val clip: TimelineClip) : VisualItem {
            override val timelineStartUs: Long get() = clip.timelineStartUs
            override val ownerId: String get() = clip.id
        }

        data class Image(override val trackOrder: Int, val overlay: ImageOverlay) : VisualItem {
            override val timelineStartUs: Long get() = overlay.timelineStartUs
            override val ownerId: String get() = overlay.id
        }

        data class Text(override val trackOrder: Int, val overlay: TextOverlay) : VisualItem {
            override val timelineStartUs: Long get() = overlay.timelineStartUs
            override val ownerId: String get() = overlay.id
        }
    }

    private class ConstantSpeedProvider(private val speed: Float) : SpeedProvider {
        init {
            require(speed.isFinite() && speed > 0f)
        }

        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }
}
