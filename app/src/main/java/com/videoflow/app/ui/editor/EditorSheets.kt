package com.videoflow.app.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videoflow.app.data.db.SnapshotEntity
import com.videoflow.app.data.editor.EditorProject
import com.videoflow.app.domain.editor.ImageOverlay
import com.videoflow.app.domain.editor.KeyframeInterpolation
import com.videoflow.app.domain.editor.KeyframeProperty
import com.videoflow.app.domain.editor.ProxyQuality
import com.videoflow.app.domain.editor.TextOverlay
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineTrack
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.domain.model.MediaAsset
import com.videoflow.app.domain.model.SourceStatus
import com.videoflow.app.domain.model.VideoFlowProject
import com.videoflow.app.ui.CachedThumbnailPreview
import com.videoflow.app.ui.EditorViewModel
import com.videoflow.app.ui.OverlayAdvancedViewModel
import com.videoflow.app.ui.WaveformPreview
import com.videoflow.app.util.formatBytes
import com.videoflow.app.util.formatDurationUs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorPanelHost(
    panel: EditorPanel?,
    projectId: String,
    project: VideoFlowProject?,
    editor: EditorProject?,
    snapshots: List<SnapshotEntity>,
    thumbnails: Map<String, String>,
    waveforms: Map<String, FloatArray>,
    proxyGeneratingAssetId: String?,
    proxyPercent: Int?,
    playheadUs: Long,
    editorVm: EditorViewModel,
    overlayVm: OverlayAdvancedViewModel,
    onDismiss: () -> Unit,
    onImport: (Array<String>) -> Unit,
    onRelink: (String) -> Unit,
    onSelect: (EditorSelection) -> Unit,
    onDeleteTrack: (String) -> Unit,
    onOpenPanel: (EditorPanel) -> Unit
) {
    if (panel == null) return
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VideoFlowEditorColors.EditorSurfaceElevated) {
        when (panel) {
            EditorPanel.Media -> MediaLibrary(
                assets = project?.mediaAssets.orEmpty().filterNot { it.mimeType?.startsWith("audio/") == true },
                editor = editor,
                thumbnails = thumbnails,
                generatingAssetId = proxyGeneratingAssetId,
                proxyPercent = proxyPercent,
                onImport = { onImport(arrayOf("video/*", "audio/*", "image/*")) },
                onAdd = { editorVm.addAsset(it); onDismiss() },
                onDetails = { onOpenPanel(EditorPanel.MediaDetails(it)) }
            )
            EditorPanel.Audio -> AudioLibrary(
                assets = project?.mediaAssets.orEmpty().filter { it.mimeType?.startsWith("audio/") == true || (it.audioTrackCount > 0 && it.videoTrackCount == 0) },
                waveforms = waveforms,
                onImport = { onImport(arrayOf("audio/*")) },
                onAdd = { editorVm.addAsset(it); onDismiss() },
                onGenerateWaveform = editorVm::generateWaveform
            )
            EditorPanel.Overlay -> OverlayLibrary(
                assets = project?.mediaAssets.orEmpty().filter { it.mimeType?.startsWith("image/") == true },
                thumbnails = thumbnails,
                onImport = { onImport(arrayOf("image/*")) },
                onAdd = { editorVm.addAsset(it); onDismiss() },
                onDetails = { onOpenPanel(EditorPanel.MediaDetails(it)) }
            )
            EditorPanel.Canvas -> CanvasPanel(editor)
            EditorPanel.Snapshots -> SnapshotsPanel(snapshots, editorVm)
            EditorPanel.More -> MorePanel(editorVm, onOpenPanel)
            is EditorPanel.TrackSettings -> {
                val track = editor?.timeline?.tracks?.firstOrNull { it.id == panel.trackId }
                if (track != null) TrackSettingsPanel(track, editorVm, onDeleteTrack)
            }
            is EditorPanel.MediaDetails -> {
                val asset = project?.mediaAssets?.firstOrNull { it.id == panel.assetId }
                if (asset != null) MediaDetailsPanel(
                    asset = asset,
                    editor = editor,
                    generating = proxyGeneratingAssetId == asset.id,
                    progress = proxyPercent,
                    onRelink = { onRelink(asset.id) },
                    onGenerate = { editorVm.generateProxy(asset.id, it) },
                    onCancel = { editorVm.cancelProxy(asset.id) },
                    onDeleteProxy = { editorVm.deleteProxy(asset.id) }
                )
            }
            is EditorPanel.ClipTool -> {
                val clip = editor?.timeline?.clips?.firstOrNull { it.id == panel.clipId }
                if (clip != null) ClipToolPanel(panel.kind, clip, editor?.timeline?.tracks.orEmpty(), editorVm, onDismiss)
            }
            is EditorPanel.TextTool -> {
                if (panel.overlayId == "new") {
                    AddTextPanel { editorVm.addText(); onDismiss() }
                } else {
                    val overlay = editor?.timeline?.textOverlays?.firstOrNull { it.id == panel.overlayId }
                    if (overlay != null) TextToolPanel(panel.kind, projectId, overlay, playheadUs, editorVm, overlayVm, onDismiss) {
                        editorVm.load(projectId)
                    }
                }
            }
            is EditorPanel.ImageTool -> {
                val overlay = editor?.timeline?.imageOverlays?.firstOrNull { it.id == panel.overlayId }
                if (overlay != null) ImageToolPanel(panel.kind, projectId, overlay, playheadUs, editorVm, overlayVm, onDismiss) {
                    editorVm.load(projectId)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(title, color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) Text(subtitle, color = VideoFlowEditorColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MediaLibrary(
    assets: List<MediaAsset>,
    editor: EditorProject?,
    thumbnails: Map<String, String>,
    generatingAssetId: String?,
    proxyPercent: Int?,
    onImport: () -> Unit,
    onAdd: (String) -> Unit,
    onDetails: (String) -> Unit
) {
    SheetTitle("Media", "Project video and image sources")
    Button(onClick = onImport, modifier = Modifier.padding(horizontal = 18.dp)) { Text("+ Import") }
    if (assets.isEmpty()) Text("No media yet.", color = VideoFlowEditorColors.SecondaryText, modifier = Modifier.padding(18.dp))
    LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
        items(assets, key = { it.id }) { asset ->
            val proxy = editor?.proxies?.firstOrNull { it.assetId == asset.id }
            MediaRow(asset, thumbnails[asset.id], onAdd = { onAdd(asset.id) }, onDetails = { onDetails(asset.id) })
            if (generatingAssetId == asset.id) {
                Text("Generating editing proxy… ${proxyPercent?.let { "$it%" } ?: ""}", color = VideoFlowEditorColors.SecondaryText, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            } else if (proxy != null) {
                Text("Performance media: Ready", color = VideoFlowEditorColors.SuccessColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 3.dp))
            }
            HorizontalDivider(color = VideoFlowEditorColors.EditorDivider)
        }
    }
}

@Composable
private fun AudioLibrary(
    assets: List<MediaAsset>,
    waveforms: Map<String, FloatArray>,
    onImport: () -> Unit,
    onAdd: (String) -> Unit,
    onGenerateWaveform: (String) -> Unit
) {
    SheetTitle("Audio", "Music and audio assets")
    Button(onClick = onImport, modifier = Modifier.padding(horizontal = 18.dp)) { Text("+ Import Audio") }
    LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
        items(assets, key = { it.id }) { asset ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(asset.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(asset.durationUs?.let(::formatDurationUs) ?: "Audio", style = MaterialTheme.typography.bodySmall)
                    WaveformPreview(waveforms[asset.id], Modifier.fillMaxWidth().height(32.dp), VideoFlowEditorColors.SelectionAccent)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAdd(asset.id) }, enabled = asset.sourceStatus == SourceStatus.AVAILABLE) { Text("Add") }
                        if (waveforms[asset.id] == null) OutlinedButton(onClick = { onGenerateWaveform(asset.id) }) { Text("Prepare waveform") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayLibrary(
    assets: List<MediaAsset>,
    thumbnails: Map<String, String>,
    onImport: () -> Unit,
    onAdd: (String) -> Unit,
    onDetails: (String) -> Unit
) {
    SheetTitle("Overlay", "Images used as visual overlays")
    Button(onClick = onImport, modifier = Modifier.padding(horizontal = 18.dp)) { Text("+ Import Image") }
    LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
        items(assets, key = { it.id }) { asset ->
            MediaRow(asset, thumbnails[asset.id], { onAdd(asset.id) }, { onDetails(asset.id) })
        }
    }
}

@Composable
private fun MediaRow(asset: MediaAsset, thumbnail: String?, onAdd: () -> Unit, onDetails: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CachedThumbnailPreview(thumbnail, Modifier.width(84.dp).height(52.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(asset.displayName, color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            val meta = buildList {
                if (asset.width != null && asset.height != null) add("${asset.width}×${asset.height}")
                asset.durationUs?.let { add(formatDurationUs(it)) }
            }.joinToString(" • ")
            if (meta.isNotBlank()) Text(meta, color = VideoFlowEditorColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
            if (asset.sourceStatus != SourceStatus.AVAILABLE) Text(sourceStatusLabel(asset.sourceStatus), color = VideoFlowEditorColors.WarningColor, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(onClick = onAdd, enabled = asset.sourceStatus == SourceStatus.AVAILABLE) { Text("Add") }
                TextButton(onClick = onDetails) { Text("Details") }
            }
        }
    }
}

@Composable
private fun CanvasPanel(editor: EditorProject?) {
    val settings = editor?.settings
    SheetTitle("Canvas", "Project canvas settings")
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (settings == null) Text("Project settings unavailable.") else {
            val aspect = aspectLabel(settings.width, settings.height)
            Text("Aspect ratio", style = MaterialTheme.typography.labelLarge)
            Text(aspect, color = VideoFlowEditorColors.PrimaryText)
            Text("Resolution", style = MaterialTheme.typography.labelLarge)
            Text("${settings.width}×${settings.height}", color = VideoFlowEditorColors.PrimaryText)
            Text("Background", style = MaterialTheme.typography.labelLarge)
            Text("Project background is preserved from the current project settings.", color = VideoFlowEditorColors.SecondaryText)
            Text("Output resolution remains in Export.", color = VideoFlowEditorColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SnapshotsPanel(snapshots: List<SnapshotEntity>, vm: EditorViewModel) {
    SheetTitle("Snapshots", "Restoreable project edit states")
    Button(onClick = { vm.createSnapshot("Snapshot ${snapshots.size + 1}") }, modifier = Modifier.padding(horizontal = 18.dp)) { Text("+ Save Snapshot") }
    LazyColumn(Modifier.fillMaxWidth().height(300.dp)) {
        items(snapshots, key = { it.id }) { snapshot ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(snapshot.name, color = VideoFlowEditorColors.PrimaryText, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.restoreSnapshot(snapshot.id) }) { Text("Restore") }
                TextButton(onClick = { vm.deleteSnapshot(snapshot.id) }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun MorePanel(vm: EditorViewModel, onOpenPanel: (EditorPanel) -> Unit) {
    SheetTitle("More")
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onOpenPanel(EditorPanel.Snapshots) }, modifier = Modifier.fillMaxWidth()) { Text("Snapshots") }
        OutlinedButton(onClick = { vm.createTrack(TrackType.VIDEO) }, modifier = Modifier.fillMaxWidth()) { Text("Add Video Track") }
        OutlinedButton(onClick = { vm.createTrack(TrackType.AUDIO) }, modifier = Modifier.fillMaxWidth()) { Text("Add Audio Track") }
        OutlinedButton(onClick = { vm.createTrack(TrackType.OVERLAY) }, modifier = Modifier.fillMaxWidth()) { Text("Add Overlay Track") }
    }
}

@Composable
private fun TrackSettingsPanel(track: TimelineTrack, vm: EditorViewModel, onDeleteTrack: (String) -> Unit) {
    var name by remember(track.id, track.name) { mutableStateOf(track.name) }
    SheetTitle("${track.name} settings", track.type.name.lowercase().replaceFirstChar { it.uppercase() } + " track")
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it.take(48) }, label = { Text("Track name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.renameTrack(track.id, name) }, enabled = name.isNotBlank() && name != track.name) { Text("Rename") }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (track.type != TrackType.AUDIO) OutlinedButton(onClick = { vm.toggleTrackVisible(track.id, !track.visible) }) { Text(if (track.visible) "Visible" else "Hidden") }
            OutlinedButton(onClick = { vm.toggleTrackMute(track.id, !track.muted) }) { Text(if (track.muted) "Muted" else "Mute") }
            OutlinedButton(onClick = { vm.toggleTrackSolo(track.id, !track.solo) }) { Text(if (track.solo) "Solo On" else "Solo") }
            OutlinedButton(onClick = { vm.toggleTrackLock(track.id, !track.locked) }) { Text(if (track.locked) "Locked" else "Lock") }
        }
        Text("Gain ${track.gainDb.roundToInt()} dB")
        Slider(value = track.gainDb.coerceIn(-60f, 24f), onValueChange = { vm.setTrackGain(track.id, it) }, valueRange = -60f..24f, enabled = !track.locked)
        TextButton(onClick = { onDeleteTrack(track.id) }, enabled = !track.locked) { Text("Delete Track") }
    }
}

@Composable
private fun MediaDetailsPanel(
    asset: MediaAsset,
    editor: EditorProject?,
    generating: Boolean,
    progress: Int?,
    onRelink: () -> Unit,
    onGenerate: (ProxyQuality) -> Unit,
    onCancel: () -> Unit,
    onDeleteProxy: () -> Unit
) {
    val proxy = editor?.proxies?.firstOrNull { it.assetId == asset.id }
    SheetTitle("Media details", asset.displayName)
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(sourceStatusLabel(asset.sourceStatus), color = if (asset.sourceStatus == SourceStatus.AVAILABLE) VideoFlowEditorColors.SuccessColor else VideoFlowEditorColors.WarningColor)
        if (asset.width != null && asset.height != null) Text("Resolution: ${asset.width}×${asset.height}")
        asset.durationUs?.let { Text("Duration: ${formatDurationUs(it)}") }
        asset.sizeBytes?.let { Text("Size: ${formatBytes(it)}") }
        asset.videoCodecMime?.let { Text("Video codec: $it") }
        asset.audioCodecMime?.let { Text("Audio codec: $it") }
        if (asset.sourceStatus != SourceStatus.AVAILABLE) Button(onClick = onRelink) { Text("Locate Original") }
        HorizontalDivider()
        Text("Performance / Proxy", style = MaterialTheme.typography.titleMedium)
        Text(if (proxy == null) "No editing proxy" else "Ready • ${proxy.width}×${proxy.height} • ${proxy.quality.name.lowercase().replaceFirstChar { it.uppercase() }}")
        if (generating) {
            Text("Generating editing proxy… ${progress?.let { "$it%" } ?: ""}")
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = { onGenerate(ProxyQuality.PERFORMANCE) }, enabled = asset.sourceStatus == SourceStatus.AVAILABLE) { Text("Performance") }
                OutlinedButton(onClick = { onGenerate(ProxyQuality.BALANCED) }, enabled = asset.sourceStatus == SourceStatus.AVAILABLE) { Text("Balanced") }
                OutlinedButton(onClick = { onGenerate(ProxyQuality.HIGH) }, enabled = asset.sourceStatus == SourceStatus.AVAILABLE) { Text("High") }
            }
            if (proxy != null) TextButton(onClick = onDeleteProxy) { Text("Delete Proxy") }
        }
    }
}

@Composable
private fun AddTextPanel(onAdd: () -> Unit) {
    SheetTitle("Text", "Add a text overlay at the playhead")
    Button(onClick = onAdd, modifier = Modifier.padding(18.dp).fillMaxWidth()) { Text("Add Text") }
}

@Composable
private fun ClipToolPanel(kind: EditorPanelKind, clip: TimelineClip, tracks: List<TimelineTrack>, vm: EditorViewModel, onDismiss: () -> Unit) {
    SheetTitle(
        when (kind) {
            EditorPanelKind.CLIP_TRIM -> "Trim"
            EditorPanelKind.CLIP_SPEED -> "Speed"
            EditorPanelKind.CLIP_CROP -> "Crop"
            EditorPanelKind.CLIP_VOLUME -> "Volume"
            EditorPanelKind.CLIP_FADE -> "Fade"
            else -> "Clip tools"
        },
        "${formatDurationUs(clip.timelineStartUs)} • ${formatDurationUs(clip.timelineDurationUs)}"
    )
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        when (kind) {
            EditorPanelKind.CLIP_TRIM -> {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = { vm.trimSelectedStart(-250_000L) }) { Text("Extend In") }
                    OutlinedButton(onClick = { vm.trimSelectedStart(250_000L) }) { Text("Trim In") }
                    OutlinedButton(onClick = { vm.trimSelectedEnd(-250_000L) }) { Text("Trim Out") }
                    OutlinedButton(onClick = { vm.trimSelectedEnd(250_000L) }) { Text("Extend Out") }
                }
            }
            EditorPanelKind.CLIP_SPEED -> {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(0.25, 0.5, 1.0, 1.5, 2.0, 4.0).forEach { speed -> OutlinedButton(onClick = { vm.setSpeed(speed) }) { Text("${speed}×") } }
                }
            }
            EditorPanelKind.CLIP_CROP -> {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(16 to 9, 9 to 16, 4 to 3, 3 to 2, 1 to 1, 4 to 5).forEach { (w, h) -> OutlinedButton(onClick = { vm.setCropPreset(w, h) }) { Text("$w:$h") } }
                }
            }
            EditorPanelKind.CLIP_VOLUME -> {
                Text("Clip gain ${clip.gainDb.roundToInt()} dB")
                Slider(value = clip.gainDb.coerceIn(-60f, 24f), onValueChange = vm::setClipGain, valueRange = -60f..24f)
            }
            EditorPanelKind.CLIP_FADE -> {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(0L, 500_000L, 1_000_000L, 2_000_000L).forEach { fade ->
                        val bounded = fade.coerceAtMost(clip.timelineDurationUs)
                        OutlinedButton(onClick = { vm.setFades(bounded, clip.fadeOutUs.coerceAtMost(clip.timelineDurationUs)) }) { Text("In ${fade / 1_000_000f}s") }
                        OutlinedButton(onClick = { vm.setFades(clip.fadeInUs.coerceAtMost(clip.timelineDurationUs), bounded) }) { Text("Out ${fade / 1_000_000f}s") }
                    }
                }
            }
            else -> ClipMorePanel(clip, tracks, vm, onDismiss)
        }
    }
}

