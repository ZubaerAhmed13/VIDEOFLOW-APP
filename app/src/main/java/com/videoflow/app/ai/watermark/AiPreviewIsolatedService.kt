package com.videoflow.app.ai.watermark

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.videoflow.app.BuildConfig
import com.videoflow.app.domain.ai.AiModelRole
import com.videoflow.app.domain.ai.AiWatermarkEffect
import com.videoflow.app.domain.ai.NormalizedRoi
import com.videoflow.app.domain.ai.RoiMotionAnchor
import com.videoflow.app.export.ExportProcessIdentity
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns all editor-time LaMa/Media3 preview reconstruction in the dedicated :ai_preview process.
 *
 * No Bitmap is sent through Binder. Still previews are written atomically to the shared app cache;
 * moving previews already use bounded cache files. If ORT/GPU/codec work terminates this process,
 * Binder death is reported to [AiPreviewProcessClient] while the editor process stays alive.
 */
@AndroidEntryPoint
class AiPreviewIsolatedService : Service() {
    @Inject lateinit var previewEngine: LocalWatermarkPreviewEngine
    @Inject lateinit var processedPreviewManager: AiProcessedPreviewManager
    @Inject lateinit var modelPackManager: AiModelPackManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = mutableMapOf<String, Job>()
    private val messenger by lazy {
        Messenger(Handler(Looper.getMainLooper()) { message ->
            handleMessage(message)
            true
        })
    }

