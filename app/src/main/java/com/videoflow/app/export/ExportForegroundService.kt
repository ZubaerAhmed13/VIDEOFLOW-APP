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

@AndroidEntryPoint
class ExportForegroundService : Service() {
    @Inject lateinit var coordinator: ExportCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeJobId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            updateNotification(ExportJobStatus.RENDERING, 0f, "Cancelling export…")
            serviceScope.launch { coordinator.cancel() }
            return START_NOT_STICKY
        }

        val jobId = intent?.getStringExtra(EXTRA_JOB_ID) ?: return START_NOT_STICKY
        activeJobId = jobId
        startAsForeground(jobId, ExportJobStatus.PREPARING, 0f, "Preparing native export…")
        serviceScope.launch {
            val finalStatus = coordinator.execute(jobId) { status, progress ->
                updateNotification(status, progress)
            }
            if (finalStatus == ExportJobStatus.COMPLETED) {
                updateNotification(finalStatus, 1f, "Export completed")
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground(jobId: String, status: ExportJobStatus, progress: Float, text: String) {
        val notification = buildNotification(jobId, status, progress, text)
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
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
