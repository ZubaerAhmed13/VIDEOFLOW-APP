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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.videoflow.app.data.editor.EditorProject
import com.videoflow.app.domain.editor.CropRect
import com.videoflow.app.domain.editor.KeyframeInterpolation
import com.videoflow.app.domain.editor.KeyframeOwnerType
import com.videoflow.app.domain.editor.KeyframeProperty
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TrimTimecode
import com.videoflow.app.domain.model.VideoFlowProject
import com.videoflow.app.ui.CachedThumbnailPreview
import com.videoflow.app.ui.ContextualEditingViewModel
import com.videoflow.app.ui.EditorViewModel
import com.videoflow.app.ui.OverlayAdvancedViewModel
import com.videoflow.app.ui.WaveformPreview
import com.videoflow.app.util.formatDurationUs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * 0.36f)
        }
        Surface(
            modifier = panelModifier.imePadding(),
            color = VideoFlowEditorColors.EditorSurfaceElevated,
            tonalElevation = 8.dp
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                when (tool) {
                    is EditorTool.Trim -> timeline.clips.firstOrNull { it.id == tool.clipId }?.let { clip ->
                        TrimPanel(tool, clip, project, thumbnails, waveforms, contextualVm, projectId, playheadUs, onPreviewSeek, refresh, onDismiss)
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
    playheadUs: Long,
    onPreviewSeek: (Long) -> Unit,
    refresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val asset = project?.mediaAssets?.firstOrNull { it.id == clip.assetId }
    val sourceDuration = (asset?.durationUs ?: clip.sourceEndUs).coerceAtLeast(1L)
    val minimumGapUs = minOf(TrimTimecode.DEFAULT_MIN_DURATION_US, sourceDuration).coerceAtLeast(1L)
    val isAudio = asset?.mimeType?.startsWith("audio/") == true || (asset?.videoTrackCount ?: 0) == 0
    var draftStartUs by remember(tool.clipId, clip.sourceStartUs) { mutableLongStateOf(clip.sourceStartUs) }
    var draftEndUs by remember(tool.clipId, clip.sourceEndUs) { mutableLongStateOf(clip.sourceEndUs) }
    var startText by remember(tool.clipId, clip.sourceStartUs) { mutableStateOf(TrimTimecode.formatUs(clip.sourceStartUs)) }
    var endText by remember(tool.clipId, clip.sourceEndUs) { mutableStateOf(TrimTimecode.formatUs(clip.sourceEndUs)) }
    var preciseMode by remember(tool.clipId, tool.startPrecise) { mutableStateOf(tool.startPrecise) }
    var pendingPreviewUs by remember(tool.clipId) { mutableLongStateOf(clip.timelineStartUs) }
    var lastPreviewSeekMs by remember(tool.clipId) { mutableLongStateOf(0L) }

    val safeTimelineEndUs = (clip.timelineEndUs - 1L).coerceAtLeast(clip.timelineStartUs)
    val sourceAtPlayhead = (
        clip.sourceStartUs +
            ((playheadUs - clip.timelineStartUs).coerceIn(0L, clip.timelineDurationUs).toDouble() * clip.speed).roundToLong()
        ).coerceIn(0L, sourceDuration)

    fun setDraft(startUs: Long, endUs: Long, previewBoundaryUs: Long? = null) {
        val start = startUs.coerceIn(0L, (sourceDuration - minimumGapUs).coerceAtLeast(0L))
        val end = endUs.coerceIn((start + minimumGapUs).coerceAtMost(sourceDuration), sourceDuration)
        draftStartUs = start
        draftEndUs = end
        startText = TrimTimecode.formatUs(start)
        endText = TrimTimecode.formatUs(end)
        previewBoundaryUs?.let { boundary ->
            val timelineOffsetUs = ((boundary - clip.sourceStartUs).toDouble() / clip.speed).roundToLong()
            pendingPreviewUs = (clip.timelineStartUs + timelineOffsetUs)
                .coerceIn(clip.timelineStartUs, safeTimelineEndUs)
            val nowMs = android.os.SystemClock.elapsedRealtime()
            if (nowMs - lastPreviewSeekMs >= 50L) {
                lastPreviewSeekMs = nowMs
                onPreviewSeek(pendingPreviewUs)
            }
        }
    }

    ToolHeader("Trim", if (preciseMode) "Precise — exact Long-microsecond boundaries" else if (isAudio) "Drag the waveform handles" else "Drag the trim handles")
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { preciseMode = false }) { Text(if (!preciseMode) "✓ Trim" else "Trim") }
        OutlinedButton(onClick = { preciseMode = true }) { Text(if (preciseMode) "✓ Precise" else "Precise") }
    }

    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!preciseMode) {
            if (isAudio) {
                WaveformPreview(waveforms[clip.assetId], Modifier.fillMaxWidth().height(52.dp), VideoFlowEditorColors.SelectionAccent)
            } else {
                CachedThumbnailPreview(thumbnails[clip.assetId], Modifier.fillMaxWidth().height(52.dp))
            }
            val startFraction = (draftStartUs.toDouble() / sourceDuration.toDouble()).toFloat().coerceIn(0f, 1f)
            val endFraction = (draftEndUs.toDouble() / sourceDuration.toDouble()).toFloat().coerceIn(startFraction, 1f)
            RangeSlider(
                value = startFraction..endFraction,
                onValueChange = { next ->
                    val candidateStart = (next.start.toDouble() * sourceDuration).roundToLong()
                    val candidateEnd = (next.endInclusive.toDouble() * sourceDuration).roundToLong()
                    if (candidateEnd - candidateStart >= minimumGapUs) {
                        val oldStart = draftStartUs
                        val oldEnd = draftEndUs
                        val boundary = if (kotlin.math.abs(candidateStart - oldStart) >= kotlin.math.abs(candidateEnd - oldEnd)) candidateStart else candidateEnd
                        setDraft(candidateStart, candidateEnd, boundary)
                    }
                },
                onValueChangeFinished = { onPreviewSeek(pendingPreviewUs.coerceIn(clip.timelineStartUs, safeTimelineEndUs)) },
                valueRange = 0f..1f,
                modifier = Modifier.semantics { contentDescription = "Trim start and end handles" }
            )
        } else {
            val validation = TrimTimecode.validationMessage(draftStartUs, draftEndUs, sourceDuration, minimumGapUs)
            OutlinedTextField(
                value = startText,
                onValueChange = { value ->
                    startText = value
                    TrimTimecode.parseToUs(value).getOrNull()?.let { parsed ->
                        if (parsed >= 0L && parsed + minimumGapUs <= draftEndUs) {
                            draftStartUs = parsed
                            setDraft(draftStartUs, draftEndUs, draftStartUs)
                        }
                    }
                },
                label = { Text("Start") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Precise trim start" }
            )
            OutlinedTextField(
                value = endText,
                onValueChange = { value ->
                    endText = value
                    TrimTimecode.parseToUs(value).getOrNull()?.let { parsed ->
                        if (parsed <= sourceDuration && parsed - draftStartUs >= minimumGapUs) {
                            draftEndUs = parsed
                            setDraft(draftStartUs, draftEndUs, draftEndUs)
                        }
                    }
                },
                label = { Text("End") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Precise trim end" }
            )
            validation?.let { Text(it, color = VideoFlowEditorColors.WarningColor) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { setDraft(sourceAtPlayhead.coerceAtMost(draftEndUs - minimumGapUs), draftEndUs, sourceAtPlayhead) }) { Text("Set Start") }
                OutlinedButton(onClick = { setDraft(draftStartUs, sourceAtPlayhead.coerceAtLeast(draftStartUs + minimumGapUs), sourceAtPlayhead) }) { Text("Set End") }
                OutlinedButton(onClick = { onPreviewSeek((clip.timelineStartUs + ((draftStartUs - clip.sourceStartUs).toDouble() / clip.speed).roundToLong()).coerceIn(clip.timelineStartUs, safeTimelineEndUs)) }) { Text("Jump Start") }
                OutlinedButton(onClick = { onPreviewSeek((clip.timelineStartUs + ((draftEndUs - clip.sourceStartUs).toDouble() / clip.speed).roundToLong()).coerceIn(clip.timelineStartUs, safeTimelineEndUs)) }) { Text("Jump End") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { setDraft((draftStartUs - 1_000L).coerceAtLeast(0L), draftEndUs, draftStartUs - 1_000L) }) { Text("Start −1ms") }
                OutlinedButton(onClick = { setDraft((draftStartUs + 1_000L).coerceAtMost(draftEndUs - minimumGapUs), draftEndUs, draftStartUs + 1_000L) }) { Text("Start +1ms") }
            }
        }
        Text("Start      ${TrimTimecode.formatUs(draftStartUs)}")
        Text("End        ${TrimTimecode.formatUs(draftEndUs)}")
        Text("Duration   ${TrimTimecode.formatUs(((draftEndUs - draftStartUs).toDouble() / clip.speed).roundToLong().coerceAtLeast(0L))}")
    }
    ActionRow(
        onCancel = onDismiss,
        onReset = { setDraft(0L, sourceDuration, 0L) },
        onDone = {
            if (TrimTimecode.validationMessage(draftStartUs, draftEndUs, sourceDuration, minimumGapUs) == null) {
                contextualVm.commitTrim(projectId, clip.id, draftStartUs, draftEndUs) {
                    refresh()
                    onDismiss()
                }
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
    var showPrecision by remember(tool.clipId) { mutableStateOf(false) }
    fun update(next: CropRect, aspect: Float? = previewDraft.cropNormalizedAspect) {
        onPreviewDraftChange(previewDraft.copy(crop = next, cropNormalizedAspect = aspect))
    }
    fun preset(w: Int, h: Int) {
        val encodedWidth = asset?.width ?: return
        val encodedHeight = asset.height ?: return
        val (sw, sh) = displayDimensionsForRotation(encodedWidth, encodedHeight, asset.rotationDegrees)
        val targetAspect = w.toFloat() / h.toFloat()
        val normalizedAspect = targetAspect / (sw.toFloat() / sh.toFloat())
        update(centeredCrop(sw, sh, targetAspect), normalizedAspect)
    }

    ToolHeader("Crop", "Drag corners or edges directly on the video preview")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { update(crop, null) }) { Text(if (previewDraft.cropNormalizedAspect == null) "✓ Free" else "Free") }
            OutlinedButton(onClick = { update(CropRect(), null) }) { Text("Original") }
            listOf(1 to 1, 4 to 5, 3 to 4, 4 to 3, 3 to 2, 16 to 9, 9 to 16).forEach { (w, h) ->
                OutlinedButton(onClick = { preset(w, h) }) { Text("$w:$h") }
            }
            OutlinedButton(onClick = { showPrecision = !showPrecision }) { Text(if (showPrecision) "Hide Precision" else "Precision") }
        }
        Text("Use the frame handles in the preview. The outside area stays dimmed and the aspect preset is enforced while dragging.", color = VideoFlowEditorColors.SecondaryText)
        if (showPrecision) {
            CropEdgeSlider("Left", crop.left, 0f, crop.right - 0.01f) { update(CropRect(it, crop.top, crop.right, crop.bottom)) }
            CropEdgeSlider("Right", crop.right, crop.left + 0.01f, 1f) { update(CropRect(crop.left, crop.top, it, crop.bottom)) }
            CropEdgeSlider("Top", crop.top, 0f, crop.bottom - 0.01f) { update(CropRect(crop.left, it, crop.right, crop.bottom)) }
            CropEdgeSlider("Bottom", crop.bottom, crop.top + 0.01f, 1f) { update(CropRect(crop.left, crop.top, crop.right, it)) }
        }
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
        modifier = Modifier.semantics { contentDescription = "$label crop edge" }
    )
}

