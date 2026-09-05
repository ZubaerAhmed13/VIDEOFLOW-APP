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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoflow.app.data.editor.EditorProject
import com.videoflow.app.domain.editor.AudioMath
import com.videoflow.app.domain.editor.CropRect
import com.videoflow.app.domain.editor.KeyframeEvaluator
import com.videoflow.app.domain.editor.KeyframeProperty
import com.videoflow.app.domain.editor.ProxyStatus
import com.videoflow.app.domain.editor.TimelineEngine
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.domain.model.SourceStatus
import com.videoflow.app.domain.model.VideoFlowProject
import com.videoflow.app.ui.BoundedImagePreview
import com.videoflow.app.ui.NativeAudioPreview
import com.videoflow.app.ui.NativeVideoPlayer

@Composable
fun PreviewWorkspace(
    project: VideoFlowProject?,
    editor: EditorProject?,
    playheadUs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    activeTool: EditorTool? = null,
    onCropChange: (CropRect) -> Unit = {},
    onTransformGesture: (dxNormalized: Float, dyNormalized: Float, zoom: Float, rotationDelta: Float) -> Unit = { _, _, _, _ -> }
) {
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
    val activeVideoTrack = activeVideoClip?.let { clip -> tracks.firstOrNull { it.id == clip.trackId } }
    val effectiveAudioTrackIds = TimelineEngine.effectiveAudioTracks(tracks).map { it.id }.toSet()
    val activeLocalUs = activeVideoClip?.let { (playheadUs - it.timelineStartUs).coerceAtLeast(0L) } ?: 0L
    val activeFrames = activeVideoClip?.let { clip -> keyframes.filter { it.ownerId == clip.id } }.orEmpty()
    val sourcePositionMs = activeVideoClip?.let { clip ->
        ((clip.sourceStartUs + activeLocalUs * clip.speed) / 1000.0).toLong()
    } ?: 0L
    val evaluatedGain = activeVideoClip?.let { clip ->
        KeyframeEvaluator.evaluate(
            clip.gainDb,
            activeLocalUs,
            activeFrames.filter { it.property == KeyframeProperty.AUDIO_GAIN }
        )
    } ?: 0f
    val videoVolume = if (activeVideoClip != null && activeVideoTrack != null && activeVideoTrack.id in effectiveAudioTrackIds) {
        val gain = AudioMath.dbToLinear(evaluatedGain + activeVideoTrack.gainDb)
        val fade = AudioMath.fadeGain(activeLocalUs, activeVideoClip.timelineDurationUs, activeVideoClip.fadeInUs, activeVideoClip.fadeOutUs)
        (gain * fade).coerceIn(0f, 1f)
    } else 0f

    fun evaluated(property: KeyframeProperty, base: Float): Float =
        KeyframeEvaluator.evaluate(base, activeLocalUs, activeFrames.filter { it.property == property })

    val transform = activeVideoClip?.let { clip ->
        EvaluatedPreviewTransform(
            x = evaluated(KeyframeProperty.POSITION_X, clip.transform.x),
            y = evaluated(KeyframeProperty.POSITION_Y, clip.transform.y),
            scaleX = evaluated(KeyframeProperty.SCALE_X, clip.transform.scaleX),
            scaleY = evaluated(KeyframeProperty.SCALE_Y, clip.transform.scaleY),
            rotation = evaluated(KeyframeProperty.ROTATION, clip.transform.rotationDegrees),
            opacity = evaluated(KeyframeProperty.OPACITY, clip.opacity),
            flipHorizontal = clip.transform.flipHorizontal,
            flipVertical = clip.transform.flipVertical
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
            .clipToBounds(),
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
                    val crop = activeVideoClip?.transform?.crop
                    val cropWidth = (crop?.right?.minus(crop.left) ?: 1f).coerceAtLeast(0.01f)
                    val cropHeight = (crop?.bottom?.minus(crop.top) ?: 1f).coerceAtLeast(0.01f)
                    val cropCenterX = ((crop?.left ?: 0f) + (crop?.right ?: 1f)) / 2f
                    val cropCenterY = ((crop?.top ?: 0f) + (crop?.bottom ?: 1f)) / 2f
                    NativeVideoPlayer(
                        uri = previewSource,
                        startPositionMs = sourcePositionMs,
                        playWhenReady = isPlaying,
                        speed = activeVideoClip?.speed?.toFloat() ?: 1f,
                        volume = videoVolume,
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(
                                x = maxWidth * (t.x - 0.5f),
                                y = maxHeight * (t.y - 0.5f)
                            )
                            .graphicsLayer {
                                scaleX = (t.scaleX / cropWidth) * if (t.flipHorizontal) -1f else 1f
                                scaleY = (t.scaleY / cropHeight) * if (t.flipVertical) -1f else 1f
                                rotationZ = t.rotation
                                alpha = t.opacity.coerceIn(0f, 1f)
                                translationX = (0.5f - cropCenterX) * size.width / cropWidth
                                translationY = (0.5f - cropCenterY) * size.height / cropHeight
                            }
                    )
                }
                activeAsset != null && activeAsset.sourceStatus != SourceStatus.AVAILABLE && activeProxy == null -> {
                    ColumnMessage(
                        title = "Original unavailable",
                        subtitle = "Locate the source from Media details."
                    )
                }
                (timeline?.durationUs ?: 0L) == 0L -> ColumnMessage("Start your video", "Add media from the toolbar below.")
                else -> Text("Project background", color = VideoFlowEditorColors.SecondaryText)
            }

            activeImages.forEach { overlay ->
                val asset = project?.mediaAssets?.firstOrNull { it.id == overlay.assetId } ?: return@forEach
                val localUs = (playheadUs - overlay.timelineStartUs).coerceAtLeast(0L)
                val frames = keyframes.filter { it.ownerId == overlay.id }
                fun value(property: KeyframeProperty, base: Float) = KeyframeEvaluator.evaluate(base, localUs, frames.filter { it.property == property })
                BoundedImagePreview(
                    sourceUri = asset.sourceUri,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .offset(
                            x = maxWidth * (value(KeyframeProperty.POSITION_X, overlay.transform.x) - 0.5f),
                            y = maxHeight * (value(KeyframeProperty.POSITION_Y, overlay.transform.y) - 0.5f)
                        )
                        .graphicsLayer(
                            scaleX = value(KeyframeProperty.SCALE_X, overlay.transform.scaleX),
                            scaleY = value(KeyframeProperty.SCALE_Y, overlay.transform.scaleY),
                            rotationZ = value(KeyframeProperty.ROTATION, overlay.transform.rotationDegrees),
                            alpha = value(KeyframeProperty.OPACITY, overlay.transform.opacity).coerceIn(0f, 1f)
                        )
                )
            }

            activeText.forEach { overlay ->
                val localUs = (playheadUs - overlay.timelineStartUs).coerceAtLeast(0L)
                val frames = keyframes.filter { it.ownerId == overlay.id }
                fun value(property: KeyframeProperty, base: Float) = KeyframeEvaluator.evaluate(base, localUs, frames.filter { it.property == property })
                Text(
                    text = overlay.content,
                    color = Color(overlay.colorArgb.toInt()).copy(alpha = value(KeyframeProperty.OPACITY, overlay.opacity).coerceIn(0f, 1f)),
                    fontSize = overlay.fontSizeSp.sp,
                    fontWeight = FontWeight(overlay.fontWeight.coerceIn(100, 900)),
                    fontStyle = if (overlay.italic) FontStyle.Italic else FontStyle.Normal,
                    textAlign = when (overlay.alignment) {
                        "START" -> TextAlign.Start
                        "END" -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .offset(
                            x = maxWidth * (value(KeyframeProperty.POSITION_X, overlay.transform.x) - 0.5f),
                            y = maxHeight * (value(KeyframeProperty.POSITION_Y, overlay.transform.y) - 0.5f)
                        )
                        .graphicsLayer(
                            scaleX = value(KeyframeProperty.SCALE_X, overlay.transform.scaleX),
                            scaleY = value(KeyframeProperty.SCALE_Y, overlay.transform.scaleY),
                            rotationZ = value(KeyframeProperty.ROTATION, overlay.transform.rotationDegrees)
                        )
                )
            }

            when (val tool = activeTool) {
                is EditorTool.Crop -> {
                    val target = timeline?.clips?.firstOrNull { it.id == tool.clipId }
                    if (target != null) {
                        CropInteractionOverlay(
                            crop = target.transform.crop,
                            onCropChange = onCropChange,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                is EditorTool.Transform -> {
                    val t = when (tool.ownerType) {
                        VisualOwnerType.CLIP -> timeline?.clips?.firstOrNull { it.id == tool.ownerId }?.transform
                        VisualOwnerType.TEXT -> timeline?.textOverlays?.firstOrNull { it.id == tool.ownerId }?.transform
                        VisualOwnerType.IMAGE -> timeline?.imageOverlays?.firstOrNull { it.id == tool.ownerId }?.transform
                    }
                    if (t != null) {
                        TransformInteractionOverlay(
                            centerX = t.x,
                            centerY = t.y,
                            scale = t.scaleX,
                            onGesture = onTransformGesture,
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
    }

    activeAudioOnly.forEach { clip ->
        val asset = project?.mediaAssets?.firstOrNull { it.id == clip.assetId } ?: return@forEach
        val track = tracks.firstOrNull { it.id == clip.trackId } ?: return@forEach
        val localUs = (playheadUs - clip.timelineStartUs).coerceAtLeast(0L)
        val frames = keyframes.filter { it.ownerId == clip.id && it.property == KeyframeProperty.AUDIO_GAIN }
        val clipGain = KeyframeEvaluator.evaluate(clip.gainDb, localUs, frames)
        val gain = AudioMath.dbToLinear(clipGain + track.gainDb)
        val fade = AudioMath.fadeGain(localUs, clip.timelineDurationUs, clip.fadeInUs, clip.fadeOutUs)
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
