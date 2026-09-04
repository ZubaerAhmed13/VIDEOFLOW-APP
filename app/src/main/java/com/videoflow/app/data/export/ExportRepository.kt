package com.videoflow.app.data.export

import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.data.db.toDomain
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.domain.editor.PlanBuilder
import com.videoflow.app.domain.export.ExportFailureCode
import com.videoflow.app.domain.export.ExportJob
import com.videoflow.app.domain.export.ExportJobStatus
import com.videoflow.app.domain.export.ExportReport
import com.videoflow.app.domain.export.ExportSettings
import com.videoflow.app.domain.export.ExportSettingsCodec
import com.videoflow.app.domain.export.FinalRenderCompileResult
import com.videoflow.app.domain.export.FinalRenderPlanCompiler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportRepository @Inject constructor(
    private val db: VideoFlowDatabase,
    private val editorRepository: EditorRepository
) {
    suspend fun compileFinalPlan(projectId: String): FinalRenderCompileResult = withContext(Dispatchers.IO) {
        val editor = editorRepository.load(projectId)
        val project = db.projectDao().get(projectId)?.toDomain()
            ?: error("Project $projectId does not exist")
        FinalRenderPlanCompiler.compile(
            editorPlan = PlanBuilder.render(editor.settings, editor.state),
            assets = project.mediaAssets
        )
    }

    fun observeJobs(projectId: String): Flow<List<ExportJob>> =
        db.exportDao().observeJobs(projectId).map { rows -> rows.map { it.toDomain() } }

    suspend fun getJob(jobId: String): ExportJob? = withContext(Dispatchers.IO) {
        db.exportDao().getJob(jobId)?.toDomain()
    }

    suspend fun createJob(
        projectId: String,
        destinationUri: String,
        displayName: String,
        settings: ExportSettings,
        now: Long = System.currentTimeMillis()
    ): ExportJob = withContext(Dispatchers.IO) {
        require(destinationUri.isNotBlank())
        val row = ExportJobEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            destinationUri = destinationUri,
            displayName = displayName.ifBlank { "VideoFlow_${now}.mp4" },
            settingsJson = ExportSettingsCodec.encode(settings),
            status = ExportJobStatus.QUEUED.name,
            progress = 0f,
            createdAt = now,
            startedAt = null,
            completedAt = null,
            failureCode = null,
            failureMessage = null
        )
        db.exportDao().putJob(row)
        row.toDomain()
    }

    suspend fun updateJob(
        jobId: String,
        status: ExportJobStatus,
        progress: Float,
        failureCode: ExportFailureCode? = null,
        failureMessage: String? = null,
        now: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val current = requireNotNull(db.exportDao().getJob(jobId)) { "Export job $jobId not found" }
        val startedAt = if (status in setOf(
                ExportJobStatus.PREPARING,
                ExportJobStatus.RENDERING,
                ExportJobStatus.FINALIZING,
                ExportJobStatus.VALIDATING,
                ExportJobStatus.COMPLETED
            )) current.startedAt ?: now else current.startedAt
        val completedAt = if (status in setOf(
                ExportJobStatus.COMPLETED,
                ExportJobStatus.FAILED,
                ExportJobStatus.CANCELLED,
                ExportJobStatus.INTERRUPTED
            )) now else null
        db.exportDao().updateState(
            id = jobId,
            status = status.name,
            progress = progress.coerceIn(0f, 1f),
            startedAt = startedAt,
            completedAt = completedAt,
            failureCode = failureCode?.name,
            failureMessage = failureMessage
        )
    }

    suspend fun markInterruptedAfterProcessRestart(now: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        db.exportDao().markInterruptedJobs(now, "Rendering process was interrupted before completion. Render Again is required.")
    }

    suspend fun putReport(report: ExportReport) = withContext(Dispatchers.IO) {
        db.exportDao().putReport(report.toEntity())
    }

    suspend fun getReport(jobId: String): ExportReport? = withContext(Dispatchers.IO) {
        db.exportDao().getReport(jobId)?.toDomain()
    }

    private fun ExportReport.toEntity() = ExportReportEntity(
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
}
