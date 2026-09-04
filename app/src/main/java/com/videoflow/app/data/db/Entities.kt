package com.videoflow.app.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import com.videoflow.app.data.export.ExportDao
import com.videoflow.app.data.export.ExportJobEntity
import com.videoflow.app.data.export.ExportReportEntity
import com.videoflow.app.domain.model.FingerprintStrength
import com.videoflow.app.domain.model.MediaAsset
import com.videoflow.app.domain.model.SourceStatus
import com.videoflow.app.domain.model.VideoFlowProject
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val projectFormatVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long?
)

@Entity(
    tableName = "media_assets",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId"), Index(value = ["projectId", "sourceUri"])]
)
data class MediaAssetEntity(
    @PrimaryKey val assetId: String,
    val projectId: String,
    val sourceUri: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val durationUs: Long?,
    val width: Int?,
    val height: Int?,
    val rotationDegrees: Int?,
    val frameRate: Double?,
    val videoCodecMime: String?,
    val audioCodecMime: String?,
    val audioSampleRate: Int?,
    val audioChannelCount: Int?,
    val videoTrackCount: Int,
    val audioTrackCount: Int,
    val videoBitrate: Int?,
    val videoProfile: Int?,
    val videoLevel: Int?,
    val colorStandard: Int?,
    val colorTransfer: Int?,
    val colorRange: Int?,
    val hdrStaticInfoPresent: Boolean,
    val fingerprintSha256: String?,
    val fingerprintAlgorithm: String?,
    val fingerprintStrength: String,
    val fingerprintSampledBytes: Long,
    val fingerprintNote: String?,
    val permissionPersisted: Boolean,
    val sourceStatus: String,
    val createdAt: Long
)

@Entity(
    tableName = "project_settings",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)]
)
data class ProjectSettingsEntity(
    @PrimaryKey val projectId: String,
    val width: Int,
    val height: Int,
    val frameRateNumerator: Int,
    val frameRateDenominator: Int,
    val backgroundArgb: Long,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "tracks",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index(value = ["projectId", "orderIndex"], unique = true)]
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val type: String,
    val name: String,
    val orderIndex: Int,
    val muted: Boolean,
    val solo: Boolean,
    val locked: Boolean,
    val visible: Boolean,
    val gainDb: Float
)

