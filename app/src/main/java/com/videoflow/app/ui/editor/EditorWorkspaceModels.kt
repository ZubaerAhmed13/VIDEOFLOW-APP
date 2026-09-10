package com.videoflow.app.ui.editor

import androidx.compose.runtime.saveable.Saver
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
 * Single explicit contextual editing state for UI Step 2.
 *
 * Passive library/settings sheets remain EditorPanel. Any operation that changes media
 * or an overlay is represented here so Back/Cancel/Done and preview gestures have one
 * predictable state machine.
 */
sealed interface EditorTool {
    data class Trim(val clipId: String, val startPrecise: Boolean = false) : EditorTool
    data class Speed(val clipId: String) : EditorTool
    data class Crop(val clipId: String) : EditorTool
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

/** Explicit, centralized editor layout state. */
sealed interface EditorWorkspaceMode {
    data object Main : EditorWorkspaceMode
    data class FocusedTool(val tool: EditorTool) : EditorWorkspaceMode
}

private const val EditorStateSeparator = "\u001F"

val EditorSelectionSaver = Saver<EditorSelection, String>(
    save = { selection ->
        when (selection) {
            EditorSelection.None -> "none"
            is EditorSelection.Clip -> listOf("clip", selection.clipId).joinToString(EditorStateSeparator)
            is EditorSelection.Track -> listOf("track", selection.trackId).joinToString(EditorStateSeparator)
            is EditorSelection.TextOverlay -> listOf("text", selection.overlayId).joinToString(EditorStateSeparator)
            is EditorSelection.ImageOverlay -> listOf("image", selection.overlayId).joinToString(EditorStateSeparator)
        }
    },
    restore = { token ->
        val parts = token.split(EditorStateSeparator)
        when (parts.firstOrNull()) {
            "clip" -> parts.getOrNull(1)?.let(EditorSelection::Clip) ?: EditorSelection.None
            "track" -> parts.getOrNull(1)?.let(EditorSelection::Track) ?: EditorSelection.None
            "text" -> parts.getOrNull(1)?.let(EditorSelection::TextOverlay) ?: EditorSelection.None
            "image" -> parts.getOrNull(1)?.let(EditorSelection::ImageOverlay) ?: EditorSelection.None
            else -> EditorSelection.None
        }
    }
)

val EditorToolSaver = Saver<EditorTool?, String>(
    save = { tool ->
        when (tool) {
            null -> "none"
            is EditorTool.Trim -> listOf("trim", tool.clipId, tool.startPrecise.toString()).joinToString(EditorStateSeparator)
            is EditorTool.Speed -> listOf("speed", tool.clipId).joinToString(EditorStateSeparator)
            is EditorTool.Crop -> listOf("crop", tool.clipId).joinToString(EditorStateSeparator)
            is EditorTool.Transform -> listOf("transform", tool.ownerId, tool.ownerType.name).joinToString(EditorStateSeparator)
            is EditorTool.Opacity -> listOf("opacity", tool.ownerId, tool.ownerType.name).joinToString(EditorStateSeparator)
            is EditorTool.Volume -> listOf("volume", tool.clipId).joinToString(EditorStateSeparator)
            is EditorTool.Fade -> listOf("fade", tool.clipId).joinToString(EditorStateSeparator)
            is EditorTool.TextEditor -> listOf("text-editor", tool.overlayId.orEmpty()).joinToString(EditorStateSeparator)
            is EditorTool.TextStyle -> listOf("text-style", tool.overlayId).joinToString(EditorStateSeparator)
            is EditorTool.Timing -> listOf("timing", tool.ownerId, tool.ownerType.name).joinToString(EditorStateSeparator)
            is EditorTool.Keyframes -> listOf("keyframes", tool.ownerId, tool.ownerType.name).joinToString(EditorStateSeparator)
            is EditorTool.More -> listOf("more", tool.ownerId, tool.ownerType.name).joinToString(EditorStateSeparator)
        }
    },
    restore = { token ->
        val p = token.split(EditorStateSeparator)
        when (p.firstOrNull()) {
            "trim" -> p.getOrNull(1)?.let { EditorTool.Trim(it, p.getOrNull(2).toBoolean()) }
            "speed" -> p.getOrNull(1)?.let(EditorTool::Speed)
            "crop" -> p.getOrNull(1)?.let(EditorTool::Crop)
            "transform" -> if (p.size >= 3) EditorTool.Transform(p[1], VisualOwnerType.valueOf(p[2])) else null
            "opacity" -> if (p.size >= 3) EditorTool.Opacity(p[1], VisualOwnerType.valueOf(p[2])) else null
            "volume" -> p.getOrNull(1)?.let(EditorTool::Volume)
            "fade" -> p.getOrNull(1)?.let(EditorTool::Fade)
            "text-editor" -> EditorTool.TextEditor(p.getOrNull(1)?.takeIf(String::isNotEmpty))
            "text-style" -> p.getOrNull(1)?.let(EditorTool::TextStyle)
            "timing" -> if (p.size >= 3) EditorTool.Timing(p[1], TimedOwnerType.valueOf(p[2])) else null
            "keyframes" -> if (p.size >= 3) EditorTool.Keyframes(p[1], VisualOwnerType.valueOf(p[2])) else null
            "more" -> if (p.size >= 3) EditorTool.More(p[1], VisualOwnerType.valueOf(p[2])) else null
            else -> null
        }
    }
)

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
    val speed: Double? = null,
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
