package com.videoflow.app.render

import android.content.Context
import android.media.MediaCodecInfo
import android.os.StatFs
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
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.domain.export.BitrateMode
import com.videoflow.app.domain.export.ExportFailureCode
import com.videoflow.app.domain.export.ExportMath
import com.videoflow.app.domain.export.ExportProblem
import com.videoflow.app.domain.export.ExportWarning
import com.videoflow.app.domain.export.FinalRenderPlan
import com.videoflow.app.domain.export.HdrPolicy
import com.videoflow.app.domain.export.ResolvedExportSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
 * Native Step 3 renderer. Transformer writes to bounded app-private temporary storage because its
 * stable export API targets filesystem paths; the completed MP4 is then streamed to the user's SAF
 * destination with a fixed buffer. Original media remains reference-based and is never copied into
 * RAM or duplicated on import.
 */
@Singleton
@UnstableApi
class Media3RenderEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : RenderEngine {
    private val renderMutex = Mutex()
    private val validator = OutputValidator(context.contentResolver)
    private val exportRoot = File(context.filesDir, "exports")
    @Volatile private var activeTransformer: Transformer? = null
    @Volatile private var activeCompletion: CompletableDeferred<ExportResult>? = null

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
        val estimate = runCatching {
            ExportMath.estimateOutputSize(plan.durationUs, settings.videoBitrate, if (expectsAudio(plan)) settings.audioBitrate else 0)
        }.getOrElse {
            problems += ExportProblem(ExportFailureCode.STORAGE_FULL, "Output size estimate overflowed: ${it.message ?: "unknown error"}.")
            null
        }

        exportRoot.mkdirs()
        val requiredBytes = estimate?.requiredBytes ?: Long.MAX_VALUE
        if (estimate != null) {
            val tempHeadroom = maxOf(256L * 1024L * 1024L, estimate.requiredBytes / 4L)
            val totalTempRequirement = runCatching { Math.addExact(estimate.requiredBytes, tempHeadroom) }.getOrDefault(Long.MAX_VALUE)
            val available = runCatching { StatFs(exportRoot.absolutePath).availableBytes }.getOrDefault(0L)
            if (available < totalTempRequirement) {
                problems += ExportProblem(
                    ExportFailureCode.STORAGE_FULL,
                    "Temporary export needs about $totalTempRequirement bytes but only $available bytes are available in app storage."
                )
            }
        }

        if (!canOpenDestination(destination)) {
            problems += ExportProblem(ExportFailureCode.DESTINATION_IO, "The selected Android document destination cannot be opened for writing.")
        }

        val selected = capability.selectedEncoder
        if (selected == null || problems.isNotEmpty()) {
            return@withContext RenderPreparationResult(null, warnings, problems.distinct())
        }
        warnings += ExportWarning(
            "TEMP_SAF_BRIDGE",
            "Rendering uses bounded app-private temporary storage, then streams the completed MP4 to the selected Android document URI."
        )
        RenderPreparationResult(
            preparation = RenderPreparation(
                plan = plan,
                destination = destination,
                settings = settings,
                encoder = selected,
                estimatedRequiredBytes = requiredBytes,
                warnings = warnings.distinct(),
                usesTemporaryLocalOutput = true
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
        val tempOutput = File(jobRoot, "render.mp4")
        jobRoot.mkdirs()
        val fallbackDetected = AtomicBoolean(false)
        var transformerForCleanup: Transformer? = null
        try {
            listener.onProgress(0.01f)
            val bundle = withContext(Dispatchers.IO) {
                Media3CompositionBuilder(RenderRasterAssets(rasterRoot)).build(preparation.plan, preparation.settings)
            }
            listener.onProgress(0.04f)

            val completion = CompletableDeferred<ExportResult>()
            activeCompletion = completion
            val transformer = withContext(Dispatchers.Main.immediate) {
                buildTransformer(preparation, completion, fallbackDetected).also {
                    activeTransformer = it
                    transformerForCleanup = it
                    it.start(bundle.composition, tempOutput.absolutePath)
                }
            }

            val exportResult = coroutineScope {
                val progressJob = launch {
                    val holder = ProgressHolder()
                    while (isActive && !completion.isCompleted) {
                        val percent = withContext(Dispatchers.Main.immediate) {
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else -1
                        }
                        if (percent >= 0) listener.onProgress(0.04f + (percent.coerceIn(0, 100) / 100f) * 0.82f)
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
            listener.onProgress(0.87f)

            val expectedHdr = expectedHdr(preparation.plan, preparation.settings.hdrPolicy)
            val localValidation = withContext(Dispatchers.IO) {
                validator.validateFile(
                    tempOutput,
                    preparation.settings,
                    preparation.plan.durationUs,
                    expectsAudio(preparation.plan),
                    expectedHdr
                )
            }
            if (!localValidation.passed) {
                throw RenderPipelineException(
                    ExportFailureCode.VALIDATION_FAILED,
                    "Native render failed validation before SAF copy: ${localValidation.problems.joinToString(" ")}"
                )
            }

            listener.onProgress(0.90f)
            withContext(Dispatchers.IO) {
                copyToDestination(tempOutput, preparation.destination, listener)
            }
            listener.onProgress(0.98f)
            val finalValidation = withContext(Dispatchers.IO) {
                validator.validateUri(
                    preparation.destination.uri,
                    preparation.settings,
                    preparation.plan.durationUs,
                    expectsAudio(preparation.plan),
                    expectedHdr
                )
            }
            if (!finalValidation.passed) {
                throw RenderPipelineException(
                    ExportFailureCode.VALIDATION_FAILED,
                    "SAF output failed post-copy validation: ${finalValidation.problems.joinToString(" ")}"
                )
            }
            listener.onProgress(1f)
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
            withContext(NonCancellable + Dispatchers.Main.immediate) { transformerForCleanup?.cancel() }
            Result.failure(RenderPipelineException(ExportFailureCode.CANCELLED, "Export was cancelled.", cancelled))
        } catch (t: Throwable) {
            val wrapped = if (t is RenderPipelineException) t else mapFailure(t)
            Result.failure(wrapped)
        } finally {
            activeTransformer = null
            activeCompletion = null
            withContext(NonCancellable + Dispatchers.IO) { jobRoot.deleteRecursively() }
        }
    }

    override suspend fun cancel() {
        val cancellation = RenderPipelineException(ExportFailureCode.CANCELLED, "Export cancelled by user.")
        activeCompletion?.completeExceptionally(cancellation)
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
        val listener = object : Transformer.Listener {
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
            .addListener(listener)
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

    private fun canOpenDestination(destination: OutputDestination): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(destination.uri, "rw")?.use { true } ?: false
    }.getOrDefault(false)

    private fun copyToDestination(source: File, destination: OutputDestination, listener: RenderProgressListener) {
        val total = source.length().coerceAtLeast(1L)
        val pfd = context.contentResolver.openFileDescriptor(destination.uri, "rwt")
            ?: throw RenderPipelineException(ExportFailureCode.DESTINATION_IO, "Destination file descriptor is unavailable.")
        pfd.use { descriptor ->
            FileInputStream(source).use { input ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        listener.onProgress(0.90f + (copied.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat() * 0.07f)
                    }
                    output.flush()
                    descriptor.fileDescriptor.sync()
                }
            }
        }
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

    private companion object {
        const val COPY_BUFFER_BYTES = 1024 * 1024
    }
}
