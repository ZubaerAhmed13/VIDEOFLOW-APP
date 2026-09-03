package com.videoflow.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

@Composable
fun CachedThumbnailPreview(path: String?, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, path) {
        value = if (path.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            val file = File(path)
            if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Timeline media thumbnail",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

@Composable
fun WaveformPreview(
    peaks: FloatArray?,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val waveform = peaks ?: return
    if (waveform.isEmpty()) return
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val visibleBins = max(1, size.width.toInt())
        val stride = max(1, ceil(waveform.size.toDouble() / visibleBins.toDouble()).toInt())
        var outputIndex = 0
        var sourceIndex = 0
        while (sourceIndex < waveform.size && outputIndex < visibleBins) {
            var peak = 0f
            val end = (sourceIndex + stride).coerceAtMost(waveform.size)
            for (index in sourceIndex until end) peak = max(peak, waveform[index].coerceIn(0f, 1f))
            val x = if (visibleBins <= 1) 0f else outputIndex.toFloat() / (visibleBins - 1).toFloat() * size.width
            val halfHeight = peak * centerY
            drawLine(
                color = color.copy(alpha = 0.78f),
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = 1f
            )
            outputIndex += 1
            sourceIndex = end
        }
    }
}
