package com.videoflow.app.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoflow.app.domain.editor.AudioMath
import com.videoflow.app.domain.editor.KeyframeEvaluator
import com.videoflow.app.domain.editor.KeyframeProperty
import com.videoflow.app.domain.editor.ProxyQuality
import com.videoflow.app.domain.editor.ProxyStatus
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineEngine
import com.videoflow.app.domain.editor.TimelineTrack
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.ui.BoundedImagePreview
import com.videoflow.app.ui.EditorViewModel
import com.videoflow.app.ui.NativeAudioPreview
import com.videoflow.app.ui.NativeVideoPlayer
import com.videoflow.app.util.formatBytes
import com.videoflow.app.util.formatDurationUs
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(id: String, onBack: () -> Unit, vm: EditorViewModel) {
    val project by vm.project.collectAsState()
    val editor by vm.editor.collectAsState()
    val playheadUs by vm.playheadUs.collectAsState()
    val selectedClipId by vm.selectedClipId.collectAsState()
    val message by vm.message.collectAsState()
    val saving by vm.saving.collectAsState()
    val proxyProgress by vm.proxyProgress.collectAsState()
    val history by vm.history.collectAsState()
    val snapshots by vm.snapshots.collectAsState()
    val waveforms by vm.waveforms.collectAsState()
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var pixelsPerSecond by rememberSaveable { mutableFloatStateOf(72f) }

    LaunchedEffect(id) { vm.load(id) }

    val timeline = editor?.timeline
    val durationUs = maxOf(0L, timeline?.durationUs ?: 0L)
    LaunchedEffect(isPlaying, durationUs) {
        if (!isPlaying || durationUs <= 0L) return@LaunchedEffect
        val startUs = playheadUs
        val startedNs = SystemClock.elapsedRealtimeNanos()
        while (isPlaying) {
            val elapsedUs = (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000L
            val next = startUs + elapsedUs
            if (next >= durationUs) {
                vm.setPlayheadUs(durationUs)
                isPlaying = false
                break
            }
            vm.setPlayheadUs(next)
            delay(16L)
        }
    }

    val tracks = timeline?.tracks.orEmpty()
    val clips = timeline?.clips.orEmpty()
    val videoTracks = tracks.filter { it.type == TrackType.VIDEO && it.visible }.map { it.id }.toSet()
    val activeVideoClip = clips
        .filter { it.enabled && it.trackId in videoTracks && playheadUs in it.timelineStartUs until it.timelineEndUs }
        .maxByOrNull { clip -> tracks.firstOrNull { it.id == clip.trackId }?.orderIndex ?: -1 }
    val activeVideoAsset = activeVideoClip?.let { clip -> project?.mediaAssets?.firstOrNull { it.id == clip.assetId } }
    val activeProxy = activeVideoAsset?.let { asset ->
        editor?.proxies?.firstOrNull { it.assetId == asset.id && it.status == ProxyStatus.READY }
    }
    val previewSource = activeProxy?.path ?: activeVideoAsset?.sourceUri
    val activeVideoTrack = activeVideoClip?.let { clip -> tracks.firstOrNull { it.id == clip.trackId } }
    val effectiveAudioTrackIds = TimelineEngine.effectiveAudioTracks(tracks).map { it.id }.toSet()
    val activeLocalUs = activeVideoClip?.let { (playheadUs - it.timelineStartUs).coerceAtLeast(0) } ?: 0L
    val activeSourcePositionMs = activeVideoClip?.let { clip ->
        ((clip.sourceStartUs + activeLocalUs * clip.speed) / 1000.0).toLong()
    } ?: 0L
    val videoVolume = if (activeVideoClip != null && activeVideoTrack != null && activeVideoTrack.id in effectiveAudioTrackIds) {
        val gain = AudioMath.dbToLinear(activeVideoClip.gainDb + activeVideoTrack.gainDb)
        val fade = AudioMath.fadeGain(activeLocalUs, activeVideoClip.timelineDurationUs, activeVideoClip.fadeInUs, activeVideoClip.fadeOutUs)
        (gain * fade).coerceIn(0f, 1f)
    } else 0f

    val evalTransform = activeVideoClip?.let { clip ->
        val frames = timeline?.keyframes.orEmpty().filter { it.ownerId == clip.id }
        fun value(property: KeyframeProperty, base: Float): Float =
            KeyframeEvaluator.evaluate(base, activeLocalUs, frames.filter { it.property == property })
        EvaluatedTransform(
            x = value(KeyframeProperty.POSITION_X, clip.transform.x),
            y = value(KeyframeProperty.POSITION_Y, clip.transform.y),
            scaleX = value(KeyframeProperty.SCALE_X, clip.transform.scaleX),
            scaleY = value(KeyframeProperty.SCALE_Y, clip.transform.scaleY),
            rotation = value(KeyframeProperty.ROTATION, clip.transform.rotationDegrees),
            opacity = value(KeyframeProperty.OPACITY, clip.opacity),
            flipHorizontal = clip.transform.flipHorizontal,
            flipVertical = clip.transform.flipVertical
        )
    }

    val selectedClip = clips.firstOrNull { it.id == selectedClipId }
    val activeText = timeline?.textOverlays.orEmpty().filter { playheadUs in it.timelineStartUs until it.timelineEndUs }
    val activeImages = timeline?.imageOverlays.orEmpty().filter { playheadUs in it.timelineStartUs until it.timelineEndUs }
    val activeAudioOnlyClips = clips.filter { clip ->
        val asset = project?.mediaAssets?.firstOrNull { it.id == clip.assetId }
        asset?.mimeType?.startsWith("audio/") == true &&
            clip.trackId in effectiveAudioTrackIds &&
            playheadUs in clip.timelineStartUs until clip.timelineEndUs
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(project?.name ?: "VideoFlow Editor")
                        Text(if (saving) "Saving…" else "Saved", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(onClick = vm::undo, enabled = history.canUndo && !saving) { Text("Undo") }
                    TextButton(onClick = vm::redo, enabled = history.canRedo && !saving) { Text("Redo") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier.fillMaxWidth().height(300.dp).background(Color.Black).clipToBounds(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewSource != null && activeVideoAsset?.mimeType?.startsWith("video/") == true) {
                            val t = evalTransform ?: EvaluatedTransform()
                            NativeVideoPlayer(
                                uri = previewSource,
                                startPositionMs = activeSourcePositionMs,
                                playWhenReady = isPlaying,
                                speed = activeVideoClip?.speed?.toFloat() ?: 1f,
                                volume = videoVolume,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(
                                        x = ((t.x - 0.5f) * 160f).dp,
                                        y = ((t.y - 0.5f) * 100f).dp
                                    )
                                    .graphicsLayer(
                                        scaleX = t.scaleX * if (t.flipHorizontal) -1f else 1f,
                                        scaleY = t.scaleY * if (t.flipVertical) -1f else 1f,
                                        rotationZ = t.rotation,
                                        alpha = t.opacity.coerceIn(0f, 1f)
                                    )
                            )
                        } else {
                            Text(
                                if (durationUs == 0L) "Add media to the timeline" else "Timeline gap • project background",
                                color = Color.White
                            )
                        }

                        activeImages.forEach { overlay ->
                            val asset = project?.mediaAssets?.firstOrNull { it.id == overlay.assetId }
                            if (asset != null) {
                                BoundedImagePreview(
                                    sourceUri = asset.sourceUri,
                                    modifier = Modifier
                                        .widthIn(max = 220.dp)
                                        .offset(
                                            x = ((overlay.transform.x - 0.5f) * 160f).dp,
                                            y = ((overlay.transform.y - 0.5f) * 100f).dp
                                        )
                                        .graphicsLayer(
                                            scaleX = overlay.transform.scaleX,
                                            scaleY = overlay.transform.scaleY,
                                            rotationZ = overlay.transform.rotationDegrees,
                                            alpha = overlay.transform.opacity
                                        )
                                )
                            }
                        }

                        activeText.forEach { overlay ->
                            Text(
                                text = overlay.content,
                                color = Color(overlay.colorArgb.toInt()).copy(alpha = overlay.opacity),
                                fontSize = overlay.fontSizeSp.sp,
                                fontWeight = FontWeight(overlay.fontWeight.coerceIn(100, 900)),
                                fontStyle = if (overlay.italic) FontStyle.Italic else FontStyle.Normal,
                                modifier = Modifier
                                    .offset(
                                        x = ((overlay.transform.x - 0.5f) * 160f).dp,
                                        y = ((overlay.transform.y - 0.5f) * 100f).dp
                                    )
                                    .graphicsLayer(
                                        scaleX = overlay.transform.scaleX,
                                        scaleY = overlay.transform.scaleY,
                                        rotationZ = overlay.transform.rotationDegrees,
                                        alpha = overlay.opacity
                                    )
                            )
                        }
                    }
                    Column(Modifier.padding(8.dp)) {
                        Text(
                            when {
                                activeProxy != null -> "Preview source: ${activeProxy.quality.name} proxy • render source remains original"
                                activeVideoAsset != null -> "Preview source: original"
                                else -> "Preview source: project background"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                activeAudioOnlyClips.forEach { clip ->
                    val asset = project?.mediaAssets?.firstOrNull { it.id == clip.assetId } ?: return@forEach
                    val track = tracks.firstOrNull { it.id == clip.trackId } ?: return@forEach
                    val localUs = (playheadUs - clip.timelineStartUs).coerceAtLeast(0)
                    val gain = AudioMath.dbToLinear(clip.gainDb + track.gainDb)
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

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${formatDurationUs(playheadUs)} / ${formatDurationUs(durationUs)}")
                    Button(onClick = { isPlaying = !isPlaying }, enabled = durationUs > 0L) {
                        Text(if (isPlaying) "Pause" else "Play")
                    }
                }
                Slider(
                    value = if (durationUs <= 0L) 0f else (playheadUs.toDouble() / durationUs.toDouble()).toFloat().coerceIn(0f, 1f),
                    onValueChange = { fraction ->
                        isPlaying = false
                        vm.setPlayheadUs((durationUs * fraction.toDouble()).toLong())
                    },
                    valueRange = 0f..1f,
                    enabled = durationUs > 0L
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { isPlaying = false; vm.setPlayheadUs(0) }) { Text("Start") }
                    OutlinedButton(onClick = { isPlaying = false; vm.setPlayheadUs((playheadUs - 1_000_000).coerceAtLeast(0)) }) { Text("−1s") }
                    OutlinedButton(onClick = { isPlaying = false; vm.setPlayheadUs((playheadUs + 1_000_000).coerceAtMost(durationUs)) }) { Text("+1s") }
                }
            }

            item {
                Text("Media Bin", style = MaterialTheme.typography.titleMedium)
                Text("Original media remains referenced; proxies are derived editor files only.", style = MaterialTheme.typography.bodySmall)
            }
            items(project?.mediaAssets.orEmpty(), key = { "asset-${it.id}" }) { asset ->
                val proxy = editor?.proxies?.firstOrNull { it.assetId == asset.id }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(asset.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    listOfNotNull(
                                        asset.mimeType,
                                        asset.sizeBytes?.let(::formatBytes),
                                        if (asset.width != null && asset.height != null) "${asset.width}×${asset.height}" else null
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(onClick = { vm.addAsset(asset.id) }, enabled = asset.sourceStatus.name == "AVAILABLE") { Text("Add") }
                        }
                        Text("Source: ${asset.sourceStatus.name.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
                        if (asset.mimeType?.startsWith("video/") == true) {
                            Text(
                                "Proxy: ${proxy?.status?.name?.replace('_', ' ') ?: "NONE"}" +
                                    (proxy?.let { " • ${it.width}×${it.height} • ${it.sizeBytes?.let(::formatBytes) ?: "size pending"}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (proxyProgress.assetId == asset.id && proxyProgress.status == ProxyStatus.GENERATING) {
                                    OutlinedButton(onClick = { vm.cancelProxy(asset.id) }) { Text("Cancel Proxy") }
                                    Text(proxyProgress.percent?.let { "$it%" } ?: "Working…", modifier = Modifier.padding(top = 12.dp))
                                } else {
                                    OutlinedButton(onClick = { vm.generateProxy(asset.id, ProxyQuality.BALANCED) }, enabled = asset.sourceStatus.name == "AVAILABLE") {
                                        Text(if (proxy?.status == ProxyStatus.READY) "Regenerate" else "Proxy 720p")
                                    }
                                    if (proxy != null) OutlinedButton(onClick = { vm.deleteProxy(asset.id) }) { Text("Delete Proxy") }
                                }
                            }
                        }
                        if (asset.mimeType?.startsWith("audio/") == true || asset.audioTrackCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { vm.generateWaveform(asset.id) }) { Text("Waveform") }
                                Spacer(Modifier.width(8.dp))
                                val peaks = waveforms[asset.id]
                                Text(if (peaks == null) "Not cached in editor session" else "${peaks.size} bounded peaks", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Timeline", style = MaterialTheme.typography.titleMedium)
                        Text("${pixelsPerSecond.roundToInt()} dp/s • pinch or buttons to zoom", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { pixelsPerSecond = (pixelsPerSecond / 1.35f).coerceAtLeast(12f) }) { Text("− Zoom") }
                        TextButton(onClick = { pixelsPerSecond = (pixelsPerSecond * 1.35f).coerceAtMost(240f) }) { Text("+ Zoom") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { vm.createTrack(TrackType.VIDEO) }) { Text("+ Video") }
                    TextButton(onClick = { vm.createTrack(TrackType.AUDIO) }) { Text("+ Audio") }
                    TextButton(onClick = { vm.createTrack(TrackType.OVERLAY) }) { Text("+ Overlay") }
                }
            }

            items(tracks, key = { "track-${it.id}" }) { track ->
                TrackLane(
                    track = track,
                    clips = clips.filter { it.trackId == track.id },
                    selectedClipId = selectedClipId,
                    pixelsPerSecond = pixelsPerSecond,
                    keyframes = timeline?.keyframes.orEmpty(),
                    waveforms = waveforms,
                    onZoom = { factor -> pixelsPerSecond = (pixelsPerSecond * factor).coerceIn(12f, 240f) },
                    onSelect = vm::selectClip,
                    onMute = { vm.toggleTrackMute(track.id, !track.muted) },
                    onSolo = { vm.toggleTrackSolo(track.id, !track.solo) },
                    onLock = { vm.toggleTrackLock(track.id, !track.locked) },
                    onVisible = { vm.toggleTrackVisible(track.id, !track.visible) },
                    onGain = { vm.setTrackGain(track.id, it) }
                )
            }

            selectedClip?.let { clip ->
                item {
                    ClipInspector(clip = clip, vm = vm)
                }
            }

            if (!timeline?.textOverlays.isNullOrEmpty() || !timeline?.imageOverlays.isNullOrEmpty()) {
                item { Text("Overlays", style = MaterialTheme.typography.titleMedium) }
                items(timeline?.textOverlays.orEmpty(), key = { "text-${it.id}" }) { overlay ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Text: ${overlay.content}")
                            Text("${formatDurationUs(overlay.timelineStartUs)} → ${formatDurationUs(overlay.timelineEndUs)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                items(timeline?.imageOverlays.orEmpty(), key = { "image-${it.id}" }) { overlay ->
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            "Image overlay • ${formatDurationUs(overlay.timelineStartUs)} → ${formatDurationUs(overlay.timelineEndUs)}",
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            item {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Snapshots", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { vm.createSnapshot("Snapshot ${snapshots.size + 1}") }) { Text("Save Snapshot") }
                }
            }
            items(snapshots, key = { "snapshot-${it.id}" }) { snapshot ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(snapshot.name, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { isPlaying = false; vm.restoreSnapshot(snapshot.id) }) { Text("Restore") }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = { vm.deleteSnapshot(snapshot.id) }) { Text("Delete") }
                    }
                }
            }

            item {
                Text(
                    "Step 2 is non-destructive. Professional final encoding/export is intentionally excluded and remains Step 3.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    message?.let {
        AlertDialog(
            onDismissRequest = vm::clearMessage,
            title = { Text("VideoFlow Editor") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text("OK") } }
        )
    }
}

@Composable
private fun ClipInspector(clip: TimelineClip, vm: EditorViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Selected Clip Inspector", style = MaterialTheme.typography.titleMedium)
            Text(
                "Position (${"%.2f".format(clip.transform.x)}, ${"%.2f".format(clip.transform.y)}) • Scale ${"%.2f".format(clip.transform.scaleX)} • Rotation ${clip.transform.rotationDegrees.roundToInt()}° • Opacity ${"%.2f".format(clip.opacity)}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { vm.moveTransform(-0.05f, 0f) }) { Text("←") }
                OutlinedButton(onClick = { vm.moveTransform(0f, -0.05f) }) { Text("↑") }
                OutlinedButton(onClick = { vm.moveTransform(0f, 0.05f) }) { Text("↓") }
                OutlinedButton(onClick = { vm.moveTransform(0.05f, 0f) }) { Text("→") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { vm.setScale((clip.transform.scaleX - 0.1f).coerceAtLeast(0.05f)) }) { Text("Scale −") }
                OutlinedButton(onClick = { vm.setScale(clip.transform.scaleX + 0.1f) }) { Text("Scale +") }
                OutlinedButton(onClick = vm::rotateSelected90) { Text("Rotate 90°") }
                OutlinedButton(onClick = vm::resetTransform) { Text("Reset") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = vm::toggleFlipHorizontal) { Text("Flip H") }
                OutlinedButton(onClick = vm::toggleFlipVertical) { Text("Flip V") }
                OutlinedButton(onClick = { vm.setOpacity((clip.opacity - 0.1f).coerceAtLeast(0f)) }) { Text("Opacity −") }
                OutlinedButton(onClick = { vm.setOpacity((clip.opacity + 0.1f).coerceAtMost(1f)) }) { Text("Opacity +") }
            }
            Text("Crop presets", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(16 to 9, 9 to 16, 4 to 3, 3 to 2, 1 to 1, 4 to 5).forEach { (w, h) ->
                    OutlinedButton(onClick = { vm.setCropPreset(w, h) }) { Text("$w:$h") }
                }
            }
            Text("Speed", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0.5, 1.0, 1.5, 2.0).forEach { speed ->
                    OutlinedButton(onClick = { vm.setSpeed(speed) }) { Text("${speed}×") }
                }
            }
            Text("Audio", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(-6f, 0f, 6f).forEach { gain -> OutlinedButton(onClick = { vm.setClipGain(gain) }) { Text("${gain.roundToInt()} dB") } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { vm.setFades(1_000_000L.coerceAtMost(clip.timelineDurationUs), clip.fadeOutUs) }) { Text("Fade In 1s") }
                OutlinedButton(onClick = { vm.setFades(clip.fadeInUs, 1_000_000L.coerceAtMost(clip.timelineDurationUs)) }) { Text("Fade Out 1s") }
                OutlinedButton(onClick = { vm.setFades(0L, 0L) }) { Text("Clear Fades") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = vm::splitSelected) { Text("Split") }
                OutlinedButton(onClick = vm::duplicateSelected) { Text("Duplicate") }
                OutlinedButton(onClick = vm::deleteSelected) { Text("Delete") }
                OutlinedButton(onClick = vm::addOpacityKeyframe) { Text("◇ Opacity Keyframe") }
            }
        }
    }
}

@Composable
private fun TrackLane(
    track: TimelineTrack,
    clips: List<TimelineClip>,
    selectedClipId: String?,
    pixelsPerSecond: Float,
    keyframes: List<com.videoflow.app.domain.editor.Keyframe>,
    waveforms: Map<String, FloatArray>,
    onZoom: (Float) -> Unit,
    onSelect: (String?) -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    onLock: () -> Unit,
    onVisible: () -> Unit,
    onGain: (Float) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${track.name} • ${track.type.name}", style = MaterialTheme.typography.titleSmall)
                Text("${track.gainDb.roundToInt()} dB", style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onMute) { Text(if (track.muted) "Muted" else "Mute") }
                OutlinedButton(onClick = onSolo) { Text(if (track.solo) "Solo On" else "Solo") }
                OutlinedButton(onClick = onLock) { Text(if (track.locked) "Locked" else "Lock") }
                if (track.type != TrackType.AUDIO) OutlinedButton(onClick = onVisible) { Text(if (track.visible) "Visible" else "Hidden") }
                OutlinedButton(onClick = { onGain((track.gainDb - 3f).coerceAtLeast(-60f)) }) { Text("Gain −") }
                OutlinedButton(onClick = { onGain((track.gainDb + 3f).coerceAtMost(24f)) }) { Text("Gain +") }
            }

            val scroll = rememberScrollState()
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll)
                    .pointerInput(track.id) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom.isFinite() && zoom > 0f) onZoom(zoom)
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (clips.isEmpty()) {
                    Box(Modifier.width(320.dp).height(70.dp), contentAlignment = Alignment.CenterStart) {
                        Text("Empty track", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    var cursorUs = 0L
                    clips.sortedBy { it.timelineStartUs }.forEach { clip ->
                        val gapUs = (clip.timelineStartUs - cursorUs).coerceAtLeast(0L)
                        if (gapUs > 0) Spacer(Modifier.width(timeWidthDp(gapUs, pixelsPerSecond)))
                        val width = timeWidthDp(clip.timelineDurationUs, pixelsPerSecond).coerceAtLeast(72.dp)
                        Card(
                            onClick = { onSelect(clip.id) },
                            border = if (clip.id == selectedClipId) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.width(width).height(86.dp)
                        ) {
                            Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(if (clip.id == selectedClipId) "Selected" else "Clip", style = MaterialTheme.typography.labelLarge)
                                Text("${formatDurationUs(clip.timelineStartUs)} • ${formatDurationUs(clip.timelineDurationUs)}", style = MaterialTheme.typography.bodySmall)
                                Text("${clip.speed}× • ${clip.gainDb.roundToInt()} dB", style = MaterialTheme.typography.bodySmall)
                                val frameCount = keyframes.count { it.ownerId == clip.id }
                                if (frameCount > 0) Text("◆ $frameCount keyframe${if (frameCount == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
                                waveforms[clip.assetId]?.let { Text("Waveform ${it.size} peaks", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        cursorUs = maxOf(cursorUs, clip.timelineEndUs)
                    }
                }
            }
        }
    }
}

private fun timeWidthDp(durationUs: Long, pixelsPerSecond: Float) =
    ((durationUs.toDouble() / 1_000_000.0) * pixelsPerSecond.toDouble()).toFloat().coerceAtMost(50_000f).dp

private data class EvaluatedTransform(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
)
