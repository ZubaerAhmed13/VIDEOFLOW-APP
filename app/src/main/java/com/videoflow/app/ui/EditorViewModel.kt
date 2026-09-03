package com.videoflow.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoflow.app.data.audio.WaveformService
import com.videoflow.app.data.db.SnapshotEntity
import com.videoflow.app.data.editor.EditorProject
import com.videoflow.app.data.editor.EditorPropertyService
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.data.editor.LockedTrackException
import com.videoflow.app.data.editor.TimelineOverlapException
import com.videoflow.app.data.history.ClipHistoryEntry
import com.videoflow.app.data.history.EditHistoryService
import com.videoflow.app.data.history.KeyframeHistoryEntry
import com.videoflow.app.data.history.TrackHistoryEntry
import com.videoflow.app.data.project.ProjectRepository
import com.videoflow.app.data.proxy.ProxyManager
import com.videoflow.app.data.snapshot.SnapshotService
import com.videoflow.app.domain.editor.CropRect
import com.videoflow.app.domain.editor.Keyframe
import com.videoflow.app.domain.editor.KeyframeInterpolation
import com.videoflow.app.domain.editor.KeyframeOwnerType
import com.videoflow.app.domain.editor.KeyframeProperty
import com.videoflow.app.domain.editor.ProxyQuality
import com.videoflow.app.domain.editor.TimelineClip
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
    private val projectRepository: ProjectRepository,
    private val proxyManager: ProxyManager,
    private val snapshotService: SnapshotService,
    private val waveformService: WaveformService,
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

    val proxyProgress = proxyManager.progress
    val history = historyService.state

    private var projectId: String? = null

    fun load(id: String) {
        projectId = id
        historyService.activateProject(id)
        viewModelScope.launch {
            reload(id)
            refreshSnapshots(id)
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
            editorRepository.addImageOverlay(id, assetId, _playheadUs.value)
            _message.value = "Image overlay added at playhead."
        } else {
            val clip = editorRepository.addClip(id, assetId, _playheadUs.value)
            historyService.record(ClipHistoryEntry(id, "Add Clip", emptyList(), listOf(clip)))
            _selectedClipId.value = clip.id
            _message.value = "Clip added at playhead."
            if (asset.mimeType?.startsWith("audio/") == true || asset.audioTrackCount > 0) {
                generateWaveform(assetId)
            }
        }
    }

    fun createTrack(type: TrackType) = edit { id ->
        editorRepository.createTrack(id, type, "")
        _message.value = "${type.name.lowercase().replaceFirstChar { it.uppercase() }} track created."
    }

    fun moveSelected(deltaUs: Long) = edit { id ->
        val before = selectedClip() ?: error("Select a clip first")
        val after = editorRepository.moveClip(id, before.id, (before.timelineStartUs + deltaUs).coerceAtLeast(0))
        historyService.record(ClipHistoryEntry(id, "Move Clip", listOf(before), listOf(after), keyframesFor(before.id), keyframesFor(before.id)))
    }

    fun trimSelectedStart(deltaSourceUs: Long) = edit { id ->
        val before = selectedClip() ?: error("Select a clip first")
        val next = (before.sourceStartUs + deltaSourceUs).coerceAtLeast(0)
        if (next >= before.sourceEndUs) error("Trim would remove the whole clip")
        val after = editorRepository.trimClipStart(id, before.id, next)
        historyService.record(ClipHistoryEntry(id, "Trim Clip Start", listOf(before), listOf(after), keyframesFor(before.id), keyframesFor(before.id)))
    }

    fun trimSelectedEnd(deltaSourceUs: Long) = edit { id ->
        val before = selectedClip() ?: error("Select a clip first")
        val assetDuration = _project.value?.mediaAssets?.firstOrNull { it.id == before.assetId }?.durationUs
            ?: error("Source duration unavailable")
        val next = (before.sourceEndUs + deltaSourceUs).coerceAtMost(assetDuration)
        if (next <= before.sourceStartUs) error("Trim would remove the whole clip")
        val after = editorRepository.trimClipEnd(id, before.id, next)
        historyService.record(ClipHistoryEntry(id, "Trim Clip End", listOf(before), listOf(after), keyframesFor(before.id), keyframesFor(before.id)))
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
        editorRepository.addTextOverlay(id, _playheadUs.value, "Text")
        _message.value = "Text overlay added."
    }

    fun addOpacityKeyframe() = edit { id ->
        val clip = selectedClip() ?: error("Select a clip first")
        val beforeFrames = keyframesFor(clip.id)
        val local = (_playheadUs.value - clip.timelineStartUs).coerceIn(0L, clip.timelineDurationUs)
        editorRepository.putKeyframe(
            id,
            Keyframe(
                id = UUID.randomUUID().toString(),
                ownerId = clip.id,
                ownerType = KeyframeOwnerType.CLIP,
                property = KeyframeProperty.OPACITY,
                timeUs = local,
                value = clip.opacity,
                interpolation = KeyframeInterpolation.LINEAR
            )
        )
        val fresh = editorRepository.load(id)
        val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == clip.id }
        historyService.record(KeyframeHistoryEntry(id, "Add Keyframe", beforeFrames, afterFrames))
        _message.value = "Opacity keyframe added at playhead."
    }

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
            historyService.record(ClipHistoryEntry(id, label, listOf(before), listOf(after), beforeFrames, afterFrames))
        }
    }

    private fun mutateTrack(label: String, trackId: String, block: suspend (String) -> Unit) {
        edit { id ->
            val before = _editor.value?.timeline?.tracks?.first { it.id == trackId } ?: error("Track not found")
            block(id)
            val after = editorRepository.load(id).timeline.tracks.first { it.id == trackId }
            historyService.record(TrackHistoryEntry(id, label, before, after))
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
}
