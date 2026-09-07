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
                    }, modifier=Modifier.fillMaxWidth().height(48.dp).semantics {
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

            if (!hasTimelineItems) {
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
    val density = LocalDensity.current
    val laneHeight = 104.dp
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
                    IconButton(onClick = onTrackSettings, modifier = Modifier.width(48.dp).height(48.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Open ${track.name} settings", tint = VideoFlowEditorColors.SecondaryText)
                    }
                }
                Row {
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
                    IconButton(onClick = onToggleLock, modifier = Modifier.width(48.dp).height(48.dp)) {
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
            val visibleStart = TimelineViewport.timeAt(originUs,with(density) { horizontal.value.toDp().value.toDouble() },pixelsPerSecond)
            val visibleEnd = minOf(windowEndUs,TimelineViewport.timeAt(visibleStart,maxWidth.value.toDouble(),pixelsPerSecond))
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
                        .pointerInput(pixelsPerSecond, originUs, windowEndUs) {
                            detectTapGestures { offset ->
                                val xDp = with(density) { offset.x.toDp().value }
                                onClearSelection()
                                onSeek(TimelineViewport.timeAt(originUs,xDp.toDouble(),pixelsPerSecond).coerceIn(originUs,windowEndUs))
                            }
                        }
                ) {
                    clips.filter { TimelineViewport.intersects(it.timelineStartUs,it.timelineStartUs+it.timelineDurationUs,visibleStart,visibleEnd) }.forEach { clip ->
                        TimelineClipCard(
                            clip = clip,
                            originUs = originUs,
                            windowEndUs = windowEndUs,
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
                        if(!TimelineViewport.intersects(overlay.timelineStartUs,overlay.timelineEndUs,visibleStart,visibleEnd)) return@forEach
                        OverlayBlock(
                            label = overlay.content.ifBlank { "Text" },
                            startUs = maxOf(overlay.timelineStartUs,originUs)-originUs,
                            endUs = minOf(overlay.timelineEndUs,windowEndUs)-originUs,
                            pixelsPerSecond = pixelsPerSecond,
                            selected = selection == EditorSelection.TextOverlay(overlay.id),
                            keyframes = keyframes.filter { it.ownerId == overlay.id && it.timeUs in
                                (maxOf(overlay.timelineStartUs,originUs)-overlay.timelineStartUs)..(minOf(overlay.timelineEndUs,windowEndUs)-overlay.timelineStartUs) }
                                .map { it.copy(timeUs=it.timeUs-(maxOf(overlay.timelineStartUs,originUs)-overlay.timelineStartUs)) },
                            onSelect = { onSelect(EditorSelection.TextOverlay(overlay.id)) }
                        )
                    }
                    imageOverlays.forEach { overlay ->
                        if(!TimelineViewport.intersects(overlay.timelineStartUs,overlay.timelineEndUs,visibleStart,visibleEnd)) return@forEach
                        OverlayBlock(
                            label = mediaNames[overlay.assetId] ?: "Image",
                            startUs = maxOf(overlay.timelineStartUs,originUs)-originUs,
                            endUs = minOf(overlay.timelineEndUs,windowEndUs)-originUs,
                            pixelsPerSecond = pixelsPerSecond,
                            selected = selection == EditorSelection.ImageOverlay(overlay.id),
                            keyframes = keyframes.filter { it.ownerId == overlay.id && it.timeUs in
                                (maxOf(overlay.timelineStartUs,originUs)-overlay.timelineStartUs)..(minOf(overlay.timelineEndUs,windowEndUs)-overlay.timelineStartUs) }
                                .map { it.copy(timeUs=it.timeUs-(maxOf(overlay.timelineStartUs,originUs)-overlay.timelineStartUs)) },
                            onSelect = { onSelect(EditorSelection.ImageOverlay(overlay.id)) }
                        )
                    }
                    val playheadX = timeWidth(playheadUs-originUs, pixelsPerSecond)
                    if(playheadUs in originUs..windowEndUs) Box(
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
    originUs: Long,
    windowEndUs: Long,
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
    val shownStartUs=maxOf(originUs,clip.timelineStartUs)
    val shownEndUs=minOf(windowEndUs,clip.timelineStartUs+clip.timelineDurationUs)
    val baseWidthDp = timeWidth(shownEndUs-shownStartUs, pixelsPerSecond).coerceAtLeast(72.dp)
    val baseWidthPx = with(density) { baseWidthDp.toPx() }
    val minWidthPx = with(density) { 48.dp.toPx() }
    val baseStartPx = with(density) { timeWidth(shownStartUs-originUs, pixelsPerSecond).toPx() }
    val visualWidthPx = (baseWidthPx + endTrimPx - startTrimPx).coerceAtLeast(minWidthPx)
    val visualWidthDp = with(density) { visualWidthPx.toDp() }
    val visualStartDp = with(density) { (baseStartPx + startTrimPx).toDp() }
    val isAudio = waveform != null
    val categoryColor = if (isAudio) VideoFlowEditorColors.TimelineAudioClip else VideoFlowEditorColors.TimelineVideoClip

    fun timelineDeltaUs(deltaPx: Float): Long {
        val deltaDp = with(density) { deltaPx.toDp().value }
        return ((deltaDp.toDouble() / pixelsPerSecond) * 1_000_000.0).roundToLong()
    }

    fun autoScroll(pointerTimelinePx: Float) {
        if (viewportWidthPx <= 0f) return
        val visibleX = pointerTimelinePx - horizontal.value.toFloat()
        val edgePx = with(density) { 56.dp.toPx() }
        val maxStepPx = with(density) { 18.dp.toPx() }
        val delta = timelineAutoScrollDelta(visibleX, viewportWidthPx, edgePx, maxStepPx)
        if (abs(delta) > 0.01f) horizontal.dispatchRawDelta(delta)
    }

    val bodyDrag = if (locked) Modifier else Modifier.pointerInput(clip, originUs, windowEndUs, pixelsPerSecond, selected) {
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
                    CachedThumbnailPreview(thumbnail, Modifier.width(48.dp).height(24.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(name, color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                if (waveform != null) {
                    WaveformPreview(waveform, Modifier.fillMaxWidth().height(22.dp), VideoFlowEditorColors.SelectionAccent)
                }
                KeyframeStrip(keyframes.filter { it.timeUs in (shownStartUs-clip.timelineStartUs)..(shownEndUs-clip.timelineStartUs) }.map { it.copy(timeUs=it.timeUs-(shownStartUs-clip.timelineStartUs)) }, shownEndUs-shownStartUs, Modifier.fillMaxWidth().height(12.dp))
            }
        }

        if (selected && !locked) {
            if(shownStartUs==clip.timelineStartUs) TimelineTrimHandle(
                description = "Trim clip start",
                onCancelled = { startTrimPx=0f },
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
            if(shownEndUs==clip.timelineStartUs+clip.timelineDurationUs) TimelineTrimHandle(
                description = "Trim clip end",
                onCancelled = { endTrimPx=0f },
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
    onFinished: () -> Unit,
    onCancelled: () -> Unit
) {
    val currentDrag by rememberUpdatedState(onDrag)
    val currentFinished by rememberUpdatedState(onFinished)
    val currentCancelled by rememberUpdatedState(onCancelled)
    var dragged by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .width(TimelineTrimHandleWidth)
            .fillMaxHeight()
            .background(VideoFlowEditorColors.SelectionAccent.copy(alpha = 0.22f))
            .semantics { contentDescription = description }
            .pointerInput(description) {
                detectHorizontalDragGestures(
                    onDragCancel = { dragged = 0f; currentCancelled() },
                    onDragEnd = { dragged = 0f; currentFinished() }
                ) { change, amount ->
                    change.consume()
                    dragged += amount
                    currentDrag(amount, change.position.x)
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
private fun TimelineRuler(durationUs: Long, pixelsPerSecond: Float, width: Dp, originUs: Long) {
    val intervalSeconds = when {
        pixelsPerSecond < 28f -> 10
        pixelsPerSecond < 65f -> 5
        else -> 1
    }
    val firstSecond = (originUs/1_000_000L/intervalSeconds)*intervalSeconds
    val ticks = (((durationUs-originUs) / 1_000_000L) / intervalSeconds + 2L).coerceAtMost(1500L).toInt()
    Box(Modifier.width(width).height(48.dp).clearAndSetSemantics { }) {
        repeat(ticks) { index ->
            val second = firstSecond+index * intervalSeconds
            Text(
                formatDurationUs(second*1_000_000L),
                color = VideoFlowEditorColors.SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.offset(x = TimelineViewport.positionDp(second*1_000_000L,originUs,pixelsPerSecond).toFloat().dp, y = 7.dp)
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
    val calculated = ((durationUs.toDouble() / 1_000_000.0) * pixelsPerSecond).toFloat().coerceAtMost(12_000f)
    return calculated.coerceAtLeast(360f).dp
}

private fun timeWidth(durationUs: Long, pixelsPerSecond: Float): Dp =
    ((durationUs.toDouble() / 1_000_000.0) * pixelsPerSecond).toFloat().coerceIn(0f, 12_000f).dp
