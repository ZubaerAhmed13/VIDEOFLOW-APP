package com.videoflow.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoflow.app.data.editor.TrackLifecycleService
import com.videoflow.app.data.history.EditHistoryService
import com.videoflow.app.data.history.TrackBundleHistoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackLifecycleViewModel @Inject constructor(
    private val trackLifecycleService: TrackLifecycleService,
    private val historyService: EditHistoryService
) : ViewModel() {
    fun deleteConfirmed(projectId: String, trackId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val bundle = trackLifecycleService.deleteTrack(projectId, trackId, confirmed = true)
            historyService.record(
                TrackBundleHistoryEntry(
                    projectId = projectId,
                    label = "Delete Track",
                    track = bundle.track,
                    clips = bundle.clips,
                    textOverlays = bundle.textOverlays,
                    imageOverlays = bundle.imageOverlays,
                    keyframes = bundle.keyframes
                )
            )
            onDone()
        }
    }
}
