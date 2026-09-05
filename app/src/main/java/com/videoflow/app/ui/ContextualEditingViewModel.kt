package com.videoflow.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoflow.app.data.editor.ContextualEditingService
import com.videoflow.app.data.editor.EditorPropertyService
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.data.editor.OverlayEditorService
import com.videoflow.app.data.history.ClipHistoryEntry
import com.videoflow.app.data.history.EditHistoryService
import com.videoflow.app.data.history.ImageOverlayHistoryEntry
import com.videoflow.app.data.history.KeyframeHistoryEntry
import com.videoflow.app.data.history.TextOverlayHistoryEntry
import com.videoflow.app.domain.editor.CropRect
import com.videoflow.app.domain.editor.Keyframe
import com.videoflow.app.domain.editor.KeyframeInterpolation
import com.videoflow.app.domain.editor.KeyframeOwnerType
import com.videoflow.app.domain.editor.KeyframeProperty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * UI Step 2 presentation-facing mutation coordinator.
 *
 * High-frequency pointer/slider state stays in Compose. Methods in this class are durable
 * commit boundaries: one completed gesture/Done action produces one project mutation/history
 * record. If a property has a keyframe at the exact owner-local playhead, that keyframe is
 * updated instead of silently changing the animated base value.
 */
@HiltViewModel
class ContextualEditingViewModel @Inject constructor(
    private val editorRepository: EditorRepository,
    private val propertyService: EditorPropertyService,
    private val overlayService: OverlayEditorService,
    private val contextualService: ContextualEditingService,
    private val historyService: EditHistoryService
) : ViewModel() {

    fun commitTrim(
        projectId: String,
        clipId: String,
        sourceStartUs: Long,
        sourceEndUs: Long,
        onDone: () -> Unit
    ) = launchEdit(onDone) {
        require(sourceStartUs >= 0L && sourceEndUs > sourceStartUs) { "Choose a valid trim range" }
        val beforeProject = editorRepository.load(projectId)
        val before = beforeProject.timeline.clips.first { it.id == clipId }
        val beforeFrames = beforeProject.timeline.keyframes.filter { it.ownerId == clipId }
        if (sourceStartUs != before.sourceStartUs) editorRepository.trimClipStart(projectId, clipId, sourceStartUs)
        if (sourceEndUs != before.sourceEndUs) editorRepository.trimClipEnd(projectId, clipId, sourceEndUs)
        val fresh = editorRepository.load(projectId)
        val after = fresh.timeline.clips.first { it.id == clipId }
        val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == clipId }
        if (after != before || afterFrames != beforeFrames) {
            historyService.record(ClipHistoryEntry(projectId, "Trim Clip", listOf(before), listOf(after), beforeFrames, afterFrames))
        }
    }

    fun setClipTransform(
        projectId: String,
        clipId: String,
        x: Float,
        y: Float,
        scale: Float,
        rotationDegrees: Float,
        playheadUs: Long? = null,
        flipHorizontal: Boolean? = null,
        flipVertical: Boolean? = null,
        onDone: () -> Unit = {}
    ) = launchEdit(onDone) {
        val editor = editorRepository.load(projectId)
        val before = editor.timeline.clips.first { it.id == clipId }
        val beforeFrames = editor.timeline.keyframes.filter { it.ownerId == clipId }
        val localUs = playheadUs?.let { (it - before.timelineStartUs).coerceIn(0L, before.timelineDurationUs) }
        val exact = localUs?.let { t -> beforeFrames.filter { it.timeUs == t }.associateBy { it.property } }.orEmpty()
        val newX = x.coerceIn(0f, 1f)
        val newY = y.coerceIn(0f, 1f)
        val newScale = scale.coerceIn(0.05f, 10f)
        val newRotation = normalizeRotation(rotationDegrees)

        propertyService.setTransform(
            projectId = projectId,
            clipId = clipId,
            x = if (KeyframeProperty.POSITION_X in exact) before.transform.x else newX,
            y = if (KeyframeProperty.POSITION_Y in exact) before.transform.y else newY,
            scaleX = if (KeyframeProperty.SCALE_X in exact) before.transform.scaleX else newScale,
            scaleY = if (KeyframeProperty.SCALE_Y in exact) before.transform.scaleY else newScale,
            rotationDegrees = if (KeyframeProperty.ROTATION in exact) before.transform.rotationDegrees else newRotation,
            opacity = before.opacity
        )
        exact[KeyframeProperty.POSITION_X]?.let { editorRepository.putKeyframe(projectId, it.copy(value = newX)) }
        exact[KeyframeProperty.POSITION_Y]?.let { editorRepository.putKeyframe(projectId, it.copy(value = newY)) }
        exact[KeyframeProperty.SCALE_X]?.let { editorRepository.putKeyframe(projectId, it.copy(value = newScale)) }
        exact[KeyframeProperty.SCALE_Y]?.let { editorRepository.putKeyframe(projectId, it.copy(value = newScale)) }
        exact[KeyframeProperty.ROTATION]?.let { editorRepository.putKeyframe(projectId, it.copy(value = newRotation)) }
        if (flipHorizontal != null && flipHorizontal != before.transform.flipHorizontal) {
            propertyService.setFlipHorizontal(projectId, clipId, flipHorizontal)
        }
        if (flipVertical != null && flipVertical != before.transform.flipVertical) {
            propertyService.setFlipVertical(projectId, clipId, flipVertical)
        }

        val fresh = editorRepository.load(projectId)
        val after = fresh.timeline.clips.first { it.id == clipId }
        val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == clipId }
        if (after != before || afterFrames != beforeFrames) {
            historyService.record(ClipHistoryEntry(projectId, "Transform Clip", listOf(before), listOf(after), beforeFrames, afterFrames))
        }
    }

    fun setClipCrop(
        projectId: String,
        clipId: String,
        crop: CropRect,
        onDone: () -> Unit = {}
    ) = mutateClip(projectId, clipId, "Crop Clip", "clip:$clipId:context-crop", onDone) {
        propertyService.setCrop(projectId, clipId, crop)
    }

    fun setTextTransform(
        projectId: String,
        overlayId: String,
        x: Float,
        y: Float,
        scale: Float,
        rotationDegrees: Float,
        playheadUs: Long? = null,
        onDone: () -> Unit = {}
    ) = launchEdit(onDone) {
        val editor = editorRepository.load(projectId)
        val before = editor.timeline.textOverlays.first { it.id == overlayId }
        val beforeFrames = editor.timeline.keyframes.filter { it.ownerId == overlayId }
        val duration = (before.timelineEndUs - before.timelineStartUs).coerceAtLeast(1L)
        val localUs = playheadUs?.let { (it - before.timelineStartUs).coerceIn(0L, duration) }
        val exact = localUs?.let { t -> beforeFrames.filter { it.timeUs == t }.associateBy { it.property } }.orEmpty()
        val newX = x.coerceIn(0f, 1f)
        val newY = y.coerceIn(0f, 1f)
        val newScale = scale.coerceIn(0.05f, 10f)
        val newRotation = normalizeRotation(rotationDegrees)

        overlayService.updateText(
            projectId,
            overlayId,
            x = if (KeyframeProperty.POSITION_X in exact) before.transform.x else newX,
            y = if (KeyframeProperty.POSITION_Y in exact) before.transform.y else newY,
            scale = if (KeyframeProperty.SCALE_X in exact || KeyframeProperty.SCALE_Y in exact) before.transform.scaleX else newScale,
            rotationDegrees = if (KeyframeProperty.ROTATION in exact) before.transform.rotationDegrees else newRotation
        )
        exact[KeyframeProperty.POSITION_X]?.let { editorRepository.putKeyframe(projectId, it.copy(value = newX)) }
        exact[KeyframeProperty.POSITION_Y]?.let { editorRepository.putKeyframe(projectId, it.copy(value = newY)) }
        exact[KeyframeProperty.SCALE_X]?.let { editorRepository.putKeyframe(projectId, it.copy(value = newScale)) }
        exact[KeyframeProperty.SCALE_Y]?.let { editorRepository.putKeyframe(projectId, it.copy(value = newScale)) }
        exact[KeyframeProperty.ROTATION]?.let { editorRepository.putKeyframe(projectId, it.copy(value = newRotation)) }

        val fresh = editorRepository.load(projectId)
        val after = fresh.timeline.textOverlays.first { it.id == overlayId }
        val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == overlayId }
        if (after != before || afterFrames != beforeFrames) {
            historyService.record(TextOverlayHistoryEntry(projectId, "Transform Text", before, after, beforeFrames, afterFrames))
        }
    }

    fun setImageTransform(
        projectId: String,
        overlayId: String,
        x: Float,
        y: Float,
        scale: Float,
        rotationDegrees: Float,
        playheadUs: Long? = null,
        onDone: () -> Unit = {}
    ) = launchEdit(onDone) {
        val editor = editorRepository.load(projectId)
        val before = editor.timeline.imageOverlays.first { it.id == overlayId }
        val beforeFrames = editor.timeline.keyframes.filter { it.ownerId == overlayId }
        val duration = (before.timelineEndUs - before.timelineStartUs).coerceAtLeast(1L)
        val localUs = playheadUs?.let { (it - before.timelineStartUs).coerceIn(0L, duration) }
        val exact = localUs?.let { t -> beforeFrames.filter { it.timeUs == t }.associateBy { it.property } }.orEmpty()
        val newX = x.coerceIn(0f, 1f)
        val newY = y.coerceIn(0f, 1f)
        val newScale = scale.coerceIn(0.05f, 10f)
        val newRotation = normalizeRotation(rotationDegrees)

        overlayService.updateImage(
            projectId,
            overlayId,
            x = if (KeyframeProperty.POSITION_X in exact) before.transform.x else newX,
            y = if (KeyframeProperty.POSITION_Y in exact) before.transform.y else newY,
            scale = if (KeyframeProperty.SCALE_X in exact || KeyframeProperty.SCALE_Y in exact) before.transform.scaleX else newScale,
            rotationDegrees = if (KeyframeProperty.ROTATION in exact) before.transform.rotationDegrees else newRotation
        )
        exact[KeyframeProperty.POSITION_X]?.let { editorRepository.putKeyframe(projectId, it.copy(value = newX)) }
        exact[KeyframeProperty.POSITION_Y]?.let { editorRepository.putKeyframe(projectId, it.copy(value = newY)) }
        exact[KeyframeProperty.SCALE_X]?.let { editorRepository.putKeyframe(projectId, it.copy(value = newScale)) }
        exact[KeyframeProperty.SCALE_Y]?.let { editorRepository.putKeyframe(projectId, it.copy(value = newScale)) }
        exact[KeyframeProperty.ROTATION]?.let { editorRepository.putKeyframe(projectId, it.copy(value = newRotation)) }

        val fresh = editorRepository.load(projectId)
        val after = fresh.timeline.imageOverlays.first { it.id == overlayId }
        val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == overlayId }
        if (after != before || afterFrames != beforeFrames) {
            historyService.record(ImageOverlayHistoryEntry(projectId, "Transform Image", before, after, beforeFrames, afterFrames))
        }
    }

    fun setVisualOpacity(
        projectId: String,
        ownerId: String,
        ownerType: KeyframeOwnerType,
        opacity: Float,
        playheadUs: Long?,
        onDone: () -> Unit = {}
    ) = launchEdit(onDone) {
        val editor = editorRepository.load(projectId)
        val beforeFrames = editor.timeline.keyframes.filter { it.ownerId == ownerId }
        val newOpacity = opacity.coerceIn(0f, 1f)
        val (startUs, durationUs) = when (ownerType) {
            KeyframeOwnerType.CLIP -> editor.timeline.clips.first { it.id == ownerId }.let { it.timelineStartUs to it.timelineDurationUs }
            KeyframeOwnerType.TEXT_OVERLAY -> editor.timeline.textOverlays.first { it.id == ownerId }.let { it.timelineStartUs to (it.timelineEndUs - it.timelineStartUs) }
            KeyframeOwnerType.IMAGE_OVERLAY -> editor.timeline.imageOverlays.first { it.id == ownerId }.let { it.timelineStartUs to (it.timelineEndUs - it.timelineStartUs) }
        }
        val localUs = playheadUs?.let { (it - startUs).coerceIn(0L, durationUs.coerceAtLeast(1L)) }
        val exact = localUs?.let { t -> beforeFrames.firstOrNull { it.property == KeyframeProperty.OPACITY && it.timeUs == t } }

        when (ownerType) {
            KeyframeOwnerType.CLIP -> {
                val before = editor.timeline.clips.first { it.id == ownerId }
                if (exact == null) propertyService.setOpacity(projectId, ownerId, newOpacity)
                else editorRepository.putKeyframe(projectId, exact.copy(value = newOpacity))
                val fresh = editorRepository.load(projectId)
                val after = fresh.timeline.clips.first { it.id == ownerId }
                val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == ownerId }
                if (after != before || afterFrames != beforeFrames) historyService.record(ClipHistoryEntry(projectId, "Set Opacity", listOf(before), listOf(after), beforeFrames, afterFrames))
            }
            KeyframeOwnerType.TEXT_OVERLAY -> {
                val before = editor.timeline.textOverlays.first { it.id == ownerId }
                if (exact == null) overlayService.updateText(projectId, ownerId, opacity = newOpacity)
                else editorRepository.putKeyframe(projectId, exact.copy(value = newOpacity))
                val fresh = editorRepository.load(projectId)
                val after = fresh.timeline.textOverlays.first { it.id == ownerId }
                val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == ownerId }
                if (after != before || afterFrames != beforeFrames) historyService.record(TextOverlayHistoryEntry(projectId, "Set Text Opacity", before, after, beforeFrames, afterFrames))
            }
            KeyframeOwnerType.IMAGE_OVERLAY -> {
                val before = editor.timeline.imageOverlays.first { it.id == ownerId }
                if (exact == null) overlayService.updateImage(projectId, ownerId, opacity = newOpacity)
                else editorRepository.putKeyframe(projectId, exact.copy(value = newOpacity))
                val fresh = editorRepository.load(projectId)
                val after = fresh.timeline.imageOverlays.first { it.id == ownerId }
                val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == ownerId }
                if (after != before || afterFrames != beforeFrames) historyService.record(ImageOverlayHistoryEntry(projectId, "Set Image Opacity", before, after, beforeFrames, afterFrames))
            }
        }
    }

    fun setClipGain(
        projectId: String,
        clipId: String,
        gainDb: Float,
        playheadUs: Long?,
        onDone: () -> Unit = {}
    ) = launchEdit(onDone) {
        val editor = editorRepository.load(projectId)
        val before = editor.timeline.clips.first { it.id == clipId }
        val beforeFrames = editor.timeline.keyframes.filter { it.ownerId == clipId }
        val localUs = playheadUs?.let { (it - before.timelineStartUs).coerceIn(0L, before.timelineDurationUs) }
        val exact = localUs?.let { t -> beforeFrames.firstOrNull { it.property == KeyframeProperty.AUDIO_GAIN && it.timeUs == t } }
        if (exact == null) propertyService.setClipGain(projectId, clipId, gainDb.coerceIn(-60f, 24f))
        else editorRepository.putKeyframe(projectId, exact.copy(value = gainDb.coerceIn(-60f, 24f)))
        val fresh = editorRepository.load(projectId)
        val after = fresh.timeline.clips.first { it.id == clipId }
        val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == clipId }
        if (after != before || afterFrames != beforeFrames) historyService.record(ClipHistoryEntry(projectId, "Set Clip Gain", listOf(before), listOf(after), beforeFrames, afterFrames))
    }

    fun setTextStyle(
        projectId: String,
        overlayId: String,
        fontSizeSp: Float,
        fontWeight: Int,
        italic: Boolean,
        alignment: String,
        onDone: () -> Unit
    ) = mutateText(projectId, overlayId, "Text Style", "text:$overlayId:style", onDone) {
        overlayService.updateText(
            projectId,
            overlayId,
            fontSizeSp = fontSizeSp.coerceIn(6f, 256f),
            fontWeight = fontWeight.coerceIn(100, 900),
            italic = italic,
            alignment = alignment
        )
    }

    fun setTextTiming(
        projectId: String,
        overlayId: String,
        startUs: Long,
        endUs: Long,
        onDone: () -> Unit
    ) = mutateText(projectId, overlayId, "Text Timing", "text:$overlayId:timing", onDone) {
        require(startUs >= 0L && endUs > startUs)
        overlayService.updateText(projectId, overlayId, timelineStartUs = startUs, timelineEndUs = endUs)
    }

    fun setImageTiming(
        projectId: String,
        overlayId: String,
        startUs: Long,
        endUs: Long,
        onDone: () -> Unit
    ) = mutateImage(projectId, overlayId, "Image Timing", "image:$overlayId:timing", onDone) {
        require(startUs >= 0L && endUs > startUs)
        overlayService.updateImage(projectId, overlayId, timelineStartUs = startUs, timelineEndUs = endUs)
    }

    fun addText(projectId: String, playheadUs: Long, content: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val overlay = editorRepository.addTextOverlay(projectId, playheadUs, content.ifBlank { "Text" })
            historyService.record(TextOverlayHistoryEntry(projectId, "Add Text Overlay", null, overlay))
            onDone(overlay.id)
        }
    }

    fun duplicateText(projectId: String, overlayId: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val newId = contextualService.duplicateTextOverlay(projectId, overlayId)
            val fresh = editorRepository.load(projectId)
            val overlay = fresh.timeline.textOverlays.first { it.id == newId }
            val frames = fresh.timeline.keyframes.filter { it.ownerId == newId }
            historyService.record(TextOverlayHistoryEntry(projectId, "Duplicate Text Overlay", null, overlay, emptyList(), frames))
            onDone(newId)
        }
    }

    fun duplicateImage(projectId: String, overlayId: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val newId = contextualService.duplicateImageOverlay(projectId, overlayId)
            val fresh = editorRepository.load(projectId)
            val overlay = fresh.timeline.imageOverlays.first { it.id == newId }
            val frames = fresh.timeline.keyframes.filter { it.ownerId == newId }
            historyService.record(ImageOverlayHistoryEntry(projectId, "Duplicate Image Overlay", null, overlay, emptyList(), frames))
            onDone(newId)
        }
    }

    fun addKeyframe(
        projectId: String,
        ownerId: String,
        ownerType: KeyframeOwnerType,
        property: KeyframeProperty,
        playheadUs: Long,
        interpolation: KeyframeInterpolation,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val editor = editorRepository.load(projectId)
            val before = editor.timeline.keyframes.filter { it.ownerId == ownerId }
            val (ownerStartUs, ownerDurationUs, value) = ownerValue(editor, ownerId, ownerType, property)
            val localUs = (playheadUs - ownerStartUs).coerceIn(0L, ownerDurationUs)
            val existing = before.firstOrNull { it.property == property && it.timeUs == localUs }
            val frame = Keyframe(
                id = existing?.id ?: UUID.randomUUID().toString(),
                ownerId = ownerId,
                ownerType = ownerType,
                property = property,
                timeUs = localUs,
                value = value,
                interpolation = interpolation
            )
            editorRepository.putKeyframe(projectId, frame)
            val after = editorRepository.load(projectId).timeline.keyframes.filter { it.ownerId == ownerId }
            historyService.record(KeyframeHistoryEntry(projectId, if (existing == null) "Add Keyframe" else "Update Keyframe", before, after))
            onDone()
        }
    }

    /** User-facing Scale is uniform, so both backend scale axes are keyed together. */
    fun addUniformScaleKeyframe(
        projectId: String,
        ownerId: String,
        ownerType: KeyframeOwnerType,
        playheadUs: Long,
        interpolation: KeyframeInterpolation,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val editor = editorRepository.load(projectId)
            val before = editor.timeline.keyframes.filter { it.ownerId == ownerId }
            val (startUs, durationUs, scaleX) = ownerValue(editor, ownerId, ownerType, KeyframeProperty.SCALE_X)
            val (_, _, scaleY) = ownerValue(editor, ownerId, ownerType, KeyframeProperty.SCALE_Y)
            val localUs = (playheadUs - startUs).coerceIn(0L, durationUs)
            listOf(KeyframeProperty.SCALE_X to scaleX, KeyframeProperty.SCALE_Y to scaleY).forEach { (property, value) ->
                val existing = before.firstOrNull { it.property == property && it.timeUs == localUs }
                editorRepository.putKeyframe(
                    projectId,
                    Keyframe(existing?.id ?: UUID.randomUUID().toString(), ownerId, ownerType, property, localUs, value, interpolation)
                )
            }
            val after = editorRepository.load(projectId).timeline.keyframes.filter { it.ownerId == ownerId }
            historyService.record(KeyframeHistoryEntry(projectId, "Add Scale Keyframe", before, after))
            onDone()
        }
    }

    fun removeUniformScaleKeyframes(projectId: String, ownerId: String, timeUs: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            val before = editorRepository.load(projectId).timeline.keyframes.filter { it.ownerId == ownerId }
            before.filter { it.timeUs == timeUs && it.property in setOf(KeyframeProperty.SCALE_X, KeyframeProperty.SCALE_Y) }
                .forEach { contextualService.deleteKeyframe(projectId, it.id) }
            val after = editorRepository.load(projectId).timeline.keyframes.filter { it.ownerId == ownerId }
            historyService.record(KeyframeHistoryEntry(projectId, "Remove Scale Keyframe", before, after))
            onDone()
        }
    }

    fun removeKeyframe(projectId: String, ownerId: String, keyframeId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val before = editorRepository.load(projectId).timeline.keyframes.filter { it.ownerId == ownerId }
            contextualService.deleteKeyframe(projectId, keyframeId)
            val after = editorRepository.load(projectId).timeline.keyframes.filter { it.ownerId == ownerId }
            historyService.record(KeyframeHistoryEntry(projectId, "Remove Keyframe", before, after))
            onDone()
        }
    }

    fun setKeyframeInterpolation(
        projectId: String,
        ownerId: String,
        keyframeId: String,
        interpolation: KeyframeInterpolation,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val editor = editorRepository.load(projectId)
            val before = editor.timeline.keyframes.filter { it.ownerId == ownerId }
            val current = before.first { it.id == keyframeId }
            editorRepository.putKeyframe(projectId, current.copy(interpolation = interpolation))
            val after = editorRepository.load(projectId).timeline.keyframes.filter { it.ownerId == ownerId }
            historyService.record(KeyframeHistoryEntry(projectId, "Keyframe Interpolation", before, after))
            onDone()
        }
    }

    private fun mutateClip(
        projectId: String,
        clipId: String,
        label: String,
        key: String,
        onDone: () -> Unit,
        block: suspend (com.videoflow.app.domain.editor.TimelineClip) -> Unit
    ) = launchEdit(onDone) {
        val editor = editorRepository.load(projectId)
        val before = editor.timeline.clips.first { it.id == clipId }
        val beforeFrames = editor.timeline.keyframes.filter { it.ownerId == clipId }
        block(before)
        val fresh = editorRepository.load(projectId)
        val after = fresh.timeline.clips.first { it.id == clipId }
        val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == clipId }
        if (after != before || afterFrames != beforeFrames) {
            historyService.recordCoalesced(ClipHistoryEntry(projectId, label, listOf(before), listOf(after), beforeFrames, afterFrames), key)
        }
    }

    private fun mutateText(
        projectId: String,
        overlayId: String,
        label: String,
        key: String,
        onDone: () -> Unit,
        block: suspend () -> Unit
    ) = launchEdit(onDone) {
        val editor = editorRepository.load(projectId)
        val before = editor.timeline.textOverlays.first { it.id == overlayId }
        val beforeFrames = editor.timeline.keyframes.filter { it.ownerId == overlayId }
        block()
        val fresh = editorRepository.load(projectId)
        val after = fresh.timeline.textOverlays.first { it.id == overlayId }
        val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == overlayId }
        if (after != before || afterFrames != beforeFrames) {
            historyService.recordCoalesced(TextOverlayHistoryEntry(projectId, label, before, after, beforeFrames, afterFrames), key)
        }
    }

    private fun mutateImage(
        projectId: String,
        overlayId: String,
        label: String,
        key: String,
        onDone: () -> Unit,
        block: suspend () -> Unit
    ) = launchEdit(onDone) {
        val editor = editorRepository.load(projectId)
        val before = editor.timeline.imageOverlays.first { it.id == overlayId }
        val beforeFrames = editor.timeline.keyframes.filter { it.ownerId == overlayId }
        block()
        val fresh = editorRepository.load(projectId)
        val after = fresh.timeline.imageOverlays.first { it.id == overlayId }
        val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == overlayId }
        if (after != before || afterFrames != beforeFrames) {
            historyService.recordCoalesced(ImageOverlayHistoryEntry(projectId, label, before, after, beforeFrames, afterFrames), key)
        }
    }

    private fun launchEdit(onDone: () -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            onDone()
        }
    }

    private fun ownerValue(
        editor: com.videoflow.app.data.editor.EditorProject,
        ownerId: String,
        ownerType: KeyframeOwnerType,
        property: KeyframeProperty
    ): Triple<Long, Long, Float> = when (ownerType) {
        KeyframeOwnerType.CLIP -> {
            val clip = editor.timeline.clips.first { it.id == ownerId }
            Triple(clip.timelineStartUs, clip.timelineDurationUs, clipValue(clip, property))
        }
        KeyframeOwnerType.TEXT_OVERLAY -> {
            val overlay = editor.timeline.textOverlays.first { it.id == ownerId }
            require(property != KeyframeProperty.AUDIO_GAIN)
            Triple(overlay.timelineStartUs, overlay.timelineEndUs - overlay.timelineStartUs, textValue(overlay, property))
        }
        KeyframeOwnerType.IMAGE_OVERLAY -> {
            val overlay = editor.timeline.imageOverlays.first { it.id == ownerId }
            require(property != KeyframeProperty.AUDIO_GAIN)
            Triple(overlay.timelineStartUs, overlay.timelineEndUs - overlay.timelineStartUs, imageValue(overlay, property))
        }
    }

    private fun clipValue(clip: com.videoflow.app.domain.editor.TimelineClip, property: KeyframeProperty): Float = when (property) {
        KeyframeProperty.POSITION_X -> clip.transform.x
        KeyframeProperty.POSITION_Y -> clip.transform.y
        KeyframeProperty.SCALE_X -> clip.transform.scaleX
        KeyframeProperty.SCALE_Y -> clip.transform.scaleY
        KeyframeProperty.ROTATION -> clip.transform.rotationDegrees
        KeyframeProperty.OPACITY -> clip.opacity
        KeyframeProperty.AUDIO_GAIN -> clip.gainDb
    }

    private fun textValue(overlay: com.videoflow.app.domain.editor.TextOverlay, property: KeyframeProperty): Float = when (property) {
        KeyframeProperty.POSITION_X -> overlay.transform.x
        KeyframeProperty.POSITION_Y -> overlay.transform.y
        KeyframeProperty.SCALE_X -> overlay.transform.scaleX
        KeyframeProperty.SCALE_Y -> overlay.transform.scaleY
        KeyframeProperty.ROTATION -> overlay.transform.rotationDegrees
        KeyframeProperty.OPACITY -> overlay.opacity
        KeyframeProperty.AUDIO_GAIN -> error("Audio gain is not a text property")
    }

    private fun imageValue(overlay: com.videoflow.app.domain.editor.ImageOverlay, property: KeyframeProperty): Float = when (property) {
        KeyframeProperty.POSITION_X -> overlay.transform.x
        KeyframeProperty.POSITION_Y -> overlay.transform.y
        KeyframeProperty.SCALE_X -> overlay.transform.scaleX
        KeyframeProperty.SCALE_Y -> overlay.transform.scaleY
        KeyframeProperty.ROTATION -> overlay.transform.rotationDegrees
        KeyframeProperty.OPACITY -> overlay.transform.opacity
        KeyframeProperty.AUDIO_GAIN -> error("Audio gain is not an image property")
    }

    private fun normalizeRotation(value: Float): Float {
        if (!value.isFinite()) return 0f
        var result = value % 360f
        if (result > 180f) result -= 360f
        if (result < -180f) result += 360f
        return result
    }
}
