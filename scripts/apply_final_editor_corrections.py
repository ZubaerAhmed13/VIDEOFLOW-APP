#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def exact(path: str, old: str, new: str) -> None:
    text = read(path)
    if new in text and old not in text:
        return
    if old not in text:
        raise SystemExit(f"Expected text not found in {path}: {old[:120]!r}")
    write(path, text.replace(old, new))


def regex(path: str, pattern: str, replacement: str, flags=re.S) -> None:
    text = read(path)
    next_text, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f"Expected exactly one regex match in {path}, got {count}: {pattern[:100]!r}")
    write(path, next_text)


# ---------------------------------------------------------------------------
# 1. ONE user-facing Trim destination. Precise Trim remains functionality,
#    but is integrated into Trim instead of being a separate tool/route.
# ---------------------------------------------------------------------------
professional_tool = "app/src/main/java/com/videoflow/app/ui/editor/ProfessionalEditorTool.kt"
text = read(professional_tool)
text = re.sub(r"\n\s*data class PreciseTrim\(val clipId: String\) : ProfessionalEditorTool", "", text)
write(professional_tool, text)

editor_chrome = "app/src/main/java/com/videoflow/app/ui/editor/EditorChrome.kt"
text = read(editor_chrome)
text, n = re.subn(
    r'\n\s*EditorToolbarItem\("Precise Trim",\s*Icons\.Default\.ContentCut,\s*action\s*=\s*\{\s*onProfessionalTool\(ProfessionalEditorTool\.PreciseTrim\(selection\.videoClip\.id\)\)\s*\}\),?', 
    "", text, count=1, flags=re.S
)
if n != 1:
    raise SystemExit("Could not remove standalone Precise Trim toolbar item")
write(editor_chrome, text)

# Delete the old independent precise-trim dialog and its ViewModel. The exact
# parsing/Long-us functionality is reused inside the normal Trim panel below.
write(
    "app/src/main/java/com/videoflow/app/ui/screens/FinalQualityEditorRoute.kt",
    '''package com.videoflow.app.ui.screens

import androidx.compose.runtime.Composable
import com.videoflow.app.ui.EditorViewModel
import com.videoflow.app.ui.editor.ProfessionalEditorTool

/** Final editor route. Precise trim is intentionally part of the normal Trim tool. */
@Composable
fun FinalQualityEditorRoute(
    id: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    editorVm: EditorViewModel,
    onProfessionalTool: (ProfessionalEditorTool) -> Unit = {}
) {
    EditorScreen(
        id = id,
        onBack = onBack,
        onExport = onExport,
        vm = editorVm,
        onProfessionalTool = onProfessionalTool
    )
}
'''
)

# ---------------------------------------------------------------------------
# 2. Context panel correction: smaller phone overlay + professional unified
#    Trim + direct Crop as the primary workflow.
# ---------------------------------------------------------------------------
context_panels = "app/src/main/java/com/videoflow/app/ui/editor/ContextualToolPanels.kt"
text = read(context_panels)
if "import androidx.compose.foundation.text.KeyboardOptions" not in text:
    text = text.replace(
        "import androidx.compose.foundation.verticalScroll\n",
        "import androidx.compose.foundation.verticalScroll\nimport androidx.compose.foundation.text.KeyboardOptions\n"
    )
if "import androidx.compose.ui.text.input.KeyboardType" not in text:
    text = text.replace(
        "import androidx.compose.ui.text.font.FontWeight\n",
        "import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.input.KeyboardType\n"
    )
if "import com.videoflow.app.domain.editor.TrimTimecode" not in text:
    text = text.replace(
        "import com.videoflow.app.domain.editor.TimelineClip\n",
        "import com.videoflow.app.domain.editor.TimelineClip\nimport com.videoflow.app.domain.editor.TrimTimecode\n"
    )
if "import kotlin.math.roundToLong" not in text:
    text = text.replace("import kotlin.math.roundToInt\n", "import kotlin.math.roundToInt\nimport kotlin.math.roundToLong\n")
text = text.replace(
    "Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * 0.45f)",
    "Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * 0.36f)"
)
text = text.replace(
    "TrimPanel(tool, clip, project, thumbnails, waveforms, contextualVm, projectId, onPreviewSeek, refresh, onDismiss)",
    "TrimPanel(tool, clip, project, thumbnails, waveforms, contextualVm, projectId, playheadUs, onPreviewSeek, refresh, onDismiss)"
)
write(context_panels, text)

new_trim = r'''@Composable
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
    var preciseMode by remember(tool.clipId) { mutableStateOf(false) }
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
                // A single truthful cached representative frame is preferable to faking a
                // filmstrip by repeating the same frame six times. Bounded multi-frame extraction
                // can replace this cache when available.
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
}'''

