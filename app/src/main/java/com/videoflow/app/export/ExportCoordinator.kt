@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.videoflow.app.export

import androidx.media3.common.C
import com.videoflow.app.data.export.ExportRepository
import com.videoflow.app.domain.export.ExportFailureCode
import com.videoflow.app.domain.export.ExportJobStatus
import com.videoflow.app.domain.export.ExportMath
import com.videoflow.app.domain.export.ExportReport
import com.videoflow.app.domain.export.ExportSettingsCodec
import com.videoflow.app.domain.export.ExportSize
import com.videoflow.app.render.OutputDestination
import com.videoflow.app.render.RenderEngine
import com.videoflow.app.render.RenderPipelineException
import com.videoflow.app.render.RenderProgressListener
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ExportCoordinator @Inject constructor(
    private val repository: ExportRepository,
    private val renderEngine: RenderEngine
) {
    private val queueMutex = Mutex()

    suspend fun execute(
        jobId: String,
        onProgress: (ExportJobStatus, Float) -> Unit = { _, _ -> }
    ): ExportJobStatus = queueMutex.withLock {
        val job = repository.getJob(jobId) ?: return@withLock ExportJobStatus.FAILED
        try {
            update(jobId, ExportJobStatus.PREPARING, 0.01f, onProgress)
            val compile = repository.compileFinalPlan(job.projectId)
            val plan = compile.plan
            if (plan == null || compile.problems.isNotEmpty()) {
                val problem = compile.problems.firstOrNull()
                return@withLock fail(
                    jobId,
                    problem?.code ?: ExportFailureCode.VALIDATION_FAILED,
                    problem?.message ?: "Final RenderPlan could not be compiled.",
                    onProgress
                )
            }

            val requested = ExportSettingsCodec.decode(job.settingsJson)
            val resolved = ExportMath.resolve(
                ExportSize(plan.editorPlan.width, plan.editorPlan.height),
                plan.editorPlan.frameRate,
                requested
            )
            val prepared = renderEngine.prepare(
                plan,
                OutputDestination(android.net.Uri.parse(job.destinationUri), job.displayName),
                resolved
            )
            val preparation = prepared.preparation
            if (preparation == null || prepared.problems.isNotEmpty()) {
                val problem = prepared.problems.firstOrNull()
                return@withLock fail(
                    jobId,
                    problem?.code ?: ExportFailureCode.ENCODER_INIT_FAILED,
                    problem?.message ?: "Export preflight failed.",
                    onProgress
                )
            }

            update(jobId, ExportJobStatus.RENDERING, 0.03f, onProgress)
            val result = coroutineScope {
                val progress = Channel<Float>(Channel.CONFLATED)
                val writer = launch {
                    for (value in progress) {
                        val p = value.coerceIn(0f, 1f)
                        val status = when {
                            p >= 0.98f -> ExportJobStatus.VALIDATING
                            p >= 0.90f -> ExportJobStatus.FINALIZING
                            else -> ExportJobStatus.RENDERING
                        }
                        update(jobId, status, p, onProgress)
                    }
                }
                try {
                    renderEngine.render(preparation, RenderProgressListener { progress.trySend(it) })
                } finally {
                    progress.close()
                    writer.join()
                }
            }

            val execution = result.getOrElse { error ->
                val code = (error as? RenderPipelineException)?.failureCode ?: ExportFailureCode.UNKNOWN
                val status = if (code == ExportFailureCode.CANCELLED) ExportJobStatus.CANCELLED else ExportJobStatus.FAILED
                repository.updateJob(jobId, status, 0f, code, error.message ?: "Native export failed.")
                onProgress(status, 0f)
                return@withLock status
            }
            if (!execution.validation.passed) {
                return@withLock fail(
                    jobId,
                    ExportFailureCode.VALIDATION_FAILED,
                    "Native export returned an output that did not pass certification: ${execution.validation.problems.joinToString(" ")}",
                    onProgress
                )
            }

            val outputVideo = execution.validation.video
            val hdrPreserved = outputVideo?.colorTransfer == C.COLOR_TRANSFER_HLG || outputVideo?.colorTransfer == C.COLOR_TRANSFER_ST2084
            repository.putReport(
                ExportReport(
                    id = UUID.randomUUID().toString(),
                    jobId = jobId,
                    outputWidth = resolved.size.width,
                    outputHeight = resolved.size.height,
                    frameRateNumerator = resolved.frameRate.numerator,
                    frameRateDenominator = resolved.frameRate.denominator,
                    videoCodecMime = resolved.videoCodec.mimeType,
                    encoderName = execution.videoEncoderName,
                    videoBitrate = resolved.videoBitrate,
                    audioCodecMime = execution.validation.audio?.mimeType,
                    audioBitrate = execution.validation.audio?.let { resolved.audioBitrate },
                    colorStandard = outputVideo?.colorStandard,
                    colorRange = outputVideo?.colorRange,
                    colorTransfer = outputVideo?.colorTransfer,
                    hdrPreserved = hdrPreserved,
                    durationUs = execution.validation.durationUs ?: plan.durationUs,
                    fileSizeBytes = execution.outputBytes,
                    renderDurationMs = execution.renderDurationMs,
                    validationPassed = true,
                    createdAt = System.currentTimeMillis()
                )
            )
            update(jobId, ExportJobStatus.COMPLETED, 1f, onProgress)
            ExportJobStatus.COMPLETED
        } catch (t: Throwable) {
            val code = (t as? RenderPipelineException)?.failureCode ?: ExportFailureCode.UNKNOWN
            val status = if (code == ExportFailureCode.CANCELLED) ExportJobStatus.CANCELLED else ExportJobStatus.FAILED
            repository.updateJob(jobId, status, 0f, code, t.message ?: t::class.java.simpleName)
            onProgress(status, 0f)
            status
        }
    }

    suspend fun cancel() = renderEngine.cancel()

    private suspend fun update(
        jobId: String,
        status: ExportJobStatus,
        progress: Float,
        callback: (ExportJobStatus, Float) -> Unit
    ) {
        repository.updateJob(jobId, status, progress)
        callback(status, progress)
    }

    private suspend fun fail(
        jobId: String,
        code: ExportFailureCode,
        message: String,
        callback: (ExportJobStatus, Float) -> Unit
    ): ExportJobStatus {
        repository.updateJob(jobId, ExportJobStatus.FAILED, 0f, code, message)
        callback(ExportJobStatus.FAILED, 0f)
        return ExportJobStatus.FAILED
    }
}
