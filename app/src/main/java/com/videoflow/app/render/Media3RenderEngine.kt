package com.videoflow.app.render

import android.content.Context
import android.media.MediaCodecInfo
import android.system.Os
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.videoflow.app.ai.watermark.AiModelPackManager
import com.videoflow.app.ai.watermark.SharedLamaRenderRuntime
import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.domain.ai.AiModelRole
import com.videoflow.app.domain.ai.AiReconstructionExportPolicy
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.domain.export.BitrateMode
import com.videoflow.app.domain.export.ExportFailureCode
import com.videoflow.app.domain.export.ExportMath
import com.videoflow.app.domain.export.ExportProblem
import com.videoflow.app.domain.export.ExportWarning
import com.videoflow.app.domain.export.FinalRenderPlan
import com.videoflow.app.domain.export.HdrPolicy
import com.videoflow.app.domain.export.OriginalRenderSource
import com.videoflow.app.domain.export.ResolvedExportSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RenderPipelineException(
    val failureCode: ExportFailureCode,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Native renderer with Step-4 local AI integration. Source media stays SAF/reference based. AI
 * reconstruction is applied to bounded original-resolution ROIs before crop/transform and encoded
 * samples are still muxed directly to the user-selected SAF destination.
 */
@Singleton
@UnstableApi
class Media3RenderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiRepository: AiWatermarkRepository,
    private val aiModelPackManager: AiModelPackManager
) : RenderEngine {
    private val renderMutex = Mutex()
    private val validator = OutputValidator(context.contentResolver)
    private val exportRoot = File(context.cacheDir, "render-raster")
    @Volatile private var activeTransformer: Transformer? = null
    @Volatile private var activeCompletion: CompletableDeferred<ExportResult>? = null
    @Volatile private var activeAiRuntime: SharedLamaRenderRuntime? = null

    override suspend fun prepare(
        plan: FinalRenderPlan,
        destination: OutputDestination,
        settings: ResolvedExportSettings
    ): RenderPreparationResult = withContext(Dispatchers.IO) {
        val sourceHasHdr = sourceHasHdr(plan)
        val capability = ExportCapabilityValidator.validate(settings, sourceHasHdr, AndroidEncoderCapabilitySource())
        val problems = capability.problems.toMutableList()
        val warnings = capability.warnings.toMutableList()

        if (plan.durationUs <= 0L) {
            problems += ExportProblem(ExportFailureCode.VALIDATION_FAILED, "Timeline is empty and has no frames to export.")
        }

        val allAiEffects = runCatching { aiRepository.load(plan.editorPlan.projectId) }
            .getOrElse {
                problems += ExportProblem(ExportFailureCode.VALIDATION_FAILED, "Could not read local AI edit state: ${it.message ?: "unknown error"}.")
                emptyList()
            }
        val aiEffects = AiReconstructionExportPolicy.activeForProject(allAiEffects, plan.editorPlan.clips)
        AiReconstructionExportPolicy.validationProblems(allAiEffects, plan.editorPlan.clips).forEach { message ->
            problems += ExportProblem(ExportFailureCode.VALIDATION_FAILED, message)
        }
        if (aiEffects.isNotEmpty()) {
            val clipsById = plan.editorPlan.clips.associateBy { it.id }
            aiEffects.forEach { effect ->
                val clip = clipsById[effect.clipId] ?: return@forEach
                val source = plan.originalSources[clip.assetId]
                if (source?.width == null || source.height == null || source.width <= 0 || source.height <= 0) {
                    problems += ExportProblem(
                        ExportFailureCode.VALIDATION_FAILED,
                        "Local AI reconstruction needs known original source dimensions for clip ${clip.id}. Relink/re-analyze the source before export."
                    )
                }
            }
            if (sourceHasHdr && settings.hdrPolicy != HdrPolicy.CONVERT_TO_SDR) {
                problems += ExportProblem(
                    ExportFailureCode.VALIDATION_FAILED,
                    "Local AI reconstruction currently processes SDR pixel buffers. This project contains HDR media; choose explicit HDR-to-SDR conversion or remove the AI reconstruction edit. HDR is never silently downgraded."
                )
            }
            runCatching { aiModelPackManager.ensureInstalled(AiModelRole.FINAL) }
                .onFailure { error ->
                    problems += ExportProblem(
                        ExportFailureCode.VALIDATION_FAILED,
                        "Final local LaMa model pack is unavailable or failed integrity validation: ${error.message ?: error::class.java.simpleName}."
                    )
                }
            warnings += ExportWarning(
                "LOCAL_AI_RENDER_REQUIRED",
                "Local AI reconstruction is active. Video will be rendered at the selected source-fidelity settings; Smart Copy cannot be used for pixel-changing AI edits."
            )
        }

        val estimate = runCatching {
            ExportMath.estimateOutputSize(plan.durationUs, settings.videoBitrate, if (expectsAudio(plan)) settings.audioBitrate else 0)
        }.getOrElse {
            problems += ExportProblem(ExportFailureCode.STORAGE_FULL, "Output size estimate overflowed: ${it.message ?: "unknown error"}.")
            null
        }

        val visibleVideoSources = visibleVideoSources(plan)
        if (hasMixedColourMetadata(visibleVideoSources)) {
            warnings += ExportWarning(
                "MIXED_SOURCE_COLOUR",
                "Visible video sources use different colour metadata. Export keeps the GPU/HDR policy explicit, but a single output track cannot preserve conflicting source standards/ranges simultaneously."
            )
        }

        val destinationProbe = probeDestination(destination)
        if (!destinationProbe.writable) {
            problems += ExportProblem(ExportFailureCode.DESTINATION_IO, "The selected Android document destination cannot be opened for direct MP4 writing.")
        }
        if (estimate != null && destinationProbe.availableBytes != null && destinationProbe.availableBytes < estimate.requiredBytes) {
            problems += ExportProblem(
                ExportFailureCode.STORAGE_FULL,
                "Destination needs about ${estimate.requiredBytes} bytes including safety margin, but only ${destinationProbe.availableBytes} bytes are available."
            )
        }
        if (estimate != null && destinationProbe.availableBytes == null && destinationProbe.writable) {
            warnings += ExportWarning(
                "DESTINATION_CAPACITY_UNKNOWN",
                "The SAF provider does not expose reliable free-space information. Estimated required size is ${estimate.requiredBytes} bytes; an out-of-space write will fail explicitly."
            )
        }

        val selected = capability.selectedEncoder
        if (selected == null || problems.isNotEmpty()) {
            return@withContext RenderPreparationResult(null, warnings.distinct(), problems.distinct())
        }
        warnings += ExportWarning(
            "DIRECT_SAF_MUX",
            "Encoded samples are written directly to the selected Android document using a file-descriptor MP4 muxer; no second full-size output copy is created."
        )
        RenderPreparationResult(
            preparation = RenderPreparation(
                plan = plan,
                destination = destination,
                settings = settings,
                encoder = selected,
                estimatedRequiredBytes = estimate?.requiredBytes ?: 0L,
                warnings = warnings.distinct(),
                usesTemporaryLocalOutput = false
            ),
            warnings = warnings.distinct(),
            problems = emptyList()
        )
    }

    override suspend fun render(
        preparation: RenderPreparation,
        listener: RenderProgressListener
    ): Result<RenderExecutionResult> = renderMutex.withLock {
        val startedAt = System.currentTimeMillis()
        val jobRoot = File(exportRoot, "job-${startedAt}-${Thread.currentThread().id}")
        val rasterRoot = File(jobRoot, "raster")
        jobRoot.mkdirs()
        val fallbackDetected = AtomicBoolean(false)
        var transformerForCleanup: Transformer? = null
        var aiRuntimeForCleanup: SharedLamaRenderRuntime? = null
        var completedSuccessfully = false
        try {
            listener.onProgress(0.01f)
            val allAiEffects = withContext(Dispatchers.IO) { aiRepository.load(preparation.plan.editorPlan.projectId) }
            val aiEffects = AiReconstructionExportPolicy.activeForProject(allAiEffects, preparation.plan.editorPlan.clips)
            if (aiEffects.isNotEmpty()) {
                aiRuntimeForCleanup = SharedLamaRenderRuntime.create(aiModelPackManager)
                activeAiRuntime = aiRuntimeForCleanup
            }
            val bundle = withContext(Dispatchers.IO) {
                Media3CompositionBuilder(RenderRasterAssets(rasterRoot)).build(
                    plan = preparation.plan,
                    settings = preparation.settings,
                    aiEffects = aiEffects,
                    aiRuntime = aiRuntimeForCleanup
                )
            }
            listener.onProgress(0.04f)

            val completion = CompletableDeferred<ExportResult>()
            activeCompletion = completion
            val transformer = withContext(Dispatchers.Main.immediate) {
                buildTransformer(preparation, completion, fallbackDetected).also {
                    activeTransformer = it
                    transformerForCleanup = it
                    it.start(bundle.composition, File(jobRoot, "direct-saf-output.mp4").absolutePath)
                }
            }

            val exportResult = coroutineScope {
                val progressJob = launch {
                    val holder = ProgressHolder()
                    while (isActive && !completion.isCompleted) {
                        val percent = withContext(Dispatchers.Main.immediate) {
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else -1
                        }
                        if (percent >= 0) listener.onProgress(0.04f + (percent.coerceIn(0, 100) / 100f) * 0.90f)
                        delay(400)
                    }
                }
                try {
                    completion.await()
                } finally {
                    progressJob.cancel()
                }
            }

            if (fallbackDetected.get()) {
                throw RenderPipelineException(
                    ExportFailureCode.ENCODER_INIT_FAILED,
                    "Media3 requested a transformation fallback. Professional export refuses silent codec, HDR or resolution changes."
                )
            }

            listener.onProgress(0.96f)
            val expectedHdr = expectedHdr(preparation.plan, preparation.settings.hdrPolicy)
            val expectedColour = colourExpectation(preparation.plan, preparation.settings.hdrPolicy)
            val finalValidation = withContext(Dispatchers.IO) {
                validator.validateUri(
                    preparation.destination.uri,
                    preparation.settings,
                    preparation.plan.durationUs,
                    expectsAudio(preparation.plan),
                    expectedHdr,
                    expectedColour
                )
            }
            if (!finalValidation.passed) {
                throw RenderPipelineException(
                    ExportFailureCode.VALIDATION_FAILED,
                    "Direct SAF output failed validation: ${finalValidation.problems.joinToString(" ")}"
                )
            }
            listener.onProgress(1f)
            completedSuccessfully = true
            Result.success(
                RenderExecutionResult(
                    outputUri = preparation.destination.uri,
                    outputBytes = finalValidation.fileSizeBytes,
                    renderDurationMs = System.currentTimeMillis() - startedAt,
                    videoEncoderName = exportResult.videoEncoderName,
                    audioEncoderName = exportResult.audioEncoderName,
                    validation = finalValidation
                )
            )
        } catch (cancelled: CancellationException) {
            aiRuntimeForCleanup?.cancel()
            withContext(NonCancellable + Dispatchers.Main.immediate) { transformerForCleanup?.cancel() }
            Result.failure(RenderPipelineException(ExportFailureCode.CANCELLED, "Export was cancelled.", cancelled))
        } catch (t: Throwable) {
            val wrapped = if (t is RenderPipelineException) t else mapFailure(t)
            Result.failure(wrapped)
        } finally {
            activeTransformer = null
            activeCompletion = null
            activeAiRuntime = null
            runCatching { aiRuntimeForCleanup?.close() }
            if (!completedSuccessfully) {
                withContext(NonCancellable + Dispatchers.IO) { truncateDestination(preparation.destination) }
            }
            withContext(NonCancellable + Dispatchers.IO) { jobRoot.deleteRecursively() }
        }
    }

    override suspend fun cancel() {
        val cancellation = RenderPipelineException(ExportFailureCode.CANCELLED, "Export cancelled by user.")
        activeCompletion?.completeExceptionally(cancellation)
        activeAiRuntime?.cancel()
        withContext(NonCancellable + Dispatchers.Main.immediate) {
            activeTransformer?.cancel()
        }
    }

    private fun buildTransformer(
        preparation: RenderPreparation,
        completion: CompletableDeferred<ExportResult>,
        fallbackDetected: AtomicBoolean
    ): Transformer {
        val selectedMode = chooseBitrateMode(preparation)
        val videoSettings = VideoEncoderSettings.Builder()
            .setBitrate(preparation.settings.videoBitrate)
            .setBitrateMode(selectedMode)
            .build()
        val audioSettings = AudioEncoderSettings.Builder()
            .setBitrate(preparation.settings.audioBitrate)
            .build()
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setEnableFallback(false)
            .setEnableFormatFallback(false)
            .setRequestedVideoEncoderSettings(videoSettings)
            .setRequestedAudioEncoderSettings(audioSettings)
            .build()
        val transformerListener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                completion.complete(exportResult)
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                completion.completeExceptionally(exportException)
            }

            override fun onFallbackApplied(
                composition: Composition,
                originalTransformationRequest: TransformationRequest,
                fallbackTransformationRequest: TransformationRequest
            ) {
                fallbackDetected.set(true)
            }
        }
        return Transformer.Builder(context)
            .setVideoMimeType(preparation.settings.videoCodec.mimeType)
            .setAudioMimeType(preparation.settings.audioCodec.mimeType)
            .setEncoderFactory(encoderFactory)
            .setMuxerFactory(SafMediaMuxerFactory(context.contentResolver, preparation.destination.uri))
            .addListener(transformerListener)
            .build()
    }

    private fun chooseBitrateMode(preparation: RenderPreparation): Int {
        val capability = preparation.encoder
        val mode = when (preparation.settings.bitrateMode) {
            BitrateMode.CQ -> if (capability.supportsCq) BitrateMode.CQ else null
            BitrateMode.VBR -> if (capability.supportsVbr) BitrateMode.VBR else null
            BitrateMode.CBR -> if (capability.supportsCbr) BitrateMode.CBR else null
            BitrateMode.AUTO -> null
        } ?: when {
            capability.supportsCq -> BitrateMode.CQ
            capability.supportsVbr -> BitrateMode.VBR
            capability.supportsCbr -> BitrateMode.CBR
            else -> throw RenderPipelineException(ExportFailureCode.ENCODER_INIT_FAILED, "Encoder ${capability.name} exposes no supported bitrate mode.")
        }
        return when (mode) {
            BitrateMode.CQ -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ
            BitrateMode.VBR -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
            BitrateMode.CBR -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
            BitrateMode.AUTO -> error("AUTO must be resolved before encoder creation")
        }
    }

    private data class DestinationProbe(val writable: Boolean, val availableBytes: Long?)

    private fun probeDestination(destination: OutputDestination): DestinationProbe = runCatching {
        context.contentResolver.openFileDescriptor(destination.uri, "rw")?.use { pfd ->
            val available = runCatching {
                val stat = Os.fstatvfs(pfd.fileDescriptor)
                Math.multiplyExact(stat.f_bavail, if (stat.f_frsize > 0) stat.f_frsize else stat.f_bsize)
            }.getOrNull()
            DestinationProbe(true, available)
        } ?: DestinationProbe(false, null)
    }.getOrDefault(DestinationProbe(false, null))

    private fun truncateDestination(destination: OutputDestination) {
        runCatching { context.contentResolver.openFileDescriptor(destination.uri, "rwt")?.close() }
    }

    private fun expectsAudio(plan: FinalRenderPlan): Boolean {
        val tracks = plan.editorPlan.tracks.associateBy { it.id }
        val audioCapable = tracks.values.filter { it.type == TrackType.AUDIO || it.type == TrackType.VIDEO }
        val solo = audioCapable.filter { it.solo }
        val audible = (if (solo.isNotEmpty()) solo else audioCapable).filterNot { it.muted }.map { it.id }.toSet()
        return plan.editorPlan.clips.any { clip ->
            clip.enabled && clip.trackId in audible && plan.originalSources[clip.assetId]?.audioCodecMime != null
        }
    }

    private fun visibleVideoSources(plan: FinalRenderPlan): List<OriginalRenderSource> {
        val visibleVideoTrackIds = plan.editorPlan.tracks
            .filter { it.type == TrackType.VIDEO && it.visible }
            .map { it.id }
            .toSet()
        return plan.editorPlan.clips
            .asSequence()
            .filter { it.enabled && it.trackId in visibleVideoTrackIds }
            .mapNotNull { plan.originalSources[it.assetId] }
            .distinctBy { it.assetId }
            .toList()
    }

    private fun hasMixedColourMetadata(sources: List<OriginalRenderSource>): Boolean {
        fun <T> mixed(selector: (OriginalRenderSource) -> T?): Boolean = sources.mapNotNull(selector).distinct().size > 1
        return mixed { it.colorStandard } || mixed { it.colorRange } || mixed { it.colorTransfer }
    }

    private fun colourExpectation(plan: FinalRenderPlan, policy: HdrPolicy): OutputColourExpectation? {
        if (policy == HdrPolicy.CONVERT_TO_SDR) return null
        val sources = visibleVideoSources(plan)
        if (sources.isEmpty()) return null
        fun homogeneous(selector: (OriginalRenderSource) -> Int?): Int? {
            val values = sources.mapNotNull(selector).distinct()
            return values.singleOrNull()
        }
        return OutputColourExpectation(
            colorStandard = homogeneous { it.colorStandard },
            colorRange = homogeneous { it.colorRange },
            colorTransfer = homogeneous { it.colorTransfer }
        ).takeIf { it.hasAny }
    }

    private fun sourceHasHdr(plan: FinalRenderPlan): Boolean = plan.originalSources.values.any { source ->
        source.hdrStaticInfoPresent || source.colorTransfer == C.COLOR_TRANSFER_HLG || source.colorTransfer == C.COLOR_TRANSFER_ST2084
    }

    private fun expectedHdr(plan: FinalRenderPlan, policy: HdrPolicy): Boolean =
        sourceHasHdr(plan) && policy != HdrPolicy.CONVERT_TO_SDR

    private fun mapFailure(t: Throwable): RenderPipelineException {
        if (t is RenderPipelineException) return t
        val code = when (t) {
            is ExportException -> when (t.errorCode) {
                ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
                ExportException.ERROR_CODE_ENCODER_INIT_FAILED -> ExportFailureCode.ENCODER_INIT_FAILED
                ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                ExportException.ERROR_CODE_DECODER_INIT_FAILED -> ExportFailureCode.DECODER_FAILED
                ExportException.ERROR_CODE_MUXING_FAILED,
                ExportException.ERROR_CODE_MUXING_TIMEOUT -> ExportFailureCode.MUXER_FAILED
                else -> ExportFailureCode.UNKNOWN
            }
            is java.io.IOException -> ExportFailureCode.DESTINATION_IO
            else -> ExportFailureCode.UNKNOWN
        }
        return RenderPipelineException(code, t.message ?: t::class.java.simpleName, t)
    }
}
