@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.videoflow.app.export

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import com.videoflow.app.data.export.ExportRepository
import com.videoflow.app.domain.export.ExportFailureCode
import com.videoflow.app.domain.export.ExportJobStatus
import com.videoflow.app.domain.export.ExportMath
import com.videoflow.app.domain.export.ExportMode
import com.videoflow.app.domain.export.ExportReport
import com.videoflow.app.domain.export.ExportSettingsCodec
import com.videoflow.app.domain.export.ExportSize
import com.videoflow.app.domain.export.SourcePreservationPolicy
import com.videoflow.app.render.OutputColourExpectation
import com.videoflow.app.render.OutputDestination
import com.videoflow.app.render.OutputValidator
import com.videoflow.app.render.RenderEngine
import com.videoflow.app.render.RenderPipelineException
import com.videoflow.app.render.RenderProgressListener
import com.videoflow.app.render.SmartCopyEngine
import com.videoflow.app.render.SmartCopyException
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val repository: ExportRepository,
    private val renderEngine: RenderEngine,
    private val smartCopyEngine: SmartCopyEngine
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

            val decoded = ExportSettingsCodec.decode(job.settingsJson)
            val requested = if (decoded.mode == ExportMode.MATCH_SOURCE || decoded.mode == ExportMode.SMART_COPY) {
                SourcePreservationPolicy.settingsForMode(plan, decoded, decoded.mode)
            } else decoded
            val resolved = ExportMath.resolve(
                ExportSize(plan.editorPlan.width, plan.editorPlan.height),
                plan.editorPlan.frameRate,
                requested
            )

            if (requested.mode == ExportMode.SMART_COPY) {
                return@withLock executeSmartCopy(jobId, job.destinationUri, plan, resolved, onProgress)
            }

            val prepared = renderEngine.prepare(
                plan,
                OutputDestination(Uri.parse(job.destinationUri), job.displayName),
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
            val code = when (t) {
                is RenderPipelineException -> t.failureCode
                is SmartCopyException -> ExportFailureCode.VALIDATION_FAILED
                else -> ExportFailureCode.UNKNOWN
            }
            val status = if (code == ExportFailureCode.CANCELLED) ExportJobStatus.CANCELLED else ExportJobStatus.FAILED
            repository.updateJob(jobId, status, 0f, code, t.message ?: t::class.java.simpleName)
            onProgress(status, 0f)
            status
        }
    }

    private suspend fun executeSmartCopy(
        jobId: String,
        destinationUri: String,
        plan: com.videoflow.app.domain.export.FinalRenderPlan,
        resolved: com.videoflow.app.domain.export.ResolvedExportSettings,
        onProgress: (ExportJobStatus, Float) -> Unit
    ): ExportJobStatus {
        val preflight = smartCopyEngine.preflight(plan)
        if (!preflight.eligible) {
            return fail(
                jobId,
                ExportFailureCode.VALIDATION_FAILED,
                "Smart Copy is unavailable for this exact edit. ${preflight.reasons.joinToString(" ")} No rendered fallback was started.",
                onProgress
            )
        }
        update(jobId, ExportJobStatus.RENDERING, 0.10f, onProgress)
        val started = System.currentTimeMillis()
        val destination = Uri.parse(destinationUri)
        val copied = try {
            smartCopyEngine.copy(plan, destination)
        } catch (error: SmartCopyException) {
            return fail(jobId, ExportFailureCode.VALIDATION_FAILED, error.message ?: "Smart Copy failed.", onProgress)
        }
        update(jobId, ExportJobStatus.VALIDATING, 0.95f, onProgress)
        val policy = SourcePreservationPolicy.analyze(plan)
        val profile = policy.profile
        val expectAudio = plan.editorPlan.clips.any { clip ->
            clip.enabled && plan.originalSources[clip.assetId]?.audioCodecMime != null
        }
        val validation = OutputValidator(context.contentResolver).validateUri(
            destination,
            expected = resolved,
            expectedDurationUs = plan.durationUs,
            expectAudio = expectAudio,
            expectedHdr = profile.hdrPresent,
            expectedColour = OutputColourExpectation(profile.colorStandard, profile.colorRange, profile.colorTransfer)
        )
        if (!validation.passed) {
            return fail(
                jobId,
                ExportFailureCode.VALIDATION_FAILED,
                "Smart Copy wrote an output but certification failed: ${validation.problems.joinToString(" ")}",
                onProgress
            )
        }
        val outputVideo = validation.video
        val hdrPreserved = outputVideo?.colorTransfer == C.COLOR_TRANSFER_HLG || outputVideo?.colorTransfer == C.COLOR_TRANSFER_ST2084
        repository.putReport(
            ExportReport(
                id = UUID.randomUUID().toString(),
                jobId = jobId,
                outputWidth = resolved.size.width,
                outputHeight = resolved.size.height,
                frameRateNumerator = resolved.frameRate.numerator,
                frameRateDenominator = resolved.frameRate.denominator,
                videoCodecMime = validation.video?.mimeType ?: resolved.videoCodec.mimeType,
                encoderName = "Smart Copy (no video re-encoding)",
                videoBitrate = resolved.videoBitrate,
                audioCodecMime = validation.audio?.mimeType,
                audioBitrate = null,
                colorStandard = outputVideo?.colorStandard,
                colorRange = outputVideo?.colorRange,
                colorTransfer = outputVideo?.colorTransfer,
                hdrPreserved = hdrPreserved,
                durationUs = validation.durationUs ?: copied.durationUs,
                fileSizeBytes = validation.fileSizeBytes,
                renderDurationMs = System.currentTimeMillis() - started,
                validationPassed = true,
                createdAt = System.currentTimeMillis()
            )
        )
        update(jobId, ExportJobStatus.COMPLETED, 1f, onProgress)
        return ExportJobStatus.COMPLETED
    }

    suspend fun cancel() {
        smartCopyEngine.cancel()
        renderEngine.cancel()
    }

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
