package com.videoflow.app.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.videoflow.app.domain.editor.AudioMath
import com.videoflow.app.domain.editor.Keyframe
import com.videoflow.app.domain.editor.KeyframeEvaluator
import com.videoflow.app.domain.editor.KeyframeInterpolation
import com.videoflow.app.domain.editor.KeyframeProperty
import com.videoflow.app.domain.editor.ProxyQuality
import com.videoflow.app.domain.editor.ProxyStatus
import com.videoflow.app.domain.editor.TextOverlay
import com.videoflow.app.domain.editor.ImageOverlay
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineEngine
import com.videoflow.app.domain.editor.TimelineTrack
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.ui.BoundedImagePreview
import com.videoflow.app.ui.CachedThumbnailPreview
import com.videoflow.app.ui.EditorViewModel
import com.videoflow.app.ui.NativeAudioPreview
import com.videoflow.app.ui.NativeVideoPlayer
import com.videoflow.app.ui.OverlayAdvancedViewModel
import com.videoflow.app.ui.TrackLifecycleViewModel
import com.videoflow.app.ui.WaveformPreview
import com.videoflow.app.util.formatBytes
import com.videoflow.app.util.formatDurationUs
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(id: String, onBack: () -> Unit, vm: EditorViewModel) {
    val overlayVm: OverlayAdvancedViewModel = hiltViewModel()
    val trackVm: TrackLifecycleViewModel = hiltViewModel()
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
    val thumbnails by vm.thumbnails.collectAsState()
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var pixelsPerSecond by rememberSaveable { mutableFloatStateOf(72f) }
    var pendingDeleteTrackId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(id) { vm.load(id) }
    LaunchedEffect(project?.mediaAssets?.map { it.id }) {
        project?.mediaAssets.orEmpty()
            .filter { it.mimeType?.startsWith("audio/") == true || it.audioTrackCount > 0 }
            .take(12)
            .forEach { vm.generateWaveform(it.id) }
    }

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
    val allKeyframes = timeline?.keyframes.orEmpty()
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
    val activeVideoFrames = activeVideoClip?.let { clip -> allKeyframes.filter { it.ownerId == clip.id } }.orEmpty()
    val activeSourcePositionMs = activeVideoClip?.let { clip ->
        ((clip.sourceStartUs + activeLocalUs * clip.speed) / 1000.0).toLong()
    } ?: 0L
    val evaluatedVideoGainDb = activeVideoClip?.let { clip ->
        KeyframeEvaluator.evaluate(
            clip.gainDb,
            activeLocalUs,
            activeVideoFrames.filter { it.property == KeyframeProperty.AUDIO_GAIN }
        )
    } ?: 0f
    val videoVolume = if (activeVideoClip != null && activeVideoTrack != null && activeVideoTrack.id in effectiveAudioTrackIds) {
        val gain = AudioMath.dbToLinear(evaluatedVideoGainDb + activeVideoTrack.gainDb)
        val fade = AudioMath.fadeGain(activeLocalUs, activeVideoClip.timelineDurationUs, activeVideoClip.fadeInUs, activeVideoClip.fadeOutUs)
        (gain * fade).coerceIn(0f, 1f)
    } else 0f

    val evalTransform = activeVideoClip?.let { clip ->
        fun value(property: KeyframeProperty, base: Float): Float =
            KeyframeEvaluator.evaluate(base, activeLocalUs, activeVideoFrames.filter { it.property == property })
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
                            val crop = activeVideoClip?.transform?.crop
                            val cropWidth = (crop?.right?.minus(crop.left) ?: 1f).coerceAtLeast(0.01f)
                            val cropHeight = (crop?.bottom?.minus(crop.top) ?: 1f).coerceAtLeast(0.01f)
                            val cropCenterX = ((crop?.left ?: 0f) + (crop?.right ?: 1f)) / 2f
                            val cropCenterY = ((crop?.top ?: 0f) + (crop?.bottom ?: 1f)) / 2f
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
                                    .graphicsLayer {
                                        scaleX = (t.scaleX / cropWidth) * if (t.flipHorizontal) -1f else 1f
                                        scaleY = (t.scaleY / cropHeight) * if (t.flipVertical) -1f else 1f
                                        rotationZ = t.rotation
                                        alpha = t.opacity.coerceIn(0f, 1f)
                                        translationX = (0.5f - cropCenterX) * size.width / cropWidth
                                        translationY = (0.5f - cropCenterY) * size.height / cropHeight
                                    }
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
                                val localUs = (playheadUs - overlay.timelineStartUs).coerceAtLeast(0L)
                                val frames = allKeyframes.filter { it.ownerId == overlay.id }
                                fun value(property: KeyframeProperty, base: Float) =
                                    KeyframeEvaluator.evaluate(base, localUs, frames.filter { it.property == property })
                                val x = value(KeyframeProperty.POSITION_X, overlay.transform.x)
                                val y = value(KeyframeProperty.POSITION_Y, overlay.transform.y)
                                BoundedImagePreview(
                                    sourceUri = asset.sourceUri,
                                    modifier = Modifier
                                        .widthIn(max = 220.dp)
                                        .offset(x = ((x - 0.5f) * 160f).dp, y = ((y - 0.5f) * 100f).dp)
                                        .graphicsLayer(
                                            scaleX = value(KeyframeProperty.SCALE_X, overlay.transform.scaleX),
                                            scaleY = value(KeyframeProperty.SCALE_Y, overlay.transform.scaleY),
                                            rotationZ = value(KeyframeProperty.ROTATION, overlay.transform.rotationDegrees),
                                            alpha = value(KeyframeProperty.OPACITY, overlay.transform.opacity).coerceIn(0f, 1f)
                                        )
                                )
                            }
                        }

                        activeText.forEach { overlay ->
                            val localUs = (playheadUs - overlay.timelineStartUs).coerceAtLeast(0L)
                            val frames = allKeyframes.filter { it.ownerId == overlay.id }
                            fun value(property: KeyframeProperty, base: Float) =
                                KeyframeEvaluator.evaluate(base, localUs, frames.filter { it.property == property })
                            val x = value(KeyframeProperty.POSITION_X, overlay.transform.x)
                            val y = value(KeyframeProperty.POSITION_Y, overlay.transform.y)
                            Text(
                                text = overlay.content,
                                color = Color(overlay.colorArgb.toInt()).copy(
                                    alpha = value(KeyframeProperty.OPACITY, overlay.opacity).coerceIn(0f, 1f)
                                ),
                                fontSize = overlay.fontSizeSp.sp,
                                fontWeight = FontWeight(overlay.fontWeight.coerceIn(100, 900)),
                                fontStyle = if (overlay.italic) FontStyle.Italic else FontStyle.Normal,
                                textAlign = when (overlay.alignment) {
                                    "START" -> TextAlign.Start
                                    "END" -> TextAlign.End
                                    else -> TextAlign.Center
                                },
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .offset(x = ((x - 0.5f) * 160f).dp, y = ((y - 0.5f) * 100f).dp)
                                    .graphicsLayer(
                                        scaleX = value(KeyframeProperty.SCALE_X, overlay.transform.scaleX),
                                        scaleY = value(KeyframeProperty.SCALE_Y, overlay.transform.scaleY),
                                        rotationZ = value(KeyframeProperty.ROTATION, overlay.transform.rotationDegrees)
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
                    val frames = allKeyframes.filter { it.ownerId == clip.id && it.property == KeyframeProperty.AUDIO_GAIN }
                    val clipGainDb = KeyframeEvaluator.evaluate(clip.gainDb, localUs, frames)
                    val gain = AudioMath.dbToLinear(clipGainDb + track.gainDb)
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
                Text("Original media remains referenced; proxies, thumbnails and waveforms are bounded derived editor data.", style = MaterialTheme.typography.bodySmall)
            }
            items(project?.mediaAssets.orEmpty(), key = { "asset-${it.id}" }) { asset ->
                val proxy = editor?.proxies?.firstOrNull { it.assetId == asset.id }
                val peaks = waveforms[asset.id]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CachedThumbnailPreview(
                                path = thumbnails[asset.id],
                                modifier = Modifier.width(96.dp).height(58.dp)
                            )
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
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (proxyProgress.assetId == asset.id && proxyProgress.status == ProxyStatus.GENERATING) {
                                    OutlinedButton(onClick = { vm.cancelProxy(asset.id) }) { Text("Cancel Proxy") }
                                    Text(proxyProgress.percent?.let { "$it%" } ?: "Working…", modifier = Modifier.padding(top = 12.dp))
                                } else {
                                    OutlinedButton(onClick = { vm.generateProxy(asset.id, ProxyQuality.PERFORMANCE) }, enabled = asset.sourceStatus.name == "AVAILABLE") { Text("Proxy 540p") }
                                    OutlinedButton(onClick = { vm.generateProxy(asset.id, ProxyQuality.BALANCED) }, enabled = asset.sourceStatus.name == "AVAILABLE") { Text("Proxy 720p") }
                                    OutlinedButton(onClick = { vm.generateProxy(asset.id, ProxyQuality.HIGH) }, enabled = asset.sourceStatus.name == "AVAILABLE") { Text("Proxy 1080p") }
                                    if (proxy != null) OutlinedButton(onClick = { vm.deleteProxy(asset.id) }) { Text("Delete Proxy") }
                                }
                            }
                        }
                        if (asset.mimeType?.startsWith("audio/") == true || asset.audioTrackCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { vm.generateWaveform(asset.id) }) { Text("Waveform") }
                                Spacer(Modifier.width(8.dp))
                                Text(if (peaks == null) "Cache pending" else "${peaks.size} bounded peaks", style = MaterialTheme.typography.bodySmall)
                            }
                            WaveformPreview(
                                peaks = peaks,
                                modifier = Modifier.fillMaxWidth().height(34.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Timeline", style = MaterialTheme.typography.titleMedium)
                        Text("${pixelsPerSecond.roundToInt()} dp/s • clip drag snaps to nearby cuts", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { pixelsPerSecond = (pixelsPerSecond / 1.35f).coerceAtLeast(12f) }) { Text("− Zoom") }
                        TextButton(onClick = { pixelsPerSecond = (pixelsPerSecond * 1.35f).coerceAtMost(240f) }) { Text("+ Zoom") }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .pointerInput("timeline-pinch") {
                            detectTransformGestures { _, _, zoom, _ ->
                                if (zoom.isFinite() && zoom > 0f) {
                                    pixelsPerSecond = (pixelsPerSecond * zoom).coerceIn(12f, 240f)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Pinch here to zoom timeline", style = MaterialTheme.typography.bodySmall)
                }
                TimelineRuler(durationUs = durationUs, pixelsPerSecond = pixelsPerSecond)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { vm.createTrack(TrackType.VIDEO) }) { Text("+ Video") }
                    TextButton(onClick = { vm.createTrack(TrackType.AUDIO) }) { Text("+ Audio") }
                    TextButton(onClick = { vm.createTrack(TrackType.OVERLAY) }) { Text("+ Overlay") }
                    TextButton(onClick = vm::addText) { Text("+ Text") }
                }
            }

            items(tracks, key = { "track-${it.id}" }) { track ->
                TrackLane(
                    track = track,
                    clips = clips.filter { it.trackId == track.id },
                    selectedClipId = selectedClipId,
                    pixelsPerSecond = pixelsPerSecond,
                    keyframes = allKeyframes,
                    waveforms = waveforms,
                    thumbnails = thumbnails,
                    onSelect = vm::selectClip,
                    onMoveClip = { clipId, deltaUs ->
                        vm.selectClip(clipId)
                        vm.moveSelectedSnapped(deltaUs, pixelsPerSecond.toDouble())
                    },
                    onMute = { vm.toggleTrackMute(track.id, !track.muted) },
                    onSolo = { vm.toggleTrackSolo(track.id, !track.solo) },
                    onLock = { vm.toggleTrackLock(track.id, !track.locked) },
                    onVisible = { vm.toggleTrackVisible(track.id, !track.visible) },
                    onGain = { vm.setTrackGain(track.id, it) },
                    onRename = { vm.renameTrack(track.id, it) },
                    onDeleteRequest = { pendingDeleteTrackId = track.id }
                )
            }

            selectedClip?.let { clip ->
                item {
                    ClipInspector(clip = clip, tracks = tracks, pixelsPerSecond = pixelsPerSecond, vm = vm)
                }
            }

            item { Text("Text & Image Overlays", style = MaterialTheme.typography.titleMedium) }
            items(timeline?.textOverlays.orEmpty(), key = { "text-${it.id}" }) { overlay ->
                TextOverlayEditor(
                    projectId = id,
                    overlay = overlay,
                    playheadUs = playheadUs,
                    editorVm = vm,
                    overlayVm = overlayVm,
                    refresh = { vm.load(id) }
                )
            }
            items(timeline?.imageOverlays.orEmpty(), key = { "image-${it.id}" }) { overlay ->
                ImageOverlayEditor(
                    projectId = id,
                    overlay = overlay,
                    playheadUs = playheadUs,
                    editorVm = vm,
                    overlayVm = overlayVm,
                    refresh = { vm.load(id) }
                )
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
                    "Step 2 keeps edits metadata-only and originals untouched. Final encoding/export remains Step 3.",
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

    pendingDeleteTrackId?.let { trackId ->
        val track = tracks.firstOrNull { it.id == trackId }
        if (track != null) {
            val itemCount = clips.count { it.trackId == trackId } +
                timeline?.textOverlays.orEmpty().count { it.trackId == trackId } +
                timeline?.imageOverlays.orEmpty().count { it.trackId == trackId }
            AlertDialog(
                onDismissRequest = { pendingDeleteTrackId = null },
                title = { Text("Delete ${track.name}?") },
                text = {
                    Text(
                        if (itemCount > 0) "$itemCount timeline item(s) will be deleted with this track. The operation is undoable."
                        else "This empty track will be deleted. The operation is undoable."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDeleteTrackId = null
                            trackVm.deleteConfirmed(id, trackId) { vm.load(id) }
                        },
                        enabled = !track.locked
                    ) { Text("Delete Track") }
                },
                dismissButton = { TextButton(onClick = { pendingDeleteTrackId = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun ClipInspector(
    clip: TimelineClip,
    tracks: List<TimelineTrack>,
    pixelsPerSecond: Float,
    vm: EditorViewModel
) {
    val currentTrack = tracks.firstOrNull { it.id == clip.trackId }
    val compatibleTracks = tracks.filter { it.type == currentTrack?.type && it.id != clip.trackId }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Selected Clip Inspector", style = MaterialTheme.typography.titleMedium)
            Text(
                "Position (${"%.2f".format(clip.transform.x)}, ${"%.2f".format(clip.transform.y)}) • Scale ${"%.2f".format(clip.transform.scaleX)} • Rotation ${clip.transform.rotationDegrees.roundToInt()}° • Opacity ${"%.2f".format(clip.opacity)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Timeline editing", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { vm.moveSelectedSnapped(-500_000L, pixelsPerSecond.toDouble()) }) { Text("Move −0.5s") }
                OutlinedButton(onClick = { vm.moveSelectedSnapped(500_000L, pixelsPerSecond.toDouble()) }) { Text("Move +0.5s") }
                OutlinedButton(onClick = { vm.trimSelectedStart(-250_000L) }) { Text("Extend In") }
                OutlinedButton(onClick = { vm.trimSelectedStart(250_000L) }) { Text("Trim In") }
                OutlinedButton(onClick = { vm.trimSelectedEnd(-250_000L) }) { Text("Trim Out") }
                OutlinedButton(onClick = { vm.trimSelectedEnd(250_000L) }) { Text("Extend Out") }
            }
            if (compatibleTracks.isNotEmpty()) {
                Text("Move to compatible track", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    compatibleTracks.forEach { track ->
                        OutlinedButton(onClick = { vm.moveSelectedToTrack(track.id) }, enabled = !track.locked) { Text(track.name) }
                    }
                }
            }
            Text("Transform", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { vm.moveTransform(-0.05f, 0f) }) { Text("←") }
                OutlinedButton(onClick = { vm.moveTransform(0f, -0.05f) }) { Text("↑") }
                OutlinedButton(onClick = { vm.moveTransform(0f, 0.05f) }) { Text("↓") }
                OutlinedButton(onClick = { vm.moveTransform(0.05f, 0f) }) { Text("→") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { vm.setScale((clip.transform.scaleX - 0.1f).coerceAtLeast(0.05f)) }) { Text("Scale −") }
                OutlinedButton(onClick = { vm.setScale(clip.transform.scaleX + 0.1f) }) { Text("Scale +") }
                OutlinedButton(onClick = vm::rotateSelected90) { Text("Rotate 90°") }
                OutlinedButton(onClick = vm::toggleFlipHorizontal) { Text("Flip H") }
                OutlinedButton(onClick = vm::toggleFlipVertical) { Text("Flip V") }
                OutlinedButton(onClick = vm::resetTransform) { Text("Reset") }
            }
            Text("Opacity")
            Slider(value = clip.opacity, onValueChange = vm::setOpacity, valueRange = 0f..1f)
            Text("Crop presets", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(16 to 9, 9 to 16, 4 to 3, 3 to 2, 1 to 1, 4 to 5).forEach { (w, h) ->
                    OutlinedButton(onClick = { vm.setCropPreset(w, h) }) { Text("$w:$h") }
                }
            }
            Text("Speed", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0.25, 0.5, 1.0, 1.5, 2.0, 4.0).forEach { speed ->
                    OutlinedButton(onClick = { vm.setSpeed(speed) }) { Text("${speed}×") }
                }
            }
            Text("Clip audio gain: ${clip.gainDb.roundToInt()} dB", style = MaterialTheme.typography.labelLarge)
            Slider(value = clip.gainDb.coerceIn(-60f, 24f), onValueChange = vm::setClipGain, valueRange = -60f..24f)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0L, 500_000L, 1_000_000L, 2_000_000L).forEach { fade ->
                    val bounded = fade.coerceAtMost(clip.timelineDurationUs)
                    OutlinedButton(onClick = { vm.setFades(bounded, clip.fadeOutUs.coerceAtMost(clip.timelineDurationUs)) }) { Text("In ${fade / 1_000_000f}s") }
                    OutlinedButton(onClick = { vm.setFades(clip.fadeInUs.coerceAtMost(clip.timelineDurationUs), bounded) }) { Text("Out ${fade / 1_000_000f}s") }
                }
            }
            Text("Generic keyframes", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyframeProperty.entries.forEach { property ->
                    OutlinedButton(onClick = { vm.addKeyframe(property, KeyframeInterpolation.LINEAR) }) {
                        Text("◇ ${property.shortLabel()}")
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(KeyframeProperty.POSITION_X, KeyframeProperty.ROTATION, KeyframeProperty.OPACITY).forEach { property ->
                    OutlinedButton(onClick = { vm.addKeyframe(property, KeyframeInterpolation.HOLD) }) {
                        Text("Hold ${property.shortLabel()}")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = vm::splitSelected) { Text("Split") }
                OutlinedButton(onClick = vm::duplicateSelected) { Text("Duplicate") }
                OutlinedButton(onClick = vm::deleteSelected) { Text("Delete") }
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
    keyframes: List<Keyframe>,
    waveforms: Map<String, FloatArray>,
    thumbnails: Map<String, String>,
    onSelect: (String?) -> Unit,
    onMoveClip: (String, Long) -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    onLock: () -> Unit,
    onVisible: () -> Unit,
    onGain: (Float) -> Unit,
    onRename: (String) -> Unit,
    onDeleteRequest: () -> Unit
) {
    var nameDraft by remember(track.id, track.name) { mutableStateOf(track.name) }
    val density = LocalDensity.current
    val laneItems = remember(clips) {
        buildList<TimelineLaneItem> {
            var cursorUs = 0L
            clips.sortedBy { it.timelineStartUs }.forEach { clip ->
                val gapUs = (clip.timelineStartUs - cursorUs).coerceAtLeast(0L)
                if (gapUs > 0) add(TimelineLaneItem.Gap("gap-${clip.id}", gapUs))
                add(TimelineLaneItem.ClipItem(clip))
                cursorUs = maxOf(cursorUs, clip.timelineEndUs)
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${track.name} • ${track.type.name}", style = MaterialTheme.typography.titleSmall)
                    Text("${track.gainDb.roundToInt()} dB", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onDeleteRequest, enabled = !track.locked) { Text("Delete Track") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(48) },
                    label = { Text("Track name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { onRename(nameDraft) }, enabled = nameDraft.isNotBlank() && nameDraft != track.name) { Text("Rename") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onMute) { Text(if (track.muted) "Muted" else "Mute") }
                OutlinedButton(onClick = onSolo) { Text(if (track.solo) "Solo On" else "Solo") }
                OutlinedButton(onClick = onLock) { Text(if (track.locked) "Locked" else "Lock") }
                if (track.type != TrackType.AUDIO) OutlinedButton(onClick = onVisible) { Text(if (track.visible) "Visible" else "Hidden") }
            }
            Text("Track gain ${track.gainDb.roundToInt()} dB", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = track.gainDb.coerceIn(-60f, 24f),
                onValueChange = onGain,
                valueRange = -60f..24f,
                enabled = !track.locked
            )

            if (laneItems.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.CenterStart) {
                    Text("Empty track", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(laneItems, key = { it.key }) { laneItem ->
                        when (laneItem) {
                            is TimelineLaneItem.Gap -> Spacer(Modifier.width(timeWidthDp(laneItem.durationUs, pixelsPerSecond)).height(96.dp))
                            is TimelineLaneItem.ClipItem -> {
                                val clip = laneItem.clip
                                var dragPx by remember(clip.id) { mutableFloatStateOf(0f) }
                                val width = timeWidthDp(clip.timelineDurationUs, pixelsPerSecond).coerceAtLeast(84.dp)
                                val dragModifier = if (track.locked) Modifier else Modifier.pointerInput(clip.id, pixelsPerSecond) {
                                    detectHorizontalDragGestures(
                                        onDragCancel = { dragPx = 0f },
                                        onDragEnd = {
                                            val deltaDp = with(density) { dragPx.toDp().value }
                                            val deltaUs = ((deltaDp / pixelsPerSecond) * 1_000_000f).roundToLong()
                                            dragPx = 0f
                                            if (deltaUs != 0L) onMoveClip(clip.id, deltaUs)
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        dragPx += dragAmount
                                    }
                                }
                                Card(
                                    onClick = { onSelect(clip.id) },
                                    border = if (clip.id == selectedClipId) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .width(width)
                                        .height(110.dp)
                                        .graphicsLayer { translationX = dragPx }
                                        .then(dragModifier)
                                ) {
                                    Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                            CachedThumbnailPreview(thumbnails[clip.assetId], Modifier.width(48.dp).height(30.dp))
                                            Column {
                                                Text(if (clip.id == selectedClipId) "Selected" else "Clip", style = MaterialTheme.typography.labelLarge)
                                                Text("${formatDurationUs(clip.timelineStartUs)} • ${formatDurationUs(clip.timelineDurationUs)}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        Text("${clip.speed}× • ${clip.gainDb.roundToInt()} dB", style = MaterialTheme.typography.bodySmall)
                                        val frames = keyframes.filter { it.ownerId == clip.id }
                                        KeyframeIndicators(frames, clip.timelineDurationUs, Modifier.fillMaxWidth().height(9.dp))
                                        WaveformPreview(
                                            peaks = waveforms[clip.assetId],
                                            modifier = Modifier.fillMaxWidth().height(24.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextOverlayEditor(
    projectId: String,
    overlay: TextOverlay,
    playheadUs: Long,
    editorVm: EditorViewModel,
    overlayVm: OverlayAdvancedViewModel,
    refresh: () -> Unit
) {
    var draft by remember(overlay.id, overlay.content) { mutableStateOf(overlay.content) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Text overlay • ${formatDurationUs(overlay.timelineStartUs)} → ${formatDurationUs(overlay.timelineEndUs)}", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(4096) },
                label = { Text("Content") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { editorVm.updateTextContent(overlay.id, draft) }) { Text("Save Text") }
                OutlinedButton(onClick = { editorVm.adjustTextSize(overlay.id, -2f) }) { Text("Size −") }
                OutlinedButton(onClick = { editorVm.adjustTextSize(overlay.id, 2f) }) { Text("Size +") }
                OutlinedButton(onClick = { editorVm.toggleTextBold(overlay.id) }) { Text("Bold") }
                OutlinedButton(onClick = { editorVm.toggleTextItalic(overlay.id) }) { Text("Italic") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { overlayVm.setTextColor(projectId, overlay.id, 0xFFFFFFFFL, refresh) }) { Text("White") }
                OutlinedButton(onClick = { overlayVm.setTextColor(projectId, overlay.id, 0xFFFFFF00L, refresh) }) { Text("Yellow") }
                OutlinedButton(onClick = { overlayVm.setTextColor(projectId, overlay.id, 0xFFFF5252L, refresh) }) { Text("Red") }
                listOf("START", "CENTER", "END").forEach { alignment ->
                    OutlinedButton(onClick = { overlayVm.setTextAlignment(projectId, overlay.id, alignment, refresh) }) { Text(alignment.lowercase().replaceFirstChar { it.uppercase() }) }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { editorVm.moveText(overlay.id, -0.05f, 0f) }) { Text("←") }
                OutlinedButton(onClick = { editorVm.moveText(overlay.id, 0f, -0.05f) }) { Text("↑") }
                OutlinedButton(onClick = { editorVm.moveText(overlay.id, 0f, 0.05f) }) { Text("↓") }
                OutlinedButton(onClick = { editorVm.moveText(overlay.id, 0.05f, 0f) }) { Text("→") }
                OutlinedButton(onClick = { overlayVm.setTextScale(projectId, overlay.id, (overlay.transform.scaleX - 0.1f).coerceAtLeast(0.05f), refresh) }) { Text("Scale −") }
                OutlinedButton(onClick = { overlayVm.setTextScale(projectId, overlay.id, overlay.transform.scaleX + 0.1f, refresh) }) { Text("Scale +") }
                OutlinedButton(onClick = { editorVm.rotateText(overlay.id) }) { Text("Rotate") }
            }
            Text("Opacity")
            Slider(value = overlay.opacity, onValueChange = { editorVm.setTextOpacity(overlay.id, it) }, valueRange = 0f..1f)
            Text("Timeline", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { overlayVm.moveTextTimeline(projectId, overlay.id, -500_000L, refresh) }) { Text("Move −0.5s") }
                OutlinedButton(onClick = { overlayVm.moveTextTimeline(projectId, overlay.id, 500_000L, refresh) }) { Text("Move +0.5s") }
                OutlinedButton(onClick = { overlayVm.trimTextStart(projectId, overlay.id, 250_000L, refresh) }) { Text("Trim In") }
                OutlinedButton(onClick = { overlayVm.trimTextStart(projectId, overlay.id, -250_000L, refresh) }) { Text("Extend In") }
                OutlinedButton(onClick = { overlayVm.trimTextEnd(projectId, overlay.id, -250_000L, refresh) }) { Text("Trim Out") }
                OutlinedButton(onClick = { overlayVm.trimTextEnd(projectId, overlay.id, 250_000L, refresh) }) { Text("Extend Out") }
            }
            Text("Keyframes", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(KeyframeProperty.POSITION_X, KeyframeProperty.POSITION_Y, KeyframeProperty.SCALE_X, KeyframeProperty.ROTATION, KeyframeProperty.OPACITY).forEach { property ->
                    OutlinedButton(onClick = {
                        overlayVm.addTextKeyframe(projectId, overlay.id, playheadUs, property, KeyframeInterpolation.LINEAR, refresh)
                    }) { Text("◇ ${property.shortLabel()}") }
                }
                OutlinedButton(onClick = {
                    overlayVm.addTextKeyframe(projectId, overlay.id, playheadUs, KeyframeProperty.OPACITY, KeyframeInterpolation.HOLD, refresh)
                }) { Text("Hold Opacity") }
            }
            TextButton(onClick = { editorVm.deleteText(overlay.id) }) { Text("Delete Text") }
        }
    }
}

@Composable
private fun ImageOverlayEditor(
    projectId: String,
    overlay: ImageOverlay,
    playheadUs: Long,
    editorVm: EditorViewModel,
    overlayVm: OverlayAdvancedViewModel,
    refresh: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Image overlay • ${formatDurationUs(overlay.timelineStartUs)} → ${formatDurationUs(overlay.timelineEndUs)}", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { editorVm.moveImage(overlay.id, -0.05f, 0f) }) { Text("←") }
                OutlinedButton(onClick = { editorVm.moveImage(overlay.id, 0f, -0.05f) }) { Text("↑") }
                OutlinedButton(onClick = { editorVm.moveImage(overlay.id, 0f, 0.05f) }) { Text("↓") }
                OutlinedButton(onClick = { editorVm.moveImage(overlay.id, 0.05f, 0f) }) { Text("→") }
                OutlinedButton(onClick = { editorVm.adjustImageScale(overlay.id, -0.1f) }) { Text("Scale −") }
                OutlinedButton(onClick = { editorVm.adjustImageScale(overlay.id, 0.1f) }) { Text("Scale +") }
                OutlinedButton(onClick = { editorVm.rotateImage(overlay.id) }) { Text("Rotate") }
            }
            Text("Opacity")
            Slider(value = overlay.transform.opacity, onValueChange = { editorVm.setImageOpacity(overlay.id, it) }, valueRange = 0f..1f)
            Text("Timeline", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { overlayVm.moveImageTimeline(projectId, overlay.id, -500_000L, refresh) }) { Text("Move −0.5s") }
                OutlinedButton(onClick = { overlayVm.moveImageTimeline(projectId, overlay.id, 500_000L, refresh) }) { Text("Move +0.5s") }
                OutlinedButton(onClick = { overlayVm.trimImageStart(projectId, overlay.id, 250_000L, refresh) }) { Text("Trim In") }
                OutlinedButton(onClick = { overlayVm.trimImageStart(projectId, overlay.id, -250_000L, refresh) }) { Text("Extend In") }
                OutlinedButton(onClick = { overlayVm.trimImageEnd(projectId, overlay.id, -250_000L, refresh) }) { Text("Trim Out") }
                OutlinedButton(onClick = { overlayVm.trimImageEnd(projectId, overlay.id, 250_000L, refresh) }) { Text("Extend Out") }
            }
            Text("Keyframes", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(KeyframeProperty.POSITION_X, KeyframeProperty.POSITION_Y, KeyframeProperty.SCALE_X, KeyframeProperty.ROTATION, KeyframeProperty.OPACITY).forEach { property ->
                    OutlinedButton(onClick = {
                        overlayVm.addImageKeyframe(projectId, overlay.id, playheadUs, property, KeyframeInterpolation.LINEAR, refresh)
                    }) { Text("◇ ${property.shortLabel()}") }
                }
                OutlinedButton(onClick = {
                    overlayVm.addImageKeyframe(projectId, overlay.id, playheadUs, KeyframeProperty.OPACITY, KeyframeInterpolation.HOLD, refresh)
                }) { Text("Hold Opacity") }
            }
            TextButton(onClick = { editorVm.deleteImage(overlay.id) }) { Text("Delete Image") }
        }
    }
}

@Composable
private fun TimelineRuler(durationUs: Long, pixelsPerSecond: Float) {
    val tickCount = ((durationUs / 1_000_000L) + 2L).coerceIn(2L, 1_000_000L).toInt()
    LazyRow(Modifier.fillMaxWidth().height(30.dp)) {
        items(count = tickCount, key = { it }) { second ->
            Box(Modifier.width(pixelsPerSecond.dp).height(30.dp)) {
                Text("${second}s", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun KeyframeIndicators(frames: List<Keyframe>, durationUs: Long, modifier: Modifier = Modifier) {
    if (frames.isEmpty() || durationUs <= 0L) return
    Canvas(modifier) {
        frames.forEach { frame ->
            val fraction = (frame.timeUs.toDouble() / durationUs.toDouble()).coerceIn(0.0, 1.0).toFloat()
            val x = fraction * size.width
            val radius = (size.height / 3f).coerceAtLeast(2f)
            drawCircle(
                color = if (frame.interpolation == KeyframeInterpolation.HOLD) Color.Magenta else Color.Cyan,
                radius = radius,
                center = Offset(x, size.height / 2f)
            )
        }
    }
}

private fun timeWidthDp(durationUs: Long, pixelsPerSecond: Float) =
    ((durationUs.toDouble() / 1_000_000.0) * pixelsPerSecond.toDouble()).toFloat().coerceAtMost(50_000f).dp

private fun KeyframeProperty.shortLabel(): String = when (this) {
    KeyframeProperty.POSITION_X -> "X"
    KeyframeProperty.POSITION_Y -> "Y"
    KeyframeProperty.SCALE_X -> "Scale X"
    KeyframeProperty.SCALE_Y -> "Scale Y"
    KeyframeProperty.ROTATION -> "Rotate"
    KeyframeProperty.OPACITY -> "Opacity"
    KeyframeProperty.AUDIO_GAIN -> "Gain"
}

private sealed interface TimelineLaneItem {
    val key: String

    data class Gap(override val key: String, val durationUs: Long) : TimelineLaneItem
    data class ClipItem(val clip: TimelineClip) : TimelineLaneItem {
        override val key: String = "clip-${clip.id}"
    }
}

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
