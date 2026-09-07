package com.videoflow.app.export

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.data.export.ExportRepository
import com.videoflow.app.domain.export.ExportFailureCode
import com.videoflow.app.domain.export.ExportJob
import com.videoflow.app.domain.export.ExportJobStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Conservative last-boundary mapping. Lower layers keep their more precise domain mappings. */
object ExportFailureClassifier {
    data class Classified(val code: ExportFailureCode, val userMessage: String)

    fun classify(error: Throwable): Classified {
        val name = error::class.java.name.lowercase()
        val message = error.message.orEmpty().lowercase()
        return when {
            error is SecurityException || "permission" in message -> Classified(
                ExportFailureCode.PERMISSION_LOST,
                "VideoFlow no longer has permission to read the source or write the selected destination."
            )
            error is OutOfMemoryError -> Classified(
                ExportFailureCode.UNKNOWN,
                "Export ran out of available memory. The project is safe; close other heavy apps and retry."
            )
            "encoder" in message || "mediacodec" in name && "encode" in message -> Classified(
                ExportFailureCode.ENCODER_INIT_FAILED,
                "Export couldn't start or continue the selected video encoder on this device."
            )
            "decoder" in message || "extractor" in name -> Classified(
                ExportFailureCode.DECODER_FAILED,
                "Export couldn't decode part of the selected source media."
            )
            "mux" in message -> Classified(
                ExportFailureCode.MUXER_FAILED,
                "Export couldn't finalize the output media container."
            )
            "space" in message || "enospc" in message || "no space" in message -> Classified(
                ExportFailureCode.STORAGE_FULL,
                "There is not enough writable storage to finish this export."
            )
            "onnx" in name || "inference" in message || "lama" in message -> Classified(
                ExportFailureCode.UNKNOWN,
                "Local AI processing failed during export. The project and original video are unchanged."
            )
            "egl" in name || "opengl" in name || "gl" in message -> Classified(
                ExportFailureCode.UNKNOWN,
                "The device graphics pipeline failed during export. The editor remains safe and you can retry."
            )
            error is java.io.IOException -> Classified(
                ExportFailureCode.DESTINATION_IO,
                "VideoFlow couldn't read or write media required for this export."
            )
            else -> Classified(
                ExportFailureCode.UNKNOWN,
                "Export stopped because a rendering component failed. The project is safe and can be retried."
            )
        }
    }

    /** Fatal VM/linkage failures are deliberately not swallowed in :export. */
    fun shouldRethrow(error: Throwable): Boolean =
        error is VirtualMachineError && error !is OutOfMemoryError ||
            error is ThreadDeath ||
            error is LinkageError
}

/** Local/private bounded diagnostics. No media payload, frames, or arbitrary source paths are stored. */
@Singleton
class ExportDiagnosticsRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val file: File get() = File(context.filesDir, "diagnostics/export-events.jsonl")

    suspend fun record(
        job: ExportJob?,
        stage: String,
        event: String,
        error: Throwable? = null,
        exitReason: String? = null
    ) = withContext(Dispatchers.IO) {
        runCatching {
            file.parentFile?.mkdirs()
            val row = JSONObject()
                .put("timestamp", System.currentTimeMillis())
                .put("jobId", job?.id ?: JSONObject.NULL)
                .put("projectId", job?.projectId ?: JSONObject.NULL)
                .put("stage", stage.take(80))
                .put("event", event.take(120))
                .put("status", job?.status?.name ?: JSONObject.NULL)
                .put("progress", job?.progress ?: JSONObject.NULL)
                .put("aiActive", event.contains("AI", ignoreCase = true))
                .put("api", Build.VERSION.SDK_INT)
                .put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
                .put("memoryClassMb", context.getSystemService(ActivityManager::class.java)?.memoryClass ?: -1)
                .put("exceptionClass", error?.javaClass?.name ?: JSONObject.NULL)
                .put("exceptionMessage", sanitize(error?.message))
                .put("exitReason", exitReason ?: JSONObject.NULL)
                .toString()
            val existing = if (file.isFile) file.readLines().takeLast(MAX_ROWS - 1) else emptyList()
            file.writeText((existing + row).joinToString("\n", postfix = "\n"))
        }
    }

    private fun sanitize(value: String?): Any {
        if (value.isNullOrBlank()) return JSONObject.NULL
        // Keep only a bounded diagnostic sentence; redact common absolute/content URI shapes.
        return value
            .replace(Regex("content://\\S+"), "[content-uri]")
            .replace(Regex("/(?:[^\\s/]+/)+[^\\s]+"), "[path]")
            .take(320)
    }

    companion object { private const val MAX_ROWS = 64 }
}

/** Truncates an unusable destination after an escaped failure; never mutates the original source. */
object ExportPartialOutputCleanup {
    fun invalidate(context: Context, destinationUri: String) {
        if (destinationUri.isBlank()) return
        runCatching {
            context.contentResolver.openFileDescriptor(android.net.Uri.parse(destinationUri), "rwt")?.use { }
        }
    }
}

/**
 * Main-process watchdog for a dead :export process. This is intentionally persistence-based: it
 * never assumes export-process singletons are shared with the editor.
 */
@Singleton
class ExportProcessRecoveryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: VideoFlowDatabase,
    private val repository: ExportRepository,
    private val diagnostics: ExportDiagnosticsRecorder
) {
    suspend fun reconcile() = withContext(Dispatchers.IO) {
        if (!ExportProcessIdentity.isMainProcess(context)) return@withContext
        val active = db.exportDao().activeJobs().filter {
            it.status in setOf("PREPARING", "RENDERING", "FINALIZING", "VALIDATING")
        }
        if (active.isEmpty() || exportProcessRunning()) return@withContext
        val exitReason = latestExportExitReason()
        active.forEach { row ->
            val job = repository.getJob(row.id) ?: return@forEach
            ExportPartialOutputCleanup.invalidate(context, job.destinationUri)
            repository.updateJob(
                job.id,
                ExportJobStatus.INTERRUPTED,
                job.progress,
                ExportFailureCode.UNKNOWN,
                "Export process was interrupted${exitReason?.let { " ($it)" } ?: ""}. The project is safe; retry the export."
            )
            diagnostics.record(job, job.status.name, "export-process-interrupted", exitReason = exitReason)
        }
    }

    private fun exportProcessRunning(): Boolean {
        val expected = "${context.packageName}:export"
        return context.getSystemService(ActivityManager::class.java)
            ?.runningAppProcesses
            ?.any { it.processName == expected } == true
    }

    private fun latestExportExitReason(): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        val expected = "${context.packageName}:export"
        val exit = context.getSystemService(ActivityManager::class.java)
            ?.getHistoricalProcessExitReasons(null, 0, 8)
            ?.firstOrNull { it.processName == expected }
            ?: return null
        return when (exit.reason) {
            android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
            android.app.ApplicationExitInfo.REASON_CRASH -> "process crash"
            android.app.ApplicationExitInfo.REASON_ANR -> "ANR"
            android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
            android.app.ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency died"
            android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "user/system requested stop"
            else -> "system interruption"
        }
    }
}