@Composable
private fun ClipMorePanel(clip: TimelineClip, tracks: List<TimelineTrack>, vm: EditorViewModel, onDismiss: () -> Unit) {
    Text("Transform", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { vm.moveTransform(-0.05f, 0f) }) { Text("←") }
        OutlinedButton(onClick = { vm.moveTransform(0f, -0.05f) }) { Text("↑") }
        OutlinedButton(onClick = { vm.moveTransform(0f, 0.05f) }) { Text("↓") }
        OutlinedButton(onClick = { vm.moveTransform(0.05f, 0f) }) { Text("→") }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { vm.setScale((clip.transform.scaleX - 0.1f).coerceAtLeast(0.05f)) }) { Text("Scale −") }
        OutlinedButton(onClick = { vm.setScale(clip.transform.scaleX + 0.1f) }) { Text("Scale +") }
        OutlinedButton(onClick = vm::rotateSelected90) { Text("Rotate 90°") }
        OutlinedButton(onClick = vm::toggleFlipHorizontal) { Text("Flip H") }
        OutlinedButton(onClick = vm::toggleFlipVertical) { Text("Flip V") }
        OutlinedButton(onClick = vm::resetTransform) { Text("Reset") }
    }
    Text("Opacity")
    Slider(value = clip.opacity, onValueChange = vm::setOpacity, valueRange = 0f..1f)
    Text("Keyframes", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyframeProperty.entries.forEach { property -> OutlinedButton(onClick = { vm.addKeyframe(property, KeyframeInterpolation.LINEAR) }) { Text("◇ ${property.name.replace('_', ' ')}") } }
    }
    val currentType = tracks.firstOrNull { it.id == clip.trackId }?.type
    val compatible = tracks.filter { it.type == currentType && it.id != clip.trackId }
    if (compatible.isNotEmpty()) {
        Text("Move to track", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            compatible.forEach { track -> OutlinedButton(onClick = { vm.moveSelectedToTrack(track.id) }, enabled = !track.locked) { Text(track.name) } }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = vm::duplicateSelected) { Text("Duplicate") }
        TextButton(onClick = { vm.deleteSelected(); onDismiss() }) { Text("Delete") }
    }
}

