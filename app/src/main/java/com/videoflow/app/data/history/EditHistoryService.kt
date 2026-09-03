package com.videoflow.app.data.history

import androidx.room.withTransaction
import com.videoflow.app.data.db.ClipEntity
import com.videoflow.app.data.db.ImageOverlayEntity
import com.videoflow.app.data.db.KeyframeEntity
import com.videoflow.app.data.db.TextOverlayEntity
import com.videoflow.app.data.db.TrackEntity
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.domain.editor.ImageOverlay
import com.videoflow.app.domain.editor.Keyframe
import com.videoflow.app.domain.editor.TextOverlay
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface HistoryEntry {
    val projectId: String
    val label: String
}

data class ClipHistoryEntry(
    override val projectId: String,
    override val label: String,
    val beforeClips: List<TimelineClip>,
    val afterClips: List<TimelineClip>,
    val beforeKeyframes: List<Keyframe> = emptyList(),
    val afterKeyframes: List<Keyframe> = emptyList()
) : HistoryEntry

data class TrackHistoryEntry(
    override val projectId: String,
    override val label: String,
    val before: TimelineTrack?,
    val after: TimelineTrack?
) : HistoryEntry {
    init { require(before != null || after != null) }
}

data class TrackBundleHistoryEntry(
    override val projectId: String,
    override val label: String,
    val track: TimelineTrack,
    val clips: List<TimelineClip>,
    val textOverlays: List<TextOverlay>,
    val imageOverlays: List<ImageOverlay>,
    val keyframes: List<Keyframe>
) : HistoryEntry

data class KeyframeHistoryEntry(
    override val projectId: String,
    override val label: String,
    val before: List<Keyframe>,
    val after: List<Keyframe>
) : HistoryEntry

data class TextOverlayHistoryEntry(
    override val projectId: String,
    override val label: String,
    val before: TextOverlay?,
    val after: TextOverlay?,
    val beforeKeyframes: List<Keyframe> = emptyList(),
    val afterKeyframes: List<Keyframe> = emptyList()
) : HistoryEntry {
    init { require(before != null || after != null) }
}

data class ImageOverlayHistoryEntry(
    override val projectId: String,
    override val label: String,
    val before: ImageOverlay?,
    val after: ImageOverlay?,
    val beforeKeyframes: List<Keyframe> = emptyList(),
    val afterKeyframes: List<Keyframe> = emptyList()
) : HistoryEntry {
    init { require(before != null || after != null) }
}

@Singleton
class EditHistoryService @Inject constructor(private val db: VideoFlowDatabase) {
    private val undo = ArrayDeque<HistoryEntry>()
    private val redo = ArrayDeque<HistoryEntry>()
    private val _state = MutableStateFlow(HistoryState())
    val state = _state.asStateFlow()
    private var activeProjectId: String? = null
    private var lastCoalesceKey: String? = null
    private var lastCoalesceAtMs: Long = 0L

    fun activateProject(projectId: String) {
        if (activeProjectId != projectId) {
            activeProjectId = projectId
            undo.clear()
            redo.clear()
            lastCoalesceKey = null
            lastCoalesceAtMs = 0L
            publish()
        }
    }

    fun record(entry: HistoryEntry) {
        append(entry)
        lastCoalesceKey = null
    }

    fun recordCoalesced(entry: HistoryEntry, key: String, nowMs: Long = System.currentTimeMillis()) {
        activateProject(entry.projectId)
        val previous = undo.lastOrNull()
        val canMerge = previous != null &&
            key == lastCoalesceKey &&
            nowMs - lastCoalesceAtMs in 0..COALESCE_WINDOW_MS
        val merged = if (canMerge) merge(previous!!, entry) else null
        if (merged != null) {
            undo.removeLast()
            undo.addLast(merged)
            redo.clear()
            lastCoalesceAtMs = nowMs
            publish()
        } else {
            append(entry)
            lastCoalesceKey = key
            lastCoalesceAtMs = nowMs
        }
    }

    suspend fun undo(): String? = withContext(Dispatchers.IO) {
        val entry = undo.removeLastOrNull() ?: return@withContext null
        apply(entry, before = true)
        redo.addLast(entry)
        lastCoalesceKey = null
        publish()
        entry.label
    }