@Entity(
    tableName = "clips",
    foreignKeys = [
        ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(MediaAssetEntity::class, ["assetId"], ["assetId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("projectId"), Index("trackId"), Index("assetId"), Index(value = ["trackId", "timelineStartUs"])]
)
data class ClipEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val trackId: String,
    val assetId: String,
    val timelineStartUs: Long,
    val sourceStartUs: Long,
    val sourceEndUs: Long,
    val speed: Double,
    val opacity: Float,
    val enabled: Boolean,
    val gainDb: Float,
    val fadeInUs: Long,
    val fadeOutUs: Long,
    val x: Float,
    val y: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotationDegrees: Float,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
    val cropLeft: Float,
    val cropTop: Float,
    val cropRight: Float,
    val cropBottom: Float
)

@Entity(
    tableName = "text_overlays",
    foreignKeys = [
        ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("projectId"), Index("trackId")]
)
data class TextOverlayEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val trackId: String,
    val timelineStartUs: Long,
    val timelineEndUs: Long,
    val content: String,
    val fontSizeSp: Float,
    val fontWeight: Int,
    val italic: Boolean,
    val colorArgb: Long,
    val opacity: Float,
    val alignment: String,
    val x: Float,
    val y: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotationDegrees: Float
)

@Entity(
    tableName = "image_overlays",
    foreignKeys = [
        ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(MediaAssetEntity::class, ["assetId"], ["assetId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("projectId"), Index("trackId"), Index("assetId")]
)
data class ImageOverlayEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val trackId: String,
    val assetId: String,
    val timelineStartUs: Long,
    val timelineEndUs: Long,
    val x: Float,
    val y: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotationDegrees: Float,
    val opacity: Float
)

@Entity(tableName = "keyframes", indices = [Index("ownerId"), Index(value = ["ownerId", "property", "timeUs"])])
data class KeyframeEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val ownerType: String,
    val property: String,
    val timeUs: Long,
    val value: Float,
    val interpolation: String
)

@Entity(
    tableName = "proxies",
    foreignKeys = [ForeignKey(MediaAssetEntity::class, ["assetId"], ["assetId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["assetId"], unique = true)]
)
data class ProxyEntity(
    @PrimaryKey val id: String,
    val assetId: String,
    val path: String,
    val width: Int,
    val height: Int,
    val codecMime: String,
    val sourceFingerprint: String?,
    val status: String,
    val quality: String,
    val createdAt: Long,
    val sizeBytes: Long?
)

@Entity(
    tableName = "snapshots",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")]
)
data class SnapshotEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val projectFormatVersion: Int,
    val payloadJson: String,
    val createdAt: Long
)

data class ProjectWithMedia(
    @Embedded val project: ProjectEntity,
    @Relation(parentColumn = "id", entityColumn = "projectId") val media: List<MediaAssetEntity>
)

@Dao
interface ProjectDao {
    @Transaction
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectWithMedia>>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectWithMedia?

    @Insert suspend fun insert(project: ProjectEntity)
    @Update suspend fun update(project: ProjectEntity)
    @Query("DELETE FROM projects WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface MediaAssetDao {
    @Insert suspend fun insert(asset: MediaAssetEntity)
    @Update suspend fun update(asset: MediaAssetEntity)
    @Query("DELETE FROM media_assets WHERE assetId = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM media_assets WHERE assetId = :id") suspend fun get(id: String): MediaAssetEntity?
    @Query("SELECT * FROM media_assets WHERE projectId = :projectId AND sourceUri = :sourceUri LIMIT 1")
    suspend fun findByUri(projectId: String, sourceUri: String): MediaAssetEntity?
    @Query("SELECT * FROM media_assets WHERE projectId = :projectId AND fingerprintSha256 = :sha256")
    suspend fun findByFingerprint(projectId: String, sha256: String): List<MediaAssetEntity>
}

@Dao
interface EditorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putProjectSettings(settings: ProjectSettingsEntity)

    @Query("SELECT * FROM project_settings WHERE projectId = :projectId")
    suspend fun getProjectSettings(projectId: String): ProjectSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTracks(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks WHERE projectId = :projectId ORDER BY orderIndex ASC")
    suspend fun getTracks(projectId: String): List<TrackEntity>

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrack(trackId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putClip(clip: ClipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putClips(clips: List<ClipEntity>)

    @Query("SELECT * FROM clips WHERE projectId = :projectId ORDER BY timelineStartUs ASC")
    suspend fun getClips(projectId: String): List<ClipEntity>

    @Query("DELETE FROM clips WHERE id = :clipId")
    suspend fun deleteClip(clipId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTextOverlay(overlay: TextOverlayEntity)

    @Query("SELECT * FROM text_overlays WHERE projectId = :projectId ORDER BY timelineStartUs ASC")
    suspend fun getTextOverlays(projectId: String): List<TextOverlayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putImageOverlay(overlay: ImageOverlayEntity)

    @Query("SELECT * FROM image_overlays WHERE projectId = :projectId ORDER BY timelineStartUs ASC")
    suspend fun getImageOverlays(projectId: String): List<ImageOverlayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putKeyframe(keyframe: KeyframeEntity)

    @Query("SELECT * FROM keyframes WHERE ownerId IN (:ownerIds) ORDER BY ownerId, property, timeUs")
    suspend fun getKeyframes(ownerIds: List<String>): List<KeyframeEntity>

    @Query("DELETE FROM keyframes WHERE ownerId = :ownerId")
    suspend fun deleteKeyframes(ownerId: String)
}

@Dao
interface ProxyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(proxy: ProxyEntity)

    @Query("SELECT * FROM proxies WHERE assetId = :assetId LIMIT 1")
    suspend fun getForAsset(assetId: String): ProxyEntity?

    @Query("SELECT proxies.* FROM proxies INNER JOIN media_assets ON media_assets.assetId = proxies.assetId WHERE media_assets.projectId = :projectId")
    suspend fun getForProject(projectId: String): List<ProxyEntity>

    @Query("UPDATE proxies SET status = 'STALE' WHERE assetId = :assetId")
    suspend fun markStale(assetId: String)

    @Query("DELETE FROM proxies WHERE assetId = :assetId")
    suspend fun deleteForAsset(assetId: String)
}

@Dao
interface SnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(snapshot: SnapshotEntity)

    @Query("SELECT * FROM snapshots WHERE projectId = :projectId ORDER BY createdAt DESC")
    suspend fun getForProject(projectId: String): List<SnapshotEntity>

    @Query("SELECT * FROM snapshots WHERE id = :id")
    suspend fun get(id: String): SnapshotEntity?

    @Query("DELETE FROM snapshots WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [
        ProjectEntity::class,
        MediaAssetEntity::class,
        ProjectSettingsEntity::class,
        TrackEntity::class,
        ClipEntity::class,
        TextOverlayEntity::class,
        ImageOverlayEntity::class,
        KeyframeEntity::class,
        ProxyEntity::class,
        SnapshotEntity::class,
        ExportJobEntity::class,
        ExportReportEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class VideoFlowDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun mediaAssetDao(): MediaAssetDao
    abstract fun editorDao(): EditorDao
    abstract fun proxyDao(): ProxyDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun exportDao(): ExportDao
}

fun MediaAssetEntity.toDomain() = MediaAsset(
    id = assetId,
    projectId = projectId,
    sourceUri = sourceUri,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    durationUs = durationUs,
    width = width,
    height = height,
    rotationDegrees = rotationDegrees,
    frameRate = frameRate,
    videoCodecMime = videoCodecMime,
    audioCodecMime = audioCodecMime,
    audioSampleRate = audioSampleRate,
    audioChannelCount = audioChannelCount,
    videoTrackCount = videoTrackCount,
    audioTrackCount = audioTrackCount,
    videoBitrate = videoBitrate,
    videoProfile = videoProfile,
    videoLevel = videoLevel,
    colorStandard = colorStandard,
    colorTransfer = colorTransfer,
    colorRange = colorRange,
    hdrStaticInfoPresent = hdrStaticInfoPresent,
    fingerprintSha256 = fingerprintSha256,
    fingerprintAlgorithm = fingerprintAlgorithm,
    fingerprintStrength = runCatching { FingerprintStrength.valueOf(fingerprintStrength) }.getOrDefault(FingerprintStrength.UNAVAILABLE),
    fingerprintSampledBytes = fingerprintSampledBytes,
    fingerprintNote = fingerprintNote,
    permissionPersisted = permissionPersisted,
    sourceStatus = runCatching { SourceStatus.valueOf(sourceStatus) }.getOrDefault(SourceStatus.UNKNOWN),
    createdAt = createdAt
)

fun ProjectWithMedia.toDomain() = VideoFlowProject(
    id = project.id,
    name = project.name,
    projectFormatVersion = project.projectFormatVersion,
    createdAt = project.createdAt,
    updatedAt = project.updatedAt,
    lastOpenedAt = project.lastOpenedAt,
    mediaAssets = media.map { it.toDomain() }
)