regex(
    context_panels,
    r"@Composable\nprivate fun TrimPanel\(.*?\n\}\n\n@Composable\nprivate fun SpeedPanel\(",
    new_trim + "\n\n@Composable\nprivate fun SpeedPanel("
)

new_crop = r'''@Composable
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
        modifier = Modifier.semantics { contentDescription = "$label crop edge, ${(value * 100).roundToInt()} percent" }
    )
}'''

regex(
    context_panels,
    r"@Composable\nprivate fun CropPanel\(.*?\n\}\n\n@Composable\nprivate fun CropEdgeSlider\(.*?\n\}\n\n@Composable\nprivate fun TransformPanel\(",
    new_crop + "\n\n@Composable\nprivate fun TransformPanel("
)

# ---------------------------------------------------------------------------
# 3. Safe trim mutation order. This supports moving a trim window entirely
#    before or after the old source interval while retaining one history entry.
# ---------------------------------------------------------------------------
vm_path = "app/src/main/java/com/videoflow/app/ui/ContextualEditingViewModel.kt"
text = read(vm_path)
old = '''            if (sourceStartUs != before.sourceStartUs) editorRepository.trimClipStart(clipId, sourceStartUs)
            if (sourceEndUs != before.sourceEndUs) editorRepository.trimClipEnd(clipId, sourceEndUs)'''
new = '''            // Mutate the boundary that expands the valid interval first. This prevents a
            // transient invalid clip when the requested range moves completely before/after the
            // previous source interval.
            if (sourceEndUs < before.sourceStartUs) {
                if (sourceStartUs != before.sourceStartUs) editorRepository.trimClipStart(clipId, sourceStartUs)
                if (sourceEndUs != before.sourceEndUs) editorRepository.trimClipEnd(clipId, sourceEndUs)
            } else {
                if (sourceEndUs != before.sourceEndUs) editorRepository.trimClipEnd(clipId, sourceEndUs)
                if (sourceStartUs != before.sourceStartUs) editorRepository.trimClipStart(clipId, sourceStartUs)
            }'''
if old not in text:
    raise SystemExit("Could not locate commitTrim mutation order")
write(vm_path, text.replace(old, new))

# ---------------------------------------------------------------------------
# 4. Portrait workspace: video is primary, timeline is bounded independently
#    of track count. Timeline density is compact enough for three useful rows;
#    track 4+ continues through the existing LazyColumn vertical scroll.
# ---------------------------------------------------------------------------
editor_screen = "app/src/main/java/com/videoflow/app/ui/screens/EditorScreen.kt"
text = read(editor_screen)
if text.count("Modifier.weight(0.42f)") != 1 or text.count("Modifier.weight(0.58f)") != 1:
    raise SystemExit("Unexpected portrait preview/timeline weight layout")
text = text.replace("Modifier.weight(0.42f)", "Modifier.weight(0.48f)")
text = text.replace("Modifier.weight(0.58f)", "Modifier.weight(0.52f)")
write(editor_screen, text)

timeline_path = "app/src/main/java/com/videoflow/app/ui/editor/TimelineWorkspace.kt"
text = read(timeline_path)
text = text.replace("modifier=Modifier.fillMaxWidth().height(48.dp).semantics", "modifier=Modifier.fillMaxWidth().height(36.dp).semantics", 1)
text = text.replace("Modifier.fillMaxWidth().height(48.dp),\n                verticalAlignment", "Modifier.fillMaxWidth().height(40.dp),\n                verticalAlignment", 1)
header_pattern = r'''    val laneHeight = 104\.dp\n    Row\(Modifier\.fillMaxWidth\(\)\.height\(laneHeight\)\) \{\n        Surface\(color = VideoFlowEditorColors\.TimelineTrackHeader, modifier = Modifier\.width\(TrackHeaderWidth\)\.fillMaxHeight\(\)\) \{.*?\n        \}\n\n        BoxWithConstraints'''
header_replacement = '''    val laneHeight = 88.dp
    Row(Modifier.fillMaxWidth().height(laneHeight)) {
        Surface(color = VideoFlowEditorColors.TimelineTrackHeader, modifier = Modifier.width(TrackHeaderWidth).fillMaxHeight()) {
            Row(
                Modifier.fillMaxSize().padding(start = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    track.name.take(7),
                    color = VideoFlowEditorColors.PrimaryText,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (track.type == TrackType.AUDIO) {
                    IconButton(onClick = onToggleMute, modifier = Modifier.width(48.dp).height(48.dp)) {
                        Icon(
                            if (track.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (track.muted) "Unmute ${track.name}" else "Mute ${track.name}",
                            tint = VideoFlowEditorColors.SecondaryText
                        )
                    }
                } else {
                    IconButton(onClick = onToggleVisible, modifier = Modifier.width(48.dp).height(48.dp)) {
                        Icon(
                            if (track.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (track.visible) "Hide ${track.name}" else "Show ${track.name}",
                            tint = VideoFlowEditorColors.SecondaryText
                        )
                    }
                }
                IconButton(onClick = onTrackSettings, modifier = Modifier.width(48.dp).height(48.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Open ${track.name} settings", tint = VideoFlowEditorColors.SecondaryText)
                }
            }
        }

        BoxWithConstraints'''