    suspend fun redo(): String? = withContext(Dispatchers.IO) {
        val entry = redo.removeLastOrNull() ?: return@withContext null
        apply(entry, before = false)
        undo.addLast(entry)
        while (undo.size > MAX_HISTORY) undo.removeFirst()
        lastCoalesceKey = null
        publish()
        entry.label
    }

    private fun append(entry: HistoryEntry) {
        activateProject(entry.projectId)
        undo.addLast(entry)
        while (undo.size > MAX_HISTORY) undo.removeFirst()
        redo.clear()
        publish()
    }

    private fun merge(previous: HistoryEntry, next: HistoryEntry): HistoryEntry? = when {
        previous is ClipHistoryEntry && next is ClipHistoryEntry &&
            previous.projectId == next.projectId &&
            previous.afterClips.map { it.id }.toSet() == next.beforeClips.map { it.id }.toSet() ->
            previous.copy(label = next.label, afterClips = next.afterClips, afterKeyframes = next.afterKeyframes)

        previous is TrackHistoryEntry && next is TrackHistoryEntry &&
            previous.projectId == next.projectId && previous.after?.id == next.before?.id ->
            previous.copy(label = next.label, after = next.after)

        previous is KeyframeHistoryEntry && next is KeyframeHistoryEntry && previous.projectId == next.projectId ->
            previous.copy(label = next.label, after = next.after)

        previous is TextOverlayHistoryEntry && next is TextOverlayHistoryEntry &&
            previous.projectId == next.projectId && previous.after?.id == next.before?.id ->
            previous.copy(label = next.label, after = next.after, afterKeyframes = next.afterKeyframes)

        previous is ImageOverlayHistoryEntry && next is ImageOverlayHistoryEntry &&
            previous.projectId == next.projectId && previous.after?.id == next.before?.id ->
            previous.copy(label = next.label, after = next.after, afterKeyframes = next.afterKeyframes)

        else -> null
    }

    private suspend fun apply(entry: HistoryEntry, before: Boolean) {
        db.withTransaction {
            when (entry) {
                is ClipHistoryEntry -> {
                    val allClipIds = (entry.beforeClips.map { it.id } + entry.afterClips.map { it.id }).distinct()
                    allClipIds.forEach { id ->
                        db.editorDao().deleteKeyframes(id)
                        db.editorDao().deleteClip(id)
                    }
                    val clips = if (before) entry.beforeClips else entry.afterClips
                    val keyframes = if (before) entry.beforeKeyframes else entry.afterKeyframes
                    if (clips.isNotEmpty()) db.editorDao().putClips(clips.map { it.toEntity() })
                    keyframes.forEach { db.editorDao().putKeyframe(it.toEntity()) }
                }

                is TrackHistoryEntry -> {
                    val desired = if (before) entry.before else entry.after
                    val trackId = entry.before?.id ?: entry.after?.id ?: error("Track history has no target")
                    if (desired == null) db.editorDao().deleteTrack(trackId) else db.editorDao().putTrack(desired.toEntity())
                }

                is TrackBundleHistoryEntry -> {
                    if (before) {
                        db.editorDao().putTrack(entry.track.toEntity())
                        if (entry.clips.isNotEmpty()) db.editorDao().putClips(entry.clips.map { it.toEntity() })
                        entry.textOverlays.forEach { db.editorDao().putTextOverlay(it.toEntity()) }
                        entry.imageOverlays.forEach { db.editorDao().putImageOverlay(it.toEntity()) }
                        entry.keyframes.forEach { db.editorDao().putKeyframe(it.toEntity()) }
                    } else {
                        val ownerIds = buildList {
                            addAll(entry.clips.map { it.id })
                            addAll(entry.textOverlays.map { it.id })
                            addAll(entry.imageOverlays.map { it.id })
                        }
                        ownerIds.forEach { db.editorDao().deleteKeyframes(it) }
                        db.editorDao().deleteTrack(entry.track.id)
                    }
                }

                is KeyframeHistoryEntry -> {
                    val allOwnerIds = (entry.before.map { it.ownerId } + entry.after.map { it.ownerId }).distinct()
                    allOwnerIds.forEach { db.editorDao().deleteKeyframes(it) }
                    val frames = if (before) entry.before else entry.after
                    frames.forEach { db.editorDao().putKeyframe(it.toEntity()) }
                }

                is TextOverlayHistoryEntry -> {
                    val id = entry.before?.id ?: entry.after?.id ?: error("Text history has no target")
                    db.editorDao().deleteKeyframes(id)
                    db.openHelper.writableDatabase.execSQL("DELETE FROM text_overlays WHERE id=?", arrayOf<Any?>(id))
                    val desired = if (before) entry.before else entry.after
                    val frames = if (before) entry.beforeKeyframes else entry.afterKeyframes
                    if (desired != null) db.editorDao().putTextOverlay(desired.toEntity())
                    frames.forEach { db.editorDao().putKeyframe(it.toEntity()) }
                }

                is ImageOverlayHistoryEntry -> {
                    val id = entry.before?.id ?: entry.after?.id ?: error("Image history has no target")
                    db.editorDao().deleteKeyframes(id)
                    db.openHelper.writableDatabase.execSQL("DELETE FROM image_overlays WHERE id=?", arrayOf<Any?>(id))
                    val desired = if (before) entry.before else entry.after
                    val frames = if (before) entry.beforeKeyframes else entry.afterKeyframes
                    if (desired != null) db.editorDao().putImageOverlay(desired.toEntity())
                    frames.forEach { db.editorDao().putKeyframe(it.toEntity()) }
                }
            }
            touch(entry.projectId)
        }
    }

