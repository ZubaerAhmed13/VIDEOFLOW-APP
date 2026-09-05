package com.videoflow.app.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.graphics.Color
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
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val TrackHeaderWidth = 72.dp

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
                    IconButton(onClick = { onZoom((pixelsPerSecond / 1.3f).coerceAtLeast(12f)) }, modifier = Modifier.width(34.dp)) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out timeline", tint = VideoFlowEditorColors.SecondaryText)
                    }
                    IconButton(onClick = { onZoom((pixelsPerSecond * 1.3f).coerceAtMost(240f)) }, modifier = Modifier.width(34.dp)) {
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

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(vertical)
            ) {
                if (tracks.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("Add media to begin", color = VideoFlowEditorColors.SecondaryText)
                    }
                }
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
    horizontal: androidx.compose.foundation.ScrollState,
    selection: EditorSelection,
    mediaNames: Map<String, String>,
    thumbnails: Map<String, String>,
    waveforms: Map<String, FloatArray>,
    onSeek: (Long) -> Unit,
    onSelect: (EditorSelection) -> Unit,
    onClearSelection: () -> Unit,
    onMoveClip: (String, Long) -> Unit,
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
                Text(track.name.take(6), color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.labelMedium)
                Row {
                    if (track.type == TrackType.AUDIO) {
                        IconButton(onClick = onToggleMute, modifier = Modifier.width(28.dp).height(28.dp)) {
                            Icon(
                                if (track.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (track.muted) "Unmute ${track.name}" else "Mute ${track.name}",
                                tint = VideoFlowEditorColors.SecondaryText
                            )
                        }
                    } else {
                        IconButton(onClick = onToggleVisible, modifier = Modifier.width(28.dp).height(28.dp)) {
                            Icon(
                                if (track.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (track.visible) "Hide ${track.name}" else "Show ${track.name}",
                                tint = VideoFlowEditorColors.SecondaryText
                            )
                        }
                    }
                    IconButton(onClick = onToggleLock, modifier = Modifier.width(28.dp).height(28.dp)) {
                        Icon(
                            if (track.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (track.locked) "Unlock ${track.name}" else "Lock ${track.name}",
                            tint = VideoFlowEditorColors.SecondaryText
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
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
                        onSelect = { onSelect(EditorSelection.Clip(clip.id)) },
                        onMove = { onMoveClip(clip.id, it) }
                    )
                }
                textOverlays.forEach { overlay ->
                    OverlayBlock(
                        label = overlay.content.ifBlank { "Text" },
                        startUs = overlay.timelineStartUs,
                        endUs = overlay.timelineEndUs,
                        pixelsPerSecond = pixelsPerSecond,
                        selected = selection == EditorSelection.TextOverlay(overlay.id),
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
        Box(Modifier.width(0.dp)) {
            IconButton(onClick = onTrackSettings) {
                Icon(Icons.Default.MoreVert, contentDescription = "Open ${track.name} settings")
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
    onSelect: () -> Unit,
    onMove: (Long) -> Unit
) {
    val density = LocalDensity.current
    var dragPx by remember(clip.id) { mutableFloatStateOf(0f) }
    val width = timeWidth(clip.timelineDurationUs, pixelsPerSecond).coerceAtLeast(72.dp)
    val categoryColor = if (waveform != null) VideoFlowEditorColors.TimelineAudioClip else VideoFlowEditorColors.TimelineVideoClip
    val dragModifier = if (locked) Modifier else Modifier.pointerInput(clip.id, pixelsPerSecond) {
        detectHorizontalDragGestures(
            onDragCancel = { dragPx = 0f },
            onDragEnd = {
                val deltaDp = with(density) { dragPx.toDp().value }
                val deltaUs = ((deltaDp / pixelsPerSecond) * 1_000_000f).roundToLong()
                dragPx = 0f
                if (deltaUs != 0L) onMove(deltaUs)
            }
        ) { change, amount ->
            change.consume()
            dragPx += amount
        }
    }
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(containerColor = categoryColor),
        border = if (selected) BorderStroke(2.dp, VideoFlowEditorColors.SelectionAccent) else BorderStroke(1.dp, VideoFlowEditorColors.EditorDivider),
        modifier = Modifier
            .offset(x = timeWidth(clip.timelineStartUs, pixelsPerSecond), y = 5.dp)
            .width(width)
            .height(68.dp)
            .graphicsLayer { translationX = dragPx }
            .then(dragModifier)
            .semantics {
                contentDescription = "Video clip $name, ${formatDurationUs(clip.timelineDurationUs)}${if (selected) ", selected" else ""}"
                this.selected = selected
            }
    ) {
        Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CachedThumbnailPreview(thumbnail, Modifier.width(34.dp).height(24.dp))
                Spacer(Modifier.width(4.dp))
                Text(name, color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            if (waveform != null) {
                WaveformPreview(waveform, Modifier.fillMaxWidth().height(22.dp), VideoFlowEditorColors.SelectionAccent)
            } else {
                KeyframeStrip(keyframes, Modifier.fillMaxWidth().height(12.dp))
            }
        }
    }
}

@Composable
private fun OverlayBlock(
    label: String,
    startUs: Long,
    endUs: Long,
    pixelsPerSecond: Float,
    selected: Boolean,
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
        Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.CenterStart) {
            Text(label, color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
private fun KeyframeStrip(frames: List<Keyframe>, modifier: Modifier) {
    if (frames.isEmpty()) return
    Canvas(modifier.clearAndSetSemantics { }) {
        val maxUs = frames.maxOfOrNull { it.timeUs }?.coerceAtLeast(1L) ?: 1L
        frames.forEach { frame ->
            val x = (frame.timeUs.toFloat() / maxUs.toFloat()).coerceIn(0f, 1f) * size.width
            drawCircle(
                color = if (frame.interpolation == KeyframeInterpolation.HOLD) Color.Magenta else VideoFlowEditorColors.SelectionAccent,
                radius = 3f,
                center = androidx.compose.ui.geometry.Offset(x, size.height / 2f)
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