text2, count = re.subn(header_pattern, header_replacement, text, count=1, flags=re.S)
if count != 1:
    raise SystemExit("Could not compact timeline track header")
write(timeline_path, text2)

# ---------------------------------------------------------------------------
# 5. Professional tool panels no longer cover most of a phone editor.
# ---------------------------------------------------------------------------
step4 = "app/src/main/java/com/videoflow/app/ui/screens/Step4WatermarkEditorRoute.kt"
text = read(step4)
old = "Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * 0.82f)"
if old not in text:
    raise SystemExit("Could not locate professional panel phone height")
write(step4, text.replace(old, "Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * 0.50f)"))

prof_panel = "app/src/main/java/com/videoflow/app/ui/effects/ProfessionalToolPanel.kt"
text = read(prof_panel)
text = text.replace("screenHeightDp * .25f", "screenHeightDp * .18f")
text = text.replace(".coerceIn(72.dp, 220.dp)", ".coerceIn(72.dp, 150.dp)")
write(prof_panel, text)

# ---------------------------------------------------------------------------
# 6. Effects: a parameter update must visibly refresh paused playback. Some
#    devices did not redraw a cached frame reliably. Re-register the requested
#    effect list and reprepare at the retained position on every real draft
#    change; this is deterministic and surfaces Media3/GL failures.
# ---------------------------------------------------------------------------
video_player = "app/src/main/java/com/videoflow/app/ui/VideoPlayer.kt"
text = read(video_player)
old_block_pattern = r'''    LaunchedEffect\(player, videoEffects\) \{.*?\n    \}\n\n    // Do not chase every high-frequency UI playhead tick'''
new_block = '''    LaunchedEffect(player, videoEffects) {
        val current = appliedEffects.get()
        if (current === videoEffects || current == videoEffects) return@LaunchedEffect
        val stitchedPosition = if (previewSegments.isNotEmpty()) {
            AiPreviewPlaybackResolver.absoluteSourcePositionMs(
                previewSegments,
                player.currentMediaItemIndex,
                player.currentPosition,
                clipSourceStartMs
            )
        } else player.currentPosition
        runCatching {
            // Re-registering the stage list avoids device-specific stale paused-frame behavior
            // observed with parameter-only shader mutation. Keep the same ExoPlayer and position.
            player.stop()
            player.setVideoEffects(videoEffects)
            appliedEffects.set(videoEffects)
            redrawPending = false
            frameRendered = false
            player.prepare()
            if (previewSegments.isNotEmpty()) {
                AiPreviewPlaybackResolver.locate(previewSegments, stitchedPosition, clipSourceStartMs)?.let { (index, position) ->
                    player.seekTo(index, position)
                }
            } else player.seekTo(stitchedPosition)
        }.onFailure { error ->
            android.util.Log.e("VideoFlowPreview", "Effect preview update failed", error)
            playbackError = "Effect preview could not start on this device."
        }
    }

    // Do not chase every high-frequency UI playhead tick'''
text2, count = re.subn(old_block_pattern, new_block, text, count=1, flags=re.S)
if count != 1:
    raise SystemExit("Could not replace effect preview update block")
write(video_player, text2)