@Composable
private fun VolumePanel(projectId: String, clip: TimelineClip, playheadUs: Long, previewDraft: ContextualPreviewDraft, onPreviewDraftChange: (ContextualPreviewDraft) -> Unit, contextualVm: ContextualEditingViewModel, refresh: () -> Unit, onDismiss: () -> Unit) {
    val gain = previewDraft.gainDb ?: clip.gainDb
    ToolHeader("Volume")
    Column(Modifier.padding(horizontal = 18.dp)) {
        Slider(value = gain.coerceIn(-60f, 12f), onValueChange = { onPreviewDraftChange(previewDraft.copy(gainDb = it)) }, valueRange = -60f..12f)
        Text("${"%.1f".format(gain)} dB")
    }
    ActionRow(onDismiss, { onPreviewDraftChange(previewDraft.copy(gainDb = 0f)) }) { contextualVm.setClipGain(projectId, clip.id, gain) { refresh(); onDismiss() } }
}

@Composable
private fun FadePanel(clip: TimelineClip, previewDraft: ContextualPreviewDraft, onPreviewDraftChange: (ContextualPreviewDraft) -> Unit, editorVm: EditorViewModel, waveforms: Map<String, FloatArray>, onDismiss: () -> Unit) {
    val fadeIn = previewDraft.fadeInUs ?: clip.fadeInUs
    val fadeOut = previewDraft.fadeOutUs ?: clip.fadeOutUs
    ToolHeader("Fade")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WaveformPreview(waveforms[clip.assetId], Modifier.fillMaxWidth().height(52.dp), VideoFlowEditorColors.SelectionAccent)
        Text("Fade in ${formatDurationUs(fadeIn)}")
        Slider(value = fadeIn.toFloat().coerceAtMost(clip.timelineDurationUs.toFloat()), onValueChange = { onPreviewDraftChange(previewDraft.copy(fadeInUs = it.toLong())) }, valueRange = 0f..clip.timelineDurationUs.toFloat())
        Text("Fade out ${formatDurationUs(fadeOut)}")
        Slider(value = fadeOut.toFloat().coerceAtMost(clip.timelineDurationUs.toFloat()), onValueChange = { onPreviewDraftChange(previewDraft.copy(fadeOutUs = it.toLong())) }, valueRange = 0f..clip.timelineDurationUs.toFloat())
    }
    ActionRow(onDismiss, { onPreviewDraftChange(previewDraft.copy(fadeInUs = 0L, fadeOutUs = 0L)) }) { editorVm.selectClip(clip.id); editorVm.setFade(fadeIn, fadeOut); onDismiss() }
}