    override fun onCreate() {
        super.onCreate()
        check(ExportProcessIdentity.currentProcessName(this) == "$packageName:ai_preview") {
            "AiPreviewIsolatedService must run in :ai_preview"
        }
        pruneStaleStillFiles()
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        synchronized(jobs) { jobs.values.toList() }.forEach { it.cancel() }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleMessage(message: Message) {
        val reply = message.replyTo ?: return
        val data = message.data.apply { classLoader = javaClass.classLoader }
        val requestId = data.getString(KEY_REQUEST_ID) ?: return
        when (message.what) {
            CMD_CANCEL -> {
                synchronized(jobs) { jobs.remove(requestId) }?.cancel()
                serviceScope.launch { runCatching { processedPreviewManager.cancel() } }
            }
            CMD_PING -> sendSuccess(reply, requestId, Bundle().apply {
                putString(KEY_PROCESS_NAME, ExportProcessIdentity.currentProcessName(this@AiPreviewIsolatedService))
            })
            CMD_RUNTIME -> launchRequest(requestId, reply) {
                modelPackManager.ensurePackInstalled()
                val status = modelPackManager.status()
                sendSuccess(reply, requestId, Bundle().apply {
                    putBoolean(KEY_RUNTIME_COMPLETE, status.complete)
                    putBoolean(KEY_NNAPI, status.nnapiAvailable)
                    putString(KEY_DETAIL, status.detail)
                })
            }
            CMD_STILL -> launchRequest(requestId, reply) {
                val roi = roiFrom(data)
                val role = AiModelRole.valueOf(requireString(data, KEY_MODEL_ROLE))
                val result = previewEngine.render(
                    sourceUri = requireString(data, KEY_SOURCE_URI),
                    sourceTimeUs = data.getLong(KEY_SOURCE_TIME_US),
                    roi = roi,
                    sourceWidth = data.getInt(KEY_SOURCE_WIDTH),
                    sourceHeight = data.getInt(KEY_SOURCE_HEIGHT),
                    featherPx = data.getInt(KEY_FEATHER_PX),
                    modelRole = role
                )
                val path = writeStillResult(requestId, result.bitmap)
                result.bitmap.recycle()
                sendSuccess(reply, requestId, Bundle().apply {
                    putString(KEY_PATH, path)
                    putString(KEY_PROVIDER, result.provider)
                })
            }
            CMD_MOVING -> launchRequest(requestId, reply) {
                val effect = effectFrom(data)
                val ready = processedPreviewManager.prepareDraftWindow(
                    projectId = effect.projectId,
                    clipId = effect.clipId,
                    draftEffect = effect,
                    centerLocalUs = data.getLong(KEY_CENTER_LOCAL_US),
                    length = AiMovingPreviewLength.valueOf(requireString(data, KEY_LENGTH))
                ) { progress -> sendProgress(reply, requestId, progress) }
                sendSuccess(reply, requestId, Bundle().apply {
                    putString(KEY_PATH, ready.path)
                    putLong(KEY_RANGE_START_US, ready.clipLocalStartUs)
                    putLong(KEY_RANGE_END_US, ready.clipLocalEndUs)
                    putString(KEY_PROVIDER, processedPreviewManager.state.value.provider)
                })
            }
            CMD_CRASH_FOR_TEST -> {
                if (!BuildConfig.DEBUG) {
                    sendError(reply, requestId, "Crash certification command is disabled in non-debug builds.")
                } else {
                    // Deliberately terminate only :ai_preview. Instrumentation verifies main PID and
                    // Activity survive and that a later bind starts a fresh worker process.
                    Process.killProcess(Process.myPid())
                }
            }
            else -> sendError(reply, requestId, "Unknown isolated AI preview command ${message.what}.")
        }
    }

    private fun launchRequest(requestId: String, reply: Messenger, block: suspend () -> Unit) {
        synchronized(jobs) { jobs.remove(requestId) }?.cancel()
        val job = serviceScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                sendError(reply, requestId, "AI preview cancelled.")
            } catch (fatal: VirtualMachineError) {
                // OOM/VM corruption must kill only this worker process; do not pretend catch/retry
                // makes the runtime safe. The editor receives Binder death and remains usable.
                throw fatal
            } catch (fatal: LinkageError) {
                throw fatal
            } catch (error: Throwable) {
                sendError(reply, requestId, error.message ?: error::class.java.simpleName)
            } finally {
                synchronized(jobs) { jobs.remove(requestId) }
            }
        }
        synchronized(jobs) { jobs[requestId] = job }
    }

    private fun writeStillResult(requestId: String, bitmap: Bitmap): String {
        val root = File(cacheDir, "ai-preview/ipc/still").apply { mkdirs() }
        val target = File(root, "$requestId.png")
        val partial = File(root, ".$requestId.partial")
        partial.delete()
        try {
            FileOutputStream(partial).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not encode isolated AI still preview."
                }
                output.fd.sync()
            }
            if (target.exists() && !target.delete()) error("Could not replace stale AI still preview.")
            if (!partial.renameTo(target)) error("Could not finalize isolated AI still preview.")
            return target.absolutePath
        } finally {
            partial.delete()
        }
    }

    private fun pruneStaleStillFiles() {
        val root = File(cacheDir, "ai-preview/ipc/still")
        val cutoff = System.currentTimeMillis() - 6L * 60L * 60L * 1000L
        root.listFiles().orEmpty().filter { it.lastModified() < cutoff }.forEach { runCatching { it.delete() } }
    }

    private fun effectFrom(data: Bundle): AiWatermarkEffect {
        val anchors = data.getParcelableArrayList<Bundle>(KEY_ANCHORS).orEmpty().map { anchor ->
            RoiMotionAnchor(
                clipLocalTimeUs = anchor.getLong(KEY_ANCHOR_TIME_US),
                centerX = anchor.getFloat(KEY_ANCHOR_X),
                centerY = anchor.getFloat(KEY_ANCHOR_Y),
                confidence = anchor.getFloat(KEY_ANCHOR_CONFIDENCE),
                width = if (anchor.getBoolean(KEY_ANCHOR_HAS_WIDTH)) anchor.getFloat(KEY_ANCHOR_WIDTH) else null,
                height = if (anchor.getBoolean(KEY_ANCHOR_HAS_HEIGHT)) anchor.getFloat(KEY_ANCHOR_HEIGHT) else null,
                manual = anchor.getBoolean(KEY_ANCHOR_MANUAL)
            )
        }
        return AiWatermarkEffect(
            id = requireString(data, KEY_EFFECT_ID),
            projectId = requireString(data, KEY_PROJECT_ID),
            clipId = requireString(data, KEY_CLIP_ID),
            clipLocalStartUs = data.getLong(KEY_RANGE_START_US),
            clipLocalEndUs = data.getLong(KEY_RANGE_END_US),
            roi = roiFrom(data),
            motionAnchors = anchors,
            contextPaddingPx = data.getInt(KEY_CONTEXT_PADDING_PX),
            featherPx = data.getInt(KEY_FEATHER_PX),
            temporalStability = data.getFloat(KEY_TEMPORAL_STABILITY),
            modelId = requireString(data, KEY_MODEL_ID),
            enabled = data.getBoolean(KEY_ENABLED)
        )
    }

    private fun roiFrom(data: Bundle) = NormalizedRoi(
        left = data.getFloat(KEY_ROI_LEFT),
        top = data.getFloat(KEY_ROI_TOP),
        right = data.getFloat(KEY_ROI_RIGHT),
        bottom = data.getFloat(KEY_ROI_BOTTOM)
    )

    private fun requireString(data: Bundle, key: String): String =
        requireNotNull(data.getString(key)) { "Missing isolated AI argument: $key" }

    private fun sendProgress(reply: Messenger, requestId: String, progress: Float) {
        runCatching {
            reply.send(Message.obtain(null, MSG_PROGRESS).apply {
                data = Bundle().apply {
                    putString(KEY_REQUEST_ID, requestId)
                    putFloat(KEY_PROGRESS, progress.coerceIn(0f, 1f))
                }
            })
        }
    }

    private fun sendSuccess(reply: Messenger, requestId: String, payload: Bundle) {
        payload.putString(KEY_REQUEST_ID, requestId)
        runCatching { reply.send(Message.obtain(null, MSG_SUCCESS).apply { data = payload }) }
    }

    private fun sendError(reply: Messenger, requestId: String, message: String) {
        runCatching {
            reply.send(Message.obtain(null, MSG_ERROR).apply {
                data = Bundle().apply {
                    putString(KEY_REQUEST_ID, requestId)
                    putString(KEY_ERROR, message)
                }
            })
        }
    }

    companion object {
        const val CMD_RUNTIME = 1
        const val CMD_STILL = 2
        const val CMD_MOVING = 3
        const val CMD_CANCEL = 4
        const val CMD_PING = 5
        const val CMD_CRASH_FOR_TEST = 99

        const val MSG_PROGRESS = 101
        const val MSG_SUCCESS = 102
        const val MSG_ERROR = 103

        const val KEY_REQUEST_ID = "request_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val KEY_PROCESS_NAME = "process_name"
        const val KEY_RUNTIME_COMPLETE = "runtime_complete"
        const val KEY_NNAPI = "nnapi"
        const val KEY_DETAIL = "detail"
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_SOURCE_TIME_US = "source_time_us"
        const val KEY_SOURCE_WIDTH = "source_width"
        const val KEY_SOURCE_HEIGHT = "source_height"
        const val KEY_FEATHER_PX = "feather_px"
        const val KEY_MODEL_ROLE = "model_role"
        const val KEY_PATH = "path"
        const val KEY_PROVIDER = "provider"
        const val KEY_CENTER_LOCAL_US = "center_local_us"
        const val KEY_LENGTH = "length"
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_CLIP_ID = "clip_id"
        const val KEY_EFFECT_ID = "effect_id"
        const val KEY_RANGE_START_US = "range_start_us"
        const val KEY_RANGE_END_US = "range_end_us"
        const val KEY_CONTEXT_PADDING_PX = "context_padding_px"
        const val KEY_TEMPORAL_STABILITY = "temporal_stability"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_ENABLED = "enabled"
        const val KEY_ROI_LEFT = "roi_left"
        const val KEY_ROI_TOP = "roi_top"
        const val KEY_ROI_RIGHT = "roi_right"
        const val KEY_ROI_BOTTOM = "roi_bottom"
        const val KEY_ANCHORS = "anchors"
        const val KEY_ANCHOR_TIME_US = "anchor_time_us"
        const val KEY_ANCHOR_X = "anchor_x"
        const val KEY_ANCHOR_Y = "anchor_y"
        const val KEY_ANCHOR_CONFIDENCE = "anchor_confidence"
        const val KEY_ANCHOR_HAS_WIDTH = "anchor_has_width"
        const val KEY_ANCHOR_WIDTH = "anchor_width"
        const val KEY_ANCHOR_HAS_HEIGHT = "anchor_has_height"
        const val KEY_ANCHOR_HEIGHT = "anchor_height"
        const val KEY_ANCHOR_MANUAL = "anchor_manual"
    }
}
