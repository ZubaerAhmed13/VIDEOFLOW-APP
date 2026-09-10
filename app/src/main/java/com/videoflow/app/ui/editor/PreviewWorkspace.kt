package com.videoflow.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoflow.app.ai.watermark.AiPreviewCacheBus
import com.videoflow.app.ai.watermark.AiPreviewCacheStore
import com.videoflow.app.data.editor.EditorProject
import com.videoflow.app.domain.editor.AiPreviewPlaybackResolver
import com.videoflow.app.domain.editor.AudioMath
import com.videoflow.app.domain.editor.CropRect
import com.videoflow.app.domain.editor.KeyframeEvaluator
import com.videoflow.app.domain.editor.KeyframeProperty
import com.videoflow.app.domain.editor.ProxyStatus
import com.videoflow.app.domain.editor.TimelineEngine
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.domain.model.SourceStatus
import com.videoflow.app.domain.model.VideoFlowProject
import com.videoflow.app.render.effects.media3CropEffectOrNull
import com.videoflow.app.ui.BoundedImagePreview
import com.videoflow.app.ui.NativeAudioPreview
import com.videoflow.app.ui.NativeVideoPlayer

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PreviewWorkspace(
    project: VideoFlowProject?,
    editor: EditorProject?,
    playheadUs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    activeTool: EditorTool? = null,
    previewDraft: ContextualPreviewDraft = ContextualPreviewDraft(),
    onCropChange: (CropRect) -> Unit = {},
    onCropCommit: (CropRect) -> Unit = {},
    onTransformGesture: (dxNormalized: Float, dyNormalized: Float, zoom: Float, rotationDelta: Float) -> Unit = { _, _, _, _ -> },
    onTransformGestureEnd: () -> Unit = {}
) {
    val visualContext = androidx.compose.ui.platform.LocalContext.current
    val aiPreviewRevision = AiPreviewCacheBus.revision.collectAsState().value
    val visualState = androidx.compose.runtime.produceState(com.videoflow.app.domain.effects.VisualEdits(), project?.updatedAt, editor) {
        project?.id?.let { id -> value = com.videoflow.app.data.effects.VisualEditsRepository(visualContext).load(id) }
    }
    val timeline = editor?.timeline
    val tracks = timeline?.tracks.orEmpty()
    val clips = timeline?.clips.orEmpty()
    val keyframes = timeline?.keyframes.orEmpty()
    val videoTracks = tracks.filter { it.type == TrackType.VIDEO && it.visible }.map { it.id }.toSet()
    val activeVideoClip = clips
        .filter { it.enabled && it.trackId in videoTracks && playheadUs in it.timelineStartUs until it.timelineEndUs }
        .maxByOrNull { clip -> tracks.firstOrNull { it.id == clip.trackId }?.orderIndex ?: -1 }
    val activeAsset = activeVideoClip?.let { clip -> project?.mediaAssets?.firstOrNull { it.id == clip.assetId } }
    val activeProxy = activeAsset?.let { asset ->
        editor?.proxies?.firstOrNull { it.assetId == asset.id && it.status == ProxyStatus.READY }
    }
    val previewSource = activeProxy?.path ?: activeAsset
        ?.takeIf { it.sourceStatus == SourceStatus.AVAILABLE }
        ?.sourceUri
    val aiReadySegments = androidx.compose.runtime.produceState(
        initialValue = emptyList<com.videoflow.app.ai.watermark.AiPreviewReadySegment>(),
        project?.id,
        activeVideoClip?.id,
        editor,
        aiPreviewRevision
    ) {
        val p = project
        val e = editor
        val c = activeVideoClip
        value = if (p != null && e != null && c != null) {
            AiPreviewCacheStore(visualContext).resolveReadySegments(p, e, c)
        } else emptyList()
    }
    val stitchedPreviewSegments = androidx.compose.runtime.remember(previewSource, activeVideoClip, aiReadySegments.value) {
        val clip = activeVideoClip
        if (previewSource != null && clip != null && aiReadySegments.value.isNotEmpty()) {
            AiPreviewPlaybackResolver.build(previewSource, clip, aiReadySegments.value)
        } else emptyList()
    }
    val activeAiPreview = aiReadySegments.value.any { activeLocal ->
        val local = activeVideoClip?.let { (playheadUs - it.timelineStartUs).coerceAtLeast(0L) } ?: -1L
        local in activeLocal.clipLocalStartUs until activeLocal.clipLocalEndUs
    }
    val activeVideoTrack = activeVideoClip?.let { clip -> tracks.firstOrNull { it.id == clip.trackId } }
    val effectiveAudioTrackIds = TimelineEngine.effectiveAudioTracks(tracks).map { it.id }.toSet()
    val activeLocalUs = activeVideoClip?.let { (playheadUs - it.timelineStartUs).coerceAtLeast(0L) } ?: 0L
    val activeFrames = activeVideoClip?.let { clip -> keyframes.filter { it.ownerId == clip.id } }.orEmpty()
    val effectivePreviewSpeed = activeVideoClip?.let { clip ->
        (activeTool as? EditorTool.Speed)?.takeIf { it.clipId == clip.id }?.let { previewDraft.speed } ?: clip.speed
    } ?: 1.0
    val sourcePositionMs = activeVideoClip?.let { clip ->
        val sourceUs = (clip.sourceStartUs + activeLocalUs.toDouble() * effectivePreviewSpeed).toLong()
            .coerceIn(clip.sourceStartUs, (clip.sourceEndUs - 1L).coerceAtLeast(clip.sourceStartUs))
        sourceUs / 1_000L
    } ?: 0L

    fun evaluated(property: KeyframeProperty, base: Float): Float =
        KeyframeEvaluator.evaluate(base, activeLocalUs, activeFrames.filter { it.property == property })

    val videoTransformDraft = (activeTool as? EditorTool.Transform)
        ?.takeIf { it.ownerType == VisualOwnerType.CLIP && it.ownerId == activeVideoClip?.id }
        ?.let { previewDraft.transform }
    val videoOpacityDraft = (activeTool as? EditorTool.Opacity)
        ?.takeIf { it.ownerType == VisualOwnerType.CLIP && it.ownerId == activeVideoClip?.id }
        ?.let { previewDraft.opacity }
    val evaluatedGain = activeVideoClip?.let { clip ->
        KeyframeEvaluator.evaluate(clip.gainDb, activeLocalUs, activeFrames.filter { it.property == KeyframeProperty.AUDIO_GAIN })
    } ?: 0f
    val effectiveGainDb = (activeTool as? EditorTool.Volume)
        ?.takeIf { it.clipId == activeVideoClip?.id }
        ?.let { previewDraft.gainDb } ?: evaluatedGain
    val effectiveFadeInUs = (activeTool as? EditorTool.Fade)
        ?.takeIf { it.clipId == activeVideoClip?.id }
        ?.let { previewDraft.fadeInUs } ?: activeVideoClip?.fadeInUs ?: 0L
    val effectiveFadeOutUs = (activeTool as? EditorTool.Fade)
        ?.takeIf { it.clipId == activeVideoClip?.id }
        ?.let { previewDraft.fadeOutUs } ?: activeVideoClip?.fadeOutUs ?: 0L
    val videoVolume = if (activeVideoClip != null && activeVideoTrack != null && activeVideoTrack.id in effectiveAudioTrackIds) {
        val gain = AudioMath.dbToLinear(effectiveGainDb + activeVideoTrack.gainDb)
        val fade = AudioMath.fadeGain(activeLocalUs, activeVideoClip.timelineDurationUs, effectiveFadeInUs, effectiveFadeOutUs)
        (gain * fade).coerceIn(0f, 1f)
    } else 0f

    val transform = activeVideoClip?.let { clip ->
        EvaluatedPreviewTransform(
            x = videoTransformDraft?.x ?: evaluated(KeyframeProperty.POSITION_X, clip.transform.x),
            y = videoTransformDraft?.y ?: evaluated(KeyframeProperty.POSITION_Y, clip.transform.y),
            scaleX = videoTransformDraft?.scaleX ?: evaluated(KeyframeProperty.SCALE_X, clip.transform.scaleX),
            scaleY = videoTransformDraft?.scaleY ?: evaluated(KeyframeProperty.SCALE_Y, clip.transform.scaleY),
            rotation = videoTransformDraft?.rotationDegrees ?: evaluated(KeyframeProperty.ROTATION, clip.transform.rotationDegrees),
            opacity = videoOpacityDraft ?: evaluated(KeyframeProperty.OPACITY, clip.opacity),
            flipHorizontal = videoTransformDraft?.flipHorizontal ?: clip.transform.flipHorizontal,
            flipVertical = videoTransformDraft?.flipVertical ?: clip.transform.flipVertical
        )
    }

    val activeText = timeline?.textOverlays.orEmpty().filter { playheadUs in it.timelineStartUs until it.timelineEndUs }
    val activeImages = timeline?.imageOverlays.orEmpty().filter { playheadUs in it.timelineStartUs until it.timelineEndUs }
    val activeAudioOnly = clips.filter { clip ->
        val asset = project?.mediaAssets?.firstOrNull { it.id == clip.assetId }
        asset?.mimeType?.startsWith("audio/") == true &&
            clip.trackId in effectiveAudioTrackIds && playheadUs in clip.timelineStartUs until clip.timelineEndUs
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(VideoFlowEditorColors.EditorBackground)
            .clipToBounds()
            .testTag("editor-preview-host")
            .semantics { contentDescription = "Video preview" },
        contentAlignment = Alignment.Center
    ) {
        val settings = editor?.settings
        val aspect = if (settings != null && settings.height > 0) settings.width.toFloat() / settings.height.toFloat() else 16f / 9f
        val surfaceAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else aspect
        val frameModifier = if (surfaceAspect > aspect) {
            Modifier.height(maxHeight).width(maxHeight * aspect)
        } else {
            Modifier.width(maxWidth).height(maxWidth / aspect)
        }
        BoxWithConstraints(
            frameModifier
                .background(settings?.let { Color(it.backgroundArgb.toInt()) } ?: Color.Black)
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            when {
                previewSource != null && activeAsset?.mimeType?.startsWith("video/") == true -> {
                    val t = transform ?: EvaluatedPreviewTransform()
                    val cropEditing = (activeTool as? EditorTool.Crop)?.takeIf { it.clipId == activeVideoClip?.id } != null
                    // Crop is a source-space operation. While editing, show the undistorted full source
                    // beneath the interactive mask. Outside Crop mode, preview the committed crop with
                    // the exact same Media3 Crop conversion used by final render.
                    val previewTransform = if (cropEditing) EvaluatedPreviewTransform() else t
                    val previewVideoEffects = androidx.compose.runtime.remember(visualState.value, activeVideoClip, cropEditing) {
                        activeVideoClip?.let { clip ->
                            buildList {
                                addAll(com.videoflow.app.render.effects.VisualEffectPipeline.create(visualState.value, clip.id, clip.sourceStartUs, clip.speed))
                                if (!cropEditing) media3CropEffectOrNull(clip.transform.crop)?.let { add(it) }
                            }
                        }.orEmpty()
                    }
                    NativeVideoPlayer(
                        uri = previewSource,
                        videoEffects = previewVideoEffects,
                        startPositionMs = sourcePositionMs,
                        playWhenReady = isPlaying,
                        speed = effectivePreviewSpeed.toFloat(),
                        volume = videoVolume,
                        previewSegments = stitchedPreviewSegments,
                        clipSourceStartMs = (activeVideoClip?.sourceStartUs ?: 0L) / 1_000L,
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(
                                x = maxWidth * (previewTransform.x - 0.5f),
                                y = maxHeight * (previewTransform.y - 0.5f)
                            )
                            .graphicsLayer {
                                // Crop itself never introduces independent X/Y scaling. Any non-uniform
                                // transform here can only come from an explicit Transform edit.
                                scaleX = previewTransform.scaleX * if (previewTransform.flipHorizontal) -1f else 1f
                                scaleY = previewTransform.scaleY * if (previewTransform.flipVertical) -1f else 1f
                                rotationZ = previewTransform.rotation
                                alpha = previewTransform.opacity.coerceIn(0f, 1f)
                            }
                    )
                }
                activeAsset != null && activeAsset.sourceStatus != SourceStatus.AVAILABLE && activeProxy == null -> {
                    ColumnMessage(title = "Original unavailable", subtitle = "Locate the source from Media details.")
                }
                (timeline?.durationUs ?: 0L) == 0L -> ColumnMessage("Start your video", "Add media from the toolbar below.")
                else -> Text("Project background", color = VideoFlowEditorColors.SecondaryText)
            }

            activeImages.forEach { overlay ->
                val asset = project?.mediaAssets?.firstOrNull { it.id == overlay.assetId } ?: return@forEach
                val localUs = (playheadUs - overlay.timelineStartUs).coerceAtLeast(0L)
                val frames = keyframes.filter { it.ownerId == overlay.id }
                fun value(property: KeyframeProperty, base: Float) = KeyframeEvaluator.evaluate(base, localUs, frames.filter { it.property == property })
                val draftTransform = (activeTool as? EditorTool.Transform)
                    ?.takeIf { it.ownerType == VisualOwnerType.IMAGE && it.ownerId == overlay.id }
                    ?.let { previewDraft.transform }
                val draftOpacity = (activeTool as? EditorTool.Opacity)
                    ?.takeIf { it.ownerType == VisualOwnerType.IMAGE && it.ownerId == overlay.id }
                    ?.let { previewDraft.opacity }
                BoundedImagePreview(
                    sourceUri = asset.sourceUri,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .offset(
                            x = maxWidth * ((draftTransform?.x ?: value(KeyframeProperty.POSITION_X, overlay.transform.x)) - 0.5f),
                            y = maxHeight * ((draftTransform?.y ?: value(KeyframeProperty.POSITION_Y, overlay.transform.y)) - 0.5f)
                        )
                        .graphicsLayer(
                            scaleX = draftTransform?.scaleX ?: value(KeyframeProperty.SCALE_X, overlay.transform.scaleX),
                            scaleY = draftTransform?.scaleY ?: value(KeyframeProperty.SCALE_Y, overlay.transform.scaleY),
                            rotationZ = draftTransform?.rotationDegrees ?: value(KeyframeProperty.ROTATION, overlay.transform.rotationDegrees),
                            alpha = (draftOpacity ?: value(KeyframeProperty.OPACITY, overlay.transform.opacity)).coerceIn(0f, 1f)
                        )
                )
            }

            activeText.forEach { overlay ->
                val localUs = (playheadUs - overlay.timelineStartUs).coerceAtLeast(0L)
                val frames = keyframes.filter { it.ownerId == overlay.id }
                fun value(property: KeyframeProperty, base: Float) = KeyframeEvaluator.evaluate(base, localUs, frames.filter { it.property == property })
                val draftTransform = (activeTool as? EditorTool.Transform)
                    ?.takeIf { it.ownerType == VisualOwnerType.TEXT && it.ownerId == overlay.id }
                    ?.let { previewDraft.transform }
                val draftOpacity = (activeTool as? EditorTool.Opacity)
                    ?.takeIf { it.ownerType == VisualOwnerType.TEXT && it.ownerId == overlay.id }
                    ?.let { previewDraft.opacity }
                val draftContent = (activeTool as? EditorTool.TextEditor)
                    ?.takeIf { it.overlayId == overlay.id }
                    ?.let { previewDraft.textContent }
                val draftStyle = (activeTool as? EditorTool.TextStyle)
                    ?.takeIf { it.overlayId == overlay.id }
                    ?.let { previewDraft.textStyle }
                Text(
                    text = draftContent ?: overlay.content,
                    color = Color((draftStyle?.colorArgb ?: overlay.colorArgb).toInt()).copy(alpha = (draftOpacity ?: value(KeyframeProperty.OPACITY, overlay.opacity)).coerceIn(0f, 1f)),
                    fontSize = (draftStyle?.fontSizeSp ?: overlay.fontSizeSp).sp,
                    fontWeight = FontWeight((draftStyle?.fontWeight ?: overlay.fontWeight).coerceIn(100, 900)),
                    fontStyle = if (draftStyle?.italic ?: overlay.italic) FontStyle.Italic else FontStyle.Normal,
                    textAlign = when (draftStyle?.alignment ?: overlay.alignment) {
                        "START" -> TextAlign.Start
                        "END" -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .offset(
                            x = maxWidth * ((draftTransform?.x ?: value(KeyframeProperty.POSITION_X, overlay.transform.x)) - 0.5f),
                            y = maxHeight * ((draftTransform?.y ?: value(KeyframeProperty.POSITION_Y, overlay.transform.y)) - 0.5f)
                        )
                        .graphicsLayer(
                            scaleX = draftTransform?.scaleX ?: value(KeyframeProperty.SCALE_X, overlay.transform.scaleX),
                            scaleY = draftTransform?.scaleY ?: value(KeyframeProperty.SCALE_Y, overlay.transform.scaleY),
                            rotationZ = draftTransform?.rotationDegrees ?: value(KeyframeProperty.ROTATION, overlay.transform.rotationDegrees)
                        )
                )
            }

            // New text gets a real preview before it is persisted.
            val newTextDraft = (activeTool as? EditorTool.TextEditor)?.takeIf { it.overlayId == null }?.let { previewDraft.textContent }
            if (newTextDraft != null && newTextDraft.isNotBlank()) {
                Text(
                    text = newTextDraft,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).widthIn(max = 280.dp)
                )
            }

            when (val tool = activeTool) {
                is EditorTool.Crop -> {
                    val target = timeline?.clips?.firstOrNull { it.id == tool.clipId }
                    if (target != null) {
                        val asset = project?.mediaAssets?.firstOrNull { it.id == target.assetId }
                        val cropModifier = asset?.let { source ->
                            val w = source.width
                            val h = source.height
                            if (w != null && h != null && w > 0 && h > 0) {
                                val (displayW, displayH) = displayDimensionsForRotation(w, h, source.rotationDegrees)
                                val sourceAspect = displayW.toFloat() / displayH.toFloat()
                                val viewportAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else sourceAspect
                                if (viewportAspect > sourceAspect) Modifier.align(Alignment.Center).height(maxHeight).width(maxHeight * sourceAspect)
                                else Modifier.align(Alignment.Center).width(maxWidth).height(maxWidth / sourceAspect)
                            } else Modifier.fillMaxSize()
                        } ?: Modifier.fillMaxSize()
                        CropInteractionOverlay(
                            crop = previewDraft.crop ?: target.transform.crop,
                            aspectRatio = previewDraft.cropNormalizedAspect,
                            onCropChange = onCropChange,
                            onCropCommit = onCropCommit,
                            modifier = cropModifier
                        )
                    }
                }
                is EditorTool.Transform -> {
                    val stored = when (tool.ownerType) {
                        VisualOwnerType.CLIP -> timeline?.clips?.firstOrNull { it.id == tool.ownerId }?.transform
                        VisualOwnerType.TEXT -> timeline?.textOverlays?.firstOrNull { it.id == tool.ownerId }?.transform
                        VisualOwnerType.IMAGE -> timeline?.imageOverlays?.firstOrNull { it.id == tool.ownerId }?.transform
                    }
                    val draft = previewDraft.transform
                    if (stored != null) {
                        TransformInteractionOverlay(
                            centerX = draft?.x ?: stored.x,
                            centerY = draft?.y ?: stored.y,
                            scale = draft?.scaleX ?: stored.scaleX,
                            onGesture = onTransformGesture,
                            onGestureEnd = onTransformGestureEnd,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                else -> Unit
            }
        }

        if (activeProxy != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Text("Proxy", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
            }
        }
        if (activeAiPreview) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Text("AI Preview", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
            }
        }
    }

    activeAudioOnly.forEach { clip ->
        val asset = project?.mediaAssets?.firstOrNull { it.id == clip.assetId } ?: return@forEach
        val track = tracks.firstOrNull { it.id == clip.trackId } ?: return@forEach
        val localUs = (playheadUs - clip.timelineStartUs).coerceAtLeast(0L)
        val frames = keyframes.filter { it.ownerId == clip.id && it.property == KeyframeProperty.AUDIO_GAIN }
        val evaluatedClipGain = KeyframeEvaluator.evaluate(clip.gainDb, localUs, frames)
        val clipGain = (activeTool as? EditorTool.Volume)?.takeIf { it.clipId == clip.id }?.let { previewDraft.gainDb } ?: evaluatedClipGain
        val fadeInUs = (activeTool as? EditorTool.Fade)?.takeIf { it.clipId == clip.id }?.let { previewDraft.fadeInUs } ?: clip.fadeInUs
        val fadeOutUs = (activeTool as? EditorTool.Fade)?.takeIf { it.clipId == clip.id }?.let { previewDraft.fadeOutUs } ?: clip.fadeOutUs
        val gain = AudioMath.dbToLinear(clipGain + track.gainDb)
        val fade = AudioMath.fadeGain(localUs, clip.timelineDurationUs, fadeInUs, fadeOutUs)
        NativeAudioPreview(
            uri = asset.sourceUri,
            startPositionMs = ((clip.sourceStartUs + localUs * clip.speed) / 1000.0).toLong(),
            playWhenReady = isPlaying,
            speed = clip.speed.toFloat(),
            volume = (gain * fade).coerceIn(0f, 1f)
        )
    }
}

@Composable
private fun ColumnMessage(title: String, subtitle: String) {
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = VideoFlowEditorColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

private data class EvaluatedPreviewTransform(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
)