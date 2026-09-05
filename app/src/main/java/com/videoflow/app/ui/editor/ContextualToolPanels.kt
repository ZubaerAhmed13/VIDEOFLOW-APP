package com.videoflow.app.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videoflow.app.data.editor.EditorProject
import com.videoflow.app.domain.editor.CropRect
import com.videoflow.app.domain.editor.KeyframeInterpolation
import com.videoflow.app.domain.editor.KeyframeOwnerType
import com.videoflow.app.domain.editor.KeyframeProperty
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.model.VideoFlowProject
import com.videoflow.app.ui.CachedThumbnailPreview
import com.videoflow.app.ui.ContextualEditingViewModel
import com.videoflow.app.ui.EditorViewModel
import com.videoflow.app.ui.OverlayAdvancedViewModel
import com.videoflow.app.ui.WaveformPreview
import com.videoflow.app.util.formatDurationUs
import kotlin.math.roundToInt

@Composable
fun ContextualToolHost(
    tool: EditorTool?,
    projectId: String,
    project: VideoFlowProject?,
    editor: EditorProject?,
    playheadUs: Long,
    thumbnails: Map<String, String>,
    waveforms: Map<String, FloatArray>,
    editorVm: EditorViewModel,
    contextualVm: ContextualEditingViewModel,
    overlayVm: OverlayAdvancedViewModel,
    previewDraft: ContextualPreviewDraft,
    onPreviewDraftChange: (ContextualPreviewDraft) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (EditorSelection) -> Unit,
    onOpenTool: (EditorTool) -> Unit,
    onPreviewSeek: (Long) -> Unit,
    refresh: () -> Unit
) {
    if (tool == null || editor == null) return
    val timeline = editor.timeline

    // Non-modal contextual inspector: the preview remains touchable for Crop/Transform.
    // Portrait uses a bounded bottom panel; landscape/expanded width uses a side inspector.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight || maxWidth.value >= 700f
        val panelModifier = if (wide) {
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(min = 320.dp, max = 390.dp)
        } else {
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * 0.45f)
        }
        Surface(
            modifier = panelModifier.imePadding(),
            color = VideoFlowEditorColors.EditorSurfaceElevated,
            tonalElevation = 8.dp
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                when (tool) {
                    is EditorTool.Trim -> timeline.clips.firstOrNull { it.id == tool.clipId }?.let { clip ->
                        TrimPanel(tool, clip, project, thumbnails, waveforms, contextualVm, projectId, onPreviewSeek, refresh, onDismiss)
                    }
                    is EditorTool.Speed -> timeline.clips.firstOrNull { it.id == tool.clipId }?.let { clip ->
                        SpeedPanel(clip, editorVm, onDismiss)
                    }
                    is EditorTool.Crop -> timeline.clips.firstOrNull { it.id == tool.clipId }?.let { clip ->
                        CropPanel(tool, clip, project, previewDraft, onPreviewDraftChange, contextualVm, projectId, refresh, onDismiss)
                    }
                    is EditorTool.Transform -> TransformPanel(projectId, tool, editor, playheadUs, previewDraft, onPreviewDraftChange, contextualVm, refresh, onDismiss)
                    is EditorTool.Opacity -> OpacityPanel(projectId, tool, editor, playheadUs, previewDraft, onPreviewDraftChange, contextualVm, refresh, onDismiss)
                    is EditorTool.Volume -> timeline.clips.firstOrNull { it.id == tool.clipId }?.let { clip ->
                        VolumePanel(projectId, clip, playheadUs, previewDraft, onPreviewDraftChange, contextualVm, refresh, onDismiss)
                    }
                    is EditorTool.Fade -> timeline.clips.firstOrNull { it.id == tool.clipId }?.let { clip ->
                        FadePanel(clip, previewDraft, onPreviewDraftChange, editorVm, waveforms, onDismiss)
                    }
                    is EditorTool.TextEditor -> TextEditorPanel(tool, projectId, playheadUs, editor, editorVm, contextualVm, previewDraft, onPreviewDraftChange, onSelect, refresh, onDismiss)
                    is EditorTool.TextStyle -> TextStylePanel(tool, projectId, editor, contextualVm, overlayVm, previewDraft, onPreviewDraftChange, refresh, onDismiss)
                    is EditorTool.Timing -> TimingPanel(tool, projectId, editor, contextualVm, refresh, onDismiss)
                    is EditorTool.Keyframes -> KeyframePanel(tool, projectId, editor, playheadUs, contextualVm, editorVm, refresh, onDismiss)
                    is EditorTool.More -> MorePanel(tool, projectId, editor, editorVm, contextualVm, onSelect, onOpenTool, refresh, onDismiss)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ToolHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(title, color = VideoFlowEditorColors.PrimaryText, fontWeight = FontWeight.SemiBold)
        subtitle?.let { Text(it, color = VideoFlowEditorColors.SecondaryText) }
    }
}

