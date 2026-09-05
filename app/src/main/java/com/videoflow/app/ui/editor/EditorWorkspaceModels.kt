package com.videoflow.app.ui.editor

import com.videoflow.app.domain.editor.TimelineClip

enum class EditorPanelKind {
    MEDIA,
    AUDIO,
    OVERLAY,
    CANVAS,
    SNAPSHOTS,
    TRACK_SETTINGS,
    MEDIA_DETAILS,
    CLIP_TRIM,
    CLIP_SPEED,
    CLIP_CROP,
    CLIP_VOLUME,
    CLIP_FADE,
    CLIP_MORE,
    TEXT_EDIT,
    TEXT_STYLE,
    TEXT_TRANSFORM,
    TEXT_OPACITY,
    TEXT_KEYFRAME,
    TEXT_MORE,
    IMAGE_TRANSFORM,
    IMAGE_OPACITY,
    IMAGE_DURATION,
    IMAGE_KEYFRAME,
    IMAGE_MORE,
    MORE
}

sealed interface EditorSelection {
    data object None : EditorSelection
    data class Clip(val clipId: String) : EditorSelection
    data class Track(val trackId: String) : EditorSelection
    data class TextOverlay(val overlayId: String) : EditorSelection
    data class ImageOverlay(val overlayId: String) : EditorSelection
}

sealed interface EditorPanel {
    val kind: EditorPanelKind

    data object Media : EditorPanel { override val kind = EditorPanelKind.MEDIA }
    data object Audio : EditorPanel { override val kind = EditorPanelKind.AUDIO }
    data object Overlay : EditorPanel { override val kind = EditorPanelKind.OVERLAY }
    data object Canvas : EditorPanel { override val kind = EditorPanelKind.CANVAS }
    data object Snapshots : EditorPanel { override val kind = EditorPanelKind.SNAPSHOTS }
    data object More : EditorPanel { override val kind = EditorPanelKind.MORE }
    data class TrackSettings(val trackId: String) : EditorPanel { override val kind = EditorPanelKind.TRACK_SETTINGS }
    data class MediaDetails(val assetId: String) : EditorPanel { override val kind = EditorPanelKind.MEDIA_DETAILS }
    data class ClipTool(override val kind: EditorPanelKind, val clipId: String) : EditorPanel
    data class TextTool(override val kind: EditorPanelKind, val overlayId: String) : EditorPanel
    data class ImageTool(override val kind: EditorPanelKind, val overlayId: String) : EditorPanel
}

data class EditorWorkspaceUiState(
    val projectId: String,
    val projectName: String,
    val playheadUs: Long,
    val durationUs: Long,
    val isPlaying: Boolean,
    val selection: EditorSelection,
    val activePanel: EditorPanel?,
    val timelineZoom: Float,
    val isSaving: Boolean,
    val offlineSourceCount: Int,
    val changedSourceCount: Int
)

fun EditorSelection.selectedClip(clips: List<TimelineClip>): TimelineClip? =
    (this as? EditorSelection.Clip)?.let { selected -> clips.firstOrNull { it.id == selected.clipId } }
