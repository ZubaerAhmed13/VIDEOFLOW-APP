package com.videoflow.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoflow.app.util.formatDurationUs
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    projectName: String,
    saving: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit
) {
    var hadSaveInFlight by remember { mutableStateOf(false) }
    var showSavedConfirmation by remember { mutableStateOf(false) }
    LaunchedEffect(saving) {
        if (saving) {
            hadSaveInFlight = true
            showSavedConfirmation = false
        } else if (hadSaveInFlight) {
            hadSaveInFlight = false
            showSavedConfirmation = true
            delay(1_500L)
            showSavedConfirmation = false
        }
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = VideoFlowEditorColors.EditorSurface,
            titleContentColor = VideoFlowEditorColors.PrimaryText,
            navigationIconContentColor = VideoFlowEditorColors.PrimaryText,
            actionIconContentColor = VideoFlowEditorColors.PrimaryText
        ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    projectName.ifBlank { "VideoFlow" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                when {
                    saving -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(16.dp).height(16.dp))
                    showSavedConfirmation -> Icon(
                        Icons.Default.Check,
                        contentDescription = "Saved",
                        tint = VideoFlowEditorColors.SuccessColor,
                        modifier = Modifier.width(16.dp).height(16.dp)
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onUndo, enabled = canUndo && !saving) {
                Icon(Icons.Default.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo, enabled = canRedo && !saving) {
                Icon(Icons.Default.Redo, contentDescription = "Redo")
            }
            TextButton(onClick = onExport) {
                Text("Export", color = VideoFlowEditorColors.PrimaryText, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
fun TransportBar(
    playheadUs: Long,
    durationUs: Long,
    isPlaying: Boolean,
    onJumpStart: () -> Unit,
    onPlayPause: () -> Unit
) {
    Surface(color = VideoFlowEditorColors.EditorSurfaceElevated) {
        Row(
            Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatDurationUs(playheadUs), color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onJumpStart, enabled = durationUs > 0L) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Jump to start", tint = VideoFlowEditorColors.PrimaryText)
                }
                IconButton(onClick = onPlayPause, enabled = durationUs > 0L) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = VideoFlowEditorColors.PrimaryText
                    )
                }
            }
            Text(formatDurationUs(durationUs), color = VideoFlowEditorColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun EditorWarningBanner(offlineCount: Int, changedCount: Int, onReview: () -> Unit) {
    if (offlineCount <= 0 && changedCount <= 0) return
    val summary = buildString {
        if (offlineCount > 0) append("$offlineCount original ${if (offlineCount == 1) "file" else "files"} unavailable")
        if (changedCount > 0) {
            if (isNotEmpty()) append(" • ")
            append("$changedCount source changed")
        }
    }
    Surface(color = VideoFlowEditorColors.WarningColor.copy(alpha = 0.16f)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚠ $summary", color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onReview) { Text("Review") }
        }
    }
}

@Composable
fun EditorBottomToolbar(
    selection: EditorSelection,
    selectedClipMime: String?,
    onPanel: (EditorPanel) -> Unit,
    onSplit: () -> Unit
) {
    Surface(
        color = VideoFlowEditorColors.EditorSurface,
        tonalElevation = 4.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        when (selection) {
            EditorSelection.None, is EditorSelection.Track -> PrimaryToolbar(onPanel)
            is EditorSelection.Clip -> {
                if (selectedClipMime?.startsWith("audio/") == true) AudioClipToolbar(selection.clipId, onPanel, onSplit)
                else VideoClipToolbar(selection.clipId, onPanel, onSplit)
            }
            is EditorSelection.TextOverlay -> TextOverlayToolbar(selection.overlayId, onPanel)
            is EditorSelection.ImageOverlay -> ImageOverlayToolbar(selection.overlayId, onPanel)
        }
    }
}

@Composable
private fun PrimaryToolbar(onPanel: (EditorPanel) -> Unit) {
    ToolRow {
        ToolButton(Icons.Default.VideoLibrary, "Media") { onPanel(EditorPanel.Media) }
        ToolButton(Icons.Default.Audiotrack, "Audio") { onPanel(EditorPanel.Audio) }
        ToolButton(Icons.Default.TextFields, "Text") { onPanel(EditorPanel.TextTool(EditorPanelKind.TEXT_EDIT, "new")) }
        ToolButton(Icons.Default.Image, "Overlay") { onPanel(EditorPanel.Overlay) }
        ToolButton(Icons.Default.Layers, "Canvas") { onPanel(EditorPanel.Canvas) }
        ToolButton(Icons.Default.MoreHoriz, "More") { onPanel(EditorPanel.More) }
    }
}

@Composable
private fun VideoClipToolbar(clipId: String, onPanel: (EditorPanel) -> Unit, onSplit: () -> Unit) {
    ToolRow {
        ToolButton(Icons.Default.ContentCut, "Split", onSplit)
        ToolButton(Icons.Default.Tune, "Trim") { onPanel(EditorPanel.ClipTool(EditorPanelKind.CLIP_TRIM, clipId)) }
        ToolButton(Icons.Default.Speed, "Speed") { onPanel(EditorPanel.ClipTool(EditorPanelKind.CLIP_SPEED, clipId)) }
        ToolButton(Icons.Default.Crop, "Crop") { onPanel(EditorPanel.ClipTool(EditorPanelKind.CLIP_CROP, clipId)) }
        ToolButton(Icons.Default.VolumeUp, "Volume") { onPanel(EditorPanel.ClipTool(EditorPanelKind.CLIP_VOLUME, clipId)) }
        ToolButton(Icons.Default.MoreHoriz, "More") { onPanel(EditorPanel.ClipTool(EditorPanelKind.CLIP_MORE, clipId)) }
    }
}

@Composable
private fun AudioClipToolbar(clipId: String, onPanel: (EditorPanel) -> Unit, onSplit: () -> Unit) {
    ToolRow {
        ToolButton(Icons.Default.ContentCut, "Split", onSplit)
        ToolButton(Icons.Default.VolumeUp, "Volume") { onPanel(EditorPanel.ClipTool(EditorPanelKind.CLIP_VOLUME, clipId)) }
        ToolButton(Icons.Default.AccessTime, "Fade") { onPanel(EditorPanel.ClipTool(EditorPanelKind.CLIP_FADE, clipId)) }
        ToolButton(Icons.Default.Speed, "Speed") { onPanel(EditorPanel.ClipTool(EditorPanelKind.CLIP_SPEED, clipId)) }
        ToolButton(Icons.Default.MoreHoriz, "More") { onPanel(EditorPanel.ClipTool(EditorPanelKind.CLIP_MORE, clipId)) }
    }
}

@Composable
private fun TextOverlayToolbar(overlayId: String, onPanel: (EditorPanel) -> Unit) {
    ToolRow {
        ToolButton(Icons.Default.TextFields, "Edit") { onPanel(EditorPanel.TextTool(EditorPanelKind.TEXT_EDIT, overlayId)) }
        ToolButton(Icons.Default.Tune, "Style") { onPanel(EditorPanel.TextTool(EditorPanelKind.TEXT_STYLE, overlayId)) }
        ToolButton(Icons.Default.Transform, "Transform") { onPanel(EditorPanel.TextTool(EditorPanelKind.TEXT_TRANSFORM, overlayId)) }
        ToolButton(Icons.Default.Opacity, "Opacity") { onPanel(EditorPanel.TextTool(EditorPanelKind.TEXT_OPACITY, overlayId)) }
        ToolButton(Icons.Default.Animation, "Keyframe") { onPanel(EditorPanel.TextTool(EditorPanelKind.TEXT_KEYFRAME, overlayId)) }
        ToolButton(Icons.Default.MoreHoriz, "More") { onPanel(EditorPanel.TextTool(EditorPanelKind.TEXT_MORE, overlayId)) }
    }
}

@Composable
private fun ImageOverlayToolbar(overlayId: String, onPanel: (EditorPanel) -> Unit) {
    ToolRow {
        ToolButton(Icons.Default.Transform, "Transform") { onPanel(EditorPanel.ImageTool(EditorPanelKind.IMAGE_TRANSFORM, overlayId)) }
        ToolButton(Icons.Default.Opacity, "Opacity") { onPanel(EditorPanel.ImageTool(EditorPanelKind.IMAGE_OPACITY, overlayId)) }
        ToolButton(Icons.Default.AccessTime, "Duration") { onPanel(EditorPanel.ImageTool(EditorPanelKind.IMAGE_DURATION, overlayId)) }
        ToolButton(Icons.Default.Animation, "Keyframe") { onPanel(EditorPanel.ImageTool(EditorPanelKind.IMAGE_KEYFRAME, overlayId)) }
        ToolButton(Icons.Default.MoreHoriz, "More") { onPanel(EditorPanel.ImageTool(EditorPanelKind.IMAGE_MORE, overlayId)) }
    }
}

@Composable
private fun ToolRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) { content() }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(66.dp)
            .height(58.dp)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = VideoFlowEditorColors.PrimaryText)
        Spacer(Modifier.height(2.dp))
        Text(label, color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
fun LandscapeInfoPane(
    selection: EditorSelection,
    projectName: String,
    resolution: String,
    modifier: Modifier = Modifier
) {
    Surface(color = VideoFlowEditorColors.EditorSurface, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(projectName, color = VideoFlowEditorColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Text(resolution, color = VideoFlowEditorColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
            Text(
                when (selection) {
                    EditorSelection.None -> "Select a timeline item to reveal editing actions."
                    is EditorSelection.Clip -> "Clip selected"
                    is EditorSelection.Track -> "Track selected"
                    is EditorSelection.TextOverlay -> "Text selected"
                    is EditorSelection.ImageOverlay -> "Image selected"
                },
                color = VideoFlowEditorColors.SecondaryText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
