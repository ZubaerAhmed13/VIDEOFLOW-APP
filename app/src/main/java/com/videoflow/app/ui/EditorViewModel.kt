package com.videoflow.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoflow.app.data.editor.EditorProject
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.data.editor.LockedTrackException
import com.videoflow.app.data.editor.TimelineOverlapException
import com.videoflow.app.data.project.ProjectRepository
import com.videoflow.app.domain.editor.Keyframe
import com.videoflow.app.domain.editor.KeyframeInterpolation
import com.videoflow.app.domain.editor.KeyframeOwnerType
import com.videoflow.app.domain.editor.KeyframeProperty
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
    private val projectRepository: ProjectRepository
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

    private var projectId: String? = null

    fun load(id: String) {
        projectId = id
        viewModelScope.launch { reload(id) }
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
            _selectedClipId.value = clip.id
            _message.value = "Clip added at playhead."
        }
    }

    fun createTrack(type: TrackType) = edit { id ->
        editorRepository.createTrack(id, type, "")
        _message.value = "${type.name.lowercase().replaceFirstChar { it.uppercase() }} track created."
    }

    fun moveSelected(deltaUs: Long) = edit { id ->
        val clip = selectedClip() ?: error("Select a clip first")
        editorRepository.moveClip(id, clip.id, (clip.timelineStartUs + deltaUs).coerceAtLeast(0))
    }

    fun trimSelectedStart(deltaSourceUs: Long) = edit { id ->
        val clip = selectedClip() ?: error("Select a clip first")
        val next = (clip.sourceStartUs + deltaSourceUs).coerceAtLeast(0)
        if (next >= clip.sourceEndUs) error("Trim would remove the whole clip")
        editorRepository.trimClipStart(id, clip.id, next)
    }

    fun trimSelectedEnd(deltaSourceUs: Long) = edit { id ->
        val clip = selectedClip() ?: error("Select a clip first")
        val assetDuration = _project.value?.mediaAssets?.firstOrNull { it.id == clip.assetId }?.durationUs
            ?: error("Source duration unavailable")
        val next = (clip.sourceEndUs + deltaSourceUs).coerceAtMost(assetDuration)
        if (next <= clip.sourceStartUs) error("Trim would remove the whole clip")
        editorRepository.trimClipEnd(id, clip.id, next)
    }

    fun splitSelected() = edit { id ->
        val clip = selectedClip() ?: error("Select a clip first")
        if (_playheadUs.value <= clip.timelineStartUs || _playheadUs.value >= clip.timelineEndUs) {
            error("Move the playhead inside the selected clip before splitting")
        }
        val (_, right) = editorRepository.splitClip(id, clip.id, _playheadUs.value)
        _selectedClipId.value = right.id
        _message.value = "Clip split."
    }

    fun duplicateSelected() = edit { id ->
        val clip = selectedClip() ?: error("Select a clip first")
        val duplicate = editorRepository.duplicateClip(id, clip.id)
        _selectedClipId.value = duplicate.id
        _message.value = "Clip duplicated without duplicating source media."
    }

    fun deleteSelected() = edit { id ->
        val clip = selectedClip() ?: error("Select a clip first")
        editorRepository.deleteClip(id, clip.id)
        _selectedClipId.value = null
        _message.value = "Timeline clip deleted. Original media was not changed."
    }

    fun addText() = edit { id ->
        editorRepository.addTextOverlay(id, _playheadUs.value, "Text")
        _message.value = "Text overlay added."
    }

    fun addOpacityKeyframe() = edit { id ->
        val clip = selectedClip() ?: error("Select a clip first")
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
        _message.value = "Opacity keyframe added at playhead."
    }

    fun toggleTrackMute(trackId: String, value: Boolean) = edit { id -> editorRepository.setTrackMuted(id, trackId, value) }
    fun toggleTrackSolo(trackId: String, value: Boolean) = edit { id -> editorRepository.setTrackSolo(id, trackId, value) }
    fun toggleTrackLock(trackId: String, value: Boolean) = edit { id -> editorRepository.setTrackLocked(id, trackId, value) }
    fun toggleTrackVisible(trackId: String, value: Boolean) = edit { id -> editorRepository.setTrackVisible(id, trackId, value) }

    fun clearMessage() { _message.value = null }

    private fun selectedClip(): TimelineClip? =
        _editor.value?.timeline?.clips?.firstOrNull { it.id == _selectedClipId.value }

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
        _editor.value = editorRepository.load(id)
        val duration = _editor.value?.timeline?.durationUs ?: 0L
        if (_playheadUs.value > duration) _playheadUs.value = duration
        if (_selectedClipId.value != null && selectedClip() == null) _selectedClipId.value = null
    }
}