@Composable
private fun ActionRow(onCancel: () -> Unit, onReset: (() -> Unit)? = null, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onCancel) { Text("Cancel") }
        if (onReset != null) OutlinedButton(onClick = onReset) { Text("Reset") }
        Button(onClick = onDone) { Text("Done") }
    }
}

@Composable
private fun TrimPanel(
    tool: EditorTool.Trim,
    clip: TimelineClip,
    project: VideoFlowProject?,
    thumbnails: Map<String, String>,
    waveforms: Map<String, FloatArray>,
    contextualVm: ContextualEditingViewModel,
    projectId: String,
    onPreviewSeek: (Long) -> Unit,
    refresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val asset = project?.mediaAssets?.firstOrNull { it.id == clip.assetId }
    val sourceDuration = (asset?.durationUs ?: clip.sourceEndUs).coerceAtLeast(1L)
    val isAudio = asset?.mimeType?.startsWith("audio/") == true || (asset?.videoTrackCount ?: 0) == 0
    var range by remember(tool.clipId) {
        mutableStateOf((clip.sourceStartUs.toFloat() / sourceDuration)..(clip.sourceEndUs.toFloat() / sourceDuration))
    }
    var pendingPreviewUs by remember(tool.clipId) { mutableLongStateOf(clip.timelineStartUs) }
    var lastPreviewSeekMs by remember(tool.clipId) { mutableLongStateOf(0L) }
    val startUs = (range.start * sourceDuration).toLong().coerceIn(0L, sourceDuration - 1L)
    val endUs = (range.endInclusive * sourceDuration).toLong().coerceIn(startUs + 1L, sourceDuration)

    ToolHeader("Trim", if (isAudio) "Drag the waveform handles" else "Drag the visual handles")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isAudio) {
            WaveformPreview(waveforms[clip.assetId], Modifier.fillMaxWidth().height(58.dp), VideoFlowEditorColors.SelectionAccent)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(6) {
                    CachedThumbnailPreview(thumbnails[clip.assetId], Modifier.weight(1f).height(50.dp))
                }
            }
        }
        RangeSlider(
            value = range,
            onValueChange = { next ->
                val minGap = (100_000f / sourceDuration.toFloat()).coerceAtMost(0.2f)
                if (next.endInclusive - next.start >= minGap) {
                    val previous = range
                    range = next
                    val startDelta = kotlin.math.abs(next.start - previous.start)
                    val endDelta = kotlin.math.abs(next.endInclusive - previous.endInclusive)
                    val sourceBoundaryUs = if (startDelta >= endDelta) {
                        (next.start * sourceDuration).toLong()
                    } else {
                        (next.endInclusive * sourceDuration).toLong()
                    }.coerceIn(0L, sourceDuration)
                    val timelineOffsetUs = ((sourceBoundaryUs - clip.sourceStartUs).toDouble() / clip.speed).toLong()
                    pendingPreviewUs = (clip.timelineStartUs + timelineOffsetUs)
                        .coerceIn(clip.timelineStartUs, clip.timelineEndUs)
                    val nowMs = android.os.SystemClock.elapsedRealtime()
                    if (nowMs - lastPreviewSeekMs >= 50L) {
                        lastPreviewSeekMs = nowMs
                        onPreviewSeek(pendingPreviewUs)
                    }
                }
            },
            onValueChangeFinished = { onPreviewSeek(pendingPreviewUs) },
            valueRange = 0f..1f,
            modifier = Modifier.semantics { contentDescription = "Trim start and end handles" }
        )
        Text("Start      ${formatDurationUs(startUs)}")
        Text("End        ${formatDurationUs(endUs)}")
        Text("Duration   ${formatDurationUs(((endUs - startUs) / clip.speed).toLong())}")
    }
    ActionRow(
        onCancel = onDismiss,
        onReset = { range = 0f..1f },
        onDone = {
            contextualVm.commitTrim(projectId, clip.id, startUs, endUs) {
                refresh()
                onDismiss()
            }
        }
    )
}

@Composable
private fun SpeedPanel(clip: TimelineClip, editorVm: EditorViewModel, onDismiss: () -> Unit) {
    var speed by remember(clip.id) { mutableFloatStateOf(clip.speed.toFloat()) }
    ToolHeader("Speed", "Preview the resulting duration before applying")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 4f).forEach { preset ->
                OutlinedButton(onClick = { speed = preset }) { Text("${formatMultiplier(preset)}×") }
            }
        }
        Slider(
            value = speed,
            onValueChange = { speed = it.coerceIn(0.25f, 4f) },
            valueRange = 0.25f..4f,
            modifier = Modifier.semantics { contentDescription = "Speed, ${formatMultiplier(speed)} times" }
        )
        Text("Speed              ${formatMultiplier(speed)}×")
        Text("Current duration   ${formatDurationUs(clip.timelineDurationUs)}")
        Text("New duration       ${formatDurationUs((clip.sourceDurationUs / speed).toLong())}")
    }
    ActionRow(
        onCancel = onDismiss,
        onReset = { speed = 1f },
        onDone = { editorVm.selectClip(clip.id); editorVm.setSpeed(speed.toDouble()); onDismiss() }
    )
}

