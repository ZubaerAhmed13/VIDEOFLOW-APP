package com.videoflow.app.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.videoflow.app.MainActivity
import com.videoflow.app.domain.export.ExportJobStatus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import com.videoflow.app.data.export.ExportRepository
import com.videoflow.app.domain.export.ExportFailureCode

@AndroidEntryPoint
class ExportForegroundService : Service() {
    @Inject lateinit var coordinator: ExportCoordinator
    @Inject lateinit var repository: ExportRepository
    @Inject lateinit var diagnostics: ExportDiagnosticsRecorder

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeJobId: String? = null
    private val requests = Channel<String>(16)
    private val pending = linkedSetOf<String>()
    private var worker: Job? = null
    private var latestStartId = 0

    override fun onCreate() {
        super.onCreate()
        check(ExportProcessIdentity.isExportProcess(this)) { "ExportForegroundService must run in :export" }
        createNotificationChannel()
        worker = serviceScope.launch {
            try {
                for (jobId in requests) {
                    activeJobId = jobId
                    val status = try {
                        coordinator.execute(jobId) { state, progress -> updateNotification(state, progress) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        // Never pretend try/catch can make fatal VM/linkage damage safe. Because this
                        // service runs in :export, rethrowing these errors sacrifices only the heavy
                        // worker process; the editor/main process is protected and its watchdog will
                        // mark the persisted job INTERRUPTED.
                        if (ExportFailureClassifier.shouldRethrow(error)) throw error
                        containEscapedFailure(jobId, error)
                        ExportJobStatus.FAILED
                    }
                    if (status == ExportJobStatus.COMPLETED) updateNotification(status, 1f, "Export completed")
                    pending.remove(jobId)
                    activeJobId = null
                    if (pending.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelfResult(latestStartId)
                    }
                }
            } finally {
                // Queue ownership remains in this service; interrupted queued work must not look active.
                val interrupted = pending.toList()
                withContext(NonCancellable + Dispatchers.IO) {
                    interrupted.forEach { id ->
                        val job = repository.getJob(id)
                        if (job?.status in setOf(
                                ExportJobStatus.QUEUED,
                                ExportJobStatus.PREPARING,
                                ExportJobStatus.RENDERING,
                                ExportJobStatus.FINALIZING,
                                ExportJobStatus.VALIDATING
                            )
                        ) {
                            repository.updateJob(
                                id,
                                ExportJobStatus.INTERRUPTED,
                                job.progress,
                                ExportFailureCode.UNKNOWN,
                                "Android stopped the isolated export process. The project is safe; retry export to recover validated checkpoints where supported."
                            )
                            diagnostics.record(job, job.status.name, "export-service-interrupted")
                        }
                    }
                }
            }
        }
    }

    private suspend fun containEscapedFailure(jobId: String, error: Throwable) {
        withContext(NonCancellable + Dispatchers.IO) {
            val job = repository.getJob(jobId)
            val classified = ExportFailureClassifier.classify(error)
            if (job != null) {
                ExportPartialOutputCleanup.invalidate(this@ExportForegroundService, job.destinationUri)
                runCatching {
                    repository.updateJob(
                        jobId,
                        ExportJobStatus.FAILED,
                        job.progress,
                        classified.code,
                        classified.userMessage
                    )
                }
                diagnostics.record(job, job.status.name, "escaped-export-failure", error)
            }
        }
        updateNotification(ExportJobStatus.FAILED, 0f, "Export failed")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val jobId = intent?.getStringExtra(EXTRA_JOB_ID)
        if (intent?.action == ACTION_CANCEL) {
            if (jobId != null) serviceScope.launch {
                if (jobId == activeJobId) {
                    updateNotification(ExportJobStatus.RENDERING, 0f, "Cancelling export…")
                    coordinator.cancel(jobId)
                } else if (jobId in pending) {
                    repository.updateJob(jobId, ExportJobStatus.CANCELLED, 0f, ExportFailureCode.CANCELLED, "Queued export cancelled.")
                    pending.remove(jobId)
                }
            }
            return START_NOT_STICKY
        }
        if (jobId == null) {
            if (pending.isEmpty()) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (jobId in pending) return START_NOT_STICKY
        if (activeJobId == null) {
            try {
                startAsForeground(jobId, ExportJobStatus.PREPARING, 0f, "Preparing native export…")
            } catch (error: RuntimeException) {
                serviceScope.launch {
                    try {
                        repository.updateJob(
                            jobId,
                            ExportJobStatus.FAILED,
                            0f,
                            ExportFailureCode.UNKNOWN,
                            "Android could not start background export. Keep VideoFlow open and try again after other processing finishes."
                        )
                        diagnostics.record(repository.getJob(jobId), "FOREGROUND_START", "foreground-start-failed", error)
                    } finally {
                        stopSelf(startId)
                    }
                }
                return START_NOT_STICKY
            }
        }
        pending.add(jobId)
        if (requests.trySend(jobId).isFailure) {
            pending.remove(jobId)
            serviceScope.launch {
                repository.updateJob(
                    jobId,
                    ExportJobStatus.FAILED,
                    0f,
                    ExportFailureCode.UNKNOWN,
                    "The export queue is full. Wait for an active export to finish, then try again."
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 allows only a few seconds to stop. Coroutine cleanup retains validated work.
        worker?.cancel(CancellationException("Android background media-processing time allowance ended"))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        requests.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground(jobId: String, status: ExportJobStatus, progress: Float, text: String) {
        val notification = buildNotification(jobId, status, progress, text)
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(
        status: ExportJobStatus,
        progress: Float,
        explicitText: String? = null
    ) {
        val jobId = activeJobId ?: return
        val text = explicitText ?: when (status) {
            ExportJobStatus.QUEUED -> "Queued"
            ExportJobStatus.PREPARING -> "Checking originals and encoder…"
            ExportJobStatus.RENDERING -> "Rendering from original media…"
            ExportJobStatus.FINALIZING -> "Writing final MP4…"
            ExportJobStatus.VALIDATING -> "Validating output…"
            ExportJobStatus.COMPLETED -> "Export completed"
            ExportJobStatus.CANCELLED -> "Export cancelled"
            ExportJobStatus.FAILED -> "Export failed"
            ExportJobStatus.INTERRUPTED -> "Export interrupted"
        }
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(jobId, status, progress, text))
    }

    private fun buildNotification(
        jobId: String,
        status: ExportJobStatus,
        progress: Float,
        text: String
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = PendingIntent.getService(
            this,
            jobId.hashCode(),
            Intent(this, ExportForegroundService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_JOB_ID, jobId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val terminal = status in setOf(ExportJobStatus.COMPLETED, ExportJobStatus.CANCELLED, ExportJobStatus.FAILED, ExportJobStatus.INTERRUPTED)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("VideoFlow export")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(!terminal)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (!terminal) {
            val value = (progress.coerceIn(0f, 1f) * 100).toInt()
            builder.setProgress(100, value, status == ExportJobStatus.PREPARING && value <= 1)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Video exports", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress and cancellation for native VideoFlow exports"
                }
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "videoflow_exports"
        private const val NOTIFICATION_ID = 3003
        private const val EXTRA_JOB_ID = "export_job_id"
        private const val ACTION_CANCEL = "com.videoflow.app.action.CANCEL_EXPORT"

        fun start(context: Context, jobId: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ExportForegroundService::class.java).putExtra(EXTRA_JOB_ID, jobId)
            )
        }
    }
}
