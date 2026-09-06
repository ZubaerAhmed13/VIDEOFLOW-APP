package com.videoflow.app.ui.ai

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoflow.app.ai.watermark.AiModelPackManager
import com.videoflow.app.ai.watermark.LocalRoiTracker
import com.videoflow.app.ai.watermark.LocalWatermarkPreviewEngine
import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.domain.ai.AiWatermarkEffect
import com.videoflow.app.domain.ai.NormalizedRoi
import com.videoflow.app.domain.ai.RoiMotionAnchor
import com.videoflow.app.domain.editor.TimelineClip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToLong


enum class WatermarkStudioBusy {
    IDLE,
    PREPARING_MODELS,
    LOADING_FRAME,
    TRACKING,
    AI_PREVIEW,
    APPLYING
}

data class WatermarkStudioState(
    val busy: WatermarkStudioBusy = WatermarkStudioBusy.IDLE,
    val progress: Float = 0f,
    val runtimeReady: Boolean = false,
    val runtimeDetail: String = "Checking local AI runtime…",
    val sourceFrame: Bitmap? = null,
    val aiPreview: Bitmap? = null,
    val previewProvider: String? = null,
    val trackedAnchors: List<RoiMotionAnchor> = emptyList(),
    val trackingConfidence: Float? = null,
    val existingEffects: List<AiWatermarkEffect> = emptyList(),
    val error: String? = null
)

