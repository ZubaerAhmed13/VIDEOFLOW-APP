package com.videoflow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineTrack
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.ui.EditorViewModel
import com.videoflow.app.ui.NativeVideoPlayer
import com.videoflow.app.util.formatDurationUs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(id: String, onBack: () -> Unit, vm: EditorViewModel) {
    val project by vm.project.collectAsState()
    val editor by vm.editor.collectAsState()
    val playheadUs by vm.playheadUs.collectAsState()
    val selectedClipId by vm.selectedClipId.collectAsState()
    val message by vm.message.collectAsState()
    val saving by vm.saving.collectAsState()

    LaunchedEffect(id) { vm.load(id) }

    val timeline = editor?.timeline
    val durationUs = maxOf(1L, timeline?.durationUs ?: 1L)
    val videoTracks = timeline?.tracks.orEmpty().filter { it.type == TrackType.VIDEO && it.visible }.map { it.id }.toSet()
    val activeVideoClip = timeline?.clips.orEmpty()
        .filter { it.enabled && it.trackId in videoTracks && playheadUs in it.timelineStartUs until it.timelineEndUs }
        .maxByOrNull { clip -> timeline?.tracks.orEmpty().firstOrNull { it.id == clip.trackId }?.orderIndex ?: -1 }
    val activeAsset = activeVideoClip?.let { clip -> project?.mediaAssets?.firstOrNull { it.id == clip.assetId } }
    val sourcePositionMs = activeVideoClip?.let { clip ->
        val localTimelineUs = (playheadUs - clip.timelineStartUs).coerceAtLeast(0)
        ((clip.sourceStartUs + localTimelineUs * clip.speed) / 1000.0).toLong()
    } ?: 0L

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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
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
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        if (activeAsset?.mimeType?.startsWith("video/") == true) {
                            NativeVideoPlayer(
                                uri = activeAsset.sourceUri,
                                startPositionMs = sourcePositionMs,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                                Text("${editor?.settings?.width ?: 1920}×${editor?.settings?.height ?: 1080} preview • no active video at playhead")
                            }
                        }
                    }
                }
            }

            item {
                Text("Playhead ${formatDurationUs(playheadUs)} / ${formatDurationUs(timeline?.durationUs ?: 0L)}")
                Slider(
                    value = (playheadUs.toDouble() / durationUs.toDouble()).coerceIn(0.0, 1.0).toFloat(),
                    onValueChange = { fraction -> vm.setPlayheadUs((durationUs * fraction.toDouble()).toLong()) },
                    valueRange = 0f..1f
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.setPlayheadUs((playheadUs - 1_000_000).coerceAtLeast(0)) }) { Text("−1s") }
                    OutlinedButton(onClick = { vm.setPlayheadUs((playheadUs + 1_000_000).coerceAtMost(timeline?.durationUs ?: 0L)) }) { Text("+1s") }
                    OutlinedButton(onClick = { vm.setPlayheadUs(0) }) { Text("Start") }
                }
            }

            item {
                Text("Edit Tools", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = vm::splitSelected, enabled = selectedClipId != null) { Text("Split") }
                        OutlinedButton(onClick = vm::duplicateSelected, enabled = selectedClipId != null) { Text("Duplicate") }
                        OutlinedButton(onClick = vm::deleteSelected, enabled = selectedClipId != null) { Text("Delete") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { vm.moveSelected(-1_000_000) }, enabled = selectedClipId != null) { Text("Move −1s") }
                        OutlinedButton(onClick = { vm.moveSelected(1_000_000) }, enabled = selectedClipId != null) { Text("Move +1s") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { vm.trimSelectedStart(500_000) }, enabled = selectedClipId != null) { Text("Trim Start +0.5s") }
                        OutlinedButton(onClick = { vm.trimSelectedEnd(-500_000) }, enabled = selectedClipId != null) { Text("Trim End −0.5s") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = vm::addText) { Text("Add Text") }
                        OutlinedButton(onClick = vm::addOpacityKeyframe, enabled = selectedClipId != null) { Text("Add Opacity Keyframe") }
                    }
                }
            }

            item {
                Text("Project Media Bin", style = MaterialTheme.typography.titleMedium)
                Text("Tap Add to place video/audio at the playhead or create an image overlay.", style = MaterialTheme.typography.bodySmall)
            }
            items(project?.mediaAssets.orEmpty(), key = { "asset-${it.id}" }) { asset ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(asset.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(asset.mimeType ?: "media", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { vm.addAsset(asset.id) }, enabled = asset.sourceStatus.name == "AVAILABLE") { Text("Add") }
                    }
                }
            }

            item {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Timeline", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.createTrack(TrackType.VIDEO) }) { Text("+V") }
                        TextButton(onClick = { vm.createTrack(TrackType.AUDIO) }) { Text("+A") }
                        TextButton(onClick = { vm.createTrack(TrackType.OVERLAY) }) { Text("+O") }
                    }
                }
            }

            items(timeline?.tracks.orEmpty(), key = { "track-${it.id}" }) { track ->
                TrackLane(
                    track = track,
                    clips = timeline?.clips.orEmpty().filter { it.trackId == track.id },
                    selectedClipId = selectedClipId,
                    onSelect = vm::selectClip,
                    onMute = { vm.toggleTrackMute(track.id, !track.muted) },
                    onSolo = { vm.toggleTrackSolo(track.id, !track.solo) },
                    onLock = { vm.toggleTrackLock(track.id, !track.locked) },
                    onVisible = { vm.toggleTrackVisible(track.id, !track.visible) }
                )
            }

            if (!timeline?.textOverlays.isNullOrEmpty()) {
                item { Text("Text Overlays", style = MaterialTheme.typography.titleMedium) }
                items(timeline?.textOverlays.orEmpty(), key = { "text-${it.id}" }) { overlay ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(overlay.content)
                            Text("${formatDurationUs(overlay.timelineStartUs)} → ${formatDurationUs(overlay.timelineEndUs)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                Text(
                    "Step 2 editor uses non-destructive metadata. Final-quality export is intentionally not implemented here; that belongs to Step 3.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    message?.let {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = vm::clearMessage,
            title = { Text("Editor") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text("OK") } }
        )
    }
}

@Composable
private fun TrackLane(
    track: TimelineTrack,
    clips: List<TimelineClip>,
    selectedClipId: String?,
    onSelect: (String?) -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    onLock: () -> Unit,
    onVisible: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${track.name} • ${track.type.name}", style = MaterialTheme.typography.titleSmall)
                Text("${track.gainDb} dB", style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onMute) { Text(if (track.muted) "Muted" else "Mute") }
                OutlinedButton(onClick = onSolo) { Text(if (track.solo) "Solo On" else "Solo") }
                OutlinedButton(onClick = onLock) { Text(if (track.locked) "Locked" else "Lock") }
                if (track.type != TrackType.AUDIO) {
                    OutlinedButton(onClick = onVisible) { Text(if (track.visible) "Visible" else "Hidden") }
                }
            }
            if (clips.isEmpty()) {
                Text("Empty track", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    clips.sortedBy { it.timelineStartUs }.forEach { clip ->
                        Card(
                            onClick = { onSelect(clip.id) },
                            border = if (clip.id == selectedClipId) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.width(180.dp)
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Text(if (clip.id == selectedClipId) "Selected Clip" else "Clip")
                                Text("At ${formatDurationUs(clip.timelineStartUs)}", style = MaterialTheme.typography.bodySmall)
                                Text("Length ${formatDurationUs(clip.timelineDurationUs)}", style = MaterialTheme.typography.bodySmall)
                                Text("${clip.speed}× • ${clip.opacity} opacity", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