@Composable
private fun TransformPanel(projectId: String, tool: EditorTool.Transform, editor: EditorProject, playheadUs: Long, previewDraft: ContextualPreviewDraft, onPreviewDraftChange: (ContextualPreviewDraft) -> Unit, contextualVm: ContextualEditingViewModel, refresh: () -> Unit, onDismiss: () -> Unit) {
    ToolHeader("Transform")
    Text("Transform controls", Modifier.padding(18.dp))
}

@Composable
private fun OpacityPanel(projectId: String, tool: EditorTool.Opacity, editor: EditorProject, playheadUs: Long, previewDraft: ContextualPreviewDraft, onPreviewDraftChange: (ContextualPreviewDraft) -> Unit, contextualVm: ContextualEditingViewModel, refresh: () -> Unit, onDismiss: () -> Unit) {
    ToolHeader("Opacity")
    Text("Opacity controls", Modifier.padding(18.dp))
}

@Composable
private fun TextEditorPanel(tool: EditorTool.TextEditor, projectId: String, playheadUs: Long, editor: EditorProject, editorVm: EditorViewModel, contextualVm: ContextualEditingViewModel, previewDraft: ContextualPreviewDraft, onPreviewDraftChange: (ContextualPreviewDraft) -> Unit, onSelect: (EditorSelection) -> Unit, refresh: () -> Unit, onDismiss: () -> Unit) {
    ToolHeader("Text")
    Text("Text editor", Modifier.padding(18.dp))
}

