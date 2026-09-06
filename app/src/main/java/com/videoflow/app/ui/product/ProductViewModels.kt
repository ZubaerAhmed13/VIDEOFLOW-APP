package com.videoflow.app.ui.product

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.data.media.MediaAnalyzer
import com.videoflow.app.data.project.AddMediaResult
import com.videoflow.app.data.project.ProjectRepository
import com.videoflow.app.domain.editor.FrameRate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.videoFlowPreferences by preferencesDataStore(name = "videoflow_product_preferences")

enum class AppAppearance { SYSTEM, LIGHT, DARK }

data class ProductPreferencesState(
    val onboardingComplete: Boolean = false,
    val appearance: AppAppearance = AppAppearance.SYSTEM
)

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.videoFlowPreferences

    val state = store.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { preferences ->
            ProductPreferencesState(
                onboardingComplete = preferences[ONBOARDING_COMPLETE] ?: false,
                appearance = preferences[APPEARANCE]
                    ?.let { runCatching { AppAppearance.valueOf(it) }.getOrNull() }
                    ?: AppAppearance.SYSTEM
            )
        }

    suspend fun setOnboardingComplete(value: Boolean) {
        store.edit { it[ONBOARDING_COMPLETE] = value }
    }

    suspend fun setAppearance(value: AppAppearance) {
        store.edit { it[APPEARANCE] = value.name }
    }

    private companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val APPEARANCE = stringPreferencesKey("appearance")
    }
}

@HiltViewModel
class PreferencesViewModel @Inject constructor(private val preferences: AppPreferences) : ViewModel() {
    val state = preferences.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductPreferencesState())

    fun completeOnboarding() {
        viewModelScope.launch { preferences.setOnboardingComplete(true) }
    }

    fun setAppearance(value: AppAppearance) {
        viewModelScope.launch { preferences.setAppearance(value) }
    }
}

enum class ProjectAspectPreset(val label: String, val supporting: String, val width: Int, val height: Int) {
    LANDSCAPE("16:9", "Landscape", 1920, 1080),
    PORTRAIT("9:16", "Portrait", 1080, 1920),
    SQUARE("1:1", "Square", 1080, 1080),
    SOCIAL("4:5", "Portrait / Social", 1080, 1350)
}

data class ProjectCreationResult(val projectId: String, val importedMedia: Boolean)

data class MergeCandidate(
    val selectionId: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val displayName: String,
    val durationUs: Long?,
    val width: Int?,
    val height: Int?,
    val frameRate: Double?,
    val videoCodecMime: String?
)

