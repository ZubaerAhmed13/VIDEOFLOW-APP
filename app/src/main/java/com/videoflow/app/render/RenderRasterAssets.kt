package com.videoflow.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import com.videoflow.app.domain.editor.TextOverlay
import com.videoflow.app.domain.export.ExportSize
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max

class RenderRasterAssets(private val root: File) {
    init { root.mkdirs() }

    fun createBackground(argb: Long): File {
        val file = File(root, "background.png")
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(argb.toInt())
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    fun createText(
        overlay: TextOverlay,
        projectSize: ExportSize,
        outputSize: ExportSize
    ): File {
        val outputScale = outputSize.height.toFloat() / projectSize.height.toFloat()
        val textPx = (overlay.fontSizeSp * outputScale).coerceAtLeast(4f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = ColorUtils.setAlphaComponent(overlay.colorArgb.toInt(), 255)
            textSize = textPx
            typeface = Typeface.create(
                Typeface.DEFAULT,
                when {
                    overlay.fontWeight >= 600 && overlay.italic -> Typeface.BOLD_ITALIC
                    overlay.fontWeight >= 600 -> Typeface.BOLD
                    overlay.italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
            )
            textAlign = when (overlay.alignment.uppercase()) {
                "LEFT", "START" -> Paint.Align.LEFT
                "RIGHT", "END" -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
        }
        val lines = overlay.content.ifEmpty { " " }.split('\n')
        val fm = paint.fontMetrics
        val lineHeight = max(1f, fm.descent - fm.ascent)
        val maxWidth = lines.maxOf { paint.measureText(it).coerceAtLeast(1f) }
        val padding = ceil(textPx * 0.35f).toInt().coerceAtLeast(2)
        val width = ceil(maxWidth).toInt().coerceAtLeast(1) + padding * 2
        val height = ceil(lineHeight * lines.size).toInt().coerceAtLeast(1) + padding * 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        val x = when (paint.textAlign) {
            Paint.Align.LEFT -> padding.toFloat()
            Paint.Align.RIGHT -> (width - padding).toFloat()
            else -> width / 2f
        }
        var baseline = padding - fm.ascent
        lines.forEach { line ->
            canvas.drawText(line, x, baseline, paint)
            baseline += lineHeight
        }
        val file = File(root, "text-${overlay.id}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
}
