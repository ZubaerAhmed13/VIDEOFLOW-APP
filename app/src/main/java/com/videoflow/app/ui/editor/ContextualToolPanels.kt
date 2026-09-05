package com.videoflow.app.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videoflow.app.data.editor.EditorProject
import com.videoflow.app.domain.editor.CropRect
import com.videoflow.app.domain.editor.Keyframe
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onDismiss: () -> Unit,
    onSelect: (EditorSelection) -> Unit,
    onOpenTool: (EditorTool) -> Unit,
    refresh: () -> Unit
) {
    if (tool == null || editor == null) return

    val timeline = editor.timeline
    val targetClipId = when (tool) {
        is EditorTool.Trim -> tool.clipId
        is EditorTool.Speed -> tool.clipId
        is EditorTool.Crop -> tool.clipId
        is EditorTool.Volume -> tool.clipId
        is EditorTool.Fade -> tool.clipId
        is EditorTool.Transform -> tool.ownerId.takeIf { tool.ownerType == VisualOwnerType.CLIP }
        is EditorTool.Opacity -> tool.ownerId.takeIf { tool.ownerType == VisualOwnerType.CLIP }
        is EditorTool.Keyframes -> tool.ownerId.takeIf { tool.ownerType == VisualOwnerType.CLIP }
        is EditorTool.More -> tool.ownerId.takeIf { tool.ownerType == VisualOwnerType.CLIP }
        else -> null
    }
    val initialClip = remember(tool) { targetClipId?.let { id -> timeline.clips.firstOrNull { it.id == id } } }
    val initialText = remember(tool) {
        val id = when (tool) {
            is EditorTool.TextEditor -> tool.overlayId
            is EditorTool.TextStyle -> tool.overlayId
            is EditorTool.Transform -> tool.ownerId.takeIf { tool.ownerType == VisualOwnerType.TEXT }
            is EditorTool.Opacity -> tool.ownerId.takeIf { tool.ownerType == VisualOwnerType.TEXT }
            is EditorTool.Timing -> tool.ownerId.takeIf { tool.ownerType == TimedOwnerType.TEXT }
            is EditorTool.Keyframes -> tool.ownerId.takeIf { tool.ownerType == VisualOwnerType.TEXT }
            is EditorTool.More -> tool.ownerId.takeIf { tool.ownerType == VisualOwnerType.TEXT }
            else -> null
        }
        id?.let { ownerId -> timeline.textOverlays.firstOrNull { it.id == ownerId } }
    }
    val initialImage = remember(tool) {
        val id = when (tool) {
            is EditorTool.Transform -> tool.ownerId.takeIf { tool.ownerType == VisualOwnerType.IMAGE }
            is EditorTool.Opacity -> tool.ownerId.takeIf { tool.ownerType == VisualOwnerType.IMAGE }
            is EditorTool.Timing -> tool.ownerId.takeIf { tool.ownerType == TimedOwnerType.IMAGE }
            is EditorTool.Keyframes -> tool.ownerId.takeIf { tool.ownerType == VisualOwnerType.IMAGE }
            is EditorTool.More -> tool.ownerId.takeIf { tool.ownerType == VisualOwnerType.IMAGE }
            else -> null
        }
        id?.let { ownerId -> timeline.imageOverlays.firstOrNull { it.id == ownerId } }
    }

    fun cancelLiveEdit() {
        when (tool) {
            is EditorTool.Crop -> initialClip?.let { before ->
                contextualVm.setClipCrop(projectId, before.id, before.transform.crop) { refresh() }
            }
            is EditorTool.Transform -> when (tool.ownerType) {
                VisualOwnerType.CLIP -> initialClip?.let { before ->
                    contextualVm.setClipTransform(projectId, before.id, before.transform.x, before.transform.y, before.transform.scaleX, before.transform.rotationDegrees) { refresh() }
                }
                VisualOwnerType.TEXT -> initialText?.let { before ->
                    contextualVm.setTextTransform(projectId, before.id, before.transform.x, before.transform.y, before.transform.scaleX, before.transform.rotationDegrees) { refresh() }
                }
                VisualOwnerType.IMAGE -> initialImage?.let { before ->
                    contextualVm.setImageTransform(projectId, before.id, before.transform.x, before.transform.y, before.transform.scaleX, before.transform.rotationDegrees) { refresh() }
                }
            }
            is EditorTool.Opacity -> when (tool.ownerType) {
                VisualOwnerType.CLIP -> initialClip?.let { editorVm.selectClip(it.id); editorVm.setOpacity(it.opacity) }
                VisualOwnerType.TEXT -> initialText?.let { editorVm.setTextOpacity(it.id, it.opacity) }
                VisualOwnerType.IMAGE -> initialImage?.let { editorVm.setImageOpacity(it.id, it.transform.opacity) }
            }
            is EditorTool.Volume -> initialClip?.let { editorVm.selectClip(it.id); editorVm.setClipGain(it.gainDb) }
            is EditorTool.Fade -> initialClip?.let { editorVm.selectClip(it.id); editorVm.setFades(it.fadeInUs, it.fadeOutUs) }
            else -> Unit
        }
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = ::cancelLiveEdit,
        containerColor = VideoFlowEditorColors.EditorSurfaceElevated,
        modifier = Modifier.imePadding()
    ) {
        when (tool) {
            is EditorTool.Trim -> {
                val clip = timeline.clips.firstOrNull { it.id == tool.clipId }
                if (clip != null) TrimPanel(tool, clip, project, thumbnails, waveforms, contextualVm, projectId, refresh, onDismiss)
            }
            is EditorTool.Speed -> {
                val clip = timeline.clips.firstOrNull { it.id == tool.clipId }
                if (clip != null) SpeedPanel(clip, editorVm, onDismiss)
            }
            is EditorTool.Crop -> {
                val clip = timeline.clips.firstOrNull { it.id == tool.clipId }
                if (clip != null) CropPanel(projectId, clip, project, contextualVm, refresh, ::cancelLiveEdit, onDismiss)
            }
            is EditorTool.Transform -> TransformPanel(projectId, tool, editor, contextualVm, editorVm, refresh, ::cancelLiveEdit, onDismiss)
            is EditorTool.Opacity -> OpacityPanel(tool, editor, editorVm, ::cancelLiveEdit, onDismiss)
            is EditorTool.Volume -> {
                val clip = timeline.clips.firstOrNull { it.id == tool.clipId }
                if (clip != null) VolumePanel(clip, editorVm, ::cancelLiveEdit, onDismiss)
            }
            is EditorTool.Fade -> {
                val clip = timeline.clips.firstOrNull { it.id == tool.clipId }
                if (clip != null) FadePanel(clip, editorVm, waveforms, ::cancelLiveEdit, onDismiss)
            }
            is EditorTool.TextEditor -> TextEditorPanel(tool, projectId, playheadUs, editor, editorVm, contextualVm, onSelect, refresh, onDismiss)
            is EditorTool.TextStyle -> TextStylePanel(tool, projectId, editor, contextualVm, overlayVm, refresh, onDismiss)
            is EditorTool.Timing -> TimingPanel(tool, projectId, editor, contextualVm, refresh, onDismiss)
            is EditorTool.Keyframes -> KeyframePanel(tool, projectId, editor, playheadUs, contextualVm, editorVm, refresh, onDismiss)
            is EditorTool.More -> MorePanel(tool, projectId, editor, editorVm, contextualVm, onSelect, onOpenTool, refresh, onDismiss)
        }
        Spacer(Modifier.height(24.dp))
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
    refresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val asset = project?.mediaAssets?.firstOrNull { it.id == clip.assetId }
    val sourceDuration = (asset?.durationUs ?: clip.sourceEndUs).coerceAtLeast(1L)
    val isAudio = asset?.mimeType?.startsWith("audio/") == true || (asset?.videoTrackCount ?: 0) == 0
    var range by remember(tool.clipId) {
        mutableStateOf((clip.sourceStartUs.toFloat() / sourceDuration)..(clip.sourceEndUs.toFloat() / sourceDuration))
    }
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
                if (next.endInclusive - next.start >= minGap) range = next
            },
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
    projectId: String,
    clip: TimelineClip,
    project: VideoFlowProject?,
    contextualVm: ContextualEditingViewModel,
    refresh: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val asset = project?.mediaAssets?.firstOrNull { it.id == clip.assetId }
    val crop = clip.transform.crop
    fun apply(next: CropRect) = contextualVm.setClipCrop(projectId, clip.id, next) { refresh() }
    fun preset(w: Int, h: Int) {
        val sw = asset?.width ?: return
        val sh = asset.height ?: return
        apply(centeredCrop(sw, sh, w.toFloat() / h.toFloat()))
    }

    ToolHeader("Crop", "Drag the crop region directly on the preview or use precise bounds")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { apply(CropRect()) }) { Text("Original") }
            listOf(16 to 9, 9 to 16, 4 to 3, 3 to 2, 1 to 1, 4 to 5).forEach { (w, h) ->
                OutlinedButton(onClick = { preset(w, h) }) { Text("$w:$h") }
            }
        }
        CropEdgeSlider("Left", crop.left, 0f, crop.right - 0.01f) { apply(CropRect(it, crop.top, crop.right, crop.bottom)) }
        CropEdgeSlider("Right", crop.right, crop.left + 0.01f, 1f) { apply(CropRect(crop.left, crop.top, it, crop.bottom)) }
        CropEdgeSlider("Top", crop.top, 0f, crop.bottom - 0.01f) { apply(CropRect(crop.left, it, crop.right, crop.bottom)) }
        CropEdgeSlider("Bottom", crop.bottom, crop.top + 0.01f, 1f) { apply(CropRect(crop.left, crop.top, crop.right, it)) }
    }
    ActionRow(onCancel = onCancel, onReset = { apply(CropRect()) }, onDone = onDismiss)
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
    contextualVm: ContextualEditingViewModel,
    editorVm: EditorViewModel,
    refresh: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val transform = when (tool.ownerType) {
        VisualOwnerType.CLIP -> editor.timeline.clips.firstOrNull { it.id == tool.ownerId }?.transform
        VisualOwnerType.TEXT -> editor.timeline.textOverlays.firstOrNull { it.id == tool.ownerId }?.transform
        VisualOwnerType.IMAGE -> editor.timeline.imageOverlays.firstOrNull { it.id == tool.ownerId }?.transform
    } ?: return

    fun apply(x: Float = transform.x, y: Float = transform.y, scale: Float = transform.scaleX, rotation: Float = transform.rotationDegrees) {
        when (tool.ownerType) {
            VisualOwnerType.CLIP -> contextualVm.setClipTransform(projectId, tool.ownerId, x, y, scale, rotation) { refresh() }
            VisualOwnerType.TEXT -> contextualVm.setTextTransform(projectId, tool.ownerId, x, y, scale, rotation) { refresh() }
            VisualOwnerType.IMAGE -> contextualVm.setImageTransform(projectId, tool.ownerId, x, y, scale, rotation) { refresh() }
        }
    }

    ToolHeader("Transform", "Drag or pinch directly on the preview; sliders provide precise control")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        LabeledSlider("Position X", transform.x, 0f..1f, "${(transform.x * 100).roundToInt()}%") { apply(x = it) }
        LabeledSlider("Position Y", transform.y, 0f..1f, "${(transform.y * 100).roundToInt()}%") { apply(y = it) }
        LabeledSlider("Scale", transform.scaleX.coerceIn(0.05f, 4f), 0.05f..4f, "${(transform.scaleX * 100).roundToInt()}%") { apply(scale = it) }
        LabeledSlider("Rotation", normalize180(transform.rotationDegrees), -180f..180f, "${normalize180(transform.rotationDegrees).roundToInt()}°") { apply(rotation = it) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0f, 90f, 180f, -90f).forEach { angle -> OutlinedButton(onClick = { apply(rotation = angle) }) { Text(if (angle == -90f) "270°" else "${angle.roundToInt()}°") } }
            if (tool.ownerType == VisualOwnerType.CLIP) {
                OutlinedButton(onClick = { editorVm.selectClip(tool.ownerId); editorVm.toggleFlipHorizontal() }) { Text("Flip H") }
                OutlinedButton(onClick = { editorVm.selectClip(tool.ownerId); editorVm.toggleFlipVertical() }) { Text("Flip V") }
            }
        }
    }
    ActionRow(
        onCancel = onCancel,
        onReset = { apply(x = 0.5f, y = 0.5f, scale = 1f, rotation = 0f) },
        onDone = onDismiss
    )
}

