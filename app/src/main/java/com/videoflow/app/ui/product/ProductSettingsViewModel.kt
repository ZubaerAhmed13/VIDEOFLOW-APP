package com.videoflow.app.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.data.project.ProjectRepository
import com.videoflow.app.data.proxy.ProxyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class ProductStorageState(
    val proxyBytes: Long = 0L,
    val proxyCount: Int = 0,
    val clearing: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class ProductSettingsViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val database: VideoFlowDatabase,
    private val proxyManager: ProxyManager
) : ViewModel() {
    val projectList = projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _storage = MutableStateFlow(ProductStorageState())
    val storage: StateFlow<ProductStorageState> = _storage.asStateFlow()

    init {
        viewModelScope.launch {
            projects.observeProjects().collectLatest { current ->
                refreshProxyUsage(current.map { it.id })
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshProxyUsage(projectList.value.map { it.id }) }
    }

    fun clearAllProxies() {
        if (_storage.value.clearing) return
        viewModelScope.launch {
            _storage.value = _storage.value.copy(clearing = true, message = null)
            val assetIds = projectList.value.flatMap { project -> project.mediaAssets.map { it.id } }.distinct()
            val failures = mutableListOf<String>()
            assetIds.forEach { assetId ->
                runCatching { proxyManager.delete(assetId) }
                    .onFailure { failures += assetId }
            }
            refreshProxyUsage(projectList.value.map { it.id })
            _storage.value = _storage.value.copy(
                clearing = false,
                message = if (failures.isEmpty()) "Editing proxies cleared. Original media and project edits were not changed."
                else "Some editing proxies could not be cleared. Original media and project edits were not changed."
            )
        }
    }

    fun clearMessage() {
        _storage.value = _storage.value.copy(message = null)
    }

    private suspend fun refreshProxyUsage(projectIds: List<String>) {
        val proxies = withContext(Dispatchers.IO) {
            projectIds.flatMap { database.proxyDao().getForProject(it) }
                .distinctBy { it.id }
        }
        _storage.value = _storage.value.copy(
            proxyBytes = proxies.sumOf { it.sizeBytes ?: 0L },
            proxyCount = proxies.size
        )
    }
}
