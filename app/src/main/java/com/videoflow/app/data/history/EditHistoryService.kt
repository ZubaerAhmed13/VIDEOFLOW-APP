package com.videoflow.app.data.history

import androidx.room.withTransaction
import com.videoflow.app.data.db.ClipEntity
import com.videoflow.app.data.db.KeyframeEntity
import com.videoflow.app.data.db.TrackEntity
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.domain.editor.Keyframe
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
    val before: TimelineTrack,
    val after: TimelineTrack
) : HistoryEntry

data class KeyframeHistoryEntry(
    override val projectId: String,
    override val label: String,
    val before: List<Keyframe>,
    val after: List<Keyframe>
) : HistoryEntry

@Singleton
class EditHistoryService @Inject constructor(private val db: VideoFlowDatabase) {
    private val undo = ArrayDeque<HistoryEntry>()
    private val redo = ArrayDeque<HistoryEntry>()
    private val _state = MutableStateFlow(HistoryState())
    val state = _state.asStateFlow()
    private var activeProjectId: String? = null

    fun activateProject(projectId: String) {
        if (activeProjectId != projectId) {
            activeProjectId = projectId
            undo.clear()
            redo.clear()
            publish()
        }
    }

    fun record(entry: HistoryEntry) {
        activateProject(entry.projectId)
        undo.addLast(entry)
        while (undo.size > MAX_HISTORY) undo.removeFirst()
        redo.clear()
        publish()
    }

    suspend fun undo(): String? = withContext(Dispatchers.IO) {
        val entry = undo.removeLastOrNull() ?: return@withContext null
        apply(entry, before = true)
        redo.addLast(entry)
        publish()
        entry.label
    }

    suspend fun redo(): String? = withContext(Dispatchers.IO) {
        val entry = redo.removeLastOrNull() ?: return@withContext null
        apply(entry, before = false)
        undo.addLast(entry)
        while (undo.size > MAX_HISTORY) undo.removeFirst()
        publish()
        entry.label
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
                    db.editorDao().putTrack((if (before) entry.before else entry.after).toEntity())
                }
                is KeyframeHistoryEntry -> {
                    val allOwnerIds = (entry.before.map { it.ownerId } + entry.after.map { it.ownerId }).distinct()
                    allOwnerIds.forEach { db.editorDao().deleteKeyframes(it) }
                    val frames = if (before) entry.before else entry.after
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
