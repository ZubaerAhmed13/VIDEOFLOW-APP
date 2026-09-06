package com.videoflow.app.ui.ai

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.videoflow.app.data.editor.EditorProject
import com.videoflow.app.domain.ai.AiModelCatalog
import com.videoflow.app.domain.ai.AiWatermarkEffect
import com.videoflow.app.domain.ai.AiWatermarkMath
import com.videoflow.app.domain.ai.NormalizedRoi
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.model.SourceStatus
import com.videoflow.app.domain.model.VideoFlowProject
import com.videoflow.app.ui.editor.VideoFlowEditorColors
import com.videoflow.app.util.formatDurationUs
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Real product surface for Step 4: Mask -> Track -> AI Preview -> non-destructive Apply.
 * The interactive image is intentionally a bounded preview; saved ROI/timing remains normalized
 * and final export runs against original source pixels through Media3RenderEngine.
 */
@Composable
fun WatermarkStudioPanel(
    projectId: String,
    clipId: String,
    project: VideoFlowProject?,
    editor: EditorProject,
    playheadUs: Long,
    onDismiss: () -> Unit,
    refreshEditor: () -> Unit,
    vm: WatermarkStudioViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val clip = editor.timeline.clips.firstOrNull { it.id == clipId }
    val asset = project?.mediaAssets?.firstOrNull { it.id == clip?.assetId }
    if (clip == null || asset == null) {
        StudioHeader("AI Watermark Studio", "Selected source is unavailable.")
        TextButton(onClick = onDismiss, modifier = Modifier.padding(horizontal = 18.dp)) { Text("Close") }
        return
    }

    val durationUs = clip.timelineDurationUs.coerceAtLeast(1L)
    val localPlayheadUs = (playheadUs - clip.timelineStartUs).coerceIn(0L, durationUs - 1L)
    val sourceTimeUs = clip.sourceStartUs + (localPlayheadUs.toDouble() * clip.speed).roundToLong()
    var roi by remember(clipId) { mutableStateOf(NormalizedRoi(0.68f, 0.76f, 0.97f, 0.96f)) }
    var range by remember(clipId) { mutableStateOf(0f..1f) }
    var featherPx by remember(clipId) { mutableFloatStateOf(8f) }
    var contextPx by remember(clipId) { mutableFloatStateOf(48f) }
    var stability by remember(clipId) { mutableFloatStateOf(0.12f) }
    var hydratedExisting by remember(clipId) { mutableStateOf(false) }

    val startUs = (range.start * durationUs.toDouble()).roundToLong().coerceIn(0L, durationUs - 1L)
    val endUs = (range.endInclusive * durationUs.toDouble()).roundToLong().coerceIn(startUs + 1L, durationUs)
    val previewLocalUs = localPlayheadUs.coerceIn(startUs, endUs - 1L)
    val draftEffect = remember(projectId, clipId, startUs, endUs, roi, state.trackedAnchors) {
        AiWatermarkEffect(
            id = "draft",
            projectId = projectId,
            clipId = clipId,
            clipLocalStartUs = startUs,
            clipLocalEndUs = endUs,
            roi = roi,
            motionAnchors = state.trackedAnchors,
            contextPaddingPx = contextPx.roundToInt().coerceIn(0, 256),
            featherPx = featherPx.roundToInt().coerceIn(0, 128),
            temporalStability = stability.coerceIn(0f, 0.5f),
            modelId = AiModelCatalog.FINAL_512.id
        )
    }
    val shownRoi = if (state.trackedAnchors.isEmpty()) roi else draftEffect.roiAt(previewLocalUs)
    val shownBitmap = state.aiPreview ?: state.sourceFrame

    LaunchedEffect(projectId, clipId) { vm.bind(projectId, clipId) }
    LaunchedEffect(asset.sourceUri, sourceTimeUs) {
        vm.loadSourceFrame(asset.sourceUri, sourceTimeUs.coerceIn(clip.sourceStartUs, clip.sourceEndUs - 1L))
    }
    LaunchedEffect(state.existingEffects) {
        if (!hydratedExisting && state.existingEffects.isNotEmpty()) {
            val existing = state.existingEffects.last()
            roi = existing.roi
            range = (existing.clipLocalStartUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)..
                (existing.clipLocalEndUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
            featherPx = existing.featherPx.toFloat()
            contextPx = existing.contextPaddingPx.toFloat()
            stability = existing.temporalStability
            hydratedExisting = true
        }
    }

    StudioHeader("AI Watermark Studio", "Local • Offline • original-resolution final render")
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val statusText = when (asset.sourceStatus) {
            SourceStatus.AVAILABLE -> "Source ready"
            else -> "Source ${asset.sourceStatus.name.lowercase().replace('_', ' ')}"
        }
        Text(
            "$statusText • ${asset.width ?: "?"}×${asset.height ?: "?"} • ${asset.displayName}",
            color = if (asset.sourceStatus == SourceStatus.AVAILABLE) VideoFlowEditorColors.SecondaryText else VideoFlowEditorColors.WarningColor
        )
        Text(
            if (state.runtimeReady) "✓ ${state.runtimeDetail}" else state.runtimeDetail,
            color = if (state.runtimeReady) VideoFlowEditorColors.SuccessColor else VideoFlowEditorColors.SecondaryText
        )
        state.error?.let { Text(it, color = VideoFlowEditorColors.ErrorColor) }
        if (state.busy != WatermarkStudioBusy.IDLE) {
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Watermark Studio progress" }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(busyLabel(state.busy), color = VideoFlowEditorColors.SecondaryText)
                if (state.busy == WatermarkStudioBusy.TRACKING || state.busy == WatermarkStudioBusy.AI_PREVIEW) {
                    TextButton(onClick = vm::cancelWork) { Text("Cancel task") }
                }
            }
        }

        StepTitle("1", "Mask and timing")
        Text("Drag inside the box to move it. Drag a corner to resize it around the watermark.", color = VideoFlowEditorColors.SecondaryText)
        if (shownBitmap != null) {
            InteractiveRoiPreview(
                bitmap = shownBitmap,
                roi = shownRoi,
                onRoiChange = { next ->
                    roi = next
                    vm.clearDraftResults()
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(
                Modifier.fillMaxWidth().height(150.dp).background(VideoFlowEditorColors.TimelineBackground),
                contentAlignment = Alignment.Center
            ) { Text("Loading source preview…", color = VideoFlowEditorColors.SecondaryText) }
        }
        RangeSlider(
            value = range,
            onValueChange = { next ->
                if (next.endInclusive - next.start >= 0.001f) {
                    range = next
                    vm.clearDraftResults()
                }
            },
            valueRange = 0f..1f,
            modifier = Modifier.semantics { contentDescription = "Watermark effect start and end" }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("From ${formatDurationUs(startUs)}")
            Text("To ${formatDurationUs(endUs)}")
        }

        StepTitle("2", "Track movement")
        Text("Tracking samples small local frames and creates motion anchors; no video is uploaded.", color = VideoFlowEditorColors.SecondaryText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { vm.track(asset.sourceUri, clip, roi, startUs, endUs) },
                enabled = state.busy == WatermarkStudioBusy.IDLE && asset.sourceStatus == SourceStatus.AVAILABLE
            ) { Text(if (state.trackedAnchors.isEmpty()) "Track movement" else "Re-track") }
            if (state.trackedAnchors.isNotEmpty()) {
                Text("${state.trackedAnchors.size} anchors", color = VideoFlowEditorColors.SuccessColor)
            }
        }
        state.trackingConfidence?.let { confidence ->
            Text(
                "Average tracking confidence ${(confidence * 100f).roundToInt()}%${if (confidence < 0.45f) " — review ROI before Apply" else ""}",
                color = if (confidence >= 0.45f) VideoFlowEditorColors.SecondaryText else VideoFlowEditorColors.WarningColor
            )
        }

        StepTitle("3", "AI Preview")
        Text("Preview uses the smaller local model. Final export uses the 512px final model on bounded original-resolution ROI tiles.", color = VideoFlowEditorColors.SecondaryText)
        Button(
            onClick = {
                val width = asset.width
                val height = asset.height
                if (width != null && height != null) {
                    vm.preview(
                        sourceUri = asset.sourceUri,
                        clip = clip,
                        clipLocalTimeUs = previewLocalUs,
                        roi = roi,
                        sourceWidth = width,
                        sourceHeight = height,
                        featherPx = featherPx.roundToInt(),
                        anchors = state.trackedAnchors
                    )
                }
            },
            enabled = state.runtimeReady && state.busy == WatermarkStudioBusy.IDLE && asset.width != null && asset.height != null
        ) { Text(if (state.aiPreview == null) "Generate AI Preview" else "Refresh AI Preview") }
        state.previewProvider?.let { Text("Preview inference: $it", color = VideoFlowEditorColors.SuccessColor) }

        Text("Edge feather  ${featherPx.roundToInt()} px", color = VideoFlowEditorColors.SecondaryText)
        Slider(
            value = featherPx,
            onValueChange = { featherPx = it; vm.clearPreviewOnly() },
            valueRange = 0f..32f,
            steps = 15,
            modifier = Modifier.semantics { contentDescription = "Watermark edge feather" }
        )
        Text("AI context  ${contextPx.roundToInt()} px", color = VideoFlowEditorColors.SecondaryText)
        Slider(
            value = contextPx,
            onValueChange = { contextPx = it; vm.clearPreviewOnly() },
            valueRange = 16f..112f,
            steps = 11,
            modifier = Modifier.semantics { contentDescription = "Watermark AI context padding" }
        )
        Text("Temporal stability  ${(stability * 100).roundToInt()}%", color = VideoFlowEditorColors.SecondaryText)
        Slider(
            value = stability,
            onValueChange = { stability = it; vm.clearPreviewOnly() },
            valueRange = 0f..0.30f,
            steps = 14,
            modifier = Modifier.semantics { contentDescription = "Watermark temporal stability" }
        )

        StepTitle("4", "Apply non-destructively")
        Text("Apply stores an editable AI effect. Source media is never overwritten and Smart Copy is automatically disabled for this pixel-changing edit.", color = VideoFlowEditorColors.SecondaryText)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = {
                    val effect = draftEffect.copy(
                        id = UUID.randomUUID().toString(),
                        contextPaddingPx = contextPx.roundToInt().coerceIn(0, 256),
                        featherPx = featherPx.roundToInt().coerceIn(0, 128),
                        temporalStability = stability.coerceIn(0f, 0.5f),
                        modelId = AiModelCatalog.FINAL_512.id
                    )
                    vm.apply(effect) {
                        refreshEditor()
                        onDismiss()
                    }
                },
                enabled = state.runtimeReady && state.busy == WatermarkStudioBusy.IDLE && asset.sourceStatus == SourceStatus.AVAILABLE,
                modifier = Modifier.weight(1f)
            ) { Text("Apply") }
        }

        if (state.existingEffects.isNotEmpty()) {
            HorizontalDivider(color = VideoFlowEditorColors.EditorDivider)
            Text("Applied AI effects", color = VideoFlowEditorColors.PrimaryText)
            state.existingEffects.forEachIndexed { index, effect ->
                AppliedEffectRow(
                    index = index,
                    effect = effect,
                    onEnabled = { vm.setEnabled(effect, it) },
                    onRemove = { vm.remove(effect) },
                    onEdit = {
                        roi = effect.roi
                        range = (effect.clipLocalStartUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)..
                            (effect.clipLocalEndUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
                        featherPx = effect.featherPx.toFloat()
                        contextPx = effect.contextPaddingPx.toFloat()
                        stability = effect.temporalStability
                        vm.clearDraftResults()
                    }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StudioHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(title, color = VideoFlowEditorColors.PrimaryText)
        Text(subtitle, color = VideoFlowEditorColors.SecondaryText)
    }
}

@Composable
private fun StepTitle(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.width(26.dp).height(26.dp).background(VideoFlowEditorColors.SelectionAccent),
            contentAlignment = Alignment.Center
        ) { Text(number, color = VideoFlowEditorColors.TextOnAccent) }
        Text(title, color = VideoFlowEditorColors.PrimaryText)
    }
}