@Composable
private fun CropPanel(
    tool: EditorTool.Crop,
    clip: TimelineClip,
    project: VideoFlowProject?,
    previewDraft: ContextualPreviewDraft,
    onPreviewDraftChange: (ContextualPreviewDraft) -> Unit,
    contextualVm: ContextualEditingViewModel,
    projectId: String,
    refresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val asset = project?.mediaAssets?.firstOrNull { it.id == clip.assetId }
    val crop = previewDraft.crop ?: clip.transform.crop
    fun update(next: CropRect, aspect: Float? = previewDraft.cropNormalizedAspect) {
        onPreviewDraftChange(previewDraft.copy(crop = next, cropNormalizedAspect = aspect))
    }
    fun preset(w: Int, h: Int) {
        val sw = asset?.width ?: return
        val sh = asset.height ?: return
        val targetAspect = w.toFloat() / h.toFloat()
        val normalizedAspect = targetAspect / (sw.toFloat() / sh.toFloat())
        update(centeredCrop(sw, sh, targetAspect), normalizedAspect)
    }

    ToolHeader("Crop", "Drag the crop region directly on the preview or use precise bounds")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { update(crop, null) }) { Text(if (previewDraft.cropNormalizedAspect == null) "✓ Free" else "Free") }
            OutlinedButton(onClick = { update(CropRect(), null) }) { Text("Original") }
            listOf(16 to 9, 9 to 16, 4 to 3, 3 to 2, 1 to 1, 4 to 5).forEach { (w, h) ->
                OutlinedButton(onClick = { preset(w, h) }) { Text("$w:$h") }
            }
        }
        CropEdgeSlider("Left", crop.left, 0f, crop.right - 0.01f) { update(CropRect(it, crop.top, crop.right, crop.bottom)) }
        CropEdgeSlider("Right", crop.right, crop.left + 0.01f, 1f) { update(CropRect(crop.left, crop.top, it, crop.bottom)) }
        CropEdgeSlider("Top", crop.top, 0f, crop.bottom - 0.01f) { update(CropRect(crop.left, it, crop.right, crop.bottom)) }
        CropEdgeSlider("Bottom", crop.bottom, crop.top + 0.01f, 1f) { update(CropRect(crop.left, crop.top, crop.right, it)) }
    }
    ActionRow(
        onCancel = onDismiss,
        onReset = { update(CropRect(), null) },
        onDone = { contextualVm.setClipCrop(projectId, tool.clipId, crop) { refresh(); onDismiss() } }
    )
}

@Composable
private fun CropEdgeSlider(label: String, value: Float, min: Float, max: Float, onValue: (Float) -> Unit) {
    if (max <= min) return
    Text("$label ${(value * 100).roundToInt()}%")
    Slider(
        value = value.coerceIn(min, max),
        onValueChange = onValue,
        valueRange = min..max,
        modifier = Modifier.semantics { contentDescription = "$label crop edge, ${(value * 100).roundToInt()} percent" }
    )
}