    private suspend fun touch(projectId: String) {
        val project = db.projectDao().get(projectId)?.project ?: return
        db.projectDao().update(project.copy(projectFormatVersion = 2, updatedAt = System.currentTimeMillis()))
    }

    private fun publish() {
        _state.value = HistoryState(
            canUndo = undo.isNotEmpty(),
            canRedo = redo.isNotEmpty(),
            undoLabel = undo.lastOrNull()?.label,
            redoLabel = redo.lastOrNull()?.label
        )
    }

    companion object {
        private const val MAX_HISTORY = 200
        private const val COALESCE_WINDOW_MS = 750L
    }
}

data class HistoryState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String? = null,
    val redoLabel: String? = null
)

private fun TimelineClip.toEntity() = ClipEntity(
    id = id,
    projectId = projectId,
    trackId = trackId,
    assetId = assetId,
    timelineStartUs = timelineStartUs,
    sourceStartUs = sourceStartUs,
    sourceEndUs = sourceEndUs,
    speed = speed,
    opacity = opacity,
    enabled = enabled,
    gainDb = gainDb,
    fadeInUs = fadeInUs,
    fadeOutUs = fadeOutUs,
    x = transform.x,
    y = transform.y,
    scaleX = transform.scaleX,
    scaleY = transform.scaleY,
    rotationDegrees = transform.rotationDegrees,
    flipHorizontal = transform.flipHorizontal,
    flipVertical = transform.flipVertical,
    cropLeft = transform.crop.left,
    cropTop = transform.crop.top,
    cropRight = transform.crop.right,
    cropBottom = transform.crop.bottom
)

private fun Keyframe.toEntity() = KeyframeEntity(
    id = id,
    ownerId = ownerId,
    ownerType = ownerType.name,
    property = property.name,
    timeUs = timeUs,
    value = value,
    interpolation = interpolation.name
)

private fun TimelineTrack.toEntity() = TrackEntity(
    id = id,
    projectId = projectId,
    type = type.name,
    name = name,
    orderIndex = orderIndex,
    muted = muted,
    solo = solo,
    locked = locked,
    visible = visible,
    gainDb = gainDb
)

private fun TextOverlay.toEntity() = TextOverlayEntity(
    id = id,
    projectId = projectId,
    trackId = trackId,
    timelineStartUs = timelineStartUs,
    timelineEndUs = timelineEndUs,
    content = content,
    fontSizeSp = fontSizeSp,
    fontWeight = fontWeight,
    italic = italic,
    colorArgb = colorArgb,
    opacity = opacity,
    alignment = alignment,
    x = transform.x,
    y = transform.y,
    scaleX = transform.scaleX,
    scaleY = transform.scaleY,
    rotationDegrees = transform.rotationDegrees
)

private fun ImageOverlay.toEntity() = ImageOverlayEntity(
    id = id,
    projectId = projectId,
    trackId = trackId,
    assetId = assetId,
    timelineStartUs = timelineStartUs,
    timelineEndUs = timelineEndUs,
    x = transform.x,
    y = transform.y,
    scaleX = transform.scaleX,
    scaleY = transform.scaleY,
    rotationDegrees = transform.rotationDegrees,
    opacity = transform.opacity
)
