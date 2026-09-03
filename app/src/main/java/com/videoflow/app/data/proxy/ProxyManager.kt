package com.videoflow.app.data.proxy

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.videoflow.app.data.db.ProxyEntity
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.domain.editor.ProxyQuality
import com.videoflow.app.domain.editor.ProxyStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt


data class ProxyProgress(
    val assetId: String? = null,
    val status: ProxyStatus = ProxyStatus.NONE,
    val percent: Int? = null,
    val message: String? = null
)

class InsufficientProxyStorageException(message: String) : IllegalStateException(message)

@OptIn(UnstableApi::class)
@Singleton
class ProxyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: VideoFlowDatabase
) {
    private val proxyMutex = Mutex()
    private val _progress = MutableStateFlow(ProxyProgress())
    val progress = _progress.asStateFlow()
    private val active = mutableMapOf<String, Transformer>()

    suspend fun generate(assetId: String, quality: ProxyQuality): ProxyEntity = proxyMutex.withLock {
        val asset = withContext(Dispatchers.IO) {
            db.mediaAssetDao().get(assetId) ?: error("Media asset not found")
        }
        require(asset.mimeType?.startsWith("video/") == true) { "Proxies are generated for video assets" }
        require(asset.sourceStatus == "AVAILABLE") { "Original source must be available to generate a proxy" }

        val targetHeight = chooseTargetHeight(asset.height, quality)
        val targetWidth = calculateWidth(asset.width, asset.height, targetHeight)
        val expectedBytes = estimateProxyBytes(asset.sizeBytes, asset.width, asset.height, targetWidth, targetHeight)
        ensureStorage(expectedBytes)

        val directory = File(context.filesDir, "proxies").apply { mkdirs() }
        val fingerprintPart = asset.fingerprintSha256?.take(12) ?: "unverified"
        val output = File(directory, "${asset.assetId}-$fingerprintPart-${quality.name.lowercase()}.mp4")
        if (output.exists()) output.delete()

        val proxyId = withContext(Dispatchers.IO) {
            db.proxyDao().getForAsset(assetId)?.id ?: UUID.randomUUID().toString()
        }
        var entity = ProxyEntity(
            id = proxyId,
            assetId = assetId,
            path = output.absolutePath,
            width = targetWidth,
            height = targetHeight,
            codecMime = MimeTypes.VIDEO_H264,
            sourceFingerprint = asset.fingerprintSha256,
            status = ProxyStatus.GENERATING.name,
            quality = quality.name,
            createdAt = System.currentTimeMillis(),
            sizeBytes = null
        )
        withContext(Dispatchers.IO) { db.proxyDao().put(entity) }
        _progress.value = ProxyProgress(assetId, ProxyStatus.GENERATING, null, "Generating proxy")

        try {
            runTransform(
                assetId = assetId,
                sourceUri = asset.sourceUri,
                sourceHeight = asset.height,
                targetHeight = targetHeight,
                outputPath = output.absolutePath
            )
            if (!output.exists() || output.length() <= 0L) error("Proxy output was not created")
            entity = entity.copy(
                status = ProxyStatus.READY.name,
                sizeBytes = output.length(),
                createdAt = System.currentTimeMillis()
            )
            withContext(Dispatchers.IO) { db.proxyDao().put(entity) }
            _progress.value = ProxyProgress(assetId, ProxyStatus.READY, 100, "Proxy ready")
            entity
        } catch (t: Throwable) {
            output.delete()
            entity = entity.copy(status = ProxyStatus.FAILED.name, sizeBytes = null)
            withContext(Dispatchers.IO) { db.proxyDao().put(entity) }
            _progress.value = ProxyProgress(assetId, ProxyStatus.FAILED, null, t.message ?: "Proxy generation failed")
            throw t
        }
    }

    suspend fun cancel(assetId: String) {
        withContext(Dispatchers.Main.immediate) { active[assetId]?.cancel() }
        withContext(Dispatchers.IO) {
            val proxy = db.proxyDao().getForAsset(assetId) ?: return@withContext
            File(proxy.path).delete()
            db.proxyDao().put(proxy.copy(status = ProxyStatus.FAILED.name, sizeBytes = null))
        }
        _progress.value = ProxyProgress(assetId, ProxyStatus.FAILED, null, "Proxy generation cancelled")
    }

    suspend fun delete(assetId: String) = withContext(Dispatchers.IO) {
        val proxy = db.proxyDao().getForAsset(assetId) ?: return@withContext
        File(proxy.path).delete()
        db.proxyDao().deleteForAsset(assetId)
        if (_progress.value.assetId == assetId) _progress.value = ProxyProgress()
    }

    suspend fun reconcile(assetId: String) = withContext(Dispatchers.IO) {
        val asset = db.mediaAssetDao().get(assetId) ?: return@withContext
        val proxy = db.proxyDao().getForAsset(assetId) ?: return@withContext
        val stale = asset.sourceStatus == "CHANGED" ||
            proxy.sourceFingerprint != null && proxy.sourceFingerprint != asset.fingerprintSha256 ||
            !File(proxy.path).exists()
        if (stale && proxy.status == ProxyStatus.READY.name) db.proxyDao().markStale(assetId)
    }

    private suspend fun runTransform(
        assetId: String,
        sourceUri: String,
        sourceHeight: Int?,
        targetHeight: Int,
        outputPath: String
    ): ExportResult = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            val videoEffects: List<Effect> = if (sourceHeight != null && sourceHeight > targetHeight) {
                listOf(Presentation.createForHeight(targetHeight))
            } else {
                emptyList()
            }
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(sourceUri)))
                .setEffects(Effects(emptyList(), videoEffects))
                .build()

            lateinit var transformer: Transformer
            lateinit var poll: Runnable
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    handler.removeCallbacks(poll)
                    active.remove(assetId)
                    if (continuation.isActive) continuation.resume(result)
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException
                ) {
                    handler.removeCallbacks(poll)
                    active.remove(assetId)
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            }
            transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build()
            active[assetId] = transformer

            poll = object : Runnable {
                override fun run() {
                    val state = transformer.getProgress(progressHolder)
                    val percent = if (state == Transformer.PROGRESS_STATE_AVAILABLE) progressHolder.progress else null
                    _progress.value = ProxyProgress(assetId, ProxyStatus.GENERATING, percent, "Generating proxy")
                    if (state != Transformer.PROGRESS_STATE_NOT_STARTED && continuation.isActive) {
                        handler.postDelayed(this, 500L)
                    }
                }
            }

            continuation.invokeOnCancellation {
                handler.removeCallbacks(poll)
                transformer.cancel()
                active.remove(assetId)
                File(outputPath).delete()
            }
            transformer.start(edited, outputPath)
            handler.post(poll)
        }
    }

    private fun chooseTargetHeight(sourceHeight: Int?, quality: ProxyQuality): Int {
        val requested = when (quality) {
            ProxyQuality.PERFORMANCE -> 540
            ProxyQuality.BALANCED -> 720
            ProxyQuality.HIGH -> 1080
        }
        val source = sourceHeight?.takeIf { it > 0 } ?: requested
        return minOf(source, requested).coerceAtLeast(2)
    }

    private fun calculateWidth(sourceWidth: Int?, sourceHeight: Int?, targetHeight: Int): Int {
        if (sourceWidth == null || sourceHeight == null || sourceWidth <= 0 || sourceHeight <= 0) {
            val width = targetHeight * 16 / 9
            return if (width % 2 == 0) width else width + 1
        }
        val width = (sourceWidth.toDouble() * targetHeight.toDouble() / sourceHeight.toDouble()).roundToInt()
        return if (width % 2 == 0) width else width + 1
    }

    private fun estimateProxyBytes(
        sourceBytes: Long?,
        sourceWidth: Int?,
        sourceHeight: Int?,
        targetWidth: Int,
        targetHeight: Int
    ): Long {
        val sourcePixels = (sourceWidth?.toLong() ?: 0L) * (sourceHeight?.toLong() ?: 0L)
        val targetPixels = targetWidth.toLong() * targetHeight.toLong()
        if (sourceBytes == null || sourceBytes <= 0L || sourcePixels <= 0L) return 512L * 1024L * 1024L
        val scaled = sourceBytes.toDouble() * (targetPixels.toDouble() / sourcePixels.toDouble()).coerceAtMost(1.0) * 0.85
        return scaled.toLong().coerceAtLeast(16L * 1024L * 1024L)
    }

    private fun ensureStorage(expectedBytes: Long) {
        val stats = StatFs(context.filesDir.absolutePath)
        val available = stats.availableBytes
        val requiredWithHeadroom = (expectedBytes.toDouble() * 1.25).toLong()
        if (available < requiredWithHeadroom) {
            throw InsufficientProxyStorageException(
                "Not enough free storage for this proxy. Estimated requirement with safety headroom: $requiredWithHeadroom bytes."
            )
        }
    }
}