@Composable
private fun TextToolPanel(
    kind: EditorPanelKind,
    projectId: String,
    overlay: TextOverlay,
    playheadUs: Long,
    vm: EditorViewModel,
    overlayVm: OverlayAdvancedViewModel,
    onDismiss: () -> Unit,
    refresh: () -> Unit
) {
    var draft by remember(overlay.id, overlay.content) { mutableStateOf(overlay.content) }
    SheetTitle("Text", formatDurationUs(overlay.timelineStartUs) + " → " + formatDurationUs(overlay.timelineEndUs))
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        when (kind) {
            EditorPanelKind.TEXT_EDIT -> {
                OutlinedTextField(value = draft, onValueChange = { draft = it.take(4096) }, label = { Text("Content") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.updateTextContent(overlay.id, draft) }) { Text("Save Text") }
            }
            EditorPanelKind.TEXT_STYLE -> {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.adjustTextSize(overlay.id, -2f) }) { Text("Size −") }
                    OutlinedButton(onClick = { vm.adjustTextSize(overlay.id, 2f) }) { Text("Size +") }
                    OutlinedButton(onClick = { vm.toggleTextBold(overlay.id) }) { Text("Bold") }
                    OutlinedButton(onClick = { vm.toggleTextItalic(overlay.id) }) { Text("Italic") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { overlayVm.setTextColor(projectId, overlay.id, 0xFFFFFFFFL, refresh) }) { Text("White") }
                    OutlinedButton(onClick = { overlayVm.setTextColor(projectId, overlay.id, 0xFFFFFF00L, refresh) }) { Text("Yellow") }
                    OutlinedButton(onClick = { overlayVm.setTextColor(projectId, overlay.id, 0xFFFF5252L, refresh) }) { Text("Red") }
                    listOf("START", "CENTER", "END").forEach { alignment -> OutlinedButton(onClick = { overlayVm.setTextAlignment(projectId, overlay.id, alignment, refresh) }) { Text(alignment.lowercase().replaceFirstChar { it.uppercase() }) } }
                }
            }
            EditorPanelKind.TEXT_TRANSFORM -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.moveText(overlay.id, -0.05f, 0f) }) { Text("←") }
                    OutlinedButton(onClick = { vm.moveText(overlay.id, 0f, -0.05f) }) { Text("↑") }
                    OutlinedButton(onClick = { vm.moveText(overlay.id, 0f, 0.05f) }) { Text("↓") }
                    OutlinedButton(onClick = { vm.moveText(overlay.id, 0.05f, 0f) }) { Text("→") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { overlayVm.setTextScale(projectId, overlay.id, (overlay.transform.scaleX - 0.1f).coerceAtLeast(0.05f), refresh) }) { Text("Scale −") }
                    OutlinedButton(onClick = { overlayVm.setTextScale(projectId, overlay.id, overlay.transform.scaleX + 0.1f, refresh) }) { Text("Scale +") }
                    OutlinedButton(onClick = { vm.rotateText(overlay.id) }) { Text("Rotate") }
                }
            }
            EditorPanelKind.TEXT_OPACITY -> Slider(value = overlay.opacity, onValueChange = { vm.setTextOpacity(overlay.id, it) }, valueRange = 0f..1f)
            EditorPanelKind.TEXT_KEYFRAME -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(KeyframeProperty.POSITION_X, KeyframeProperty.POSITION_Y, KeyframeProperty.SCALE_X, KeyframeProperty.ROTATION, KeyframeProperty.OPACITY).forEach { property ->
                    OutlinedButton(onClick = { overlayVm.addTextKeyframe(projectId, overlay.id, playheadUs, property, KeyframeInterpolation.LINEAR, refresh) }) { Text("◇ ${property.name}") }
                }
            }
            else -> {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { overlayVm.moveTextTimeline(projectId, overlay.id, -500_000L, refresh) }) { Text("Move −0.5s") }
                    OutlinedButton(onClick = { overlayVm.moveTextTimeline(projectId, overlay.id, 500_000L, refresh) }) { Text("Move +0.5s") }
                    OutlinedButton(onClick = { overlayVm.trimTextStart(projectId, overlay.id, 250_000L, refresh) }) { Text("Trim In") }
                    OutlinedButton(onClick = { overlayVm.trimTextEnd(projectId, overlay.id, -250_000L, refresh) }) { Text("Trim Out") }
                }
                TextButton(onClick = { vm.deleteText(overlay.id); onDismiss() }) { Text("Delete Text") }
            }
        }
    }
}

