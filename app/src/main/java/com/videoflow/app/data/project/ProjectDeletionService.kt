package com.videoflow.app.data.project

import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.data.db.VideoFlowDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns destructive project lifecycle cleanup that spans Room and the app-private Step-4 AI
 * sidecar. The sidecar is removed before the project row, and its exact state is restored if the
 * database deletion fails so a failed delete cannot silently destroy editable AI work.
 */
@Singleton
class ProjectDeletionService @Inject constructor(
    private val db: VideoFlowDatabase,
    private val aiWatermarkRepository: AiWatermarkRepository
) {
    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val beforeVisual = aiWatermarkRepository.visualEdits.load(projectId)
        val beforeAiState = aiWatermarkRepository.exportProjectStateJson(projectId)
        try {
            aiWatermarkRepository.deleteProjectState(projectId)
            aiWatermarkRepository.visualEdits.delete(projectId)
            db.projectDao().delete(projectId)
            runCatching { aiWatermarkRepository.cleanupDerivedMedia(projectId) }
        } catch (failure: Throwable) {
            runCatching { aiWatermarkRepository.visualEdits.replace(projectId, beforeVisual) }
            runCatching { aiWatermarkRepository.restoreProjectStateJson(projectId, beforeAiState) }
            throw failure
        }
    }
}
