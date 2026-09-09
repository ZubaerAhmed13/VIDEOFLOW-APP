package com.videoflow.app.ai.watermark

import android.content.Context
import com.videoflow.app.data.ai.AiWatermarkRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application-scope bridge from a durable AI edit to normal editor playback preparation.
 *
 * Save/enable/remove first commits the authoritative sidecar and invalidates stale preview media.
 * This coordinator then regenerates bounded editor-only AI segments in the background. It is not
 * tied to Watermark Studio's ViewModel, so closing the sheet cannot cancel the preparation that
 * the editor needs after Save. Final export remains independent and source-authoritative.
 */
object AiEditorPlaybackAutoPreparation {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val projectJobs = ConcurrentHashMap<String, Job>()

    fun request(context: Context, projectId: String) {
        require(projectId.isNotBlank())
        val application = context.applicationContext
        projectJobs.remove(projectId)?.cancel()

        lateinit var job: Job
        job = scope.launch {
            val entryPoint = EntryPointAccessors.fromApplication(
                application,
                Dependencies::class.java
            )
            val clipIds = entryPoint.aiWatermarkRepository()
                .load(projectId)
                .asSequence()
                .filter { it.enabled }
                .map { it.clipId }
                .distinct()
                .toList()

            // AiProcessedPreviewManager serializes renderer ownership internally. A failure on one
            // clip must not prevent another saved AI clip in the same project from being prepared.
            clipIds.forEach { clipId ->
                runCatching {
                    entryPoint.aiProcessedPreviewManager().prepareClip(projectId, clipId)
                }
            }
        }
        projectJobs[projectId] = job
        job.invokeOnCompletion { projectJobs.remove(projectId, job) }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun aiProcessedPreviewManager(): AiProcessedPreviewManager
        fun aiWatermarkRepository(): AiWatermarkRepository
    }
}
