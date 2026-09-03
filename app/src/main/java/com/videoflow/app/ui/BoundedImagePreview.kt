package com.videoflow.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BoundedImagePreview(
    sourceUri: String,
    modifier: Modifier = Modifier,
    maxPreviewDimensionPx: Int = 1280
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, sourceUri, maxPreviewDimensionPx) {
        value = withContext(Dispatchers.IO) {
            val uri = Uri.parse(sourceUri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (bounds.outWidth / sample > maxPreviewDimensionPx * 2 ||
                bounds.outHeight / sample > maxPreviewDimensionPx * 2
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Image overlay preview",
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}