@Composable
private fun OpacityPanel(tool: EditorTool.Opacity, editor: EditorProject, editorVm: EditorViewModel, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val value = when (tool.ownerType) {
        VisualOwnerType.CLIP -> editor.timeline.clips.firstOrNull { it.id == tool.ownerId }?.opacity
        VisualOwnerType.TEXT -> editor.timeline.textOverlays.firstOrNull { it.id == tool.ownerId }?.opacity
        VisualOwnerType.IMAGE -> editor.timeline.imageOverlays.firstOrNull { it.id == tool.ownerId }?.transform?.opacity
    } ?: return
    fun apply(next: Float) = when (tool.ownerType) {
        VisualOwnerType.CLIP -> { editorVm.selectClip(tool.ownerId); editorVm.setOpacity(next) }
        VisualOwnerType.TEXT -> editorVm.setTextOpacity(tool.ownerId, next)
        VisualOwnerType.IMAGE -> editorVm.setImageOpacity(tool.ownerId, next)
    }
    ToolHeader("Opacity")
    Column(Modifier.padding(horizontal = 18.dp)) {
        Text("${(value * 100).roundToInt()}%")
        Slider(value, apply, valueRange = 0f..1f, modifier = Modifier.semantics { contentDescription = "Opacity, ${(value * 100).roundToInt()} percent" })
    }
    ActionRow(onCancel = onCancel, onReset = { apply(1f) }, onDone = onDismiss)
}

