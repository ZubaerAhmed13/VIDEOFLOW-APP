package com.videoflow.app.data.editor

import androidx.room.withTransaction
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.domain.editor.ImageOverlay
import com.videoflow.app.domain.editor.Keyframe
import com.videoflow.app.domain.editor.TextOverlay
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackLifecycleService @Inject constructor(
    private val db: VideoFlowDatabase,
    private val editorRepository: EditorRepository
) {
    suspend fun deleteTrack(projectId: String, trackId: String, confirmed: Boolean): DeletedTrackBundle {
        val editor = editorRepository.load(projectId)
        val track = editor.timeline.tracks.firstOrNull { it.id == trackId } ?: error("Track not found")
        if (track.locked) throw LockedTrackException("Track ${track.name} is locked")
        val clips = editor.timeline.clips.filter { it.trackId == trackId }
        val text = editor.timeline.textOverlays.filter { it.trackId == trackId }
        val images = editor.timeline.imageOverlays.filter { it.trackId == trackId }
        val ownerIds = buildSet {
            addAll(clips.map { it.id })
            addAll(text.map { it.id })
            addAll(images.map { it.id })
        }
        val keyframes = editor.timeline.keyframes.filter { it.ownerId in ownerIds }
        val itemCount = clips.size + text.size + images.size
        if (itemCount > 0 && !confirmed) {
            throw TrackDeleteConfirmationRequired(track.name, itemCount)
        }

        withContext(Dispatchers.IO) {
            db.withTransaction {
                ownerIds.forEach { db.editorDao().deleteKeyframes(it) }
                db.editorDao().deleteTrack(trackId)
                val project = db.projectDao().get(projectId)?.project
                if (project != null) {
                    db.projectDao().update(project.copy(projectFormatVersion = 2, updatedAt = System.currentTimeMillis()))
                }
            }
        }
        return DeletedTrackBundle(track, clips, text, images, keyframes)
    }
}

class TrackDeleteConfirmationRequired(
    val trackName: String,
    val itemCount: Int
) : IllegalStateException("Track $trackName contains $itemCount timeline item(s); confirmation is required")

data class DeletedTrackBundle(
    val track: TimelineTrack,
    val clips: List<TimelineClip>,
    val textOverlays: List<TextOverlay>,
    val imageOverlays: List<ImageOverlay>,
    val keyframes: List<Keyframe>
)