@Composable
private fun AppliedEffectRow(
    index: Int,
    effect: AiWatermarkEffect,
    onEnabled: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("AI region ${index + 1}", color = VideoFlowEditorColors.PrimaryText)
            Text(
                "${formatDurationUs(effect.clipLocalStartUs)} – ${formatDurationUs(effect.clipLocalEndUs)} • ${effect.motionAnchors.size} anchors",
                color = VideoFlowEditorColors.SecondaryText
            )
        }
        TextButton(onClick = onEdit) { Text("Edit") }
        Switch(checked = effect.enabled, onCheckedChange = onEnabled, modifier = Modifier.semantics { contentDescription = "Enable AI region ${index + 1}" })
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}

private enum class RoiDragMode { MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

@Composable
private fun InteractiveRoiPreview(
    bitmap: Bitmap,
    roi: NormalizedRoi,
    onRoiChange: (NormalizedRoi) -> Unit,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentRoi = rememberUpdatedState(roi)
    val currentOnChange = rememberUpdatedState(onRoiChange)
    val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()

    Box(
        modifier
            .aspectRatio(ratio.coerceIn(0.35f, 3.2f))
            .background(Color.Black)
            .onSizeChanged { size = it }
            .semantics { contentDescription = "Interactive watermark mask region" }
            .pointerInput(size) {
                if (size.width <= 0 || size.height <= 0) return@pointerInput
                var mode = RoiDragMode.MOVE
                var working = currentRoi.value
                detectDragGestures(
                    onDragStart = { offset ->
                        working = currentRoi.value
                        val nx = (offset.x / size.width).coerceIn(0f, 1f)
                        val ny = (offset.y / size.height).coerceIn(0f, 1f)
                        val threshold = 0.07f
                        mode = when {
                            kotlin.math.abs(nx - working.left) <= threshold && kotlin.math.abs(ny - working.top) <= threshold -> RoiDragMode.TOP_LEFT
                            kotlin.math.abs(nx - working.right) <= threshold && kotlin.math.abs(ny - working.top) <= threshold -> RoiDragMode.TOP_RIGHT
                            kotlin.math.abs(nx - working.left) <= threshold && kotlin.math.abs(ny - working.bottom) <= threshold -> RoiDragMode.BOTTOM_LEFT
                            kotlin.math.abs(nx - working.right) <= threshold && kotlin.math.abs(ny - working.bottom) <= threshold -> RoiDragMode.BOTTOM_RIGHT
                            else -> RoiDragMode.MOVE
                        }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        val dx = drag.x / size.width.toFloat()
                        val dy = drag.y / size.height.toFloat()
                        working = moveRoi(working, mode, dx, dy)
                        currentOnChange.value(working)
                    }
                )
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Watermark source preview",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize()
        )
        Canvas(Modifier.matchParentSize()) {
            val left = roi.left * this.size.width
            val top = roi.top * this.size.height
            val right = roi.right * this.size.width
            val bottom = roi.bottom * this.size.height
            val shade = Color.Black.copy(alpha = 0.34f)
            drawRect(shade, Offset.Zero, Size(this.size.width, top))
            drawRect(shade, Offset(0f, bottom), Size(this.size.width, this.size.height - bottom))
            drawRect(shade, Offset(0f, top), Size(left, bottom - top))
            drawRect(shade, Offset(right, top), Size(this.size.width - right, bottom - top))
            drawRect(
                color = VideoFlowEditorColors.SelectionAccent,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 3.dp.toPx())
            )
            val handleRadius = 7.dp.toPx()
            listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom)).forEach {
                drawCircle(VideoFlowEditorColors.SelectionAccent, handleRadius, it)
                drawCircle(VideoFlowEditorColors.TextOnAccent, handleRadius * 0.45f, it)
            }
        }
    }
}

