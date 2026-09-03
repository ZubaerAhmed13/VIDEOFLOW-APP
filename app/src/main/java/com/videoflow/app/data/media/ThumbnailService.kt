package com.videoflow.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.videoflow.app.data.db.VideoFlowDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Bounded editor-thumbnail pipeline.
 *
 * Originals stay referenced through SAF/content URIs. Only a small decoded frame is retained and
 * written to the app cache. The service never reads an entire source into a ByteArray and limits
 * concurrent decoders so several 4K/large-source clips cannot burst memory at once.
 */
@Singleton
class ThumbnailService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: VideoFlowDatabase
) {
    private val decoderSlots = Semaphore(2)

    suspend fun loadOrGenerate(
        assetId: String,
        timeUs: Long = 0L,
        maxDimensionPx: Int = DEFAULT_MAX_DIMENSION_PX
    ): ThumbnailResult? = decoderSlots.withPermit {
        require(maxDimensionPx in 64..1024)
        withContext(Dispatchers.IO) {
            val asset = db.mediaAssetDao().get(assetId) ?: return@withContext null
            val cacheDir = File(context.cacheDir, "step2-thumbnails").apply { mkdirs() }
            val identity = asset.fingerprintSha256?.take(16) ?: "uri-${asset.sourceUri.hashCode().toUInt()}"
            val bucketUs = if (asset.mimeType?.startsWith("video/") == true) {
                (timeUs.coerceAtLeast(0L) / VIDEO_BUCKET_US) * VIDEO_BUCKET_US
            } else {
                0L
            }
            val target = File(cacheDir, "${asset.assetId}-$identity-$bucketUs-$maxDimensionPx.jpg")
            if (target.isFile && target.length() > 0L) {
                return@withContext ThumbnailResult(target.absolutePath, null, null, true)
            }

            val bitmap = when {
                asset.mimeType?.startsWith("image/") == true -> decodeBoundedImage(asset.sourceUri, maxDimensionPx)
                asset.mimeType?.startsWith("video/") == true -> decodeBoundedVideoFrame(
                    sourceUri = asset.sourceUri,
                    timeUs = timeUs.coerceAtLeast(0L),
                    sourceWidth = asset.width,
                    sourceHeight = asset.height,
                    maxDimensionPx = maxDimensionPx
                )
                else -> null
            } ?: return@withContext null

            try {
                target.outputStream().buffered().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                        "Thumbnail encoder returned failure"
                    }
                }
                trimCache(cacheDir)
                ThumbnailResult(target.absolutePath, bitmap.width, bitmap.height, false)
            } finally {
                bitmap.recycle()
            }
        }
    }

    suspend fun clearAsset(assetId: String) = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "step2-thumbnails")
        cacheDir.listFiles()?.filter { it.name.startsWith("$assetId-") }?.forEach(File::delete)
    }

    private fun decodeBoundedImage(sourceUri: String, maxDimensionPx: Int): Bitmap? {
        val uri = Uri.parse(sourceUri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimensionPx * 2 || bounds.outHeight / sample > maxDimensionPx * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        return scaleDownIfNeeded(decoded, maxDimensionPx)
    }

    private fun decodeBoundedVideoFrame(
        sourceUri: String,
        timeUs: Long,
        sourceWidth: Int?,
        sourceHeight: Int?,
        maxDimensionPx: Int
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(sourceUri))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && sourceWidth != null && sourceHeight != null) {
                val (targetWidth, targetHeight) = boundedSize(sourceWidth, sourceHeight, maxDimensionPx)
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidth,
                    targetHeight
                )
            } else {
                val decoded = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return null
                scaleDownIfNeeded(decoded, maxDimensionPx)
            }
        } finally {
            retriever.release()
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
        if (max(bitmap.width, bitmap.height) <= maxDimensionPx) return bitmap
        val scale = maxDimensionPx.toDouble() / max(bitmap.width, bitmap.height).toDouble()
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun boundedSize(width: Int, height: Int, maxDimensionPx: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return maxDimensionPx to maxDimensionPx
        if (max(width, height) <= maxDimensionPx) return width to height
        val scale = maxDimensionPx.toDouble() / max(width, height).toDouble()
        return (width * scale).roundToInt().coerceAtLeast(1) to
            (height * scale).roundToInt().coerceAtLeast(1)
    }

    private fun trimCache(cacheDir: File) {
        val files = cacheDir.listFiles()?.filter(File::isFile)?.sortedByDescending(File::lastModified).orEmpty()
        var keptBytes = 0L
        files.forEachIndexed { index, file ->
            keptBytes += file.length()
            if (index >= MAX_CACHE_FILES || keptBytes > MAX_CACHE_BYTES) file.delete()
        }
    }

    companion object {
        private const val DEFAULT_MAX_DIMENSION_PX = 320
        private const val VIDEO_BUCKET_US = 2_000_000L
        private const val MAX_CACHE_FILES = 512
        private const val MAX_CACHE_BYTES = 128L * 1024L * 1024L
    }
}

data class ThumbnailResult(
    val path: String,
    val width: Int?,
    val height: Int?,
    val fromCache: Boolean
)
