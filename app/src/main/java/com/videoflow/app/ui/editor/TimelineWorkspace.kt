package com.videoflow.app.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.videoflow.app.domain.editor.ImageOverlay
import com.videoflow.app.domain.editor.Keyframe
import com.videoflow.app.domain.editor.KeyframeInterpolation
import com.videoflow.app.domain.editor.TextOverlay
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineTrack
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.ui.CachedThumbnailPreview
import com.videoflow.app.ui.WaveformPreview
import com.videoflow.app.util.formatDurationUs
import kotlin.math.abs
import kotlin.math.roundToLong

private val TrackHeaderWidth = 84.dp
private val TimelineTrimHandleWidth = 18.dp

@Composable
fun TimelineWorkspace(
    tracks: List<TimelineTrack>,
    clips: List<TimelineClip>,
    textOverlays: List<TextOverlay>,
    imageOverlays: List<ImageOverlay>,
    keyframes: List<Keyframe>,
    playheadUs: Long,
    durationUs: Long,
    pixelsPerSecond: Float,
    selection: EditorSelection,
    mediaNames: Map<String, String>,
    thumbnails: Map<String, String>,
    waveforms: Map<String, FloatArray>,
    onZoom: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    onSelect: (EditorSelection) -> Unit,
    onClearSelection: () -> Unit,
    onMoveClip: (String, Long) -> Unit,
    onTrimClipStart: (String, Long) -> Unit = { _, _ -> },
    onTrimClipEnd: (String, Long) -> Unit = { _, _ -> },
    onToggleMute: (TimelineTrack) -> Unit,
    onToggleVisible: (TimelineTrack) -> Unit,
    onToggleLock: (TimelineTrack) -> Unit,
    onTrackSettings: (TimelineTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val safeDuration = maxOf(durationUs, 5_000_000L)
    val totalWidth = timelineWidth(safeDuration, pixelsPerSecond)
    val hasTimelineItems = clips.isNotEmpty() || textOverlays.isNotEmpty() || imageOverlays.isNotEmpty()

    Surface(modifier = modifier, color = VideoFlowEditorColors.TimelineBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(34.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.width(TrackHeaderWidth).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = { onZoom((pixelsPerSecond / 1.3f).coerceAtLeast(12f)) },
                        modifier = Modifier.width(34.dp)
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out timeline", tint = VideoFlowEditorColors.SecondaryText)
                    }
                    IconButton(
                        onClick = { onZoom((pixelsPerSecond * 1.3f).coerceAtMost(240f)) },
                        modifier = Modifier.width(34.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in timeline", tint = VideoFlowEditorColors.SecondaryText)
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(horizontal)
                        .pointerInput(pixelsPerSecond) {
                            detectTransformGestures { _, _, zoom, _ ->
                                if (zoom.isFinite() && zoom > 0f) {
                                    onZoom((pixelsPerSecond * zoom).coerceIn(12f, 240f))
                                }
                            }
                        }
                ) {
                    TimelineRuler(safeDuration, pixelsPerSecond, totalWidth)
                }
            }

            if (!hasTimelineItems) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Start your video", color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                        Text("Add media from the toolbar below.", color = VideoFlowEditorColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(vertical)
                ) {
                    tracks.sortedBy { it.orderIndex }.forEach { track ->
                        TrackRow(
                            track = track,
                            clips = clips.filter { it.trackId == track.id },
                            textOverlays = textOverlays.filter { it.trackId == track.id },
                            imageOverlays = imageOverlays.filter { it.trackId == track.id },
                            keyframes = keyframes,
                            playheadUs = playheadUs,
                            totalWidth = totalWidth,
                            pixelsPerSecond = pixelsPerSecond,
                            horizontal = horizontal,
                            selection = selection,
                            mediaNames = mediaNames,
                            thumbnails = thumbnails,
                            waveforms = waveforms,
                            onSeek = onSeek,
                            onSelect = onSelect,
                            onClearSelection = onClearSelection,
                            onMoveClip = onMoveClip,
                            onTrimClipStart = onTrimClipStart,
                            onTrimClipEnd = onTrimClipEnd,
                            onToggleMute = { onToggleMute(track) },
                            onToggleVisible = { onToggleVisible(track) },
                            onToggleLock = { onToggleLock(track) },
                            onTrackSettings = { onTrackSettings(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: TimelineTrack,
    clips: List<TimelineClip>,
    textOverlays: List<TextOverlay>,
    imageOverlays: List<ImageOverlay>,
    keyframes: List<Keyframe>,
    playheadUs: Long,
    totalWidth: Dp,
    pixelsPerSecond: Float,
    horizontal: ScrollState,
    selection: EditorSelection,
    mediaNames: Map<String, String>,
    thumbnails: Map<String, String>,
    waveforms: Map<String, FloatArray>,
    onSeek: (Long) -> Unit,
    onSelect: (EditorSelection) -> Unit,
    onClearSelection: () -> Unit,
    onMoveClip: (String, Long) -> Unit,
    onTrimClipStart: (String, Long) -> Unit,
    onTrimClipEnd: (String, Long) -> Unit,
    onToggleMute: () -> Unit,
    onToggleVisible: () -> Unit,
    onToggleLock: () -> Unit,
    onTrackSettings: () -> Unit
) {
    val density = LocalDensity.current
    val laneHeight = 78.dp
    Row(Modifier.fillMaxWidth().height(laneHeight)) {
        Surface(color = VideoFlowEditorColors.TimelineTrackHeader, modifier = Modifier.width(TrackHeaderWidth).fillMaxHeight()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        track.name.take(7),
                        color = VideoFlowEditorColors.PrimaryText,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(start = 7.dp)
                    )
                    IconButton(onClick = onTrackSettings, modifier = Modifier.width(28.dp).height(28.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Open ${track.name} settings", tint = VideoFlowEditorColors.SecondaryText)
                    }
                }
                Row {
                    if (track.type == TrackType.AUDIO) {
                        IconButton(onClick = onToggleMute, modifier = Modifier.width(30.dp).height(30.dp)) {
                            Icon(
                                if (track.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (track.muted) "Unmute ${track.name}" else "Mute ${track.name}",
                                tint = VideoFlowEditorColors.SecondaryText
                            )
                        }
                    } else {
                        IconButton(onClick = onToggleVisible, modifier = Modifier.width(30.dp).height(30.dp)) {
                            Icon(
                                if (track.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (track.visible) "Hide ${track.name}" else "Show ${track.name}",
                                tint = VideoFlowEditorColors.SecondaryText
                            )
                        }
                    }
                    IconButton(onClick = onToggleLock, modifier = Modifier.width(30.dp).height(30.dp)) {
                        Icon(
                            if (track.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (track.locked) "Unlock ${track.name}" else "Lock ${track.name}",
                            tint = VideoFlowEditorColors.SecondaryText
                        )
                    }
                }
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            Box(
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontal)
                    .background(VideoFlowEditorColors.TimelineBackground)
            ) {
                Box(
                    Modifier
                        .width(totalWidth)
                        .fillMaxHeight()
                        .clipToBounds()
                        .pointerInput(pixelsPerSecond) {
                            detectTapGestures { offset ->
                                val xDp = with(density) { offset.x.toDp().value }
                                onClearSelection()
                                onSeek(((xDp / pixelsPerSecond) * 1_000_000f).roundToLong().coerceAtLeast(0L))
                            }
                        }
                ) {
                    clips.forEach { clip ->
                        TimelineClipCard(
                            clip = clip,
                            name = mediaNames[clip.assetId] ?: "Clip",
                            selected = selection == EditorSelection.Clip(clip.id),
                            pixelsPerSecond = pixelsPerSecond,
                            locked = track.locked,
                            keyframes = keyframes.filter { it.ownerId == clip.id },
                            thumbnail = thumbnails[clip.assetId],
                            waveform = waveforms[clip.assetId],
                            horizontal = horizontal,
                            viewportWidthPx = viewportWidthPx,
                            onSelect = { onSelect(EditorSelection.Clip(clip.id)) },
                            onMove = { onMoveClip(clip.id, it) },
                            onTrimStart = { onTrimClipStart(clip.id, it) },
                            onTrimEnd = { onTrimClipEnd(clip.id, it) }
                        )
                    }
                    textOverlays.forEach { overlay ->
                        OverlayBlock(
                            label = overlay.content.ifBlank { "Text" },
                            startUs = overlay.timelineStartUs,
                            endUs = overlay.timelineEndUs,
                            pixelsPerSecond = pixelsPerSecond,
                            selected = selection == EditorSelection.TextOverlay(overlay.id),
                            keyframes = keyframes.filter { it.ownerId == overlay.id },
                            onSelect = { onSelect(EditorSelection.TextOverlay(overlay.id)) }
                        )
                    }
                    imageOverlays.forEach { overlay ->
                        OverlayBlock(
                            label = mediaNames[overlay.assetId] ?: "Image",
                            startUs = overlay.timelineStartUs,
                            endUs = overlay.timelineEndUs,
                            pixelsPerSecond = pixelsPerSecond,
                            selected = selection == EditorSelection.ImageOverlay(overlay.id),
                            keyframes = keyframes.filter { it.ownerId == overlay.id },
                            onSelect = { onSelect(EditorSelection.ImageOverlay(overlay.id)) }
                        )
                    }
                    val playheadX = timeWidth(playheadUs, pixelsPerSecond)
                    Box(
                        Modifier
                            .offset(x = playheadX)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(VideoFlowEditorColors.PlayheadAccent)
                            .clearAndSetSemantics { }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineClipCard(
    clip: TimelineClip,
    name: String,
    selected: Boolean,
    pixelsPerSecond: Float,
    locked: Boolean,
    keyframes: List<Keyframe>,
    thumbnail: String?,
    waveform: FloatArray?,
    horizontal: ScrollState,
    viewportWidthPx: Float,
    onSelect: () -> Unit,
    onMove: (Long) -> Unit,
    onTrimStart: (Long) -> Unit,
    onTrimEnd: (Long) -> Unit
) {
    val density = LocalDensity.current
    var movePx by remember(clip.id) { mutableFloatStateOf(0f) }
    var startTrimPx by remember(clip.id) { mutableFloatStateOf(0f) }
    var endTrimPx by remember(clip.id) { mutableFloatStateOf(0f) }
    val baseWidthDp = timeWidth(clip.timelineDurationUs, pixelsPerSecond).coerceAtLeast(72.dp)
    val baseWidthPx = with(density) { baseWidthDp.toPx() }
    val minWidthPx = with(density) { 48.dp.toPx() }
    val baseStartPx = with(density) { timeWidth(clip.timelineStartUs, pixelsPerSecond).toPx() }
    val visualWidthPx = (baseWidthPx + endTrimPx - startTrimPx).coerceAtLeast(minWidthPx)
    val visualWidthDp = with(density) { visualWidthPx.toDp() }
    val visualStartDp = with(density) { (baseStartPx + startTrimPx).toDp() }
    val isAudio = waveform != null
    val categoryColor = if (isAudio) VideoFlowEditorColors.TimelineAudioClip else VideoFlowEditorColors.TimelineVideoClip

    fun timelineDeltaUs(deltaPx: Float): Long {
        val deltaDp = with(density) { deltaPx.toDp().value }
        return ((deltaDp / pixelsPerSecond) * 1_000_000f).roundToLong()
    }

    fun autoScroll(pointerTimelinePx: Float) {
        if (viewportWidthPx <= 0f) return
        val visibleX = pointerTimelinePx - horizontal.value.toFloat()
        val edgePx = with(density) { 56.dp.toPx() }
        val maxStepPx = with(density) { 18.dp.toPx() }
        val delta = timelineAutoScrollDelta(visibleX, viewportWidthPx, edgePx, maxStepPx)
        if (abs(delta) > 0.01f) horizontal.dispatchRawDelta(delta)
    }

    val bodyDrag = if (locked) Modifier else Modifier.pointerInput(clip.id, pixelsPerSecond, selected) {
        detectHorizontalDragGestures(
            onDragStart = { onSelect() },
            onDragCancel = { movePx = 0f },
            onDragEnd = {
                val deltaUs = timelineDeltaUs(movePx)
                movePx = 0f
                if (deltaUs != 0L) onMove(deltaUs)
            }
        ) { change, amount ->
            change.consume()
            movePx += amount
            autoScroll(baseStartPx + startTrimPx + movePx + change.position.x)
        }
    }

    Box(
        Modifier
            .offset(x = visualStartDp, y = 5.dp)
            .width(visualWidthDp)
            .height(68.dp)
            .graphicsLayer { translationX = movePx }
    ) {
        Card(
            onClick = onSelect,
            colors = CardDefaults.cardColors(containerColor = categoryColor),
            border = if (selected) BorderStroke(2.dp, VideoFlowEditorColors.SelectionAccent) else BorderStroke(1.dp, VideoFlowEditorColors.EditorDivider),
            modifier = Modifier
                .fillMaxSize()
                .then(bodyDrag)
                .semantics {
                    contentDescription = "${if (isAudio) "Audio" else "Video"} clip $name, ${formatDurationUs(clip.timelineDurationUs)}${if (selected) ", selected" else ""}"
                    this.selected = selected
                }
        ) {
            Column(Modifier.padding(horizontal = if (selected) 20.dp else 5.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CachedThumbnailPreview(thumbnail, Modifier.width(34.dp).height(24.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(name, color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                if (waveform != null) {
                    WaveformPreview(waveform, Modifier.fillMaxWidth().height(22.dp), VideoFlowEditorColors.SelectionAccent)
                }
                KeyframeStrip(keyframes, clip.timelineDurationUs, Modifier.fillMaxWidth().height(12.dp))
            }
        }

        if (selected && !locked) {
            TimelineTrimHandle(
                description = "Trim clip start",
                modifier = Modifier.align(Alignment.CenterStart),
                onDrag = { amount, pointerLocalX ->
                    val next = (startTrimPx + amount).coerceAtMost(baseWidthPx + endTrimPx - minWidthPx)
                    startTrimPx = next
                    autoScroll(baseStartPx + startTrimPx + pointerLocalX)
                },
                onFinished = {
                    val deltaUs = timelineDeltaUs(startTrimPx)
                    startTrimPx = 0f
                    if (deltaUs != 0L) onTrimStart(deltaUs)
                }
            )
            TimelineTrimHandle(
                description = "Trim clip end",
                modifier = Modifier.align(Alignment.CenterEnd),
                onDrag = { amount, pointerLocalX ->
                    val next = (endTrimPx + amount).coerceAtLeast(startTrimPx - baseWidthPx + minWidthPx)
                    endTrimPx = next
                    autoScroll(baseStartPx + baseWidthPx + endTrimPx + pointerLocalX)
                },
                onFinished = {
                    val deltaUs = timelineDeltaUs(endTrimPx)
                    endTrimPx = 0f
                    if (deltaUs != 0L) onTrimEnd(deltaUs)
                }
            )
        }
    }
}

@Composable
private fun TimelineTrimHandle(
    description: String,
    modifier: Modifier,
    onDrag: (amountPx: Float, pointerLocalX: Float) -> Unit,
    onFinished: () -> Unit
) {
    var dragged by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .width(TimelineTrimHandleWidth)
            .fillMaxHeight()
            .background(VideoFlowEditorColors.SelectionAccent.copy(alpha = 0.22f))
            .semantics { contentDescription = description }
            .pointerInput(description) {
                detectHorizontalDragGestures(
                    onDragCancel = { dragged = 0f },
                    onDragEnd = { dragged = 0f; onFinished() }
                ) { change, amount ->
                    change.consume()
                    dragged += amount
                    onDrag(amount, change.position.x)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.width(3.dp).height(30.dp).background(VideoFlowEditorColors.SelectionAccent))
    }
}

@Composable
private fun OverlayBlock(
    label: String,
    startUs: Long,
    endUs: Long,
    pixelsPerSecond: Float,
    selected: Boolean,
    keyframes: List<Keyframe>,
    onSelect: () -> Unit
) {
    val durationUs = (endUs - startUs).coerceAtLeast(1L)
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(containerColor = VideoFlowEditorColors.TimelineOverlayClip),
        border = if (selected) BorderStroke(2.dp, VideoFlowEditorColors.SelectionAccent) else null,
        modifier = Modifier
            .offset(x = timeWidth(startUs, pixelsPerSecond), y = 10.dp)
            .width(timeWidth(durationUs, pixelsPerSecond).coerceAtLeast(64.dp))
            .height(56.dp)
            .semantics {
                contentDescription = "$label overlay${if (selected) ", selected" else ""}"
                this.selected = selected
            }
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.Center) {
            Text(label, color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            KeyframeStrip(keyframes, durationUs, Modifier.fillMaxWidth().height(12.dp))
        }
    }
}

@Composable
private fun TimelineRuler(durationUs: Long, pixelsPerSecond: Float, width: Dp) {
    val intervalSeconds = when {
        pixelsPerSecond < 28f -> 10
        pixelsPerSecond < 65f -> 5
        else -> 1
    }
    val ticks = ((durationUs / 1_000_000L) / intervalSeconds + 2L).coerceAtMost(500L).toInt()
    Box(Modifier.width(width).height(34.dp).clearAndSetSemantics { }) {
        repeat(ticks) { index ->
            val second = index * intervalSeconds
            Text(
                "${second}s",
                color = VideoFlowEditorColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.offset(x = (second * pixelsPerSecond).dp, y = 7.dp)
            )
        }
    }
}

@Composable
private fun KeyframeStrip(frames: List<Keyframe>, ownerDurationUs: Long, modifier: Modifier) {
    if (frames.isEmpty() || ownerDurationUs <= 0L) return
    Canvas(modifier.clearAndSetSemantics { }) {
        frames.forEach { frame ->
            val x = keyframeMarkerFraction(frame.timeUs, ownerDurationUs) * size.width
            val y = size.height / 2f
            val radius = 4.5f
            val diamond = Path().apply {
                moveTo(x, y - radius)
                lineTo(x + radius, y)
                lineTo(x, y + radius)
                lineTo(x - radius, y)
                close()
            }
            drawPath(
                diamond,
                color = if (frame.interpolation == KeyframeInterpolation.HOLD) Color.Magenta else VideoFlowEditorColors.SelectionAccent
            )
        }
    }
}

private fun timelineWidth(durationUs: Long, pixelsPerSecond: Float): Dp {
    val calculated = ((durationUs.toDouble() / 1_000_000.0) * pixelsPerSecond).toFloat().coerceAtMost(50_000f)
    return calculated.coerceAtLeast(360f).dp
}

private fun timeWidth(durationUs: Long, pixelsPerSecond: Float): Dp =
    ((durationUs.toDouble() / 1_000_000.0) * pixelsPerSecond).toFloat().coerceIn(0f, 50_000f).dp