@Composable
private fun TransformPanel(
    projectId: String,
    tool: EditorTool.Transform,
    editor: EditorProject,
    playheadUs: Long,
    previewDraft: ContextualPreviewDraft,
    onPreviewDraftChange: (ContextualPreviewDraft) -> Unit,
    contextualVm: ContextualEditingViewModel,
    refresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val stored = when (tool.ownerType) {
        VisualOwnerType.CLIP -> editor.timeline.clips.firstOrNull { it.id == tool.ownerId }?.transform
        VisualOwnerType.TEXT -> editor.timeline.textOverlays.firstOrNull { it.id == tool.ownerId }?.transform
        VisualOwnerType.IMAGE -> editor.timeline.imageOverlays.firstOrNull { it.id == tool.ownerId }?.transform
    } ?: return
    val clip = editor.timeline.clips.firstOrNull { it.id == tool.ownerId }
    val transform = previewDraft.transform ?: PreviewTransformDraft(
        stored.x, stored.y, stored.scaleX, stored.scaleY, stored.rotationDegrees,
        clip?.transform?.flipHorizontal ?: false,
        clip?.transform?.flipVertical ?: false
    )
    fun update(next: PreviewTransformDraft) = onPreviewDraftChange(previewDraft.copy(transform = next))
    fun commit() {
        when (tool.ownerType) {
            VisualOwnerType.CLIP -> contextualVm.setClipTransform(
                projectId, tool.ownerId, transform.x, transform.y, transform.scaleX, transform.rotationDegrees,
                playheadUs = playheadUs, flipHorizontal = transform.flipHorizontal, flipVertical = transform.flipVertical
            ) { refresh(); onDismiss() }
            VisualOwnerType.TEXT -> contextualVm.setTextTransform(
                projectId, tool.ownerId, transform.x, transform.y, transform.scaleX, transform.rotationDegrees,
                playheadUs = playheadUs
            ) { refresh(); onDismiss() }
            VisualOwnerType.IMAGE -> contextualVm.setImageTransform(
                projectId, tool.ownerId, transform.x, transform.y, transform.scaleX, transform.rotationDegrees,
                playheadUs = playheadUs
            ) { refresh(); onDismiss() }
        }
    }

    ToolHeader("Transform", "Drag or pinch directly on the preview; sliders provide precise control")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        LabeledSlider("Position X", transform.x, 0f..1f, "${(transform.x * 100).roundToInt()}%") { update(transform.copy(x = it)) }
        LabeledSlider("Position Y", transform.y, 0f..1f, "${(transform.y * 100).roundToInt()}%") { update(transform.copy(y = it)) }
        LabeledSlider("Scale", transform.scaleX.coerceIn(0.05f, 4f), 0.05f..4f, "${(transform.scaleX * 100).roundToInt()}%") { update(transform.copy(scaleX = it, scaleY = it)) }
        LabeledSlider("Rotation", normalize180(transform.rotationDegrees), -180f..180f, "${normalize180(transform.rotationDegrees).roundToInt()}°") { update(transform.copy(rotationDegrees = it)) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0f, 90f, 180f, -90f).forEach { angle ->
                OutlinedButton(onClick = { update(transform.copy(rotationDegrees = angle)) }) { Text(if (angle == -90f) "270°" else "${angle.roundToInt()}°") }
            }
            if (tool.ownerType == VisualOwnerType.CLIP) {
                OutlinedButton(onClick = { update(transform.copy(flipHorizontal = !transform.flipHorizontal)) }) { Text(if (transform.flipHorizontal) "✓ Flip H" else "Flip H") }
                OutlinedButton(onClick = { update(transform.copy(flipVertical = !transform.flipVertical)) }) { Text(if (transform.flipVertical) "✓ Flip V" else "Flip V") }
            }
        }
    }
    ActionRow(
        onCancel = onDismiss,
        onReset = { update(transform.copy(x = 0.5f, y = 0.5f, scaleX = 1f, scaleY = 1f, rotationDegrees = 0f, flipHorizontal = false, flipVertical = false)) },
        onDone = ::commit
    )
}

@Composable
private fun OpacityPanel(
    projectId: String,
    tool: EditorTool.Opacity,
    editor: EditorProject,
    playheadUs: Long,
    previewDraft: ContextualPreviewDraft,
    onPreviewDraftChange: (ContextualPreviewDraft) -> Unit,
    contextualVm: ContextualEditingViewModel,
    refresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val stored = when (tool.ownerType) {
        VisualOwnerType.CLIP -> editor.timeline.clips.firstOrNull { it.id == tool.ownerId }?.opacity
        VisualOwnerType.TEXT -> editor.timeline.textOverlays.firstOrNull { it.id == tool.ownerId }?.opacity
        VisualOwnerType.IMAGE -> editor.timeline.imageOverlays.firstOrNull { it.id == tool.ownerId }?.transform?.opacity
    } ?: return
    val value = (previewDraft.opacity ?: stored).coerceIn(0f, 1f)
    fun update(next: Float) = onPreviewDraftChange(previewDraft.copy(opacity = next.coerceIn(0f, 1f)))
    val ownerType = when (tool.ownerType) {
        VisualOwnerType.CLIP -> KeyframeOwnerType.CLIP
        VisualOwnerType.TEXT -> KeyframeOwnerType.TEXT_OVERLAY
        VisualOwnerType.IMAGE -> KeyframeOwnerType.IMAGE_OVERLAY
    }
    ToolHeader("Opacity")
    Column(Modifier.padding(horizontal = 18.dp)) {
        Text("${(value * 100).roundToInt()}%")
        Slider(
            value = value,
            onValueChange = { update(it) },
            valueRange = 0f..1f,
            modifier = Modifier.semantics { contentDescription = "Opacity, ${(value * 100).roundToInt()} percent" }
        )
    }
    ActionRow(
        onCancel = onDismiss,
        onReset = { update(1f) },
        onDone = { contextualVm.setVisualOpacity(projectId, tool.ownerId, ownerType, value, playheadUs) { refresh(); onDismiss() } }
    )
}

@Composable
private fun VolumePanel(
    projectId: String,
    clip: TimelineClip,
    playheadUs: Long,
    previewDraft: ContextualPreviewDraft,
    onPreviewDraftChange: (ContextualPreviewDraft) -> Unit,
    contextualVm: ContextualEditingViewModel,
    refresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val value = (previewDraft.gainDb ?: clip.gainDb).coerceIn(-60f, 24f)
    fun update(next: Float) = onPreviewDraftChange(previewDraft.copy(gainDb = next.coerceIn(-60f, 24f)))
    ToolHeader("Clip Volume", "Track volume remains in Track Settings")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${value.roundToInt()} dB${if (value == 0f) " • 100% reference" else ""}")
        Slider(
            value = value,
            onValueChange = { update(it) },
            valueRange = -60f..24f,
            modifier = Modifier.semantics { contentDescription = "Clip volume, ${value.roundToInt()} decibels" }
        )
        // The domain has gain but no distinct clip-mute flag. This is deliberately labelled as gain.
        OutlinedButton(onClick = { update(-60f) }) { Text("Silence (-60 dB)") }
    }
    ActionRow(
        onCancel = onDismiss,
        onReset = { update(0f) },
        onDone = { contextualVm.setClipGain(projectId, clip.id, value, playheadUs) { refresh(); onDismiss() } }
    )
}

