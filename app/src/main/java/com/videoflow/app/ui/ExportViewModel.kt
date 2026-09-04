package com.videoflow.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import com.videoflow.app.data.export.ExportRepository
import com.videoflow.app.domain.editor.FrameRate
import com.videoflow.app.domain.export.BitrateMode
import com.videoflow.app.domain.export.ExportEstimate
import com.videoflow.app.domain.export.ExportJob
import com.videoflow.app.domain.export.ExportJobStatus
import com.videoflow.app.domain.export.ExportMath
import com.videoflow.app.domain.export.ExportProblem
import com.videoflow.app.domain.export.ExportQuality
import com.videoflow.app.domain.export.ExportResolutionPreset
import com.videoflow.app.domain.export.ExportSettings
import com.videoflow.app.domain.export.ExportSize
import com.videoflow.app.domain.export.ExportWarning
import com.videoflow.app.domain.export.FinalRenderPlan
import com.videoflow.app.domain.export.HdrPolicy
import com.videoflow.app.domain.export.ResolvedExportSettings
import com.videoflow.app.domain.export.VideoCodec
import com.videoflow.app.export.ExportCoordinator
import com.videoflow.app.export.ExportForegroundService
import com.videoflow.app.render.AndroidEncoderCapabilitySource
import com.videoflow.app.render.ExportCapabilityValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class ExportUiState(
    val loading: Boolean = true,
    val requested: ExportSettings = ExportSettings(),
    val resolved: ResolvedExportSettings? = null,
    val estimate: ExportEstimate? = null,
    val destinationUri: Uri? = null,
    val warnings: List<ExportWarning> = emptyList(),
    val problems: List<ExportProblem> = emptyList(),
    val jobs: List<ExportJob> = emptyList(),
    val message: String? = null
) {
    val canStart: Boolean
        get() = !loading && resolved != null && destinationUri != null && problems.isEmpty() &&
            jobs.none { it.status in ACTIVE_STATUSES }

    companion object {
        val ACTIVE_STATUSES = setOf(
            ExportJobStatus.QUEUED,
            ExportJobStatus.PREPARING,
            ExportJobStatus.RENDERING,
            ExportJobStatus.FINALIZING,
            ExportJobStatus.VALIDATING
        )
    }
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExportRepository,
    private val coordinator: ExportCoordinator
) : ViewModel() {
    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()
    private var projectId: String? = null
    private var plan: FinalRenderPlan? = null
    private var jobsCollection: Job? = null
    private var recomputeJob: Job? = null

    fun load(id: String) {
        if (projectId == id && plan != null) return
        projectId = id
        jobsCollection?.cancel()
        jobsCollection = viewModelScope.launch {
            repository.observeJobs(id).collectLatest { jobs ->
                _state.value = _state.value.copy(jobs = jobs)
            }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val compiled = repository.compileFinalPlan(id)
            plan = compiled.plan
            _state.value = _state.value.copy(
                loading = false,
                problems = compiled.problems,
                message = if (compiled.problems.isEmpty()) null else "Fix source issues before exporting."
            )
            recompute()
        }
    }

    fun setResolution(preset: ExportResolutionPreset) {
        updateRequested { current ->
            if (preset == ExportResolutionPreset.CUSTOM) {
                current.copy(resolutionPreset = preset, customWidth = current.customWidth ?: 1920, customHeight = current.customHeight ?: 1080)
            } else current.copy(resolutionPreset = preset)
        }
    }

    fun setCustomWidth(value: Int) = updateRequested { it.copy(customWidth = value.coerceAtLeast(2)) }
    fun setCustomHeight(value: Int) = updateRequested { it.copy(customHeight = value.coerceAtLeast(2)) }
    fun setFrameRate(value: FrameRate?) = updateRequested { it.copy(frameRate = value) }
    fun setVideoCodec(value: VideoCodec) = updateRequested { it.copy(videoCodec = value) }
    fun setQuality(value: ExportQuality) = updateRequested { it.copy(quality = value) }
    fun setBitrateMode(value: BitrateMode) = updateRequested { it.copy(bitrateMode = value) }
    fun setHdrPolicy(value: HdrPolicy) = updateRequested { it.copy(hdrPolicy = value) }

    fun setDestination(uri: Uri?) {
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        _state.value = _state.value.copy(destinationUri = uri)
    }

    fun startExport() {
        val id = projectId ?: return
        val snapshot = _state.value
        val destination = snapshot.destinationUri ?: return
        if (!snapshot.canStart) return
        viewModelScope.launch {
            runCatching {
                val name = "VideoFlow_${System.currentTimeMillis()}.mp4"
                val job = repository.createJob(id, destination.toString(), name, snapshot.requested)
                ExportForegroundService.start(context, job.id)
                _state.value = _state.value.copy(message = "Native export started. You can leave this screen while it renders.")
            }.onFailure {
                _state.value = _state.value.copy(message = it.message ?: "Could not start export.")
            }
        }
    }

    fun cancelActiveExport() {
        viewModelScope.launch { coordinator.cancel() }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun updateRequested(block: (ExportSettings) -> ExportSettings) {
        runCatching { block(_state.value.requested) }
            .onSuccess {
                _state.value = _state.value.copy(requested = it)
                recompute()
            }
            .onFailure { _state.value = _state.value.copy(message = it.message) }
    }

    private fun recompute() {
        val renderPlan = plan ?: return
        val requested = _state.value.requested
        recomputeJob?.cancel()
        recomputeJob = viewModelScope.launch {
            val computed = withContext(Dispatchers.Default) {
                runCatching {
                    val resolved = ExportMath.resolve(
                        ExportSize(renderPlan.editorPlan.width, renderPlan.editorPlan.height),
                        renderPlan.editorPlan.frameRate,
                        requested
                    )
                    val hasHdr = renderPlan.originalSources.values.any {
                        it.hdrStaticInfoPresent || it.colorTransfer == C.COLOR_TRANSFER_HLG || it.colorTransfer == C.COLOR_TRANSFER_ST2084
                    }
                    val capability = ExportCapabilityValidator.validate(resolved, hasHdr, AndroidEncoderCapabilitySource())
                    val expectsAudio = renderPlan.editorPlan.clips.any { clip ->
                        clip.enabled && renderPlan.originalSources[clip.assetId]?.audioCodecMime != null
                    }
                    val estimate = ExportMath.estimateOutputSize(
                        renderPlan.durationUs,
                        resolved.videoBitrate,
                        if (expectsAudio) resolved.audioBitrate else 0
                    )
                    Triple(resolved, estimate, capability)
                }
            }
            computed.onSuccess { (resolved, estimate, capability) ->
                val compileProblems = if (renderPlan.originalSources.isEmpty() && renderPlan.editorPlan.clips.isNotEmpty()) {
                    listOf(ExportProblem(com.videoflow.app.domain.export.ExportFailureCode.SOURCE_MISSING, "Original source mapping is unavailable."))
                } else emptyList()
                val warnings = buildList {
                    addAll(capability.warnings)
                    if (resolved.isUpscale) add(ExportWarning("UPSCALE", "Requested resolution is larger than the project canvas; export will upscale the composed frame."))
                }
                _state.value = _state.value.copy(
                    resolved = resolved,
                    estimate = estimate,
                    warnings = warnings,
                    problems = compileProblems + capability.problems
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    resolved = null,
                    estimate = null,
                    problems = listOf(ExportProblem(com.videoflow.app.domain.export.ExportFailureCode.UNKNOWN, it.message ?: "Export settings are invalid."))
                )
            }
        }
    }
}