@Composable
private fun VolumePanel(clip: TimelineClip, editorVm: EditorViewModel, onCancel: () -> Unit, onDismiss: () -> Unit) {
    ToolHeader("Clip Volume", "Track volume remains in Track Settings")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${clip.gainDb.roundToInt()} dB${if (clip.gainDb == 0f) " • 100% reference" else ""}")
        Slider(
            value = clip.gainDb.coerceIn(-60f, 24f),
            onValueChange = { editorVm.selectClip(clip.id); editorVm.setClipGain(it) },
            valueRange = -60f..24f,
            modifier = Modifier.semantics { contentDescription = "Clip volume, ${clip.gainDb.roundToInt()} decibels" }
        )
        OutlinedButton(onClick = { editorVm.selectClip(clip.id); editorVm.setClipGain(-60f) }) { Text("Mute") }
    }
    ActionRow(onCancel = onCancel, onReset = { editorVm.selectClip(clip.id); editorVm.setClipGain(0f) }, onDone = onDismiss)
}

@Composable
private fun FadePanel(clip: TimelineClip, editorVm: EditorViewModel, waveforms: Map<String, FloatArray>, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val maxSeconds = (clip.timelineDurationUs / 1_000_000f).coerceAtLeast(0.1f)
    ToolHeader("Audio Fade", "Fade values are bounded by the clip duration")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        WaveformPreview(waveforms[clip.assetId], Modifier.fillMaxWidth().height(48.dp), VideoFlowEditorColors.SelectionAccent)
        Text("Fade In ${"%.2f".format(clip.fadeInUs / 1_000_000f)} s")
        Slider(
            value = (clip.fadeInUs / 1_000_000f).coerceIn(0f, maxSeconds),
            onValueChange = { editorVm.selectClip(clip.id); editorVm.setFades((it * 1_000_000L).toLong(), clip.fadeOutUs) },
            valueRange = 0f..maxSeconds
        )
        Text("Fade Out ${"%.2f".format(clip.fadeOutUs / 1_000_000f)} s")
        Slider(
            value = (clip.fadeOutUs / 1_000_000f).coerceIn(0f, maxSeconds),
            onValueChange = { editorVm.selectClip(clip.id); editorVm.setFades(clip.fadeInUs, (it * 1_000_000L).toLong()) },
            valueRange = 0f..maxSeconds
        )
    }
    ActionRow(onCancel = onCancel, onReset = { editorVm.selectClip(clip.id); editorVm.setFades(0L, 0L) }, onDone = onDismiss)
}

