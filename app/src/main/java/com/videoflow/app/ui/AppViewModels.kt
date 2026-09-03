package com.videoflow.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoflow.app.data.diagnostics.DiagnosticEvent
import com.videoflow.app.data.diagnostics.LocalDiagnosticLog
import com.videoflow.app.data.device.DeviceCapabilityProfile
import com.videoflow.app.data.device.DeviceCapabilityRepository
import com.videoflow.app.data.project.AddMediaResult
import com.videoflow.app.data.project.IdentityMatch
import com.videoflow.app.data.project.PreparedMediaImport
import com.videoflow.app.data.project.ProjectRepository
import com.videoflow.app.data.project.RelinkValidation
import com.videoflow.app.domain.model.ImportState
import com.videoflow.app.domain.model.VideoFlowProject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying = _isVerifying.asStateFlow()

    private val _pendingDuplicate = MutableStateFlow<PreparedMediaImport?>(null)
    val pendingDuplicate = _pendingDuplicate.asStateFlow()

    private val _pendingWeakRelink = MutableStateFlow<RelinkValidation?>(null)
    val pendingWeakRelink = _pendingWeakRelink.asStateFlow()

    private var importJob: Job? = null
    private var loadJob: Job? = null
    private val verificationSemaphore = Semaphore(2)

    fun load(id: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _project.value = repository.getProject(id)
            val assets = _project.value?.mediaAssets.orEmpty()
            if (assets.isNotEmpty()) {
                _isVerifying.value = true
                try {
                    coroutineScope {
                        assets.map { asset ->
                            async { verificationSemaphore.withPermit { repository.verifySource(asset) } }
                        }.awaitAll()
                    }
                } finally {
                    _isVerifying.value = false
                }
                _project.value = repository.getProject(id)
            }
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
                when (val result = repository.addMedia(projectId, uri) { state -> _importState.value = state }) {
                    is AddMediaResult.Added -> {
                        _message.value = "Media analyzed and added."
                        load(projectId)
                    }
                    is AddMediaResult.DuplicateCandidate -> {
                        _pendingDuplicate.value = result.candidate
                        _importState.value = ImportState.Ready
                    }
                }
            } catch (cancelled: CancellationException) {
                _importState.value = ImportState.Cancelled
                throw cancelled
            } catch (_: Throwable) {
                _importState.value = ImportState.Error
                _message.value = "VideoFlow could not read this media file. The file may be corrupted or use an unsupported format."
            }
        }
    }

    fun confirmDuplicate(projectId: String) {
        val candidate = _pendingDuplicate.value ?: return
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            try {
                repository.confirmAddDuplicate(candidate)
                _pendingDuplicate.value = null
                _importState.value = ImportState.Ready
                _message.value = "Duplicate media reference added."
                load(projectId)
            } catch (_: Throwable) {
                _importState.value = ImportState.Error
                _message.value = "VideoFlow could not add the duplicate media reference."
            }
        }
    }

    fun cancelDuplicate() {
        _pendingDuplicate.value = null
        _importState.value = ImportState.Idle
    }

    fun relink(projectId: String, assetId: String, uri: Uri) {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            _importState.value = ImportState.ReadingMetadata
            try {
                val validation = repository.relink(assetId, uri)
                when (validation.match) {
                    IdentityMatch.STRONG_MATCH -> {
                        _message.value = validation.reason
                        _importState.value = ImportState.Ready
                        load(projectId)
                    }
                    IdentityMatch.WEAK_MATCH -> {
                        _pendingWeakRelink.value = validation
                        _importState.value = ImportState.Ready
                    }
                    IdentityMatch.MISMATCH,
                    IdentityMatch.UNVERIFIABLE -> {
                        _message.value = validation.reason
                        _importState.value = ImportState.Error
                    }
                }
            } catch (cancelled: CancellationException) {
                _importState.value = ImportState.Cancelled
                throw cancelled
            } catch (_: Throwable) {
                _importState.value = ImportState.Error
                _message.value = "Unable to analyze the selected replacement."
            }
        }
    }

    fun confirmWeakRelink(projectId: String) {
        val validation = _pendingWeakRelink.value ?: return
        val prepared = validation.prepared ?: return
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            try {
                val confirmed = repository.confirmWeakRelink(prepared)
                _pendingWeakRelink.value = null
                _message.value = confirmed.reason
                _importState.value = if (confirmed.match == IdentityMatch.WEAK_MATCH) ImportState.Ready else ImportState.Error
                load(projectId)
            } catch (_: Throwable) {
                _importState.value = ImportState.Error
                _message.value = "Unable to complete the weakly verified relink."
            }
        }
    }

    fun cancelWeakRelink() {
        _pendingWeakRelink.value = null
        _importState.value = ImportState.Idle
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