@Composable
private fun FadePanel(
    clip: TimelineClip,
    previewDraft: ContextualPreviewDraft,
    onPreviewDraftChange: (ContextualPreviewDraft) -> Unit,
    editorVm: EditorViewModel,
    waveforms: Map<String, FloatArray>,
    onDismiss: () -> Unit
) {
    val maxUs = clip.timelineDurationUs.coerceAtLeast(100_000L)
    val maxSeconds = maxUs / 1_000_000f
    val fadeInUs = (previewDraft.fadeInUs ?: clip.fadeInUs).coerceIn(0L, maxUs)
    val fadeOutUs = (previewDraft.fadeOutUs ?: clip.fadeOutUs).coerceIn(0L, maxUs)
    fun update(inUs: Long = fadeInUs, outUs: Long = fadeOutUs) {
        onPreviewDraftChange(previewDraft.copy(fadeInUs = inUs.coerceIn(0L, maxUs), fadeOutUs = outUs.coerceIn(0L, maxUs)))
    }
    ToolHeader("Audio Fade", "Fade values are bounded by the clip duration")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        WaveformPreview(waveforms[clip.assetId], Modifier.fillMaxWidth().height(48.dp), VideoFlowEditorColors.SelectionAccent)
        Text("Fade In ${"%.2f".format(fadeInUs / 1_000_000f)} s")
        Slider(value = fadeInUs / 1_000_000f, onValueChange = { update(inUs = (it * 1_000_000L).toLong()) }, valueRange = 0f..maxSeconds)
        Text("Fade Out ${"%.2f".format(fadeOutUs / 1_000_000f)} s")
        Slider(value = fadeOutUs / 1_000_000f, onValueChange = { update(outUs = (it * 1_000_000L).toLong()) }, valueRange = 0f..maxSeconds)
    }
    ActionRow(
        onCancel = onDismiss,
        onReset = { update(0L, 0L) },
        onDone = { editorVm.selectClip(clip.id); editorVm.setFades(fadeInUs, fadeOutUs); onDismiss() }
    )
}

