package com.videoflow.app.ui.editor

import com.videoflow.app.domain.editor.CropRect
import com.videoflow.app.domain.editor.TimelineClip

/** Passive sheets that do not directly manipulate the preview. */
enum class EditorPanelKind {
    MEDIA,
    AUDIO,
    OVERLAY,
    CANVAS,
    SNAPSHOTS,
    TRACK_SETTINGS,
    MEDIA_DETAILS,
    // Legacy tool kinds are retained for source compatibility while UI Step 2 routes
    // editing through EditorTool instead of a matrix of independent booleans/sheets.
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

    /** Kept only so the approved UI Step 1 code remains source-compatible. */
    data class ClipTool(override val kind: EditorPanelKind, val clipId: String) : EditorPanel
    data class TextTool(override val kind: EditorPanelKind, val overlayId: String) : EditorPanel
    data class ImageTool(override val kind: EditorPanelKind, val overlayId: String) : EditorPanel
}

enum class VisualOwnerType { CLIP, TEXT, IMAGE }
enum class TimedOwnerType { TEXT, IMAGE }

/**
 * Single explicit contextual editing state for UI Step 2+.
 *
 * Passive library/settings sheets remain EditorPanel. Any operation that changes media
 * or an overlay is represented here so Back/Cancel/Done and preview gestures have one
 * predictable state machine. Step 4 Watermark is exposed only because its local AI runtime,
 * persistence and final render path are real on the Step-4 branch.
 */
sealed interface EditorTool {
    data class Trim(val clipId: String) : EditorTool
    data class Speed(val clipId: String) : EditorTool
    data class Crop(val clipId: String) : EditorTool
    data class Watermark(val clipId: String) : EditorTool
    data class Transform(val ownerId: String, val ownerType: VisualOwnerType) : EditorTool
    data class Opacity(val ownerId: String, val ownerType: VisualOwnerType) : EditorTool
    data class Volume(val clipId: String) : EditorTool
    data class Fade(val clipId: String) : EditorTool
    data class TextEditor(val overlayId: String?) : EditorTool
    data class TextStyle(val overlayId: String) : EditorTool
    data class Timing(val ownerId: String, val ownerType: TimedOwnerType) : EditorTool
    data class Keyframes(val ownerId: String, val ownerType: VisualOwnerType) : EditorTool
    data class More(val ownerId: String, val ownerType: VisualOwnerType) : EditorTool
}

/**
 * UI-only transform values used while a pointer/slider gesture is active.
 * Nothing in this model is persisted. The owning screen commits it once at gesture end/Done.
 */
data class PreviewTransformDraft(
    val x: Float,
    val y: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotationDegrees: Float,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
)

data class PreviewTextStyleDraft(
    val fontSizeSp: Float,
    val fontWeight: Int,
    val italic: Boolean,
    val alignment: String,
    val colorArgb: Long
)

/**
 * Transient contextual edit state shared by the preview and the precision tool panel.
 * High-frequency gesture updates live here instead of Room; durable state is written once.
 */
data class ContextualPreviewDraft(
    val crop: CropRect? = null,
    /** Normalized crop width / normalized crop height. Null means unconstrained Free crop. */
    val cropNormalizedAspect: Float? = null,
    val transform: PreviewTransformDraft? = null,
    val opacity: Float? = null,
    val gainDb: Float? = null,
    val fadeInUs: Long? = null,
    val fadeOutUs: Long? = null,
    val textContent: String? = null,
    val textStyle: PreviewTextStyleDraft? = null
)

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
