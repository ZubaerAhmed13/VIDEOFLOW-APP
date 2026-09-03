package com.videoflow.app.data.editor

import androidx.room.withTransaction
import com.videoflow.app.data.db.ClipEntity
import com.videoflow.app.data.db.ImageOverlayEntity
import com.videoflow.app.data.db.KeyframeEntity
import com.videoflow.app.data.db.ProjectSettingsEntity
import com.videoflow.app.data.db.ProxyEntity
import com.videoflow.app.data.db.TextOverlayEntity
import com.videoflow.app.data.db.TrackEntity
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.domain.editor.ClipTransform
import com.videoflow.app.domain.editor.CropRect
import com.videoflow.app.domain.editor.FrameRate
import com.videoflow.app.domain.editor.ImageOverlay
import com.videoflow.app.domain.editor.Keyframe
import com.videoflow.app.domain.editor.KeyframeInterpolation
import com.videoflow.app.domain.editor.KeyframeOwnerType
import com.videoflow.app.domain.editor.KeyframeProperty
import com.videoflow.app.domain.editor.ProjectSettings
import com.videoflow.app.domain.editor.ProxyMedia
import com.videoflow.app.domain.editor.ProxyQuality
import com.videoflow.app.domain.editor.ProxyStatus
import com.videoflow.app.domain.editor.TextOverlay
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineEngine
import com.videoflow.app.domain.editor.TimelineState
import com.videoflow.app.domain.editor.TimelineTrack
import com.videoflow.app.domain.editor.TrackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

class LockedTrackException(message: String) : IllegalStateException(message)
class TimelineOverlapException(message: String) : IllegalArgumentException(message)