@Composable
private fun TextEditorPanel(
    tool: EditorTool.TextEditor,
    projectId: String,
    playheadUs: Long,
    editor: EditorProject,
    editorVm: EditorViewModel,
    contextualVm: ContextualEditingViewModel,
    previewDraft: ContextualPreviewDraft,
    onPreviewDraftChange: (ContextualPreviewDraft) -> Unit,
    onSelect: (EditorSelection) -> Unit,
    refresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val existing = tool.overlayId?.let { id -> editor.timeline.textOverlays.firstOrNull { it.id == id } }
    val draft = previewDraft.textContent ?: existing?.content.orEmpty()
    ToolHeader(if (existing == null) "Add Text" else "Edit Text", "Typing updates the preview immediately; Done saves once")
    Column(Modifier.padding(horizontal = 18.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { onPreviewDraftChange(previewDraft.copy(textContent = it.take(4096))) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            label = { Text("Text") }
        )
    }
    ActionRow(
        onCancel = onDismiss,
        onDone = {
            if (existing == null) {
                contextualVm.addText(projectId, playheadUs, draft) { id -> refresh(); onSelect(EditorSelection.TextOverlay(id)); onDismiss() }
            } else {
                editorVm.updateTextContent(existing.id, draft)
                onDismiss()
            }
        }
    )
}

@Composable
private fun TextStylePanel(
    tool: EditorTool.TextStyle,
    projectId: String,
    editor: EditorProject,
    contextualVm: ContextualEditingViewModel,
    overlayVm: OverlayAdvancedViewModel,
    previewDraft: ContextualPreviewDraft,
    onPreviewDraftChange: (ContextualPreviewDraft) -> Unit,
    refresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val overlay = editor.timeline.textOverlays.firstOrNull { it.id == tool.overlayId } ?: return
    var size by remember(tool.overlayId) { mutableFloatStateOf(overlay.fontSizeSp) }
    var weight by remember(tool.overlayId) { mutableStateOf(overlay.fontWeight) }
    var italic by remember(tool.overlayId) { mutableStateOf(overlay.italic) }
    var alignment by remember(tool.overlayId) { mutableStateOf(overlay.alignment) }
    var color by remember(tool.overlayId) { mutableLongStateOf(overlay.colorArgb) }
    var hex by remember(tool.overlayId) { mutableStateOf(argbToHex(overlay.colorArgb)) }
    val initialHsv = remember(tool.overlayId) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(overlay.colorArgb.toInt(), it) }
    }
    var hue by remember(tool.overlayId) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(tool.overlayId) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(tool.overlayId) { mutableFloatStateOf(initialHsv[2]) }

    fun publishStyle() {
        onPreviewDraftChange(
            previewDraft.copy(
                textStyle = PreviewTextStyleDraft(size, weight, italic, alignment, color)
            )
        )
    }

    fun syncHsv(nextColor: Long) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(nextColor.toInt(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
    }

    fun selectColor(nextColor: Long) {
        color = nextColor and 0xFFFFFFFFL
        hex = argbToHex(color)
        syncHsv(color)
        publishStyle()
    }

    fun selectHsv(
        nextHue: Float = hue,
        nextSaturation: Float = saturation,
        nextBrightness: Float = brightness
    ) {
        hue = nextHue.coerceIn(0f, 360f)
        saturation = nextSaturation.coerceIn(0f, 1f)
        brightness = nextBrightness.coerceIn(0f, 1f)
        val alpha = ((color ushr 24) and 0xFF).toInt().coerceIn(0, 255)
        color = android.graphics.Color.HSVToColor(
            alpha,
            floatArrayOf(hue, saturation, brightness)
        ).toLong() and 0xFFFFFFFFL
        hex = argbToHex(color)
        publishStyle()
    }

    ToolHeader("Text Style", "Only properties supported by the real text backend are shown")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        LabeledSlider("Font size", size, 6f..128f, "${size.roundToInt()} sp") { size = it; publishStyle() }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(400 to "Regular", 500 to "Medium", 700 to "Bold").forEach { (value, label) ->
                OutlinedButton(onClick = { weight = value; publishStyle() }) { Text(if (weight == value) "✓ $label" else label) }
            }
            OutlinedButton(onClick = { italic = !italic; publishStyle() }) { Text(if (italic) "✓ Italic" else "Italic") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("START" to "Left", "CENTER" to "Center", "END" to "Right").forEach { (value, label) ->
                OutlinedButton(onClick = { alignment = value; publishStyle() }) { Text(if (alignment == value) "✓ $label" else label) }
            }
        }
        Text("Color")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                0xFFFFFFFFL to "White",
                0xFF000000L to "Black",
                0xFFFF5252L to "Red",
                0xFFFFFF00L to "Yellow",
                0xFF4CAF50L to "Green",
                0xFF42A5F5L to "Blue"
            ).forEach { (value, label) -> OutlinedButton(onClick = { selectColor(value) }) { Text(label) } }
        }
        LabeledSlider("Hue", hue, 0f..360f, "${hue.roundToInt()}°") { selectHsv(nextHue = it) }
        LabeledSlider("Saturation", saturation, 0f..1f, "${(saturation * 100).roundToInt()}%") { selectHsv(nextSaturation = it) }
        LabeledSlider("Brightness", brightness, 0f..1f, "${(brightness * 100).roundToInt()}%") { selectHsv(nextBrightness = it) }
        OutlinedTextField(
            value = hex,
            onValueChange = { raw ->
                hex = raw.take(9)
                parseArgb(hex)?.let { parsed ->
                    color = parsed
                    syncHsv(parsed)
                    publishStyle()
                }
            },
            label = { Text("Hex color") },
            singleLine = true
        )
    }
    ActionRow(
        onCancel = onDismiss,
        onDone = {
            contextualVm.setTextStyle(projectId, overlay.id, size, weight, italic, alignment) {
                overlayVm.setTextColor(projectId, overlay.id, color) {
                    refresh(); onDismiss()
                }
            }
        }
    )
}

@Composable
private fun TimingPanel(
    tool: EditorTool.Timing,
    projectId: String,
    editor: EditorProject,
    contextualVm: ContextualEditingViewModel,
    refresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val (initialStart, initialEnd) = when (tool.ownerType) {
        TimedOwnerType.TEXT -> editor.timeline.textOverlays.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to it.timelineEndUs }
        TimedOwnerType.IMAGE -> editor.timeline.imageOverlays.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to it.timelineEndUs }
    } ?: return
    var startUs by remember(tool) { mutableLongStateOf(initialStart) }
    var endUs by remember(tool) { mutableLongStateOf(initialEnd) }
    val maxUs = maxOf(editor.timeline.durationUs, initialEnd + 10_000_000L, 10_000_000L)

    ToolHeader("Timing", "Set when this overlay appears")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Start      ${formatDurationUs(startUs)}")
        Slider(
            value = startUs.toFloat(),
            onValueChange = { startUs = it.toLong().coerceAtMost(endUs - 100_000L) },
            valueRange = 0f..maxUs.toFloat(),
            modifier = Modifier.semantics { contentDescription = "Overlay start time, ${formatDurationUs(startUs)}" }
        )
        Text("End        ${formatDurationUs(endUs)}")
        Slider(
            value = endUs.toFloat(),
            onValueChange = { endUs = it.toLong().coerceAtLeast(startUs + 100_000L) },
            valueRange = 100_000f..maxUs.toFloat(),
            modifier = Modifier.semantics { contentDescription = "Overlay end time, ${formatDurationUs(endUs)}" }
        )
        Text("Duration   ${formatDurationUs(endUs - startUs)}")
    }
    ActionRow(
        onCancel = onDismiss,
        onReset = { startUs = initialStart; endUs = initialEnd },
        onDone = {
            when (tool.ownerType) {
                TimedOwnerType.TEXT -> contextualVm.setTextTiming(projectId, tool.ownerId, startUs, endUs) { refresh(); onDismiss() }
                TimedOwnerType.IMAGE -> contextualVm.setImageTiming(projectId, tool.ownerId, startUs, endUs) { refresh(); onDismiss() }
            }
        }
    )
}