# ---------------------------------------------------------------------------
# 7. AI Done semantics: save one non-destructive edit definition and return.
#    Never prepare/reconstruct the full clip as part of Done. Heavy final work
#    remains in the existing isolated :export foreground-service process.
# ---------------------------------------------------------------------------
ai_vm = "app/src/main/java/com/videoflow/app/ui/ai/WatermarkStudioViewModel.kt"
text = read(ai_vm)
apply_pattern = r'''    fun apply\(effect: AiWatermarkEffect, onApplied: \(\) -> Unit\) \{.*?\n    \}\n\n    fun setEnabled'''
apply_replacement = '''    fun apply(effect: AiWatermarkEffect, onApplied: () -> Unit) {
        workJob?.cancel()
        workJob = viewModelScope.launch {
            _state.value = _state.value.copy(busy = WatermarkStudioBusy.APPLYING, progress = 0.1f, error = null)
            val persisted = runCatching {
                withContext(NonCancellable) {
                    val before = repository.load(effect.projectId)
                    val existed = before.any { it.id == effect.id }
                    repository.upsert(effect)
                    historyService.touchProject(effect.projectId)
                    val after = repository.load(effect.projectId)
                    historyService.record(
                        AiWatermarkHistoryEntry(
                            projectId = effect.projectId,
                            label = if (existed) "Update AI Watermark" else "Apply AI Watermark",
                            before = before,
                            after = after
                        )
                    )
                    after.filter { it.clipId == effect.clipId }
                }
            }
            if (persisted.isFailure) {
                val error = persisted.exceptionOrNull()
                _state.value = _state.value.copy(
                    busy = WatermarkStudioBusy.IDLE,
                    progress = 0f,
                    error = "Apply: ${error?.message ?: error?.javaClass?.simpleName ?: "Unknown error"}"
                )
                return@launch
            }
            // The edit definition is now durable. Do not synchronously prepare a full moving
            // preview here: Done must return promptly and heavy reconstruction belongs to explicit
            // Preview or final export.
            processedPreviewManager.invalidateProject(effect.projectId)
            _state.value = _state.value.copy(
                busy = WatermarkStudioBusy.IDLE,
                progress = 1f,
                existingEffects = persisted.getOrThrow(),
                error = null
            )
            onApplied()
        }
    }

    fun setEnabled'''
text2, count = re.subn(apply_pattern, apply_replacement, text, count=1, flags=re.S)
if count != 1:
    raise SystemExit("Could not replace AI apply semantics")
write(ai_vm, text2)

# Compact/simplify the user-facing AI stage labels without exposing internals.
ai_panel = "app/src/main/java/com/videoflow/app/ui/ai/WatermarkStudioPanel.kt"
text = read(ai_panel)
text = text.replace('listOf("Select", "Time", "Track", "Preview", "Apply")', 'listOf("Cover", "Duration", "Track", "Preview", "Done")')
text = text.replace('TextButton(onClick = { diagnosticsExpanded = !diagnosticsExpanded }) { Text(if (diagnosticsExpanded) "Hide details" else "Device details") }', 'TextButton(onClick = { diagnosticsExpanded = !diagnosticsExpanded }) { Text(if (diagnosticsExpanded) "Hide Advanced" else "Advanced") }')
write(ai_panel, text)

# ---------------------------------------------------------------------------
# 8. A deterministic pure helper/test for the exclusive-end Trim regression.
# ---------------------------------------------------------------------------
helper = ROOT / "app/src/main/java/com/videoflow/app/domain/editor/TrimPreviewMath.kt"
helper.write_text('''package com.videoflow.app.domain.editor

import kotlin.math.roundToLong

/** Pure Long-us mapping used to keep Trim preview inside the half-open clip interval. */
object TrimPreviewMath {
    fun safeTimelinePreviewUs(
        sourceBoundaryUs: Long,
        currentSourceStartUs: Long,
        timelineStartUs: Long,
        timelineEndUsExclusive: Long,
        speed: Double
    ): Long {
        require(speed.isFinite() && speed > 0.0)
        require(timelineEndUsExclusive > timelineStartUs)
        val safeEnd = timelineEndUsExclusive - 1L
        val offsetUs = ((sourceBoundaryUs - currentSourceStartUs).toDouble() / speed).roundToLong()
        return (timelineStartUs + offsetUs).coerceIn(timelineStartUs, safeEnd)
    }
}
''', encoding="utf-8")

test = ROOT / "app/src/test/java/com/videoflow/app/domain/editor/TrimPreviewMathTest.kt"
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text('''package com.videoflow.app.domain.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class TrimPreviewMathTest {
    @Test fun endBoundaryNeverReturnsExclusiveClipEnd() {
        assertEquals(
            9_999_999L,
            TrimPreviewMath.safeTimelinePreviewUs(
                sourceBoundaryUs = 10_000_000L,
                currentSourceStartUs = 0L,
                timelineStartUs = 0L,
                timelineEndUsExclusive = 10_000_000L,
                speed = 1.0
            )
        )
    }

    @Test fun largeHourScaleValuesRemainLongSafe() {
        val start = 36_000_000_000L
        val end = start + 3_600_000_000L
        assertEquals(
            end - 1L,
            TrimPreviewMath.safeTimelinePreviewUs(
                sourceBoundaryUs = 3_600_000_000L,
                currentSourceStartUs = 0L,
                timelineStartUs = start,
                timelineEndUsExclusive = end,
                speed = 1.0
            )
        )
    }
}
''', encoding="utf-8")

print("Final editor corrections applied")