@HiltViewModel
class ProductHomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val editorRepository: EditorRepository,
    private val database: VideoFlowDatabase,
    private val mediaAnalyzer: MediaAnalyzer
) : ViewModel() {
    val projects = projectRepository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _mergeCandidates = MutableStateFlow<List<MergeCandidate>>(emptyList())
    val mergeCandidates: StateFlow<List<MergeCandidate>> = _mergeCandidates.asStateFlow()
    private val _mergeBusy = MutableStateFlow(false)
    val mergeBusy: StateFlow<Boolean> = _mergeBusy.asStateFlow()

    fun createProject(
        name: String,
        preset: ProjectAspectPreset,
        onDone: (ProjectCreationResult) -> Unit,
        onError: (String) -> Unit
    ) {
        val safeName = validatedName(name) ?: return onError("Enter a project name.")
        viewModelScope.launch {
            runCatching {
                val id = projectRepository.createProject(safeName)
                editorRepository.ensureProjectInitialized(id)
                setCanvas(id, preset.width, preset.height, FrameRate.FPS_30)
                ProjectCreationResult(id, importedMedia = false)
            }.onSuccess(onDone).onFailure { onError("VideoFlow could not create the project.") }
        }
    }

    fun createFromMedia(
        name: String,
        uri: Uri,
        fallbackPreset: ProjectAspectPreset,
        onDone: (ProjectCreationResult) -> Unit,
        onError: (String) -> Unit
    ) {
        val safeName = validatedName(name) ?: return onError("Enter a project name.")
        viewModelScope.launch {
            var createdId: String? = null
            runCatching {
                val id = projectRepository.createProject(safeName)
                createdId = id
                editorRepository.ensureProjectInitialized(id)
                when (val result = projectRepository.addMedia(id, uri)) {
                    is AddMediaResult.Added -> {
                        val asset = result.asset
                        val sourceCanvas = sourceAwareCanvas(asset.width, asset.height)
                        val frameRate = sourceFrameRate(asset.frameRate)
                        setCanvas(
                            id,
                            sourceCanvas?.first ?: fallbackPreset.width,
                            sourceCanvas?.second ?: fallbackPreset.height,
                            frameRate
                        )
                        if (asset.mimeType?.startsWith("video/") == true || asset.mimeType?.startsWith("audio/") == true) {
                            editorRepository.addClip(id, asset.id, 0L)
                        }
                    }
                    is AddMediaResult.DuplicateCandidate -> error("Unexpected duplicate in a new project")
                }
                ProjectCreationResult(id, importedMedia = true)
            }.onSuccess(onDone).onFailure {
                createdId?.let { id -> runCatching { projectRepository.deleteProject(id) } }
                onError("VideoFlow could not create a project from this media. The original file was not changed.")
            }
        }
    }

    fun prepareMergeSelection(uris: List<Uri>, append: Boolean, onError: (String) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _mergeBusy.value = true
            try {
                val analyzed = mutableListOf<MergeCandidate>()
                for (uri in uris) {
                    val info = runCatching { mediaAnalyzer.analyze(uri) }.getOrElse {
                        onError("VideoFlow could not read one of the selected videos.")
                        continue
                    }
                    val metadata = info.metadata
                    if (metadata.videoCodecMime == null || metadata.durationUs == null || metadata.durationUs <= 0L) {
                        onError("${info.displayName} is not a supported video for Merge Videos.")
                        continue
                    }
                    analyzed += MergeCandidate(
                        uri = uri,
                        displayName = info.displayName,
                        durationUs = metadata.durationUs,
                        width = metadata.width,
                        height = metadata.height,
                        frameRate = metadata.frameRate,
                        videoCodecMime = metadata.videoCodecMime
                    )
                }
                _mergeCandidates.value = if (append) _mergeCandidates.value + analyzed else analyzed
            } finally {
                _mergeBusy.value = false
            }
        }
    }

    fun moveMergeCandidate(index: Int, delta: Int) {
        val list = _mergeCandidates.value.toMutableList()
        val target = index + delta
        if (index !in list.indices || target !in list.indices) return
        val item = list.removeAt(index)
        list.add(target, item)
        _mergeCandidates.value = list
    }

    fun removeMergeCandidate(index: Int) {
        val list = _mergeCandidates.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        _mergeCandidates.value = list
    }

    fun clearMergeSelection() {
        _mergeCandidates.value = emptyList()
    }

    fun createMergeProject(
        name: String,
        onDone: (ProjectCreationResult) -> Unit,
        onError: (String) -> Unit
    ) {
        val safeName = validatedName(name) ?: return onError("Enter a merge project name.")
        val ordered = _mergeCandidates.value
        if (ordered.size < 2) return onError("Select at least two videos to merge.")
        viewModelScope.launch {
            if (_mergeBusy.value) return@launch
            _mergeBusy.value = true
            var createdId: String? = null
            try {
                val id = projectRepository.createProject(safeName)
                createdId = id
                editorRepository.ensureProjectInitialized(id)

                // Explicit project authority: the first ordered video seeds project canvas/FPS.
                // This is a project setting only; Smart Copy still verifies every encoded source.
                val first = ordered.first()
                val canvas = sourceAwareCanvas(first.width, first.height) ?: (1920 to 1080)
                setCanvas(id, canvas.first, canvas.second, sourceFrameRate(first.frameRate))

                var timelineCursorUs = 0L
                for (candidate in ordered) {
                    val asset = when (val imported = projectRepository.addMedia(id, candidate.uri)) {
                        is AddMediaResult.Added -> imported.asset
                        is AddMediaResult.DuplicateCandidate -> projectRepository.confirmAddDuplicate(imported.candidate)
                    }
                    require(asset.mimeType?.startsWith("video/") == true || asset.videoCodecMime != null) {
                        "Merge selection is not a video"
                    }
                    val clip = editorRepository.addClip(id, asset.id, timelineCursorUs)
                    timelineCursorUs = clip.timelineEndUs
                }
                _mergeCandidates.value = emptyList()
                onDone(ProjectCreationResult(id, importedMedia = true))
            } catch (_: Throwable) {
                createdId?.let { id -> runCatching { projectRepository.deleteProject(id) } }
                onError("VideoFlow could not build this merge project. The original videos were not changed.")
            } finally {
                _mergeBusy.value = false
            }
        }
    }

    fun renameProject(id: String, name: String, onError: (String) -> Unit = {}) {
        val safeName = validatedName(name) ?: return onError("Project name cannot be blank.")
        viewModelScope.launch {
            runCatching { projectRepository.renameProject(id, safeName) }
                .onFailure { onError("VideoFlow could not rename the project.") }
        }
    }

    fun deleteProject(id: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { projectRepository.deleteProject(id) }
                .onFailure { onError("VideoFlow could not delete the project.") }
        }
    }

    private suspend fun setCanvas(projectId: String, width: Int, height: Int, frameRate: FrameRate) {
        withContext(Dispatchers.IO) {
            val current = requireNotNull(database.editorDao().getProjectSettings(projectId))
            database.editorDao().putProjectSettings(
                current.copy(
                    width = width.evenDimension(),
                    height = height.evenDimension(),
                    frameRateNumerator = frameRate.numerator,
                    frameRateDenominator = frameRate.denominator,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun validatedName(value: String): String? = value.trim().takeIf { it.isNotBlank() }?.take(80)
}

private fun sourceAwareCanvas(width: Int?, height: Int?): Pair<Int, Int>? {
    if (width == null || height == null || width <= 0 || height <= 0) return null
    val longest = max(width, height)
    val scale = if (longest > 1920) 1920.0 / longest.toDouble() else 1.0
    val scaledWidth = (width * scale).toInt().coerceAtLeast(2).evenDimension()
    val scaledHeight = (height * scale).toInt().coerceAtLeast(2).evenDimension()
    return scaledWidth to scaledHeight
}

private fun sourceFrameRate(value: Double?): FrameRate {
    if (value == null || !value.isFinite() || value <= 0.0) return FrameRate.FPS_30
    val known = listOf(
        23.976 to FrameRate(24_000, 1_001),
        24.0 to FrameRate.FPS_24,
        25.0 to FrameRate.FPS_25,
        29.97 to FrameRate.FPS_2997,
        30.0 to FrameRate.FPS_30,
        50.0 to FrameRate(50, 1),
        59.94 to FrameRate.FPS_5994,
        60.0 to FrameRate.FPS_60
    )
    return known.minByOrNull { abs(it.first - value) }
        ?.takeIf { abs(it.first - value) < 0.08 }
        ?.second
        ?: FrameRate.FPS_30
}

private fun Int.evenDimension(): Int = when {
    this < 2 -> 2
    this % 2 == 0 -> this
    else -> this - 1
}
