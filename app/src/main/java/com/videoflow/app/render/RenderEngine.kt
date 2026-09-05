package com.videoflow.app.render

import android.net.Uri
import com.videoflow.app.domain.export.ExportProblem
import com.videoflow.app.domain.export.ExportWarning
import com.videoflow.app.domain.export.FinalRenderPlan
import com.videoflow.app.domain.export.ResolvedExportSettings


data class OutputDestination(
    val uri: Uri,
    val displayName: String
)

data class RenderPreparation(
    val plan: FinalRenderPlan,
    val destination: OutputDestination,
    val settings: ResolvedExportSettings,
    val encoder: EncoderCapability,
    val estimatedRequiredBytes: Long,
    val warnings: List<ExportWarning>,
    val usesTemporaryLocalOutput: Boolean
)

data class RenderExecutionResult(
    val outputUri: Uri,
    val outputBytes: Long,
    val renderDurationMs: Long,
    val videoEncoderName: String?,
    val audioEncoderName: String?,
    val validation: OutputValidationResult
)

data class RenderPreparationResult(
    val preparation: RenderPreparation?,
    val warnings: List<ExportWarning>,
    val problems: List<ExportProblem>
) {
    val ready: Boolean get() = preparation != null && problems.isEmpty()
}

fun interface RenderProgressListener {
    fun onProgress(progress: Float)
}

interface RenderEngine {
    suspend fun prepare(
        plan: FinalRenderPlan,
        destination: OutputDestination,
        settings: ResolvedExportSettings
    ): RenderPreparationResult

    suspend fun render(
        preparation: RenderPreparation,
        listener: RenderProgressListener = RenderProgressListener { }
    ): Result<RenderExecutionResult>

    suspend fun cancel()
}
