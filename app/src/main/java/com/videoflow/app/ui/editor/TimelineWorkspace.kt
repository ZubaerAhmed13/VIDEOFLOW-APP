package com.videoflow.app.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Slider
import com.videoflow.app.domain.editor.TimelineViewport
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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

private val TrackHeaderWidth = 96.dp
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
    modifier: Modifier = Modifier,
    revision: Long = 0L,
    onProfessionalTool: (ProfessionalEditorTool)->Unit = {}
) {
    val horizontal = rememberScrollState()
    val safeDuration = maxOf(durationUs, 5_000_000L)
    var originUs by rememberSaveable { mutableLongStateOf(0L) }
    val windowDurationUs = minOf(safeDuration,TimelineViewport.durationUs(pixelsPerSecond))
    val windowEndUs = minOf(safeDuration,originUs+windowDurationUs)
    val totalWidth = timelineWidth(windowDurationUs, pixelsPerSecond)
    val density = LocalDensity.current
    var pendingZoomScroll by remember { mutableStateOf<Float?>(null) }
    fun zoomTo(next: Float, anchorDp: Double = with(density) { horizontal.value.toDp().value.toDouble() } + 120.0) {
        if(next==pixelsPerSecond) return
        val anchorTime=TimelineViewport.timeAt(originUs,anchorDp,pixelsPerSecond)
        val scrollDp=with(density) { horizontal.value.toDp().value }
        originUs=TimelineViewport.zoomOrigin(originUs,anchorDp,pixelsPerSecond,next,safeDuration)
        pendingZoomScroll=(TimelineViewport.positionDp(anchorTime,originUs,next)-(anchorDp-scrollDp)).toFloat().coerceAtLeast(0f)
        onZoom(next)
    }
    val currentPinch by rememberUpdatedState<(Float,Double)->Unit>({ factor,anchor -> zoomTo((pixelsPerSecond*factor).coerceIn(12f,240f),anchor) })
    LaunchedEffect(pixelsPerSecond) {
        pendingZoomScroll?.let { scroll ->
            androidx.compose.runtime.withFrameNanos { }
            horizontal.scrollTo(with(density) { scroll.dp.roundToPx() })
            pendingZoomScroll=null
        }
    }
    LaunchedEffect(playheadUs, safeDuration) {
        if(playheadUs < originUs || playheadUs >= windowEndUs) {
            originUs=TimelineViewport.centeredOrigin(playheadUs,pixelsPerSecond,safeDuration)
        }
        if(safeDuration>windowDurationUs) {
            androidx.compose.runtime.withFrameNanos { }
            val x=TimelineViewport.positionDp(playheadUs,originUs,pixelsPerSecond).toFloat()
            horizontal.scrollTo(with(density) { (x-120f).coerceAtLeast(0f).dp.roundToPx() })
        }
    }
    val hasTimelineItems = clips.isNotEmpty() || textOverlays.isNotEmpty() || imageOverlays.isNotEmpty()

    Surface(modifier = modifier, color = VideoFlowEditorColors.TimelineBackground) {
        Column(Modifier.fillMaxSize()) {
            if(safeDuration>windowDurationUs) {
                Slider(value=(playheadUs.toDouble()/safeDuration).toFloat().coerceIn(0f,1f),
                    onValueChange={ fraction ->
                        val time=(fraction.toDouble()*safeDuration).roundToLong()
                        originUs=TimelineViewport.centeredOrigin(time,pixelsPerSecond,safeDuration)
                        onSeek(time)
                    }, modifier=Modifier.fillMaxWidth().height(36.dp).semantics {
                        contentDescription="Navigate whole project, playhead ${formatDurationUs(playheadUs)}"
                    })
            }
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.width(TrackHeaderWidth).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = { zoomTo((pixelsPerSecond / 1.3f).coerceAtLeast(12f)) },
                        modifier = Modifier.width(48.dp).height(48.dp)
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out timeline", tint = VideoFlowEditorColors.SecondaryText)
                    }
                    IconButton(
                        onClick = { zoomTo((pixelsPerSecond * 1.3f).coerceAtMost(240f)) },
                        modifier = Modifier.width(48.dp).height(48.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in timeline", tint = VideoFlowEditorColors.SecondaryText)
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(horizontal)
                        .pointerInput(density) {
                            detectTransformGestures { centroid, _, zoom, _ ->
                                if (zoom.isFinite() && zoom > 0f) {
                                    currentPinch(zoom, with(density) { (horizontal.value+centroid.x).toDp().value.toDouble() })
                                }
                            }
                        }
                ) {
                    TimelineRuler(windowEndUs, pixelsPerSecond, totalWidth, originUs)
                }
            }

            if (!hasTimelineItems && tracks.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Start your video", color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                        Text("Add media from the toolbar below.", color = VideoFlowEditorColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    item("timed-edits") { TimedEffectIndicators(clips,revision,horizontal,totalWidth,pixelsPerSecond,onSelect,onSeek,onProfessionalTool,originUs,windowEndUs) }
                    items(tracks.sortedBy { it.orderIndex },key={it.id}) { track ->
                        TrackRow(
                            track = track,
                            originUs = originUs,
                            windowEndUs = windowEndUs,
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
    originUs: Long,
    windowEndUs: Long,
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
    val active = selection is EditorSelection.Track && selection.trackId == track.id
    val density = LocalDensity.current
    val selectionBackground = if (active) VideoFlowEditorColors.SelectionAccent.copy(alpha = 0.13f) else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(selectionBackground),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackHeader(track, active, onToggleMute, onToggleVisible, onToggleLock, onTrackSettings)
        TimelineLane(
            track = track,
            originUs = originUs,
            windowEndUs = windowEndUs,
            clips = clips,
            textOverlays = textOverlays,
            imageOverlays = imageOverlays,
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
            onTrimClipEnd = onTrimClipEnd
        )
    }
}

@Composable
private fun TrackHeader(
    track: TimelineTrack,
    selected: Boolean,
    onToggleMute: () -> Unit,
    onToggleVisible: () -> Unit,
    onToggleLock: () -> Unit,
    onSettings: () -> Unit
) {
    Column(Modifier.width(TrackHeaderWidth).padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(track.name, color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (track.type == TrackType.AUDIO) {
                IconButton(onClick = onToggleMute, modifier = Modifier.width(32.dp).height(32.dp)) {
                    Icon(if (track.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = if (track.muted) "Unmute ${track.name}" else "Mute ${track.name}", tint = VideoFlowEditorColors.SecondaryText)
                }
            } else {
                IconButton(onClick = onToggleVisible, modifier = Modifier.width(32.dp).height(32.dp)) {
                    Icon(if (track.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = if (track.visible) "Hide ${track.name}" else "Show ${track.name}", tint = VideoFlowEditorColors.SecondaryText)
                }
            }
            IconButton(onClick = onToggleLock, modifier = Modifier.width(32.dp).height(32.dp)) {
                Icon(if (track.locked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = if (track.locked) "Unlock ${track.name}" else "Lock ${track.name}", tint = VideoFlowEditorColors.SecondaryText)
            }
            IconButton(onClick = onSettings, modifier = Modifier.width(32.dp).height(32.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Open ${track.name} settings", tint = VideoFlowEditorColors.SecondaryText)
            }
        }
    }
}

@Composable
private fun TimelineRuler(