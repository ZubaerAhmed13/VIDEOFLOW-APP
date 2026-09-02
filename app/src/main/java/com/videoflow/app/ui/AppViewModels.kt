package com.videoflow.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoflow.app.data.diagnostics.DiagnosticEvent
import com.videoflow.app.data.diagnostics.LocalDiagnosticLog
import com.videoflow.app.data.device.DeviceCapabilityProfile
import com.videoflow.app.data.device.DeviceCapabilityRepository
import com.videoflow.app.data.project.AddMediaResult
import com.videoflow.app.data.project.ProjectRepository
import com.videoflow.app.domain.model.ImportState
import com.videoflow.app.domain.model.VideoFlowProject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: ProjectRepository) : ViewModel() {
    val projects = repository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String, onDone: (String) -> Unit) {
        viewModelScope.launch { onDone(repository.createProject(name)) }
    }
}

@HiltViewModel
class ProjectViewModel @Inject constructor(private val repository: ProjectRepository) : ViewModel() {
    private val _project = MutableStateFlow<VideoFlowProject?>(null)
    val project = _project.asStateFlow()

    private val _importState = MutableStateFlow(ImportState.Idle)
    val importState = _importState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var importJob: Job? = null

    fun load(id: String) {
        viewModelScope.launch {
            _project.value = repository.getProject(id)
            _project.value?.mediaAssets?.forEach { repository.verifySource(it) }
            _project.value = repository.getProject(id)
        }
    }

    fun pickerOpened() {
        if (_importState.value == ImportState.Idle || _importState.value == ImportState.Ready || _importState.value == ImportState.Error || _importState.value == ImportState.Cancelled) {
            _importState.value = ImportState.Selecting
        }
    }

    fun pickerCancelled() {
        if (_importState.value == ImportState.Selecting) _importState.value = ImportState.Cancelled
    }

    fun addMedia(projectId: String, uri: Uri) {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            try {
                val result = repository.addMedia(projectId, uri) { state -> _importState.value = state }
                _message.value = when (result) {
                    is AddMediaResult.Added -> "Media analyzed and added."
                    is AddMediaResult.Duplicate -> "This media is already part of the project. A second reference was added intentionally."
                }
            } catch (cancelled: CancellationException) {
                _importState.value = ImportState.Cancelled
                throw cancelled
            } catch (_: Throwable) {
                _importState.value = ImportState.Error
                _message.value = "VideoFlow could not read this media file. The file may be corrupted or use an unsupported format."
            } finally {
                load(projectId)
            }
        }
    }

    fun relink(projectId: String, assetId: String, uri: Uri) {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            _importState.value = ImportState.ReadingMetadata
            try {
                val validation = repository.relink(assetId, uri)
                _message.value = validation.reason
                _importState.value = if (validation.matches) ImportState.Ready else ImportState.Error
            } catch (cancelled: CancellationException) {
                _importState.value = ImportState.Cancelled
                throw cancelled
            } catch (_: Throwable) {
                _importState.value = ImportState.Error
                _message.value = "Unable to analyze the selected replacement."
            } finally {
                load(projectId)
            }
        }
    }

    fun clearMessage() {
        _message.value = null
        if (_importState.value in setOf(ImportState.Ready, ImportState.Error, ImportState.Cancelled)) {
            _importState.value = ImportState.Idle
        }
    }
}

@HiltViewModel
class DeviceViewModel @Inject constructor(
    repository: DeviceCapabilityRepository,
    diagnosticLog: LocalDiagnosticLog
) : ViewModel() {
    val profile: DeviceCapabilityProfile = repository.read()
    val diagnostics: List<DiagnosticEvent> = diagnosticLog.snapshot()
}
