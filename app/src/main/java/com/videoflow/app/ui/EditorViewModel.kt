package com.videoflow.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoflow.app.data.audio.WaveformService
import com.videoflow.app.data.db.SnapshotEntity
import com.videoflow.app.data.editor.EditorProject
import com.videoflow.app.data.editor.EditorPropertyService
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.data.editor.LockedTrackException
import com.videoflow.app.data.editor.OverlayEditorService
import com.videoflow.app.data.editor.TimelineOverlapException
import com.videoflow.app.data.history.ClipHistoryEntry
import com.videoflow.app.data.history.EditHistoryService
import com.videoflow.app.data.history.ImageOverlayHistoryEntry
import com.videoflow.app.data.history.KeyframeHistoryEntry
import com.videoflow.app.data.history.TextOverlayHistoryEntry
import com.videoflow.app.data.history.TrackHistoryEntry
import com.videoflow.app.data.media.ThumbnailService
import com.videoflow.app.data.project.ProjectRepository
import com.videoflow.app.data.proxy.ProxyManager
import com.videoflow.app.data.snapshot.SnapshotService
import com.videoflow.app.domain.editor.CropRect
import com.videoflow.app.domain.editor.ImageOverlay
import com.videoflow.app.domain.editor.Keyframe
import com.videoflow.app.domain.editor.KeyframeInterpolation
import com.videoflow.app.domain.editor.KeyframeOwnerType
import com.videoflow.app.domain.editor.KeyframeProperty
import com.videoflow.app.domain.editor.ProxyQuality
import com.videoflow.app.domain.editor.TextOverlay
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineEngine
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.domain.model.VideoFlowProject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val editorRepository: EditorRepository,
    private val propertyService: EditorPropertyService,
    private val overlayService: OverlayEditorService,
    private val projectRepository: ProjectRepository,
    private val proxyManager: ProxyManager,
    private val snapshotService: SnapshotService,
    private val waveformService: WaveformService,
    private val thumbnailService: ThumbnailService,
    private val historyService: EditHistoryService
) : ViewModel() {
    private val _project = MutableStateFlow<VideoFlowProject?>(null)
    val project = _project.asStateFlow()

    private val _editor = MutableStateFlow<EditorProject?>(null)
    val editor = _editor.asStateFlow()

    private val _playheadUs = MutableStateFlow(0L)
    val playheadUs = _playheadUs.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId = _selectedClipId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    private val _snapshots = MutableStateFlow<List<SnapshotEntity>>(emptyList())
    val snapshots = _snapshots.asStateFlow()

    private val _waveforms = MutableStateFlow<Map<String, FloatArray>>(emptyMap())
    val waveforms = _waveforms.asStateFlow()

    private val _thumbnails = MutableStateFlow<Map<String, String>>(emptyMap())
    val thumbnails = _thumbnails.asStateFlow()

    val proxyProgress = proxyManager.progress
    val history = historyService.state

    private var projectId: String? = null

    fun load(id: String) {
        projectId = id
        historyService.activateProject(id)
        viewModelScope.launch {
            reload(id)
            refreshSnapshots(id)
            warmThumbnails()
        }
    }

    fun setPlayheadUs(value: Long) {
        val duration = _editor.value?.timeline?.durationUs ?: Long.MAX_VALUE
        _playheadUs.value = value.coerceIn(0L, maxOf(0L, duration))
    }

    fun selectClip(id: String?) {
        _selectedClipId.value = id
    }

    fun addAsset(assetId: String) = edit { id ->
        val asset = _project.value?.mediaAssets?.firstOrNull { it.id == assetId }
            ?: error("Media asset is unavailable")
        if (asset.mimeType?.startsWith("image/") == true) {
            val overlay = editorRepository.addImageOverlay(id, assetId, _playheadUs.value)
            historyService.record(ImageOverlayHistoryEntry(id, "Add Image Overlay", null, overlay))
            generateThumbnail(assetId)
            _message.value = "Image overlay added at playhead."
        } else {
            val clip = editorRepository.addClip(id, assetId, _playheadUs.value)
            historyService.record(ClipHistoryEntry(id, "Add Clip", emptyList(), listOf(clip)))
            _selectedClipId.value = clip.id
            _message.value = "Clip added at playhead."
            generateThumbnail(assetId, _playheadUs.value)
            if (asset.mimeType?.startsWith("audio/") == true || asset.audioTrackCount > 0) {
                generateWaveform(assetId)
            }
        }
    }

    fun createTrack(type: TrackType) = edit { id ->
        val created = editorRepository.createTrack(id, type, "")
        historyService.record(TrackHistoryEntry(id, "Create Track", null, created))
        _message.value = "${type.name.lowercase().replaceFirstChar { it.uppercase() }} track created."
    }

    fun renameTrack(trackId: String, name: String) = edit { id ->
        val before = _editor.value?.timeline?.tracks?.firstOrNull { it.id == trackId } ?: error("Track not found")
        editorRepository.renameTrack(id, trackId, name)
        val after = editorRepository.load(id).timeline.tracks.first { it.id == trackId }
        historyService.record(TrackHistoryEntry(id, "Rename Track", before, after))
    }

    fun deleteEmptyTrack(trackId: String) = edit { id ->
        val timeline = _editor.value?.timeline ?: error("Timeline unavailable")
        val before = timeline.tracks.firstOrNull { it.id == trackId } ?: error("Track not found")
        if (before.locked) throw LockedTrackException("Track ${before.name} is locked")
        val containsItems = timeline.clips.any { it.trackId == trackId } ||
            timeline.textOverlays.any { it.trackId == trackId } ||
            timeline.imageOverlays.any { it.trackId == trackId }
        if (containsItems) error("Only an empty track can be deleted from this control. Move or delete its timeline items first.")
        editorRepository.deleteTrack(id, trackId, confirmDeleteClips = false)
        historyService.record(TrackHistoryEntry(id, "Delete Track", before, null))
        _message.value = "Empty track deleted."
    }

    fun moveSelected(deltaUs: Long) = moveSelectedSnapped(deltaUs, pixelsPerSecond = 72.0)

    fun moveSelectedSnapped(deltaUs: Long, pixelsPerSecond: Double, thresholdPx: Double = 8.0) = edit { id ->
        val before = selectedClip() ?: error("Select a clip first")
        val timeline = _editor.value?.timeline ?: error("Timeline unavailable")
        val candidate = (before.timelineStartUs + deltaUs).coerceAtLeast(0L)
        val targets = buildSet {
            add(0L)
            timeline.clips.filterNot { it.id == before.id }.forEach {
                add(it.timelineStartUs)
                add(it.timelineEndUs)
            }
        }
        val snapped = TimelineEngine.snapTime(candidate, pixelsPerSecond.coerceAtLeast(1.0), thresholdPx, targets)
        val beforeFrames = keyframesFor(before.id)
        val after = editorRepository.moveClip(id, before.id, snapped)
        historyService.recordCoalesced(
            ClipHistoryEntry(id, "Move Clip", listOf(before), listOf(after), beforeFrames, beforeFrames),
            key = "clip:${before.id}:move"
        )
    }

    fun moveSelectedToTrack(targetTrackId: String) = edit { id ->
        val before = selectedClip() ?: error("Select a clip first")
        val beforeFrames = keyframesFor(before.id)
        val after = editorRepository.moveClip(id, before.id, before.timelineStartUs, targetTrackId)
        historyService.record(
            ClipHistoryEntry(id, "Move Clip To Track", listOf(before), listOf(after), beforeFrames, beforeFrames)
        )
    }

    fun trimSelectedStart(deltaSourceUs: Long) = edit { id ->
        val before = selectedClip() ?: error("Select a clip first")
        val beforeFrames = keyframesFor(before.id)
        val next = (before.sourceStartUs + deltaSourceUs).coerceAtLeast(0)
        if (next >= before.sourceEndUs) error("Trim would remove the whole clip")
        val after = editorRepository.trimClipStart(id, before.id, next)
        historyService.recordCoalesced(
            ClipHistoryEntry(id, "Trim Clip Start", listOf(before), listOf(after), beforeFrames, beforeFrames),
            key = "clip:${before.id}:trim-start"
        )
    }

    fun trimSelectedEnd(deltaSourceUs: Long) = edit { id ->
        val before = selectedClip() ?: error("Select a clip first")
        val beforeFrames = keyframesFor(before.id)
        val assetDuration = _project.value?.mediaAssets?.firstOrNull { it.id == before.assetId }?.durationUs
            ?: error("Source duration unavailable")
        val next = (before.sourceEndUs + deltaSourceUs).coerceAtMost(assetDuration)
        if (next <= before.sourceStartUs) error("Trim would remove the whole clip")
        val after = editorRepository.trimClipEnd(id, before.id, next)
        historyService.recordCoalesced(
            ClipHistoryEntry(id, "Trim Clip End", listOf(before), listOf(after), beforeFrames, beforeFrames),
            key = "clip:${before.id}:trim-end"
        )
    }

    fun splitSelected() = edit { id ->
        val before = selectedClip() ?: error("Select a clip first")
        val beforeFrames = keyframesFor(before.id)
        if (_playheadUs.value <= before.timelineStartUs || _playheadUs.value >= before.timelineEndUs) {
            error("Move the playhead inside the selected clip before splitting")
        }
        val (left, right) = editorRepository.splitClip(id, before.id, _playheadUs.value)
        val fresh = editorRepository.load(id)
        val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == left.id || it.ownerId == right.id }
        historyService.record(
            ClipHistoryEntry(id, "Split Clip", listOf(before), listOf(left, right), beforeFrames, afterFrames)
        )
        _selectedClipId.value = right.id
        _message.value = "Clip split."
    }

    fun duplicateSelected() = edit { id ->
        val source = selectedClip() ?: error("Select a clip first")
        val duplicate = editorRepository.duplicateClip(id, source.id)
        val fresh = editorRepository.load(id)
        val duplicateFrames = fresh.timeline.keyframes.filter { it.ownerId == duplicate.id }
        historyService.record(ClipHistoryEntry(id, "Duplicate Clip", emptyList(), listOf(duplicate), emptyList(), duplicateFrames))
        _selectedClipId.value = duplicate.id
        _message.value = "Clip duplicated without duplicating source media."
    }

    fun deleteSelected() = edit { id ->
        val before = selectedClip() ?: error("Select a clip first")
        val beforeFrames = keyframesFor(before.id)
        editorRepository.deleteClip(id, before.id)
        historyService.record(ClipHistoryEntry(id, "Delete Clip", listOf(before), emptyList(), beforeFrames, emptyList()))
        _selectedClipId.value = null
        _message.value = "Timeline clip deleted. Original media was not changed."
    }

    fun addText() = edit { id ->
        val overlay = editorRepository.addTextOverlay(id, _playheadUs.value, "Text")
        historyService.record(TextOverlayHistoryEntry(id, "Add Text Overlay", null, overlay))
        _message.value = "Text overlay added."
    }

    fun addKeyframe(
        property: KeyframeProperty,
        interpolation: KeyframeInterpolation = KeyframeInterpolation.LINEAR
    ) = edit { id ->
        val clip = selectedClip() ?: error("Select a clip first")
        val beforeFrames = keyframesFor(clip.id)
        val local = (_playheadUs.value - clip.timelineStartUs).coerceIn(0L, clip.timelineDurationUs)
        val value = when (property) {
            KeyframeProperty.POSITION_X -> clip.transform.x
            KeyframeProperty.POSITION_Y -> clip.transform.y
            KeyframeProperty.SCALE_X -> clip.transform.scaleX
            KeyframeProperty.SCALE_Y -> clip.transform.scaleY
            KeyframeProperty.ROTATION -> clip.transform.rotationDegrees
            KeyframeProperty.OPACITY -> clip.opacity
            KeyframeProperty.AUDIO_GAIN -> clip.gainDb
        }
        editorRepository.putKeyframe(
            id,
            Keyframe(
                id = UUID.randomUUID().toString(),
                ownerId = clip.id,
                ownerType = KeyframeOwnerType.CLIP,
                property = property,
                timeUs = local,
                value = value,
                interpolation = interpolation
            )
        )
        val fresh = editorRepository.load(id)
        val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == clip.id }
        historyService.record(KeyframeHistoryEntry(id, "Add ${property.name} Keyframe", beforeFrames, afterFrames))
        _message.value = "${property.name.replace('_', ' ')} keyframe added at playhead."
    }

    fun addOpacityKeyframe() = addKeyframe(KeyframeProperty.OPACITY)

    fun moveTransform(dx: Float, dy: Float) = mutateSelectedProperty("Move Transform") { id, before ->
        propertyService.setTransform(
            id, before.id,
            (before.transform.x + dx).coerceIn(0f, 1f),
            (before.transform.y + dy).coerceIn(0f, 1f),
            before.transform.scaleX, before.transform.scaleY,
            before.transform.rotationDegrees, before.opacity
        )
    }

    fun setScale(scale: Float) = mutateSelectedProperty("Scale Clip") { id, before ->
        val value = scale.coerceIn(0.05f, 10f)
        propertyService.setTransform(
            id, before.id, before.transform.x, before.transform.y, value, value,
            before.transform.rotationDegrees, before.opacity
        )
    }

    fun rotateSelected90() = mutateSelectedProperty("Rotate Clip") { id, before -> propertyService.rotate90(id, before.id) }

    fun toggleFlipHorizontal() = mutateSelectedProperty("Flip Horizontal") { id, before ->
        propertyService.setFlipHorizontal(id, before.id, !before.transform.flipHorizontal)
    }

    fun toggleFlipVertical() = mutateSelectedProperty("Flip Vertical") { id, before ->
        propertyService.setFlipVertical(id, before.id, !before.transform.flipVertical)
    }

    fun setOpacity(opacity: Float) = mutateSelectedProperty("Set Opacity") { id, before ->
        propertyService.setOpacity(id, before.id, opacity.coerceIn(0f, 1f))
    }

    fun setSpeed(speed: Double) = mutateSelectedProperty("Set Speed") { id, before -> propertyService.setSpeed(id, before.id, speed) }

    fun setClipGain(gainDb: Float) = mutateSelectedProperty("Set Clip Gain") { id, before ->
        propertyService.setClipGain(id, before.id, gainDb)
    }

    fun setFades(fadeInUs: Long, fadeOutUs: Long) = mutateSelectedProperty("Set Audio Fades") { id, before ->
        propertyService.setFades(id, before.id, fadeInUs, fadeOutUs)
    }

    fun setCropPreset(width: Int, height: Int) = mutateSelectedProperty("Crop Clip") { id, before ->
        val asset = _project.value?.mediaAssets?.firstOrNull { it.id == before.assetId }
            ?: error("Media asset unavailable")
        val sourceW = asset.width ?: error("Source width unavailable")
        val sourceH = asset.height ?: error("Source height unavailable")
        propertyService.setCrop(id, before.id, centeredCrop(sourceW, sourceH, width.toFloat() / height.toFloat()))
    }

    fun resetTransform() = mutateSelectedProperty("Reset Transform") { id, before -> propertyService.resetTransform(id, before.id) }

    fun toggleTrackMute(trackId: String, value: Boolean) = mutateTrack("Track Mute", trackId) { id -> editorRepository.setTrackMuted(id, trackId, value) }
    fun toggleTrackSolo(trackId: String, value: Boolean) = mutateTrack("Track Solo", trackId) { id -> editorRepository.setTrackSolo(id, trackId, value) }
    fun toggleTrackLock(trackId: String, value: Boolean) = mutateTrack("Track Lock", trackId) { id -> editorRepository.setTrackLocked(id, trackId, value) }
    fun toggleTrackVisible(trackId: String, value: Boolean) = mutateTrack("Track Visibility", trackId) { id -> editorRepository.setTrackVisible(id, trackId, value) }
    fun setTrackGain(trackId: String, gainDb: Float) = mutateTrack("Track Gain", trackId) { id -> editorRepository.setTrackGain(id, trackId, gainDb) }

    fun updateTextContent(overlayId: String, content: String) = mutateTextOverlay("Edit Text", overlayId) { id, _ ->
        overlayService.updateText(id, overlayId, content = content)
    }

    fun adjustTextSize(overlayId: String, deltaSp: Float) = mutateTextOverlay("Text Size", overlayId) { id, before ->
        overlayService.updateText(id, overlayId, fontSizeSp = (before.fontSizeSp + deltaSp).coerceIn(6f, 256f))
    }

    fun toggleTextBold(overlayId: String) = mutateTextOverlay("Text Weight", overlayId) { id, before ->
        overlayService.updateText(id, overlayId, fontWeight = if (before.fontWeight >= 600) 400 else 700)
    }

    fun toggleTextItalic(overlayId: String) = mutateTextOverlay("Text Italic", overlayId) { id, before ->
        overlayService.updateText(id, overlayId, italic = !before.italic)
    }

    fun moveText(overlayId: String, dx: Float, dy: Float) = mutateTextOverlay("Move Text", overlayId) { id, before ->
        overlayService.updateText(
            id,
            overlayId,
            x = (before.transform.x + dx).coerceIn(0f, 1f),
            y = (before.transform.y + dy).coerceIn(0f, 1f)
        )
    }

    fun rotateText(overlayId: String) = mutateTextOverlay("Rotate Text", overlayId) { id, before ->
        overlayService.updateText(id, overlayId, rotationDegrees = before.transform.rotationDegrees + 15f)
    }

    fun setTextOpacity(overlayId: String, opacity: Float) = mutateTextOverlay("Text Opacity", overlayId) { id, _ ->
        overlayService.updateText(id, overlayId, opacity = opacity.coerceIn(0f, 1f))
    }

    fun deleteText(overlayId: String) = edit { id ->
        val before = _editor.value?.timeline?.textOverlays?.firstOrNull { it.id == overlayId } ?: error("Text overlay not found")
        val frames = keyframesFor(overlayId)
        overlayService.deleteText(id, overlayId)
        historyService.record(TextOverlayHistoryEntry(id, "Delete Text Overlay", before, null, frames, emptyList()))
    }

    fun adjustImageScale(overlayId: String, delta: Float) = mutateImageOverlay("Image Scale", overlayId) { id, before ->
        overlayService.updateImage(id, overlayId, scale = (before.transform.scaleX + delta).coerceIn(0.05f, 10f))
    }

    fun moveImage(overlayId: String, dx: Float, dy: Float) = mutateImageOverlay("Move Image", overlayId) { id, before ->
        overlayService.updateImage(
            id,
            overlayId,
            x = (before.transform.x + dx).coerceIn(0f, 1f),
            y = (before.transform.y + dy).coerceIn(0f, 1f)
        )
    }

    fun rotateImage(overlayId: String) = mutateImageOverlay("Rotate Image", overlayId) { id, before ->
        overlayService.updateImage(id, overlayId, rotationDegrees = before.transform.rotationDegrees + 15f)
    }

    fun setImageOpacity(overlayId: String, opacity: Float) = mutateImageOverlay("Image Opacity", overlayId) { id, _ ->
        overlayService.updateImage(id, overlayId, opacity = opacity.coerceIn(0f, 1f))
    }

    fun deleteImage(overlayId: String) = edit { id ->
        val before = _editor.value?.timeline?.imageOverlays?.firstOrNull { it.id == overlayId } ?: error("Image overlay not found")
        val frames = keyframesFor(overlayId)
        overlayService.deleteImage(id, overlayId)
        historyService.record(ImageOverlayHistoryEntry(id, "Delete Image Overlay", before, null, frames, emptyList()))
    }

    fun generateProxy(assetId: String, quality: ProxyQuality = ProxyQuality.BALANCED) {
        val id = projectId ?: return
        viewModelScope.launch {
            _saving.value = true
            try {
                proxyManager.generate(assetId, quality)
                reload(id)
                _message.value = "Proxy ready. The original remains the final-quality render source."
            } catch (t: Throwable) {
                _message.value = t.message ?: "Proxy generation failed."
            } finally {
                _saving.value = false
            }
        }
    }

    fun cancelProxy(assetId: String) {
        viewModelScope.launch { proxyManager.cancel(assetId); projectId?.let { reload(it) } }
    }

    fun deleteProxy(assetId: String) {
        viewModelScope.launch { proxyManager.delete(assetId); projectId?.let { reload(it) } }
    }

    fun createSnapshot(name: String = "Snapshot") {
        val id = projectId ?: return
        viewModelScope.launch {
            _saving.value = true
            try {
                snapshotService.create(id, name)
                refreshSnapshots(id)
                _message.value = "Snapshot saved without copying source media."
            } catch (t: Throwable) {
                _message.value = t.message ?: "Snapshot could not be saved."
            } finally {
                _saving.value = false
            }
        }
    }

    fun restoreSnapshot(snapshotId: String) {
        val id = projectId ?: return
        viewModelScope.launch {
            _saving.value = true
            try {
                snapshotService.restore(snapshotId)
                historyService.activateProject("snapshot-reset")
                historyService.activateProject(id)
                reload(id)
                refreshSnapshots(id)
                _message.value = "Snapshot restored transactionally."
            } catch (t: Throwable) {
                _message.value = t.message ?: "Snapshot restore failed."
            } finally {
                _saving.value = false
            }
        }
    }

    fun deleteSnapshot(snapshotId: String) {
        val id = projectId ?: return
        viewModelScope.launch {
            snapshotService.delete(snapshotId)
            refreshSnapshots(id)
        }
    }

    fun generateWaveform(assetId: String) {
        viewModelScope.launch {
            runCatching { waveformService.loadOrGenerate(assetId) }
                .onSuccess { result -> _waveforms.value = _waveforms.value + (assetId to result.peaks) }
        }
    }

    fun generateThumbnail(assetId: String, timeUs: Long = 0L) {
        viewModelScope.launch {
            runCatching { thumbnailService.loadOrGenerate(assetId, timeUs) }
                .onSuccess { result ->
                    if (result != null) _thumbnails.value = _thumbnails.value + (assetId to result.path)
                }
        }
    }

    fun undo() {
        val id = projectId ?: return
        viewModelScope.launch {
            _saving.value = true
            try {
                historyService.undo()?.let { _message.value = "Undid: $it" }
                reload(id)
            } catch (t: Throwable) {
                _message.value = t.message ?: "Undo failed."
            } finally { _saving.value = false }
        }
    }

    fun redo() {
        val id = projectId ?: return
        viewModelScope.launch {
            _saving.value = true
            try {
                historyService.redo()?.let { _message.value = "Redid: $it" }
                reload(id)
            } catch (t: Throwable) {
                _message.value = t.message ?: "Redo failed."
            } finally { _saving.value = false }
        }
    }

    fun clearMessage() { _message.value = null }

    private fun selectedClip(): TimelineClip? =
        _editor.value?.timeline?.clips?.firstOrNull { it.id == _selectedClipId.value }

    private fun keyframesFor(ownerId: String): List<Keyframe> =
        _editor.value?.timeline?.keyframes.orEmpty().filter { it.ownerId == ownerId }

    private fun mutateSelectedProperty(label: String, block: suspend (String, TimelineClip) -> Unit) {
        edit { id ->
            val before = selectedClip() ?: error("Select a clip first")
            val beforeFrames = keyframesFor(before.id)
            block(id, before)
            val fresh = editorRepository.load(id)
            val after = fresh.timeline.clips.first { it.id == before.id }
            val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == before.id }
            historyService.recordCoalesced(
                ClipHistoryEntry(id, label, listOf(before), listOf(after), beforeFrames, afterFrames),
                key = "clip:${before.id}:$label"
            )
        }
    }

    private fun mutateTrack(label: String, trackId: String, block: suspend (String) -> Unit) {
        edit { id ->
            val before = _editor.value?.timeline?.tracks?.first { it.id == trackId } ?: error("Track not found")
            block(id)
            val after = editorRepository.load(id).timeline.tracks.first { it.id == trackId }
            historyService.recordCoalesced(TrackHistoryEntry(id, label, before, after), key = "track:$trackId:$label")
        }
    }

    private fun mutateTextOverlay(
        label: String,
        overlayId: String,
        block: suspend (String, TextOverlay) -> TextOverlay
    ) {
        edit { id ->
            val before = _editor.value?.timeline?.textOverlays?.firstOrNull { it.id == overlayId }
                ?: error("Text overlay not found")
            val beforeFrames = keyframesFor(overlayId)
            val after = block(id, before)
            val freshFrames = editorRepository.load(id).timeline.keyframes.filter { it.ownerId == overlayId }
            historyService.recordCoalesced(
                TextOverlayHistoryEntry(id, label, before, after, beforeFrames, freshFrames),
                key = "text:$overlayId:$label"
            )
        }
    }

    private fun mutateImageOverlay(
        label: String,
        overlayId: String,
        block: suspend (String, ImageOverlay) -> ImageOverlay
    ) {
        edit { id ->
            val before = _editor.value?.timeline?.imageOverlays?.firstOrNull { it.id == overlayId }
                ?: error("Image overlay not found")
            val beforeFrames = keyframesFor(overlayId)
            val after = block(id, before)
            val freshFrames = editorRepository.load(id).timeline.keyframes.filter { it.ownerId == overlayId }
            historyService.recordCoalesced(
                ImageOverlayHistoryEntry(id, label, before, after, beforeFrames, freshFrames),
                key = "image:$overlayId:$label"
            )
        }
    }

    private fun edit(block: suspend (String) -> Unit) {
        val id = projectId ?: return
        viewModelScope.launch {
            _saving.value = true
            try {
                block(id)
                reload(id)
            } catch (locked: LockedTrackException) {
                _message.value = locked.message ?: "Track is locked."
            } catch (overlap: TimelineOverlapException) {
                _message.value = "That edit would overlap another clip on the same track. Use another compatible track or a free timeline position."
            } catch (t: Throwable) {
                _message.value = t.message ?: "The edit could not be completed."
            } finally {
                _saving.value = false
            }
        }
    }

    private suspend fun reload(id: String) {
        _project.value = projectRepository.getProject(id)
        _project.value?.mediaAssets.orEmpty().forEach { proxyManager.reconcile(it.id) }
        _editor.value = editorRepository.load(id)
        val duration = _editor.value?.timeline?.durationUs ?: 0L
        if (_playheadUs.value > duration) _playheadUs.value = duration
        if (_selectedClipId.value != null && selectedClip() == null) _selectedClipId.value = null
    }

    private suspend fun refreshSnapshots(id: String) {
        _snapshots.value = snapshotService.list(id)
    }

    private suspend fun warmThumbnails() {
        _project.value?.mediaAssets.orEmpty()
            .asSequence()
            .filter { it.mimeType?.startsWith("video/") == true || it.mimeType?.startsWith("image/") == true }
            .take(MAX_WARM_THUMBNAILS)
            .forEach { asset ->
                runCatching { thumbnailService.loadOrGenerate(asset.id, 0L) }
                    .getOrNull()
                    ?.let { result -> _thumbnails.value = _thumbnails.value + (asset.id to result.path) }
            }
    }

    private fun centeredCrop(sourceWidth: Int, sourceHeight: Int, targetAspect: Float): CropRect {
        require(sourceWidth > 0 && sourceHeight > 0 && targetAspect > 0f)
        val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
        return if (sourceAspect > targetAspect) {
            val normalizedWidth = targetAspect / sourceAspect
            val margin = (1f - normalizedWidth) / 2f
            CropRect(margin, 0f, 1f - margin, 1f)
        } else {
            val normalizedHeight = sourceAspect / targetAspect
            val margin = (1f - normalizedHeight) / 2f
            CropRect(0f, margin, 1f, 1f - margin)
        }
    }

    companion object {
        private const val MAX_WARM_THUMBNAILS = 24
    }
}