@Composable
private fun KeyframePanel(
    tool: EditorTool.Keyframes,
    projectId: String,
    editor: EditorProject,
    playheadUs: Long,
    contextualVm: ContextualEditingViewModel,
    editorVm: EditorViewModel,
    refresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val ownerType = when (tool.ownerType) {
        VisualOwnerType.CLIP -> KeyframeOwnerType.CLIP
        VisualOwnerType.TEXT -> KeyframeOwnerType.TEXT_OVERLAY
        VisualOwnerType.IMAGE -> KeyframeOwnerType.IMAGE_OVERLAY
    }
    val (ownerStart, ownerDuration) = when (tool.ownerType) {
        VisualOwnerType.CLIP -> editor.timeline.clips.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to it.timelineDurationUs }
        VisualOwnerType.TEXT -> editor.timeline.textOverlays.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to (it.timelineEndUs - it.timelineStartUs) }
        VisualOwnerType.IMAGE -> editor.timeline.imageOverlays.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to (it.timelineEndUs - it.timelineStartUs) }
    } ?: return
    val localUs = (playheadUs - ownerStart).coerceIn(0L, ownerDuration)
    val frames = editor.timeline.keyframes.filter { it.ownerId == tool.ownerId }
    var interpolation by remember(tool) { mutableStateOf(KeyframeInterpolation.LINEAR) }
    val properties = buildList {
        add(KeyframeProperty.POSITION_X to "Horizontal position")
        add(KeyframeProperty.POSITION_Y to "Vertical position")
        add(KeyframeProperty.SCALE_X to "Scale")
        add(KeyframeProperty.ROTATION to "Rotation")
        add(KeyframeProperty.OPACITY to "Opacity")
        if (tool.ownerType == VisualOwnerType.CLIP) add(KeyframeProperty.AUDIO_GAIN to "Volume")
    }

    ToolHeader("Keyframes", "◇ add • ◆ exists at the current playhead")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedButton(onClick = { interpolation = KeyframeInterpolation.HOLD }) { Text(if (interpolation == KeyframeInterpolation.HOLD) "✓ Hold" else "Hold") }
            OutlinedButton(onClick = { interpolation = KeyframeInterpolation.LINEAR }) { Text(if (interpolation == KeyframeInterpolation.LINEAR) "✓ Linear" else "Linear") }
        }
        properties.forEach { (property, label) ->
            val current = frames.firstOrNull { it.property == property && it.timeUs == localUs }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        if (property == KeyframeProperty.SCALE_X) {
                            if (current == null) contextualVm.addUniformScaleKeyframe(projectId, tool.ownerId, ownerType, playheadUs, interpolation) { refresh() }
                            else contextualVm.removeUniformScaleKeyframes(projectId, tool.ownerId, localUs) { refresh() }
                        } else if (current == null) {
                            contextualVm.addKeyframe(projectId, tool.ownerId, ownerType, property, playheadUs, interpolation) { refresh() }
                        } else {
                            contextualVm.removeKeyframe(projectId, tool.ownerId, current.id) { refresh() }
                        }
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (current == null) "Add $label keyframe" else "$label keyframe exists; remove"
                    }
                ) { Text(if (current == null) "◇ Add" else "◆ Remove") }
            }
        }
        HorizontalDivider()
        val ordered = frames.sortedBy { it.timeUs }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = {
                ordered.lastOrNull { it.timeUs < localUs }?.let { editorVm.setPlayheadUs(ownerStart + it.timeUs) }
            }) { Text("◀ Previous") }
            OutlinedButton(onClick = {
                ordered.firstOrNull { it.timeUs > localUs }?.let { editorVm.setPlayheadUs(ownerStart + it.timeUs) }
            }) { Text("Next ▶") }
        }
        val exact = ordered.firstOrNull { it.timeUs == localUs }
        if (exact != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Interpolation")
                OutlinedButton(onClick = { contextualVm.setKeyframeInterpolation(projectId, tool.ownerId, exact.id, KeyframeInterpolation.HOLD) { refresh() } }) { Text("Hold") }
                OutlinedButton(onClick = { contextualVm.setKeyframeInterpolation(projectId, tool.ownerId, exact.id, KeyframeInterpolation.LINEAR) { refresh() } }) { Text("Linear") }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Keyframe changes apply immediately and are undoable", modifier = Modifier.weight(1f))
        Button(onClick = onDismiss) { Text("Close") }
    }
}

