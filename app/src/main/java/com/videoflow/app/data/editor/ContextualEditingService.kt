package com.videoflow.app.data.editor

import androidx.room.withTransaction
import com.videoflow.app.data.db.VideoFlowDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small Step 2 service for mutations that were not exposed by the existing editor
 * repositories. It deliberately does not own trim/transform/crop math; those remain in
 * EditorRepository, EditorPropertyService and OverlayEditorService.
 */
@Singleton
class ContextualEditingService @Inject constructor(
    private val db: VideoFlowDatabase
) {
    suspend fun deleteKeyframe(projectId: String, keyframeId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val ownerIds = buildList {
                addAll(db.editorDao().getClips(projectId).map { it.id })
                addAll(db.editorDao().getTextOverlays(projectId).map { it.id })
                addAll(db.editorDao().getImageOverlays(projectId).map { it.id })
            }
            val target = if (ownerIds.isEmpty()) null else db.editorDao().getKeyframes(ownerIds).firstOrNull { it.id == keyframeId }
            requireNotNull(target) { "Keyframe no longer exists" }
            db.openHelper.writableDatabase.execSQL("DELETE FROM keyframes WHERE id=?", arrayOf<Any?>(keyframeId))
            touch(projectId)
        }
    }

    suspend fun duplicateTextOverlay(projectId: String, overlayId: String): String = withContext(Dispatchers.IO) {
        db.withTransaction {
            val current = db.editorDao().getTextOverlays(projectId).first { it.id == overlayId }
            requireUnlocked(projectId, current.trackId)
            val id = UUID.randomUUID().toString()
            val duration = current.timelineEndUs - current.timelineStartUs
            val start = current.timelineStartUs + DUPLICATE_OFFSET_US
            db.editorDao().putTextOverlay(current.copy(id = id, timelineStartUs = start, timelineEndUs = start + duration))
            db.editorDao().getKeyframes(listOf(overlayId)).forEach { frame ->
                db.editorDao().putKeyframe(frame.copy(id = UUID.randomUUID().toString(), ownerId = id))
            }
            touch(projectId)
            id
        }
    }

    suspend fun duplicateImageOverlay(projectId: String, overlayId: String): String = withContext(Dispatchers.IO) {
        db.withTransaction {
            val current = db.editorDao().getImageOverlays(projectId).first { it.id == overlayId }
            requireUnlocked(projectId, current.trackId)
            val id = UUID.randomUUID().toString()
            val duration = current.timelineEndUs - current.timelineStartUs
            val start = current.timelineStartUs + DUPLICATE_OFFSET_US
            db.editorDao().putImageOverlay(current.copy(id = id, timelineStartUs = start, timelineEndUs = start + duration))
            db.editorDao().getKeyframes(listOf(overlayId)).forEach { frame ->
                db.editorDao().putKeyframe(frame.copy(id = UUID.randomUUID().toString(), ownerId = id))
            }
            touch(projectId)
            id
        }
    }

    private suspend fun requireUnlocked(projectId: String, trackId: String) {
        val track = db.editorDao().getTracks(projectId).first { it.id == trackId }
        if (track.locked) throw LockedTrackException("Track ${track.name} is locked")
    }

    private suspend fun touch(projectId: String) {
        val project = db.projectDao().get(projectId)?.project ?: return
        db.projectDao().update(project.copy(projectFormatVersion = 2, updatedAt = System.currentTimeMillis()))
    }

    companion object {
        private const val DUPLICATE_OFFSET_US = 250_000L
    }
}
