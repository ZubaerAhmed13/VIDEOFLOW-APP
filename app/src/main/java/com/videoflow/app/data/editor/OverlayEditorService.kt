package com.videoflow.app.data.editor

import androidx.room.withTransaction
import com.videoflow.app.data.db.ImageOverlayEntity
import com.videoflow.app.data.db.TextOverlayEntity
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.domain.editor.ClipTransform
import com.videoflow.app.domain.editor.ImageOverlay
import com.videoflow.app.domain.editor.TextOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayEditorService @Inject constructor(private val db: VideoFlowDatabase) {
    suspend fun updateText(
        projectId: String,
        overlayId: String,
        content: String? = null,
        fontSizeSp: Float? = null,
        fontWeight: Int? = null,
        italic: Boolean? = null,
        colorArgb: Long? = null,
        opacity: Float? = null,
        x: Float? = null,
        y: Float? = null,
        scale: Float? = null,
        rotationDegrees: Float? = null
    ): TextOverlay = withContext(Dispatchers.IO) {
        db.withTransaction {
            val current = db.editorDao().getTextOverlays(projectId).first { it.id == overlayId }
            requireUnlocked(projectId, current.trackId)
            val next = current.copy(
                content = content?.take(MAX_TEXT_LENGTH) ?: current.content,
                fontSizeSp = fontSizeSp?.also { require(it.isFinite() && it in 6f..256f) } ?: current.fontSizeSp,
                fontWeight = fontWeight?.coerceIn(100, 900) ?: current.fontWeight,
                italic = italic ?: current.italic,
                colorArgb = colorArgb ?: current.colorArgb,
                opacity = opacity?.also { require(it in 0f..1f) } ?: current.opacity,
                x = x?.also { require(it.isFinite()) }?.coerceIn(0f, 1f) ?: current.x,
                y = y?.also { require(it.isFinite()) }?.coerceIn(0f, 1f) ?: current.y,
                scaleX = scale?.also { require(it.isFinite() && it in 0.05f..10f) } ?: current.scaleX,
                scaleY = scale ?: current.scaleY,
                rotationDegrees = rotationDegrees?.also { require(it.isFinite()) } ?: current.rotationDegrees
            )
            db.editorDao().putTextOverlay(next)
            touch(projectId)
            next.toDomain()
        }
    }

    suspend fun updateImage(
        projectId: String,
        overlayId: String,
        opacity: Float? = null,
        x: Float? = null,
        y: Float? = null,
        scale: Float? = null,
        rotationDegrees: Float? = null
    ): ImageOverlay = withContext(Dispatchers.IO) {
        db.withTransaction {
            val current = db.editorDao().getImageOverlays(projectId).first { it.id == overlayId }
            requireUnlocked(projectId, current.trackId)
            val next = current.copy(
                opacity = opacity?.also { require(it in 0f..1f) } ?: current.opacity,
                x = x?.also { require(it.isFinite()) }?.coerceIn(0f, 1f) ?: current.x,
                y = y?.also { require(it.isFinite()) }?.coerceIn(0f, 1f) ?: current.y,
                scaleX = scale?.also { require(it.isFinite() && it in 0.05f..10f) } ?: current.scaleX,
                scaleY = scale ?: current.scaleY,
                rotationDegrees = rotationDegrees?.also { require(it.isFinite()) } ?: current.rotationDegrees
            )
            db.editorDao().putImageOverlay(next)
            touch(projectId)
            next.toDomain()
        }
    }

    suspend fun deleteText(projectId: String, overlayId: String): TextOverlay = withContext(Dispatchers.IO) {
        db.withTransaction {
            val current = db.editorDao().getTextOverlays(projectId).first { it.id == overlayId }
            requireUnlocked(projectId, current.trackId)
            db.editorDao().deleteKeyframes(overlayId)
            db.openHelper.writableDatabase.execSQL(
                "DELETE FROM text_overlays WHERE id=?",
                arrayOf<Any?>(overlayId)
            )
            touch(projectId)
            current.toDomain()
        }
    }

    suspend fun deleteImage(projectId: String, overlayId: String): ImageOverlay = withContext(Dispatchers.IO) {
        db.withTransaction {
            val current = db.editorDao().getImageOverlays(projectId).first { it.id == overlayId }
            requireUnlocked(projectId, current.trackId)
            db.editorDao().deleteKeyframes(overlayId)
            db.openHelper.writableDatabase.execSQL(
                "DELETE FROM image_overlays WHERE id=?",
                arrayOf<Any?>(overlayId)
            )
            touch(projectId)
            current.toDomain()
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
        private const val MAX_TEXT_LENGTH = 4096
    }
}

private fun TextOverlayEntity.toDomain() = TextOverlay(
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
    transform = ClipTransform(x, y, scaleX, scaleY, rotationDegrees, opacity)
)

private fun ImageOverlayEntity.toDomain() = ImageOverlay(
    id = id,
    projectId = projectId,
    trackId = trackId,
    assetId = assetId,
    timelineStartUs = timelineStartUs,
    timelineEndUs = timelineEndUs,
    transform = ClipTransform(x, y, scaleX, scaleY, rotationDegrees, opacity)
)
