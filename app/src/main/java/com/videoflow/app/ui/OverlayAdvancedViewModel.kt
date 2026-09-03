package com.videoflow.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.data.editor.OverlayEditorService
import com.videoflow.app.data.history.EditHistoryService
import com.videoflow.app.data.history.ImageOverlayHistoryEntry
import com.videoflow.app.data.history.KeyframeHistoryEntry
import com.videoflow.app.data.history.TextOverlayHistoryEntry
import com.videoflow.app.domain.editor.ImageOverlay
import com.videoflow.app.domain.editor.Keyframe
import com.videoflow.app.domain.editor.KeyframeInterpolation
import com.videoflow.app.domain.editor.KeyframeOwnerType
import com.videoflow.app.domain.editor.KeyframeProperty
import com.videoflow.app.domain.editor.TextOverlay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class OverlayAdvancedViewModel @Inject constructor(
    private val editorRepository: EditorRepository,
    private val overlayService: OverlayEditorService,
    private val historyService: EditHistoryService
) : ViewModel() {

    fun setTextColor(projectId: String, overlayId: String, colorArgb: Long, onDone: () -> Unit) =
        mutateText(projectId, overlayId, "Text Color", onDone) { before ->
            overlayService.updateText(projectId, overlayId, colorArgb = colorArgb)
        }

    fun setTextAlignment(projectId: String, overlayId: String, alignment: String, onDone: () -> Unit) =
        mutateText(projectId, overlayId, "Text Alignment", onDone) {
            overlayService.updateText(projectId, overlayId, alignment = alignment)
        }

    fun setTextScale(projectId: String, overlayId: String, scale: Float, onDone: () -> Unit) =
        mutateText(projectId, overlayId, "Text Scale", onDone) {
            overlayService.updateText(projectId, overlayId, scale = scale.coerceIn(0.05f, 10f))
        }

    fun moveTextTimeline(projectId: String, overlayId: String, deltaUs: Long, onDone: () -> Unit) =
        mutateText(projectId, overlayId, "Move Text Timeline", onDone) { before ->
            val duration = before.timelineEndUs - before.timelineStartUs
            val start = (before.timelineStartUs + deltaUs).coerceAtLeast(0L)
            overlayService.updateText(projectId, overlayId, timelineStartUs = start, timelineEndUs = start + duration)
        }

    fun trimTextStart(projectId: String, overlayId: String, deltaUs: Long, onDone: () -> Unit) =
        mutateText(projectId, overlayId, "Trim Text Start", onDone) { before ->
            val start = (before.timelineStartUs + deltaUs).coerceAtLeast(0L)
                .coerceAtMost(before.timelineEndUs - MIN_OVERLAY_DURATION_US)
            overlayService.updateText(projectId, overlayId, timelineStartUs = start)
        }

    fun trimTextEnd(projectId: String, overlayId: String, deltaUs: Long, onDone: () -> Unit) =
        mutateText(projectId, overlayId, "Trim Text End", onDone) { before ->
            val end = (before.timelineEndUs + deltaUs)
                .coerceAtLeast(before.timelineStartUs + MIN_OVERLAY_DURATION_US)
            overlayService.updateText(projectId, overlayId, timelineEndUs = end)
        }

    fun moveImageTimeline(projectId: String, overlayId: String, deltaUs: Long, onDone: () -> Unit) =
        mutateImage(projectId, overlayId, "Move Image Timeline", onDone) { before ->
            val duration = before.timelineEndUs - before.timelineStartUs
            val start = (before.timelineStartUs + deltaUs).coerceAtLeast(0L)
            overlayService.updateImage(projectId, overlayId, timelineStartUs = start, timelineEndUs = start + duration)
        }

    fun trimImageStart(projectId: String, overlayId: String, deltaUs: Long, onDone: () -> Unit) =
        mutateImage(projectId, overlayId, "Trim Image Start", onDone) { before ->
            val start = (before.timelineStartUs + deltaUs).coerceAtLeast(0L)
                .coerceAtMost(before.timelineEndUs - MIN_OVERLAY_DURATION_US)
            overlayService.updateImage(projectId, overlayId, timelineStartUs = start)
        }

    fun trimImageEnd(projectId: String, overlayId: String, deltaUs: Long, onDone: () -> Unit) =
        mutateImage(projectId, overlayId, "Trim Image End", onDone) { before ->
            val end = (before.timelineEndUs + deltaUs)
                .coerceAtLeast(before.timelineStartUs + MIN_OVERLAY_DURATION_US)
            overlayService.updateImage(projectId, overlayId, timelineEndUs = end)
        }

    fun addTextKeyframe(
        projectId: String,
        overlayId: String,
        playheadUs: Long,
        property: KeyframeProperty,
        interpolation: KeyframeInterpolation,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val editor = editorRepository.load(projectId)
            val overlay = editor.timeline.textOverlays.first { it.id == overlayId }
            require(property != KeyframeProperty.AUDIO_GAIN) { "Audio gain is not a text property" }
            val localUs = (playheadUs - overlay.timelineStartUs)
                .coerceIn(0L, overlay.timelineEndUs - overlay.timelineStartUs)
            val before = editor.timeline.keyframes.filter { it.ownerId == overlayId }
            editorRepository.putKeyframe(
                projectId,
                Keyframe(
                    id = UUID.randomUUID().toString(),
                    ownerId = overlayId,
                    ownerType = KeyframeOwnerType.TEXT_OVERLAY,
                    property = property,
                    timeUs = localUs,
                    value = textValue(overlay, property),
                    interpolation = interpolation
                )
            )
            val after = editorRepository.load(projectId).timeline.keyframes.filter { it.ownerId == overlayId }
            historyService.record(KeyframeHistoryEntry(projectId, "Add Text Keyframe", before, after))
            onDone()
        }
    }

    fun addImageKeyframe(
        projectId: String,
        overlayId: String,
        playheadUs: Long,
        property: KeyframeProperty,
        interpolation: KeyframeInterpolation,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val editor = editorRepository.load(projectId)
            val overlay = editor.timeline.imageOverlays.first { it.id == overlayId }
            require(property != KeyframeProperty.AUDIO_GAIN) { "Audio gain is not an image property" }
            val localUs = (playheadUs - overlay.timelineStartUs)
                .coerceIn(0L, overlay.timelineEndUs - overlay.timelineStartUs)
            val before = editor.timeline.keyframes.filter { it.ownerId == overlayId }
            editorRepository.putKeyframe(
                projectId,
                Keyframe(
                    id = UUID.randomUUID().toString(),
                    ownerId = overlayId,
                    ownerType = KeyframeOwnerType.IMAGE_OVERLAY,
                    property = property,
                    timeUs = localUs,
                    value = imageValue(overlay, property),
                    interpolation = interpolation
                )
            )
            val after = editorRepository.load(projectId).timeline.keyframes.filter { it.ownerId == overlayId }
            historyService.record(KeyframeHistoryEntry(projectId, "Add Image Keyframe", before, after))
            onDone()
        }
    }

    private fun mutateText(
        projectId: String,
        overlayId: String,
        label: String,
        onDone: () -> Unit,
        block: suspend (TextOverlay) -> TextOverlay
    ) {
        viewModelScope.launch {
            val editor = editorRepository.load(projectId)
            val before = editor.timeline.textOverlays.first { it.id == overlayId }
            val beforeFrames = editor.timeline.keyframes.filter { it.ownerId == overlayId }
            val after = block(before)
            val afterFrames = editorRepository.load(projectId).timeline.keyframes.filter { it.ownerId == overlayId }
            historyService.recordCoalesced(
                TextOverlayHistoryEntry(projectId, label, before, after, beforeFrames, afterFrames),
                key = "text:$overlayId:$label"
            )
            onDone()
        }
    }

    private fun mutateImage(
        projectId: String,
        overlayId: String,
        label: String,
        onDone: () -> Unit,
        block: suspend (ImageOverlay) -> ImageOverlay
    ) {
        viewModelScope.launch {
            val editor = editorRepository.load(projectId)
            val before = editor.timeline.imageOverlays.first { it.id == overlayId }
            val beforeFrames = editor.timeline.keyframes.filter { it.ownerId == overlayId }
            val after = block(before)
            val afterFrames = editorRepository.load(projectId).timeline.keyframes.filter { it.ownerId == overlayId }
            historyService.recordCoalesced(
                ImageOverlayHistoryEntry(projectId, label, before, after, beforeFrames, afterFrames),
                key = "image:$overlayId:$label"
            )
            onDone()
        }
    }

    private fun textValue(overlay: TextOverlay, property: KeyframeProperty): Float = when (property) {
        KeyframeProperty.POSITION_X -> overlay.transform.x
        KeyframeProperty.POSITION_Y -> overlay.transform.y
        KeyframeProperty.SCALE_X -> overlay.transform.scaleX
        KeyframeProperty.SCALE_Y -> overlay.transform.scaleY
        KeyframeProperty.ROTATION -> overlay.transform.rotationDegrees
        KeyframeProperty.OPACITY -> overlay.opacity
        KeyframeProperty.AUDIO_GAIN -> error("Audio gain is not a text property")
    }

    private fun imageValue(overlay: ImageOverlay, property: KeyframeProperty): Float = when (property) {
        KeyframeProperty.POSITION_X -> overlay.transform.x
        KeyframeProperty.POSITION_Y -> overlay.transform.y
        KeyframeProperty.SCALE_X -> overlay.transform.scaleX
        KeyframeProperty.SCALE_Y -> overlay.transform.scaleY
        KeyframeProperty.ROTATION -> overlay.transform.rotationDegrees
        KeyframeProperty.OPACITY -> overlay.transform.opacity
        KeyframeProperty.AUDIO_GAIN -> error("Audio gain is not an image property")
    }

    companion object {
        private const val MIN_OVERLAY_DURATION_US = 100_000L
    }
}