@Composable
private fun TextEditorPanel(
    tool: EditorTool.TextEditor,
    projectId: String,
    playheadUs: Long,
    editor: EditorProject,
    editorVm: EditorViewModel,
    contextualVm: ContextualEditingViewModel,
    onSelect: (EditorSelection) -> Unit,
    refresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val existing = tool.overlayId?.let { id -> editor.timeline.textOverlays.firstOrNull { it.id == id } }
    var draft by remember(tool) { mutableStateOf(existing?.content.orEmpty()) }
    ToolHeader(if (existing == null) "Add Text" else "Edit Text", "Type normally; raw backend fields are never exposed")
    Column(Modifier.padding(horizontal = 18.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(4096) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            label = { Text("Text") }
        )
    }
    ActionRow(
        onCancel = onDismiss,
        onDone = {
            if (existing == null) {
                contextualVm.addText(projectId, playheadUs, draft) { id ->
                    refresh(); onSelect(EditorSelection.TextOverlay(id)); onDismiss()
                }
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

    ToolHeader("Text Style", "Only properties supported by the real text backend are shown")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        LabeledSlider("Font size", size, 6f..128f, "${size.roundToInt()} sp") { size = it }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(400 to "Regular", 500 to "Medium", 700 to "Bold").forEach { (value, label) ->
                OutlinedButton(onClick = { weight = value }) { Text(if (weight == value) "✓ $label" else label) }
            }
            OutlinedButton(onClick = { italic = !italic }) { Text(if (italic) "✓ Italic" else "Italic") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("START" to "Left", "CENTER" to "Center", "END" to "Right").forEach { (value, label) ->
                OutlinedButton(onClick = { alignment = value }) { Text(if (alignment == value) "✓ $label" else label) }
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
            ).forEach { (value, label) -> OutlinedButton(onClick = { color = value; hex = argbToHex(value) }) { Text(label) } }
        }
        OutlinedTextField(
            value = hex,
            onValueChange = { raw ->
                hex = raw.take(9)
                parseArgb(hex)?.let { color = it }
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
                        if (current == null) {
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
    ActionRow(onCancel = onDismiss, onDone = onDismiss)
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
    ToolHeader("More")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tool.ownerType != VisualOwnerType.CLIP || editor.timeline.clips.any { it.id == tool.ownerId }) {
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
