@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.videoflow.app.step5

import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.domain.editor.CropRect
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UXStep2CropRenderGeometryTest {
    @Test fun squareMarkerRemainsSquareForOriginalSquareAndPortraitCrops() = runBlocking {
        val fixture = Step5MediaFixture()
        try {
            val source = fixture.source("step2-geometry.mp4")
            val cases = listOf(
                "original" to CropRect(),
                "1x1" to CropRect(.125f, 0f, .875f, 1f),
                "9x16" to CropRect(.2890625f, 0f, .7109375f, 1f)
            )
            for ((label, crop) in cases) {
                val edited = fixture.clip.copy(transform = fixture.clip.transform.copy(crop = crop))
                val output = fixture.render(fixture.plan(source, edited)).first
                fixture.preserve(output, "step2-crop-$label.mp4")
                val ratio = whiteMarkerRatio(fixture, output)
                fixture.evidence("step2-crop-geometry.jsonl", "{\"case\":\"$label\",\"marker_ratio\":$ratio}")
                assertTrue("Crop stretched square marker for $label: ratio=$ratio", ratio in .88..1.12)
            }
        } finally { fixture.close() }
    }

    private fun whiteMarkerRatio(fixture: Step5MediaFixture, uri: Uri): Double {
        val retriever = MediaMetadataRetriever()
        val bitmap = try {
            retriever.setDataSource(fixture.context, uri)
            checkNotNull(retriever.getFrameAtTime(700_000L, MediaMetadataRetriever.OPTION_CLOSEST))
        } finally { retriever.release() }
        try {
            var minX = bitmap.width
            var maxX = -1
            var minY = bitmap.height
            var maxY = -1
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val c = bitmap.getPixel(x, y)
                if (Color.red(c) > 220 && Color.green(c) > 220 && Color.blue(c) > 220) {
                    minX = minOf(minX, x); maxX = maxOf(maxX, x)
                    minY = minOf(minY, y); maxY = maxOf(maxY, y)
                }
            }
            check(maxX >= minX && maxY >= minY) { "White geometry marker not found" }
            val width = (maxX - minX + 1).toDouble()
            val height = (maxY - minY + 1).toDouble()
            check(width > 10 && height > 10) { "Marker too small: ${width}x$height" }
            return width / height
        } finally { bitmap.recycle() }
    }
}
