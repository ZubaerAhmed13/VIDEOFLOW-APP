package com.videoflow.app.ai.watermark

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bounded source-frame decode only. No ONNX, GPU effect or Media3 reconstruction dependency. */
@Singleton
class LocalPreviewFrameDecoder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun decodeFrame(
        sourceUri: String,
        sourceTimeUs: Long,
        maxDimensionPx: Int = 960
    ): Bitmap = withContext(Dispatchers.IO) {
        require(maxDimensionPx in 128..2048)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(sourceUri))
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()?.coerceAtLeast(1) ?: 1280
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()?.coerceAtLeast(1) ?: 720
            val scale = min(1.0, maxDimensionPx.toDouble() / max(rawWidth, rawHeight).toDouble())
            val targetWidth = (rawWidth * scale).roundToInt().coerceAtLeast(1)
            val targetHeight = (rawHeight * scale).roundToInt().coerceAtLeast(1)
            val frame = if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(
                    sourceTimeUs.coerceAtLeast(0L),
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    targetWidth,
                    targetHeight
                )
            } else {
                retriever.getFrameAtTime(
                    sourceTimeUs.coerceAtLeast(0L),
                    MediaMetadataRetriever.OPTION_CLOSEST
                )?.let { original ->
                    if (original.width == targetWidth && original.height == targetHeight) original
                    else Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
                        .also { original.recycle() }
                }
            }
            requireNotNull(frame) { "Could not decode a preview frame from the selected source." }
                .copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            retriever.release()
        }
    }
}

/** Cache invalidation has no model/session dependency and is safe in the editor process. */
@Singleton
class AiPreviewCacheController @Inject constructor(
    @ApplicationContext context: Context
) {
    private val cache = AiPreviewCacheStore(context)

    suspend fun invalidateProject(projectId: String) {
        cache.removeProject(projectId)
    }
}