@Composable
private fun TextStylePanel(tool: EditorTool.TextStyle, projectId: String, editor: EditorProject, contextualVm: ContextualEditingViewModel, overlayVm: OverlayAdvancedViewModel, previewDraft: ContextualPreviewDraft, onPreviewDraftChange: (ContextualPreviewDraft) -> Unit, refresh: () -> Unit, onDismiss: () -> Unit) {
    ToolHeader("Text style")
    Text("Text style controls", Modifier.padding(18.dp))
}

@Composable
private fun TimingPanel(tool: EditorTool.Timing, projectId: String, editor: EditorProject, contextualVm: ContextualEditingViewModel, refresh: () -> Unit, onDismiss: () -> Unit) {
    ToolHeader("Timing")
    Text("Timing controls", Modifier.padding(18.dp))
}

@Composable
private fun KeyframePanel(tool: EditorTool.Keyframes, projectId: String, editor: EditorProject, playheadUs: Long, contextualVm: ContextualEditingViewModel, editorVm: EditorViewModel, refresh: () -> Unit, onDismiss: () -> Unit) {
    ToolHeader("Keyframes")
    Text("Keyframe controls", Modifier.padding(18.dp))
}

@Composable
private fun MorePanel(tool: EditorTool.More, projectId: String, editor: EditorProject, editorVm: EditorViewModel, contextualVm: ContextualEditingViewModel, onSelect: (EditorSelection) -> Unit, onOpenTool: (EditorTool) -> Unit, refresh: () -> Unit, onDismiss: () -> Unit) {
    ToolHeader("More")
    Text("More controls", Modifier.padding(18.dp))
}

private fun formatMultiplier(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else value.toString()
