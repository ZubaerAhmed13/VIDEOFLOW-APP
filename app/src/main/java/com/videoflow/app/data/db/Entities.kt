package com.videoflow.app.data.db

import androidx.room.Database
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
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
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
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

    @Insert
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MediaAssetDao {
    @Insert
    suspend fun insert(asset: MediaAssetEntity)

    @Update
    suspend fun update(asset: MediaAssetEntity)

    @Query("DELETE FROM media_assets WHERE assetId = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM media_assets WHERE assetId = :id")
    suspend fun get(id: String): MediaAssetEntity?

    @Query("SELECT * FROM media_assets WHERE projectId = :projectId AND sourceUri = :sourceUri LIMIT 1")
    suspend fun findByUri(projectId: String, sourceUri: String): MediaAssetEntity?

    @Query("SELECT * FROM media_assets WHERE projectId = :projectId AND fingerprintSha256 = :sha256")
    suspend fun findByFingerprint(projectId: String, sha256: String): List<MediaAssetEntity>
}

@Database(
    entities = [ProjectEntity::class, MediaAssetEntity::class],
    version = 1,
    exportSchema = true
)
abstract class VideoFlowDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun mediaAssetDao(): MediaAssetDao
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
    fingerprintStrength = runCatching { FingerprintStrength.valueOf(fingerprintStrength) }
        .getOrDefault(FingerprintStrength.UNAVAILABLE),
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
