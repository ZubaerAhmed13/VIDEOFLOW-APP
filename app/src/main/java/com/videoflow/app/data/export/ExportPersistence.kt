package com.videoflow.app.data.export

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.videoflow.app.data.db.ProjectEntity
import com.videoflow.app.domain.export.ExportFailureCode
import com.videoflow.app.domain.export.ExportJob
import com.videoflow.app.domain.export.ExportJobStatus
import com.videoflow.app.domain.export.ExportReport
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "export_jobs",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId"), Index("status"), Index("createdAt")]
)
data class ExportJobEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val destinationUri: String,
    val displayName: String,
    val settingsJson: String,
    val status: String,
    val progress: Float,
    val createdAt: Long,
    val startedAt: Long?,
    val completedAt: Long?,
    val failureCode: String?,
    val failureMessage: String?
)

@Entity(
    tableName = "export_reports",
    foreignKeys = [ForeignKey(
        entity = ExportJobEntity::class,
        parentColumns = ["id"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["jobId"], unique = true), Index("createdAt")]
)
data class ExportReportEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val outputWidth: Int,
    val outputHeight: Int,
    val frameRateNumerator: Int,
    val frameRateDenominator: Int,
    val videoCodecMime: String,
    val encoderName: String?,
    val videoBitrate: Int,
    val audioCodecMime: String?,
    val audioBitrate: Int?,
    val colorStandard: Int?,
    val colorRange: Int?,
    val colorTransfer: Int?,
    val hdrPreserved: Boolean,
    val durationUs: Long,
    val fileSizeBytes: Long,
    val renderDurationMs: Long,
    val validationPassed: Boolean,
    val createdAt: Long
)

@Dao
interface ExportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putJob(job: ExportJobEntity)

    @Query("SELECT * FROM export_jobs WHERE id = :id")
    suspend fun getJob(id: String): ExportJobEntity?

    @Query("SELECT * FROM export_jobs WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeJobs(projectId: String): Flow<List<ExportJobEntity>>

    @Query("SELECT * FROM export_jobs WHERE status IN ('QUEUED','PREPARING','RENDERING','FINALIZING','VALIDATING') ORDER BY createdAt ASC")
    suspend fun activeJobs(): List<ExportJobEntity>

    @Query("UPDATE export_jobs SET status = :status, progress = :progress, startedAt = COALESCE(startedAt, :startedAt), completedAt = :completedAt, failureCode = :failureCode, failureMessage = :failureMessage WHERE id = :id")
    suspend fun updateState(
        id: String,
        status: String,
        progress: Float,
        startedAt: Long?,
        completedAt: Long?,
        failureCode: String?,
        failureMessage: String?
    )

    @Query("UPDATE export_jobs SET status = 'INTERRUPTED', failureCode = 'UNKNOWN', failureMessage = :message, completedAt = :now WHERE status IN ('PREPARING','RENDERING','FINALIZING','VALIDATING')")
    suspend fun markInterruptedJobs(now: Long, message: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putReport(report: ExportReportEntity)

    @Query("SELECT * FROM export_reports WHERE jobId = :jobId LIMIT 1")
    suspend fun getReport(jobId: String): ExportReportEntity?
}

fun ExportJobEntity.toDomain() = ExportJob(
    id = id,
    projectId = projectId,
    destinationUri = destinationUri,
    displayName = displayName,
    settingsJson = settingsJson,
    status = runCatching { ExportJobStatus.valueOf(status) }.getOrDefault(ExportJobStatus.FAILED),
    progress = progress.coerceIn(0f, 1f),
    createdAt = createdAt,
    startedAt = startedAt,
    completedAt = completedAt,
    failureCode = failureCode?.let { runCatching { ExportFailureCode.valueOf(it) }.getOrNull() },
    failureMessage = failureMessage
)

fun ExportReportEntity.toDomain() = ExportReport(
    id = id,
    jobId = jobId,
    outputWidth = outputWidth,
    outputHeight = outputHeight,
    frameRateNumerator = frameRateNumerator,
    frameRateDenominator = frameRateDenominator,
    videoCodecMime = videoCodecMime,
    encoderName = encoderName,
    videoBitrate = videoBitrate,
    audioCodecMime = audioCodecMime,
    audioBitrate = audioBitrate,
    colorStandard = colorStandard,
    colorRange = colorRange,
    colorTransfer = colorTransfer,
    hdrPreserved = hdrPreserved,
    durationUs = durationUs,
    fileSizeBytes = fileSizeBytes,
    renderDurationMs = renderDurationMs,
    validationPassed = validationPassed,
    createdAt = createdAt
)