@Singleton
class EditorRepository @Inject constructor(private val db: VideoFlowDatabase) {
    suspend fun ensureProjectInitialized(projectId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val project = db.projectDao().get(projectId)?.project
                ?: error("Project $projectId does not exist")
            if (db.editorDao().getProjectSettings(projectId) == null) {
                db.editorDao().putProjectSettings(
                    ProjectSettingsEntity(
                        projectId = projectId,
                        width = 1920,
                        height = 1080,
                        frameRateNumerator = 30,
                        frameRateDenominator = 1,
                        backgroundArgb = 0xFF000000,
                        createdAt = project.createdAt,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            if (db.editorDao().getTracks(projectId).isEmpty()) {
                db.editorDao().putTracks(defaultTracks(projectId))
            }
            if (project.projectFormatVersion != 2) {
                db.projectDao().update(project.copy(projectFormatVersion = 2, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun load(projectId: String): EditorProject = withContext(Dispatchers.IO) {
        ensureProjectInitialized(projectId)
        val settings = requireNotNull(db.editorDao().getProjectSettings(projectId)).toDomain()
        val tracks = db.editorDao().getTracks(projectId).map { it.toDomain() }
        val clips = db.editorDao().getClips(projectId).map { it.toDomain() }
        val text = db.editorDao().getTextOverlays(projectId).map { it.toDomain() }
        val images = db.editorDao().getImageOverlays(projectId).map { it.toDomain() }
        val ownerIds = clips.map { it.id } + text.map { it.id } + images.map { it.id }
        val keyframes = if (ownerIds.isEmpty()) emptyList() else db.editorDao().getKeyframes(ownerIds).map { it.toDomain() }
        val proxies = db.proxyDao().getForProject(projectId).map { it.toDomain() }
        EditorProject(settings, TimelineState(projectId, tracks, clips, text, images, keyframes), proxies)
    }

    suspend fun addClip(projectId: String, assetId: String, playheadUs: Long): TimelineClip = withContext(Dispatchers.IO) {
        db.withTransaction {
            ensureProjectInitialized(projectId)
            val asset = db.mediaAssetDao().get(assetId) ?: error("Media asset not found")
            val type = when {
                asset.mimeType?.startsWith("video/") == true -> TrackType.VIDEO
                asset.mimeType?.startsWith("audio/") == true -> TrackType.AUDIO
                else -> error("Only video/audio assets become TimelineClip; images use ImageOverlay")
            }
            val tracks = db.editorDao().getTracks(projectId).map { it.toDomain() }
            val track = tracks.firstOrNull { it.type == type && !it.locked }
                ?: createTrackInternal(projectId, type, nextTrackName(type, tracks), tracks)
            val duration = asset.durationUs?.takeIf { it > 0 } ?: error("Asset duration unavailable")
            val clip = TimelineClip(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                trackId = track.id,
                assetId = assetId,
                timelineStartUs = playheadUs.coerceAtLeast(0),
                sourceStartUs = 0,
                sourceEndUs = duration
            )
            ensureNoOverlap(clip, db.editorDao().getClips(projectId).map { it.toDomain() })
            db.editorDao().putClip(clip.toEntity())
            touch(projectId)
            clip
        }
    }

    suspend fun createTrack(projectId: String, type: TrackType, name: String): TimelineTrack = withContext(Dispatchers.IO) {
        db.withTransaction {
            val tracks = db.editorDao().getTracks(projectId).map { it.toDomain() }
            createTrackInternal(projectId, type, name, tracks).also { touch(projectId) }
        }
    }

    suspend fun renameTrack(projectId: String, trackId: String, name: String) = updateTrack(projectId, trackId) {
        it.copy(name = name.trim().ifBlank { it.name })
    }

    suspend fun setTrackLocked(projectId: String, trackId: String, locked: Boolean) = updateTrack(projectId, trackId) {
        it.copy(locked = locked)
    }

    suspend fun setTrackVisible(projectId: String, trackId: String, visible: Boolean) = updateTrack(projectId, trackId) {
        it.copy(visible = visible)
    }

    suspend fun setTrackMuted(projectId: String, trackId: String, muted: Boolean) = updateTrack(projectId, trackId) {
        it.copy(muted = muted)
    }

    suspend fun setTrackSolo(projectId: String, trackId: String, solo: Boolean) = updateTrack(projectId, trackId) {
        it.copy(solo = solo)
    }

    suspend fun setTrackGain(projectId: String, trackId: String, gainDb: Float) = updateTrack(projectId, trackId) {
        require(gainDb.isFinite())
        it.copy(gainDb = gainDb)
    }

    suspend fun deleteTrack(projectId: String, trackId: String, confirmDeleteClips: Boolean) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val clips = db.editorDao().getClips(projectId).filter { it.trackId == trackId }
            if (clips.isNotEmpty() && !confirmDeleteClips) {
                error("Track contains ${clips.size} clip(s); explicit confirmation is required")
            }
            db.editorDao().deleteTrack(trackId)
            touch(projectId)
        }
    }

    suspend fun moveClip(projectId: String, clipId: String, newTimelineStartUs: Long, targetTrackId: String? = null): TimelineClip =
        mutateClip(projectId, clipId) { clip, track, clips, tracks ->
            requireUnlocked(track)
            val target = targetTrackId?.let { id -> tracks.first { it.id == id } } ?: track
            requireUnlocked(target)
            require(target.type == track.type) { "Clip may move only to a compatible track" }
            val moved = TimelineEngine.moveClip(clip.copy(trackId = target.id), newTimelineStartUs)
            ensureNoOverlap(moved, clips.filterNot { it.id == clip.id })
            moved
        }

    suspend fun trimClipStart(projectId: String, clipId: String, newSourceStartUs: Long): TimelineClip =
        mutateClip(projectId, clipId) { clip, track, clips, _ ->
            requireUnlocked(track)
            val trimmed = TimelineEngine.trimStart(clip, newSourceStartUs)
            ensureNoOverlap(trimmed, clips.filterNot { it.id == clip.id })
            trimmed
        }

    suspend fun trimClipEnd(projectId: String, clipId: String, newSourceEndUs: Long): TimelineClip = withContext(Dispatchers.IO) {
        val assetDuration = db.editorDao().getClips(projectId).first { it.id == clipId }.let { clip ->
            db.mediaAssetDao().get(clip.assetId)?.durationUs ?: error("Source duration unavailable")
        }
        mutateClip(projectId, clipId) { clip, track, clips, _ ->
            requireUnlocked(track)
            val trimmed = TimelineEngine.trimEnd(clip, newSourceEndUs, assetDuration)
            ensureNoOverlap(trimmed, clips.filterNot { it.id == clip.id })
            trimmed
        }
    }

    suspend fun splitClip(projectId: String, clipId: String, playheadUs: Long): Pair<TimelineClip, TimelineClip> = withContext(Dispatchers.IO) {
        db.withTransaction {
            val clips = db.editorDao().getClips(projectId).map { it.toDomain() }
            val original = clips.first { it.id == clipId }
            val track = db.editorDao().getTracks(projectId).map { it.toDomain() }.first { it.id == original.trackId }
            requireUnlocked(track)
            val rightId = UUID.randomUUID().toString()
            val (left, right) = TimelineEngine.splitClip(original, playheadUs, rightId)
            db.editorDao().putClips(listOf(left.toEntity(), right.toEntity()))

            val ownerFrames = db.editorDao().getKeyframes(listOf(original.id)).map { it.toDomain() }
            if (ownerFrames.isNotEmpty()) {
                val splitLocalUs = playheadUs - original.timelineStartUs
                val (_, rightFrames) = TimelineEngine.splitKeyframes(ownerFrames, original.id, right.id, splitLocalUs) {
                    UUID.randomUUID().toString()
                }
                rightFrames.forEach { db.editorDao().putKeyframe(it.toEntity()) }
            }
            touch(projectId)
            left to right
        }
    }

    suspend fun duplicateClip(projectId: String, clipId: String): TimelineClip = withContext(Dispatchers.IO) {
        db.withTransaction {
            val clips = db.editorDao().getClips(projectId).map { it.toDomain() }
            val clip = clips.first { it.id == clipId }
            val track = db.editorDao().getTracks(projectId).map { it.toDomain() }.first { it.id == clip.trackId }
            requireUnlocked(track)
            val duplicate = TimelineEngine.duplicateClip(clip, UUID.randomUUID().toString())
            ensureNoOverlap(duplicate, clips)
            db.editorDao().putClip(duplicate.toEntity())
            val keyframes = db.editorDao().getKeyframes(listOf(clip.id)).map { it.toDomain() }
            keyframes.forEach { db.editorDao().putKeyframe(it.copy(id = UUID.randomUUID().toString(), ownerId = duplicate.id).toEntity()) }
            touch(projectId)
            duplicate
        }
    }

    suspend fun deleteClip(projectId: String, clipId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val clip = db.editorDao().getClips(projectId).map { it.toDomain() }.first { it.id == clipId }
            val track = db.editorDao().getTracks(projectId).map { it.toDomain() }.first { it.id == clip.trackId }
            requireUnlocked(track)
            db.editorDao().deleteKeyframes(clipId)
            db.editorDao().deleteClip(clipId)
            touch(projectId)
        }
    }

    suspend fun putKeyframe(projectId: String, keyframe: Keyframe) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val clip = db.editorDao().getClips(projectId).map { it.toDomain() }.firstOrNull { it.id == keyframe.ownerId }
            if (clip != null) {
                val track = db.editorDao().getTracks(projectId).map { it.toDomain() }.first { it.id == clip.trackId }
                requireUnlocked(track)
                require(keyframe.timeUs <= clip.timelineDurationUs)
            }
            db.editorDao().putKeyframe(keyframe.toEntity())
            touch(projectId)
        }
    }

    suspend fun addTextOverlay(projectId: String, playheadUs: Long, content: String, durationUs: Long = 5_000_000): TextOverlay = withContext(Dispatchers.IO) {
        db.withTransaction {
            ensureProjectInitialized(projectId)
            val tracks = db.editorDao().getTracks(projectId).map { it.toDomain() }
            val track = tracks.firstOrNull { it.type == TrackType.OVERLAY && !it.locked }
                ?: createTrackInternal(projectId, TrackType.OVERLAY, nextTrackName(TrackType.OVERLAY, tracks), tracks)
            val overlay = TextOverlay(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                trackId = track.id,
                timelineStartUs = playheadUs.coerceAtLeast(0),
                timelineEndUs = playheadUs.coerceAtLeast(0) + durationUs.coerceAtLeast(1),
                content = content
            )
            db.editorDao().putTextOverlay(overlay.toEntity())
            touch(projectId)
            overlay
        }
    }

    suspend fun addImageOverlay(projectId: String, assetId: String, playheadUs: Long, durationUs: Long = 5_000_000): ImageOverlay = withContext(Dispatchers.IO) {
        db.withTransaction {
            val asset = db.mediaAssetDao().get(assetId) ?: error("Media asset not found")
            require(asset.mimeType?.startsWith("image/") == true) { "Asset is not an image" }
            ensureProjectInitialized(projectId)
            val tracks = db.editorDao().getTracks(projectId).map { it.toDomain() }
            val track = tracks.firstOrNull { it.type == TrackType.OVERLAY && !it.locked }
                ?: createTrackInternal(projectId, TrackType.OVERLAY, nextTrackName(TrackType.OVERLAY, tracks), tracks)
            val overlay = ImageOverlay(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                trackId = track.id,
                assetId = assetId,
                timelineStartUs = playheadUs.coerceAtLeast(0),
                timelineEndUs = playheadUs.coerceAtLeast(0) + durationUs.coerceAtLeast(1)
            )
            db.editorDao().putImageOverlay(overlay.toEntity())
            touch(projectId)
            overlay
        }
    }

    private suspend fun mutateClip(
        projectId: String,
        clipId: String,
        block: (TimelineClip, TimelineTrack, List<TimelineClip>, List<TimelineTrack>) -> TimelineClip
    ): TimelineClip = withContext(Dispatchers.IO) {
        db.withTransaction {
            val clips = db.editorDao().getClips(projectId).map { it.toDomain() }
            val tracks = db.editorDao().getTracks(projectId).map { it.toDomain() }
            val clip = clips.first { it.id == clipId }
            val track = tracks.first { it.id == clip.trackId }
            block(clip, track, clips, tracks).also {
                db.editorDao().putClip(it.toEntity())
                touch(projectId)
            }
        }
    }

    private suspend fun updateTrack(
        projectId: String,
        trackId: String,
        block: (TimelineTrack) -> TimelineTrack
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val track = db.editorDao().getTracks(projectId).map { it.toDomain() }.first { it.id == trackId }
            db.editorDao().putTrack(block(track).toEntity())
            touch(projectId)
        }
    }

    private suspend fun createTrackInternal(
        projectId: String,
        type: TrackType,
        name: String,
        current: List<TimelineTrack>
    ): TimelineTrack {
        val track = TimelineTrack(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            type = type,
            name = name.trim().ifBlank { nextTrackName(type, current) },
            orderIndex = (current.maxOfOrNull { it.orderIndex } ?: -1) + 1
        )
        db.editorDao().putTrack(track.toEntity())
        return track
    }

    private fun ensureNoOverlap(candidate: TimelineClip, existing: List<TimelineClip>) {
        val collision = existing.firstOrNull {
            it.trackId == candidate.trackId &&
                candidate.timelineStartUs < it.timelineEndUs &&
                candidate.timelineEndUs > it.timelineStartUs
        }
        if (collision != null) throw TimelineOverlapException("Clip overlaps ${collision.id} on the same track")
    }

    private fun requireUnlocked(track: TimelineTrack) {
        if (track.locked) throw LockedTrackException("Track ${track.name} is locked")
    }

    private suspend fun touch(projectId: String) {
        val project = db.projectDao().get(projectId)?.project ?: return
        db.projectDao().update(project.copy(projectFormatVersion = 2, updatedAt = System.currentTimeMillis()))
    }

    private fun nextTrackName(type: TrackType, current: List<TimelineTrack>): String {
        val number = current.count { it.type == type } + 1
        return when (type) {
            TrackType.VIDEO -> "V$number"
            TrackType.AUDIO -> "A$number"
            TrackType.OVERLAY -> "Overlay $number"
        }
    }

    private fun defaultTracks(projectId: String) = listOf(
        TrackEntity(UUID.randomUUID().toString(), projectId, TrackType.VIDEO.name, "V1", 0, false, false, false, true, 0f),
        TrackEntity(UUID.randomUUID().toString(), projectId, TrackType.AUDIO.name, "A1", 1, false, false, false, true, 0f),
        TrackEntity(UUID.randomUUID().toString(), projectId, TrackType.OVERLAY.name, "Overlay 1", 2, false, false, false, true, 0f)
    )
}

data class EditorProject(
    val settings: ProjectSettings,
    val timeline: TimelineState,
    val proxies: List<ProxyMedia>
)

private fun ProjectSettingsEntity.toDomain() = ProjectSettings(
    projectId, width, height, FrameRate(frameRateNumerator, frameRateDenominator), backgroundArgb, createdAt, updatedAt
)

private fun TrackEntity.toDomain() = TimelineTrack(
    id, projectId, TrackType.valueOf(type), name, orderIndex, muted, solo, locked, visible, gainDb
)

private fun TimelineTrack.toEntity() = TrackEntity(id, projectId, type.name, name, orderIndex, muted, solo, locked, visible, gainDb)

private fun ClipEntity.toDomain() = TimelineClip(
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
    transform = ClipTransform(
        x, y, scaleX, scaleY, rotationDegrees, opacity, flipHorizontal, flipVertical,
        CropRect(cropLeft, cropTop, cropRight, cropBottom)
    )
)

private fun TimelineClip.toEntity() = ClipEntity(
    id, projectId, trackId, assetId, timelineStartUs, sourceStartUs, sourceEndUs, speed, opacity, enabled,
    gainDb, fadeInUs, fadeOutUs, transform.x, transform.y, transform.scaleX, transform.scaleY,
    transform.rotationDegrees, transform.flipHorizontal, transform.flipVertical,
    transform.crop.left, transform.crop.top, transform.crop.right, transform.crop.bottom
)

private fun TextOverlayEntity.toDomain() = TextOverlay(
    id, projectId, trackId, timelineStartUs, timelineEndUs, content, fontSizeSp, fontWeight, italic,
    colorArgb, opacity, alignment, ClipTransform(x, y, scaleX, scaleY, rotationDegrees, opacity)
)

private fun TextOverlay.toEntity() = TextOverlayEntity(
    id, projectId, trackId, timelineStartUs, timelineEndUs, content, fontSizeSp, fontWeight, italic,
    colorArgb, opacity, alignment, transform.x, transform.y, transform.scaleX, transform.scaleY, transform.rotationDegrees
)

private fun ImageOverlayEntity.toDomain() = ImageOverlay(
    id, projectId, trackId, assetId, timelineStartUs, timelineEndUs,
    ClipTransform(x, y, scaleX, scaleY, rotationDegrees, opacity)
)

private fun ImageOverlay.toEntity() = ImageOverlayEntity(
    id, projectId, trackId, assetId, timelineStartUs, timelineEndUs,
    transform.x, transform.y, transform.scaleX, transform.scaleY, transform.rotationDegrees, transform.opacity
)

private fun KeyframeEntity.toDomain() = Keyframe(
    id, ownerId, KeyframeOwnerType.valueOf(ownerType), KeyframeProperty.valueOf(property), timeUs, value,
    KeyframeInterpolation.valueOf(interpolation)
)

private fun Keyframe.toEntity() = KeyframeEntity(id, ownerId, ownerType.name, property.name, timeUs, value, interpolation.name)

private fun ProxyEntity.toDomain() = ProxyMedia(
    id, assetId, path, width, height, codecMime, sourceFingerprint, ProxyStatus.valueOf(status),
    ProxyQuality.valueOf(quality), createdAt, sizeBytes
)