private fun moveRoi(
    roi: NormalizedRoi,
    mode: RoiDragMode,
    dx: Float,
    dy: Float
): NormalizedRoi {
    val minSize = 0.035f
    return when (mode) {
        RoiDragMode.MOVE -> {
            val width = roi.width
            val height = roi.height
            val left = (roi.left + dx).coerceIn(0f, 1f - width)
            val top = (roi.top + dy).coerceIn(0f, 1f - height)
            NormalizedRoi(left, top, left + width, top + height)
        }
        RoiDragMode.TOP_LEFT -> NormalizedRoi(
            (roi.left + dx).coerceIn(0f, roi.right - minSize),
            (roi.top + dy).coerceIn(0f, roi.bottom - minSize),
            roi.right,
            roi.bottom
        )
        RoiDragMode.TOP_RIGHT -> NormalizedRoi(
            roi.left,
            (roi.top + dy).coerceIn(0f, roi.bottom - minSize),
            (roi.right + dx).coerceIn(roi.left + minSize, 1f),
            roi.bottom
        )
        RoiDragMode.BOTTOM_LEFT -> NormalizedRoi(
            (roi.left + dx).coerceIn(0f, roi.right - minSize),
            roi.top,
            roi.right,
            (roi.bottom + dy).coerceIn(roi.top + minSize, 1f)
        )
        RoiDragMode.BOTTOM_RIGHT -> NormalizedRoi(
            roi.left,
            roi.top,
            (roi.right + dx).coerceIn(roi.left + minSize, 1f),
            (roi.bottom + dy).coerceIn(roi.top + minSize, 1f)
        )
    }
}

private fun busyLabel(busy: WatermarkStudioBusy): String = when (busy) {
    WatermarkStudioBusy.IDLE -> "Ready"
    WatermarkStudioBusy.PREPARING_MODELS -> "Validating local AI model pack…"
    WatermarkStudioBusy.LOADING_FRAME -> "Decoding bounded preview frame…"
    WatermarkStudioBusy.TRACKING -> "Tracking watermark movement locally…"
    WatermarkStudioBusy.AI_PREVIEW -> "Running local AI preview…"
    WatermarkStudioBusy.APPLYING -> "Saving non-destructive AI effect…"
}
