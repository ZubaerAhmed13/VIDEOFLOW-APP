package com.videoflow.app.data.editor

import androidx.room.withTransaction
import com.videoflow.app.data.db.ClipEntity
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.domain.editor.CropRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

@Singleton
class EditorPropertyService @Inject constructor(private val db: VideoFlowDatabase) {
    suspend fun setTransform(
        projectId: String,
        clipId: String,
        x: Float,
        y: Float,
        scaleX: Float,
        scaleY: Float,
        rotationDegrees: Float,
        opacity: Float
    ) = mutate(projectId, clipId) { clip ->
        require(x.isFinite() && y.isFinite())
        require(scaleX.isFinite() && scaleY.isFinite() && scaleX > 0f && scaleY > 0f)
        require(rotationDegrees.isFinite())
        require(opacity in 0f..1f)
        db.openHelper.writableDatabase.execSQL(
            "UPDATE clips SET x=?, y=?, scaleX=?, scaleY=?, rotationDegrees=?, opacity=? WHERE id=?",
            arrayOf<Any?>(x, y, scaleX, scaleY, rotationDegrees, opacity, clip.id)
        )
    }

    suspend fun resetTransform(projectId: String, clipId: String) = mutate(projectId, clipId) { clip ->
        db.openHelper.writableDatabase.execSQL(
            "UPDATE clips SET x=0.5, y=0.5, scaleX=1.0, scaleY=1.0, rotationDegrees=0.0, opacity=1.0, flipHorizontal=0, flipVertical=0, cropLeft=0.0, cropTop=0.0, cropRight=1.0, cropBottom=1.0 WHERE id=?",
            arrayOf<Any?>(clip.id)
        )
    }

    suspend fun setCrop(projectId: String, clipId: String, crop: CropRect) = mutate(projectId, clipId) { clip ->
        db.openHelper.writableDatabase.execSQL(
            "UPDATE clips SET cropLeft=?, cropTop=?, cropRight=?, cropBottom=? WHERE id=?",
            arrayOf<Any?>(crop.left, crop.top, crop.right, crop.bottom, clip.id)
        )
    }

    suspend fun rotate90(projectId: String, clipId: String) = mutate(projectId, clipId) { clip ->
        val next = ((clip.rotationDegrees + 90f) % 360f + 360f) % 360f
        db.openHelper.writableDatabase.execSQL(
            "UPDATE clips SET rotationDegrees=? WHERE id=?",
            arrayOf<Any?>(next, clip.id)
        )
    }

    suspend fun setFlipHorizontal(projectId: String, clipId: String, enabled: Boolean) = mutate(projectId, clipId) { clip ->
        db.openHelper.writableDatabase.execSQL(
            "UPDATE clips SET flipHorizontal=? WHERE id=?",
            arrayOf<Any?>(if (enabled) 1 else 0, clip.id)
        )
    }

    suspend fun setFlipVertical(projectId: String, clipId: String, enabled: Boolean) = mutate(projectId, clipId) { clip ->
        db.openHelper.writableDatabase.execSQL(
            "UPDATE clips SET flipVertical=? WHERE id=?",
            arrayOf<Any?>(if (enabled) 1 else 0, clip.id)
        )
    }

    suspend fun setOpacity(projectId: String, clipId: String, opacity: Float) = mutate(projectId, clipId) { clip ->
        require(opacity in 0f..1f)
        db.openHelper.writableDatabase.execSQL(
            "UPDATE clips SET opacity=? WHERE id=?",
            arrayOf<Any?>(opacity, clip.id)
        )
    }

    suspend fun setSpeed(projectId: String, clipId: String, speed: Double) = withContext(Dispatchers.IO) {
        require(speed.isFinite() && speed in 0.25..4.0) { "Speed must be between 0.25× and 4×" }
        db.withTransaction {
            val clips = db.editorDao().getClips(projectId)
            val clip = clips.first { it.id == clipId }
            requireUnlocked(projectId, clip.trackId)
            val durationUs = ((clip.sourceEndUs - clip.sourceStartUs).toDouble() / speed).roundToLong().coerceAtLeast(1L)
            val endUs = clip.timelineStartUs + durationUs
            val overlap = clips.any {
                it.id != clip.id && it.trackId == clip.trackId &&
                    clip.timelineStartUs < it.timelineStartUs + timelineDurationUs(it) &&
                    endUs > it.timelineStartUs
            }
            if (overlap) throw TimelineOverlapException("Speed change would overlap another clip on the same track")
            db.openHelper.writableDatabase.execSQL(
                "UPDATE clips SET speed=? WHERE id=?",
                arrayOf<Any?>(speed, clip.id)
            )
            touch(projectId)
        }
    }

    suspend fun setClipGain(projectId: String, clipId: String, gainDb: Float) = mutate(projectId, clipId) { clip ->
        require(gainDb.isFinite() && gainDb in -60f..24f)
        db.openHelper.writableDatabase.execSQL(
            "UPDATE clips SET gainDb=? WHERE id=?",
            arrayOf<Any?>(gainDb, clip.id)
        )
    }

    suspend fun setFades(projectId: String, clipId: String, fadeInUs: Long, fadeOutUs: Long) = mutate(projectId, clipId) { clip ->
        val duration = timelineDurationUs(clip)
        require(fadeInUs in 0..duration && fadeOutUs in 0..duration)
        db.openHelper.writableDatabase.execSQL(
            "UPDATE clips SET fadeInUs=?, fadeOutUs=? WHERE id=?",
            arrayOf<Any?>(fadeInUs, fadeOutUs, clip.id)
        )
    }

    suspend fun restoreClip(projectId: String, clip: ClipEntity) = withContext(Dispatchers.IO) {
        db.withTransaction {
            require(clip.projectId == projectId)
            requireUnlocked(projectId, clip.trackId)
            db.editorDao().putClip(clip)
            touch(projectId)
        }
    }

    private suspend fun mutate(projectId: String, clipId: String, block: suspend (ClipEntity) -> Unit) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val clip = db.editorDao().getClips(projectId).first { it.id == clipId }
            requireUnlocked(projectId, clip.trackId)
            block(clip)
            touch(projectId)
        }
    }

    private suspend fun requireUnlocked(projectId: String, trackId: String) {
        val track = db.editorDao().getTracks(projectId).first { it.id == trackId }
        if (track.locked) throw LockedTrackException("Track ${track.name} is locked")
    }

    private fun timelineDurationUs(clip: ClipEntity): Long =
        ((clip.sourceEndUs - clip.sourceStartUs).toDouble() / clip.speed).roundToLong().coerceAtLeast(1L)

    private suspend fun touch(projectId: String) {
        val project = db.projectDao().get(projectId)?.project ?: return
        db.projectDao().update(project.copy(projectFormatVersion = 2, updatedAt = System.currentTimeMillis()))
    }
}