@Composable
private fun ImageToolPanel(
    kind: EditorPanelKind,
    projectId: String,
    overlay: ImageOverlay,
    playheadUs: Long,
    vm: EditorViewModel,
    overlayVm: OverlayAdvancedViewModel,
    onDismiss: () -> Unit,
    refresh: () -> Unit
) {
    SheetTitle("Image overlay", formatDurationUs(overlay.timelineStartUs) + " → " + formatDurationUs(overlay.timelineEndUs))
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        when (kind) {
            EditorPanelKind.IMAGE_TRANSFORM -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.moveImage(overlay.id, -0.05f, 0f) }) { Text("←") }
                    OutlinedButton(onClick = { vm.moveImage(overlay.id, 0f, -0.05f) }) { Text("↑") }
                    OutlinedButton(onClick = { vm.moveImage(overlay.id, 0f, 0.05f) }) { Text("↓") }
                    OutlinedButton(onClick = { vm.moveImage(overlay.id, 0.05f, 0f) }) { Text("→") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.adjustImageScale(overlay.id, -0.1f) }) { Text("Scale −") }
                    OutlinedButton(onClick = { vm.adjustImageScale(overlay.id, 0.1f) }) { Text("Scale +") }
                    OutlinedButton(onClick = { vm.rotateImage(overlay.id) }) { Text("Rotate") }
                }
            }
            EditorPanelKind.IMAGE_OPACITY -> Slider(value = overlay.transform.opacity, onValueChange = { vm.setImageOpacity(overlay.id, it) }, valueRange = 0f..1f)
            EditorPanelKind.IMAGE_DURATION -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { overlayVm.moveImageTimeline(projectId, overlay.id, -500_000L, refresh) }) { Text("Move −0.5s") }
                OutlinedButton(onClick = { overlayVm.moveImageTimeline(projectId, overlay.id, 500_000L, refresh) }) { Text("Move +0.5s") }
                OutlinedButton(onClick = { overlayVm.trimImageStart(projectId, overlay.id, 250_000L, refresh) }) { Text("Trim In") }
                OutlinedButton(onClick = { overlayVm.trimImageEnd(projectId, overlay.id, -250_000L, refresh) }) { Text("Trim Out") }
            }
            EditorPanelKind.IMAGE_KEYFRAME -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(KeyframeProperty.POSITION_X, KeyframeProperty.POSITION_Y, KeyframeProperty.SCALE_X, KeyframeProperty.ROTATION, KeyframeProperty.OPACITY).forEach { property ->
                    OutlinedButton(onClick = { overlayVm.addImageKeyframe(projectId, overlay.id, playheadUs, property, KeyframeInterpolation.LINEAR, refresh) }) { Text("◇ ${property.name}") }
                }
            }
            else -> TextButton(onClick = { vm.deleteImage(overlay.id); onDismiss() }) { Text("Delete Image") }
        }
    }
}

private fun sourceStatusLabel(status: SourceStatus): String = when (status) {
    SourceStatus.AVAILABLE -> "Original available"
    SourceStatus.MISSING -> "Original unavailable"
    SourceStatus.PERMISSION_LOST -> "Access to original was lost"
    SourceStatus.CHANGED -> "Source changed"
    SourceStatus.UNSUPPORTED -> "Unsupported source"
    SourceStatus.CORRUPTED -> "Source appears corrupted"
    SourceStatus.UNKNOWN -> "Source status unknown"
}

private fun aspectLabel(width: Int, height: Int): String {
    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    val divisor = gcd(width, height).coerceAtLeast(1)
    return "${width / divisor}:${height / divisor}"
}
