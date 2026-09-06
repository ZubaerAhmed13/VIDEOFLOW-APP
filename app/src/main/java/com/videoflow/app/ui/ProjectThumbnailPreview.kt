package com.videoflow.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PROJECT_THUMBNAIL_WIDTH_PX = 352
private const val PROJECT_THUMBNAIL_HEIGHT_PX = 224

/**
 * Bounded Home-card preview for the first project asset.
 *
 * It never reads the whole source into memory. Android provider thumbnails are preferred on
 * API 29+, image sources use sampled decoding, and video fallback extracts only one frame.
 */
@Composable
fun ProjectThumbnailPreview(
    sourceUri: String?,
    mimeType: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, sourceUri, mimeType) {
        value = if (sourceUri.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                loadProjectThumbnail(context, Uri.parse(sourceUri), mimeType)
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = requireNotNull(bitmap).asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val icon = when {
                mimeType?.startsWith("audio/") == true -> Icons.Default.Audiotrack
                mimeType?.startsWith("image/") == true -> Icons.Default.BrokenImage
                else -> Icons.Default.Videocam
            }
            Icon(icon, contentDescription = null)
        }
    }
}

private fun loadProjectThumbnail(context: Context, uri: Uri, mimeType: String?): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            context.contentResolver.loadThumbnail(
                uri,
                Size(PROJECT_THUMBNAIL_WIDTH_PX, PROJECT_THUMBNAIL_HEIGHT_PX),
                null
            )
        }.getOrNull()?.let { return it }
    }

    return when {
        mimeType?.startsWith("image/") == true -> loadSampledImage(context, uri)
        mimeType?.startsWith("video/") == true -> loadVideoFrame(context, uri)
        else -> null
    }
}

private fun loadSampledImage(context: Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (
        bounds.outWidth / sample > PROJECT_THUMBNAIL_WIDTH_PX * 2 ||
        bounds.outHeight / sample > PROJECT_THUMBNAIL_HEIGHT_PX * 2
    ) {
        sample *= 2
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sample.coerceAtLeast(1)
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }?.let(::downscaleIfNeeded)
}

private fun loadVideoFrame(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                PROJECT_THUMBNAIL_WIDTH_PX,
                PROJECT_THUMBNAIL_HEIGHT_PX
            )
        } else {
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.let(::downscaleIfNeeded)
        }
        frame
    } finally {
        runCatching { retriever.release() }
    }
}

private fun downscaleIfNeeded(source: Bitmap): Bitmap {
    if (source.width <= PROJECT_THUMBNAIL_WIDTH_PX && source.height <= PROJECT_THUMBNAIL_HEIGHT_PX) {
        return source
    }
    val scale = minOf(
        PROJECT_THUMBNAIL_WIDTH_PX.toDouble() / source.width.toDouble(),
        PROJECT_THUMBNAIL_HEIGHT_PX.toDouble() / source.height.toDouble()
    )
    val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    if (scaled !== source) source.recycle()
    return scaled
}