@Composable
private fun MorePanel(
    tool: EditorTool.More,
    projectId: String,
    editor: EditorProject,
    editorVm: EditorViewModel,
    contextualVm: ContextualEditingViewModel,
    onSelect: (EditorSelection) -> Unit,
    onOpenTool: (EditorTool) -> Unit,
    refresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val clip = if (tool.ownerType == VisualOwnerType.CLIP) editor.timeline.clips.firstOrNull { it.id == tool.ownerId } else null
    val clipTrack = clip?.let { target -> editor.timeline.tracks.firstOrNull { it.id == target.trackId } }
    val audioOnlyClip = clipTrack?.type == com.videoflow.app.domain.editor.TrackType.AUDIO

    ToolHeader("More")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!audioOnlyClip) {
            OutlinedButton(onClick = { onOpenTool(EditorTool.Transform(tool.ownerId, tool.ownerType)) }, modifier = Modifier.fillMaxWidth()) { Text("Transform") }
            OutlinedButton(onClick = { onOpenTool(EditorTool.Opacity(tool.ownerId, tool.ownerType)) }, modifier = Modifier.fillMaxWidth()) { Text("Opacity") }
            OutlinedButton(onClick = { onOpenTool(EditorTool.Keyframes(tool.ownerId, tool.ownerType)) }, modifier = Modifier.fillMaxWidth()) { Text("Keyframes") }
        }
        if (tool.ownerType == VisualOwnerType.TEXT) {
            OutlinedButton(onClick = { onOpenTool(EditorTool.Timing(tool.ownerId, TimedOwnerType.TEXT)) }, modifier = Modifier.fillMaxWidth()) { Text("Timing") }
        }
        if (tool.ownerType == VisualOwnerType.IMAGE) {
            OutlinedButton(onClick = { onOpenTool(EditorTool.Timing(tool.ownerId, TimedOwnerType.IMAGE)) }, modifier = Modifier.fillMaxWidth()) { Text("Timing") }
        }
        HorizontalDivider()
        when (tool.ownerType) {
            VisualOwnerType.CLIP -> {
                OutlinedButton(onClick = { editorVm.selectClip(tool.ownerId); editorVm.duplicateSelected(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Duplicate") }
                TextButton(onClick = { editorVm.selectClip(tool.ownerId); editorVm.deleteSelected(); onSelect(EditorSelection.None); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
            }
            VisualOwnerType.TEXT -> {
                OutlinedButton(onClick = {
                    contextualVm.duplicateText(projectId, tool.ownerId) { id -> refresh(); onSelect(EditorSelection.TextOverlay(id)); onDismiss() }
                }, modifier = Modifier.fillMaxWidth()) { Text("Duplicate") }
                TextButton(onClick = { editorVm.deleteText(tool.ownerId); onSelect(EditorSelection.None); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
            }
            VisualOwnerType.IMAGE -> {
                OutlinedButton(onClick = {
                    contextualVm.duplicateImage(projectId, tool.ownerId) { id -> refresh(); onSelect(EditorSelection.ImageOverlay(id)); onDismiss() }
                }, modifier = Modifier.fillMaxWidth()) { Text("Duplicate") }
                TextButton(onClick = { editorVm.deleteImage(tool.ownerId); onSelect(EditorSelection.None); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onValue: (Float) -> Unit) {
    Text("$label   $display")
    Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValue, valueRange = range, modifier = Modifier.semantics { contentDescription = "$label, $display" })
}

private fun centeredCrop(sourceWidth: Int, sourceHeight: Int, targetAspect: Float): CropRect {
    val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
    return if (sourceAspect > targetAspect) {
        val width = targetAspect / sourceAspect
        val margin = (1f - width) / 2f
        CropRect(margin, 0f, 1f - margin, 1f)
    } else {
        val height = sourceAspect / targetAspect
        val margin = (1f - height) / 2f
        CropRect(0f, margin, 1f, 1f - margin)
    }
}

private fun normalize180(value: Float): Float {
    var normalized = value % 360f
    if (normalized > 180f) normalized -= 360f
    if (normalized < -180f) normalized += 360f
    return normalized
}

private fun formatMultiplier(value: Float): String = if (value % 1f == 0f) value.roundToInt().toString() else "%.2f".format(value).trimEnd('0').trimEnd('.')

private fun argbToHex(argb: Long): String = "#%08X".format(argb and 0xFFFFFFFFL)

private fun parseArgb(raw: String): Long? {
    val clean = raw.trim().removePrefix("#")
    return runCatching {
        when (clean.length) {
            6 -> 0xFF000000L or clean.toLong(16)
            8 -> clean.toLong(16)
            else -> return null
        }
    }.getOrNull()
}