/** Product-facing orchestration for the mask -> tracking -> AI preview -> apply workflow. */
@HiltViewModel
class WatermarkStudioViewModel @Inject constructor(
    private val repository: AiWatermarkRepository,
    private val modelPackManager: AiModelPackManager,
    private val previewEngine: LocalWatermarkPreviewEngine,
    private val tracker: LocalRoiTracker
) : ViewModel() {
    private val _state = MutableStateFlow(WatermarkStudioState())
    val state: StateFlow<WatermarkStudioState> = _state.asStateFlow()

    private var modelJob: Job? = null
    private var frameJob: Job? = null
    private var workJob: Job? = null
    private var boundProjectId: String? = null
    private var boundClipId: String? = null

    fun bind(projectId: String, clipId: String) {
        if (boundProjectId == projectId && boundClipId == clipId && modelJob?.isActive == true) return
        boundProjectId = projectId
        boundClipId = clipId
        modelJob?.cancel()
        modelJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                busy = WatermarkStudioBusy.PREPARING_MODELS,
                progress = 0f,
                error = null
            )
            runCatching {
                val effects = repository.effectsForClip(projectId, clipId)
                modelPackManager.ensurePackInstalled()
                val runtime = modelPackManager.status()
                effects to runtime
            }.onSuccess { (effects, runtime) ->
                _state.value = _state.value.copy(
                    busy = WatermarkStudioBusy.IDLE,
                    progress = 0f,
                    runtimeReady = runtime.complete,
                    runtimeDetail = runtime.detail + if (runtime.nnapiAvailable) " NNAPI available." else " CPU fallback available.",
                    existingEffects = effects,
                    error = null
                )
            }.onFailure { error ->
                val effects = runCatching { repository.effectsForClip(projectId, clipId) }.getOrDefault(emptyList())
                _state.value = _state.value.copy(
                    busy = WatermarkStudioBusy.IDLE,
                    progress = 0f,
                    runtimeReady = false,
                    runtimeDetail = "Local AI model pack is unavailable in this build.",
                    existingEffects = effects,
                    error = error.message ?: error::class.java.simpleName
                )
            }
        }
    }

    fun loadSourceFrame(sourceUri: String, sourceTimeUs: Long) {
        frameJob?.cancel()
        frameJob = viewModelScope.launch {
            val previousBusy = _state.value.busy
            if (previousBusy == WatermarkStudioBusy.IDLE) {
                _state.value = _state.value.copy(busy = WatermarkStudioBusy.LOADING_FRAME, error = null)
            }
            runCatching { previewEngine.decodeFrame(sourceUri, sourceTimeUs, maxDimensionPx = 960) }
                .onSuccess { bitmap ->
                    replaceSourceFrame(bitmap)
                    if (_state.value.busy == WatermarkStudioBusy.LOADING_FRAME) {
                        _state.value = _state.value.copy(busy = WatermarkStudioBusy.IDLE)
                    }
                }
                .onFailure { error ->
                    if (_state.value.busy == WatermarkStudioBusy.LOADING_FRAME) {
                        _state.value = _state.value.copy(
                            busy = WatermarkStudioBusy.IDLE,
                            error = "Preview frame: ${error.message ?: error::class.java.simpleName}"
                        )
                    }
                }
        }
    }

    fun clearDraftResults() {
        workJob?.cancel()
        replaceAiPreview(null)
        _state.value = _state.value.copy(
            trackedAnchors = emptyList(),
            trackingConfidence = null,
            previewProvider = null,
            progress = 0f,
            busy = WatermarkStudioBusy.IDLE,
            error = null
        )
    }

    fun clearPreviewOnly() {
        replaceAiPreview(null)
        _state.value = _state.value.copy(previewProvider = null, error = null)
    }

    fun track(
        sourceUri: String,
        clip: TimelineClip,
        roi: NormalizedRoi,
        clipLocalStartUs: Long,
        clipLocalEndUs: Long
    ) {
        workJob?.cancel()
        replaceAiPreview(null)
        workJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                busy = WatermarkStudioBusy.TRACKING,
                progress = 0f,
                trackedAnchors = emptyList(),
                trackingConfidence = null,
                previewProvider = null,
                error = null
            )
            runCatching {
                tracker.track(sourceUri, clip, roi, clipLocalStartUs, clipLocalEndUs) { progress ->
                    _state.value = _state.value.copy(progress = progress.coerceIn(0f, 1f))
                }
            }.onSuccess { result ->
                _state.value = _state.value.copy(
                    busy = WatermarkStudioBusy.IDLE,
                    progress = 1f,
                    trackedAnchors = result.anchors,
                    trackingConfidence = result.averageConfidence,
                    error = null
                )
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) return@onFailure
                _state.value = _state.value.copy(
                    busy = WatermarkStudioBusy.IDLE,
                    progress = 0f,
                    trackedAnchors = emptyList(),
                    trackingConfidence = null,
                    error = "Tracking: ${error.message ?: error::class.java.simpleName}"
                )
            }
        }
    }

    fun preview(
        sourceUri: String,
        clip: TimelineClip,
        clipLocalTimeUs: Long,
        roi: NormalizedRoi,
        sourceWidth: Int,
        sourceHeight: Int,
        featherPx: Int,
        anchors: List<RoiMotionAnchor>
    ) {
        if (!_state.value.runtimeReady) return
        workJob?.cancel()
        workJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                busy = WatermarkStudioBusy.AI_PREVIEW,
                progress = 0.05f,
                error = null
            )
            val previewRoi = if (anchors.isEmpty()) roi else {
                val draft = AiWatermarkEffect(
                    id = "preview",
                    projectId = clip.projectId,
                    clipId = clip.id,
                    clipLocalStartUs = 0L,
                    clipLocalEndUs = clip.timelineDurationUs,
                    roi = roi,
                    motionAnchors = anchors
                )
                draft.roiAt(clipLocalTimeUs)
            }
            val sourceTimeUs = clip.sourceStartUs + (clipLocalTimeUs.toDouble() * clip.speed).roundToLong()
            runCatching {
                previewEngine.render(
                    sourceUri = sourceUri,
                    sourceTimeUs = sourceTimeUs.coerceIn(clip.sourceStartUs, clip.sourceEndUs - 1L),
                    roi = previewRoi,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight,
                    featherPx = featherPx
                )
            }.onSuccess { result ->
                replaceAiPreview(result.bitmap)
                _state.value = _state.value.copy(
                    busy = WatermarkStudioBusy.IDLE,
                    progress = 1f,
                    previewProvider = result.provider,
                    error = null
                )
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) return@onFailure
                _state.value = _state.value.copy(
                    busy = WatermarkStudioBusy.IDLE,
                    progress = 0f,
                    error = "AI preview: ${error.message ?: error::class.java.simpleName}"
                )
            }
        }
    }

    fun apply(effect: AiWatermarkEffect, onApplied: () -> Unit) {
        workJob?.cancel()
        workJob = viewModelScope.launch {
            _state.value = _state.value.copy(busy = WatermarkStudioBusy.APPLYING, progress = 0.5f, error = null)
            runCatching { repository.upsert(effect) }
                .onSuccess {
                    val effects = repository.effectsForClip(effect.projectId, effect.clipId)
                    _state.value = _state.value.copy(
                        busy = WatermarkStudioBusy.IDLE,
                        progress = 1f,
                        existingEffects = effects,
                        error = null
                    )
                    onApplied()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        busy = WatermarkStudioBusy.IDLE,
                        progress = 0f,
                        error = "Apply: ${error.message ?: error::class.java.simpleName}"
                    )
                }
        }
    }

    fun setEnabled(effect: AiWatermarkEffect, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.upsert(effect.copy(enabled = enabled)) }
                .onSuccess { refreshEffects(effect.projectId, effect.clipId) }
                .onFailure { error -> _state.value = _state.value.copy(error = error.message) }
        }
    }

    fun remove(effect: AiWatermarkEffect) {
        viewModelScope.launch {
            runCatching { repository.remove(effect.projectId, effect.id) }
                .onSuccess { refreshEffects(effect.projectId, effect.clipId) }
                .onFailure { error -> _state.value = _state.value.copy(error = error.message) }
        }
    }

    fun cancelWork() {
        workJob?.cancel()
        _state.value = _state.value.copy(busy = WatermarkStudioBusy.IDLE, progress = 0f)
    }

    private suspend fun refreshEffects(projectId: String, clipId: String) {
        _state.value = _state.value.copy(existingEffects = repository.effectsForClip(projectId, clipId))
    }

    private fun replaceSourceFrame(bitmap: Bitmap?) {
        val old = _state.value.sourceFrame
        _state.value = _state.value.copy(sourceFrame = bitmap)
        if (old != null && old !== bitmap && !old.isRecycled) old.recycle()
    }

    private fun replaceAiPreview(bitmap: Bitmap?) {
        val old = _state.value.aiPreview
        _state.value = _state.value.copy(aiPreview = bitmap)
        if (old != null && old !== bitmap && !old.isRecycled) old.recycle()
    }

    override fun onCleared() {
        modelJob?.cancel()
        frameJob?.cancel()
        workJob?.cancel()
        _state.value.sourceFrame?.takeIf { !it.isRecycled }?.recycle()
        _state.value.aiPreview?.takeIf { !it.isRecycled }?.recycle()
        super.onCleared()
    }
}
