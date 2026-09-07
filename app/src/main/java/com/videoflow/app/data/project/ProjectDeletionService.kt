package com.videoflow.app.data.project

import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.data.db.VideoFlowDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import kotlinx.coroutines.NonCancellable

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
    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO + NonCancellable) {
        require(db.exportDao().activeJobs().none { it.projectId==projectId }) { "Cancel the active export before deleting this project." }
        val assets=db.projectDao().get(projectId)?.media.orEmpty().map { it.assetId }
        val proxies=db.proxyDao().getForProject(projectId).map { it.path }
        val beforeVisual = aiWatermarkRepository.visualEdits.load(projectId)
        val beforeAiState = aiWatermarkRepository.exportProjectStateJson(projectId)
        try {
            aiWatermarkRepository.deleteProjectState(projectId)
            aiWatermarkRepository.visualEdits.delete(projectId)
            db.withTransaction {
                require(db.exportDao().activeJobs().none { it.projectId==projectId }) { "Cancel the active export before deleting this project." }
                val owners=db.editorDao().getClips(projectId).map { it.id }+
                    db.editorDao().getTextOverlays(projectId).map { it.id }+db.editorDao().getImageOverlays(projectId).map { it.id }
                owners.forEach { db.editorDao().deleteKeyframes(it) }
                db.projectDao().delete(projectId)
            }
            runCatching { aiWatermarkRepository.cleanupDerivedMedia(projectId,assets,proxies) }
        } catch (failure: Throwable) {
            runCatching { aiWatermarkRepository.visualEdits.replace(projectId, beforeVisual) }
            runCatching { aiWatermarkRepository.restoreProjectStateJson(projectId, beforeAiState) }
            throw failure
        }
    }
}
