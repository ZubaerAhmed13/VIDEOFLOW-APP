package com.videoflow.app.ai.watermark

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.videoflow.app.BuildConfig
import com.videoflow.app.domain.ai.AiModelRole
import com.videoflow.app.domain.ai.AiWatermarkEffect
import com.videoflow.app.domain.ai.NormalizedRoi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class AiPreviewWorkerDiedException(message: String, cause: Throwable? = null) : Exception(message, cause)
class AiPreviewWorkerException(message: String) : Exception(message)

data class IsolatedAiRuntimeStatus(
    val complete: Boolean,
    val nnapiAvailable: Boolean,
    val detail: String
)

data class IsolatedAiStillResult(
    val bitmap: Bitmap,
    val provider: String
)

data class IsolatedAiMovingResult(
    val path: String,
    val clipLocalStartUs: Long,
    val clipLocalEndUs: Long,
    val provider: String?
)

/**
 * Main-process IPC facade for editor-time AI rendering.
 *
 * Every LaMa session and moving Media3 reconstruction is requested from :ai_preview. Fatal worker
 * death becomes an ordinary coroutine failure here; it cannot terminate the editor process.
 */
@Singleton
class AiPreviewProcessClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val requestCounter = AtomicLong(0L)
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun runtimeStatus(): IsolatedAiRuntimeStatus {
        val reply = request(AiPreviewIsolatedService.CMD_RUNTIME, Bundle())
        return IsolatedAiRuntimeStatus(
            complete = reply.getBoolean(AiPreviewIsolatedService.KEY_RUNTIME_COMPLETE),
            nnapiAvailable = reply.getBoolean(AiPreviewIsolatedService.KEY_NNAPI),
            detail = reply.getString(AiPreviewIsolatedService.KEY_DETAIL).orEmpty()
        )
    }

    suspend fun renderStill(
        sourceUri: String,
        sourceTimeUs: Long,
        roi: NormalizedRoi,
        sourceWidth: Int,
        sourceHeight: Int,
        featherPx: Int,
        modelRole: AiModelRole
    ): IsolatedAiStillResult {
        val reply = request(
            AiPreviewIsolatedService.CMD_STILL,
            Bundle().apply {
                putString(AiPreviewIsolatedService.KEY_SOURCE_URI, sourceUri)
                putLong(AiPreviewIsolatedService.KEY_SOURCE_TIME_US, sourceTimeUs)
                putInt(AiPreviewIsolatedService.KEY_SOURCE_WIDTH, sourceWidth)
                putInt(AiPreviewIsolatedService.KEY_SOURCE_HEIGHT, sourceHeight)
                putInt(AiPreviewIsolatedService.KEY_FEATHER_PX, featherPx)
                putString(AiPreviewIsolatedService.KEY_MODEL_ROLE, modelRole.name)
                putRoi(roi)
            }
        )
        val path = requireNotNull(reply.getString(AiPreviewIsolatedService.KEY_PATH)) {
            "Isolated AI worker returned no still-preview path."
        }
        val bitmap = withContext(Dispatchers.IO) {
            try {
                requireNotNull(BitmapFactory.decodeFile(path)) {
                    "Could not decode isolated AI still preview."
                }
            } finally {
                File(path).delete()
            }
        }
        return IsolatedAiStillResult(
            bitmap = bitmap,
            provider = reply.getString(AiPreviewIsolatedService.KEY_PROVIDER).orEmpty()
        )
    }

    suspend fun renderMoving(
        effect: AiWatermarkEffect,
        centerLocalUs: Long,
        length: AiMovingPreviewLength,
        onProgress: (Float) -> Unit = {}
    ): IsolatedAiMovingResult {
        val reply = request(
            AiPreviewIsolatedService.CMD_MOVING,
            Bundle().apply {
                putString(AiPreviewIsolatedService.KEY_EFFECT_ID, effect.id)
                putString(AiPreviewIsolatedService.KEY_PROJECT_ID, effect.projectId)
                putString(AiPreviewIsolatedService.KEY_CLIP_ID, effect.clipId)
                putLong(AiPreviewIsolatedService.KEY_RANGE_START_US, effect.clipLocalStartUs)
                putLong(AiPreviewIsolatedService.KEY_RANGE_END_US, effect.clipLocalEndUs)
                putInt(AiPreviewIsolatedService.KEY_CONTEXT_PADDING_PX, effect.contextPaddingPx)
                putInt(AiPreviewIsolatedService.KEY_FEATHER_PX, effect.featherPx)
                putFloat(AiPreviewIsolatedService.KEY_TEMPORAL_STABILITY, effect.temporalStability)
                putString(AiPreviewIsolatedService.KEY_MODEL_ID, effect.modelId)
                putBoolean(AiPreviewIsolatedService.KEY_ENABLED, effect.enabled)
                putLong(AiPreviewIsolatedService.KEY_CENTER_LOCAL_US, centerLocalUs)
                putString(AiPreviewIsolatedService.KEY_LENGTH, length.name)
                putRoi(effect.roi)
                putParcelableArrayList(
                    AiPreviewIsolatedService.KEY_ANCHORS,
                    ArrayList(effect.motionAnchors.map { anchor ->
                        Bundle().apply {
                            putLong(AiPreviewIsolatedService.KEY_ANCHOR_TIME_US, anchor.clipLocalTimeUs)
                            putFloat(AiPreviewIsolatedService.KEY_ANCHOR_X, anchor.centerX)
                            putFloat(AiPreviewIsolatedService.KEY_ANCHOR_Y, anchor.centerY)
                            putFloat(AiPreviewIsolatedService.KEY_ANCHOR_CONFIDENCE, anchor.confidence)
                            putBoolean(AiPreviewIsolatedService.KEY_ANCHOR_HAS_WIDTH, anchor.width != null)
                            anchor.width?.let { putFloat(AiPreviewIsolatedService.KEY_ANCHOR_WIDTH, it) }
                            putBoolean(AiPreviewIsolatedService.KEY_ANCHOR_HAS_HEIGHT, anchor.height != null)
                            anchor.height?.let { putFloat(AiPreviewIsolatedService.KEY_ANCHOR_HEIGHT, it) }
                            putBoolean(AiPreviewIsolatedService.KEY_ANCHOR_MANUAL, anchor.manual)
                        }
                    })
                )
            },
            onProgress
        )
        return IsolatedAiMovingResult(
            path = requireNotNull(reply.getString(AiPreviewIsolatedService.KEY_PATH)) {
                "Isolated AI worker returned no moving-preview path."
            },
            clipLocalStartUs = reply.getLong(AiPreviewIsolatedService.KEY_RANGE_START_US),
            clipLocalEndUs = reply.getLong(AiPreviewIsolatedService.KEY_RANGE_END_US),
            provider = reply.getString(AiPreviewIsolatedService.KEY_PROVIDER)
        )
    }

    suspend fun pingWorkerProcess(): String =
        request(AiPreviewIsolatedService.CMD_PING, Bundle())
            .getString(AiPreviewIsolatedService.KEY_PROCESS_NAME)
            .orEmpty()

    /** Debug/instrumentation-only fatal-worker certification hook. */
    suspend fun crashWorkerForCertification() {
        check(BuildConfig.DEBUG) { "Worker-death certification is debug-only." }
        try {
            request(AiPreviewIsolatedService.CMD_CRASH_FOR_TEST, Bundle())
            error("AI preview worker unexpectedly survived the crash-certification command.")
        } catch (expected: AiPreviewWorkerDiedException) {
            throw expected
        }
    }

    private suspend fun request(
        command: Int,
        payload: Bundle,
        onProgress: (Float) -> Unit = {}
    ): Bundle = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val requestId = "${android.os.Process.myPid()}-${requestCounter.incrementAndGet()}-${System.nanoTime()}"
            payload.putString(AiPreviewIsolatedService.KEY_REQUEST_ID, requestId)
            var remote: Messenger? = null
            var remoteBinder: IBinder? = null
            var bound = false
            var finished = false
            lateinit var connection: ServiceConnection

            fun cleanup() {
                remoteBinder?.let { binder -> runCatching { binder.unlinkToDeath(deathRecipient, 0) } }
                remoteBinder = null
                remote = null
                if (bound) {
                    bound = false
                    runCatching { context.unbindService(connection) }
                }
            }

            fun complete(result: Bundle) {
                if (finished || !continuation.isActive) return
                finished = true
                cleanup()
                continuation.resume(result)
            }

            fun fail(error: Throwable) {
                if (finished || !continuation.isActive) return
                finished = true
                cleanup()
                continuation.resumeWithException(error)
            }

            val replyMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
                val data = message.data
                if (data.getString(AiPreviewIsolatedService.KEY_REQUEST_ID) != requestId) return@Handler true
                when (message.what) {
                    AiPreviewIsolatedService.MSG_PROGRESS -> onProgress(
                        data.getFloat(AiPreviewIsolatedService.KEY_PROGRESS).coerceIn(0f, 1f)
                    )
                    AiPreviewIsolatedService.MSG_SUCCESS -> complete(data)
                    AiPreviewIsolatedService.MSG_ERROR -> fail(
                        AiPreviewWorkerException(
                            data.getString(AiPreviewIsolatedService.KEY_ERROR) ?: "Isolated AI preview failed."
                        )
                    )
                }
                true
            })

            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (binder == null) {
                        fail(AiPreviewWorkerDiedException("AI preview worker returned a null Binder."))
                        return
                    }
                    if (finished || !continuation.isActive) return
                    remoteBinder = binder
                    runCatching { binder.linkToDeath(deathRecipient, 0) }
                        .onFailure {
                            fail(AiPreviewWorkerDiedException("AI preview worker died during connection.", it))
                            return
                        }
                    val messenger = Messenger(binder)
                    remote = messenger
                    try {
                        messenger.send(Message.obtain(null, command).apply {
                            data = payload
                            replyTo = replyMessenger
                        })
                    } catch (dead: DeadObjectException) {
                        fail(AiPreviewWorkerDiedException("AI preview worker died before starting the request.", dead))
                    } catch (remoteError: RemoteException) {
                        fail(AiPreviewWorkerDiedException("Could not reach isolated AI preview worker.", remoteError))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    fail(AiPreviewWorkerDiedException("AI preview worker process stopped unexpectedly."))
                }

                override fun onBindingDied(name: ComponentName?) {
                    fail(AiPreviewWorkerDiedException("AI preview worker binding died; editor state is preserved."))
                }

                override fun onNullBinding(name: ComponentName?) {
                    fail(AiPreviewWorkerDiedException("AI preview worker refused the binding."))
                }
            }

            bound = context.bindService(
                Intent(context, AiPreviewIsolatedService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
            if (!bound) {
                fail(AiPreviewWorkerDiedException("Android could not start the isolated AI preview worker."))
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation {
                val cancel = Message.obtain(null, AiPreviewIsolatedService.CMD_CANCEL).apply {
                    data = Bundle().apply { putString(AiPreviewIsolatedService.KEY_REQUEST_ID, requestId) }
                    replyTo = replyMessenger
                }
                runCatching { remote?.send(cancel) }
                mainHandler.post {
                    if (!finished) {
                        finished = true
                        cleanup()
                    }
                }
            }
        }
    }

    private val deathRecipient = IBinder.DeathRecipient {
        // The per-request connection also receives process death. This field exists only so Binder
        // callbacks are linked to the client lifetime; request() installs its own failure handling.
    }

    private fun Bundle.putRoi(roi: NormalizedRoi) {
        putFloat(AiPreviewIsolatedService.KEY_ROI_LEFT, roi.left)
        putFloat(AiPreviewIsolatedService.KEY_ROI_TOP, roi.top)
        putFloat(AiPreviewIsolatedService.KEY_ROI_RIGHT, roi.right)
        putFloat(AiPreviewIsolatedService.KEY_ROI_BOTTOM, roi.bottom)
    }
}
