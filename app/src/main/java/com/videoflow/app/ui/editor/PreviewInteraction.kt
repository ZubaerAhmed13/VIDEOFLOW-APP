package com.videoflow.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.videoflow.app.domain.editor.CropRect
import kotlin.math.abs

/** Reusable geometry contract for every direct preview manipulation. */
data class PreviewContentGeometry(
    val viewportRect: Rect,
    val projectRect: Rect,
    val projectWidth: Int,
    val projectHeight: Int
) {
    fun screenToProjectNormalized(screen: Offset): Offset? {
        if (!projectRect.contains(screen) || projectRect.width <= 0f || projectRect.height <= 0f) return null
        return Offset(
            x = ((screen.x - projectRect.left) / projectRect.width).coerceIn(0f, 1f),
            y = ((screen.y - projectRect.top) / projectRect.height).coerceIn(0f, 1f)
        )
    }

    fun projectNormalizedToScreen(project: Offset): Offset = Offset(
        x = projectRect.left + project.x.coerceIn(0f, 1f) * projectRect.width,
        y = projectRect.top + project.y.coerceIn(0f, 1f) * projectRect.height
    )
}

private enum class CropDragMode { LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE }

@Composable
fun CropInteractionOverlay(
    crop: CropRect,
    onCropChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val hitPx = with(density) { 28.dp.toPx() }
    var mode by remember { mutableStateOf(CropDragMode.MOVE) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(crop) {
                detectDragGestures(
                    onDragStart = { p ->
                        val left = crop.left * size.width
                        val right = crop.right * size.width
                        val top = crop.top * size.height
                        val bottom = crop.bottom * size.height
                        val nearL = abs(p.x - left) <= hitPx
                        val nearR = abs(p.x - right) <= hitPx
                        val nearT = abs(p.y - top) <= hitPx
                        val nearB = abs(p.y - bottom) <= hitPx
                        mode = when {
                            nearL && nearT -> CropDragMode.TOP_LEFT
                            nearR && nearT -> CropDragMode.TOP_RIGHT
                            nearL && nearB -> CropDragMode.BOTTOM_LEFT
                            nearR && nearB -> CropDragMode.BOTTOM_RIGHT
                            nearL -> CropDragMode.LEFT
                            nearR -> CropDragMode.RIGHT
                            nearT -> CropDragMode.TOP
                            nearB -> CropDragMode.BOTTOM
                            else -> CropDragMode.MOVE
                        }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        val dx = drag.x / size.width.coerceAtLeast(1f)
                        val dy = drag.y / size.height.coerceAtLeast(1f)
                        val minSize = 0.02f
                        var l = crop.left
                        var r = crop.right
                        var t = crop.top
                        var b = crop.bottom
                        when (mode) {
                            CropDragMode.LEFT, CropDragMode.TOP_LEFT, CropDragMode.BOTTOM_LEFT -> l = (l + dx).coerceIn(0f, r - minSize)
                            CropDragMode.RIGHT, CropDragMode.TOP_RIGHT, CropDragMode.BOTTOM_RIGHT -> r = (r + dx).coerceIn(l + minSize, 1f)
                            else -> Unit
                        }
                        when (mode) {
                            CropDragMode.TOP, CropDragMode.TOP_LEFT, CropDragMode.TOP_RIGHT -> t = (t + dy).coerceIn(0f, b - minSize)
                            CropDragMode.BOTTOM, CropDragMode.BOTTOM_LEFT, CropDragMode.BOTTOM_RIGHT -> b = (b + dy).coerceIn(t + minSize, 1f)
                            else -> Unit
                        }
                        if (mode == CropDragMode.MOVE) {
                            val width = r - l
                            val height = b - t
                            l = (l + dx).coerceIn(0f, 1f - width)
                            t = (t + dy).coerceIn(0f, 1f - height)
                            r = l + width
                            b = t + height
                        }
                        onCropChange(CropRect(l, t, r, b))
                    }
                )
            }
    ) {
        val left = crop.left * size.width
        val right = crop.right * size.width
        val top = crop.top * size.height
        val bottom = crop.bottom * size.height
        val shade = Color.Black.copy(alpha = 0.52f)
        drawRect(shade, topLeft = Offset.Zero, size = Size(size.width, top.coerceAtLeast(0f)))
        drawRect(shade, topLeft = Offset(0f, bottom), size = Size(size.width, (size.height - bottom).coerceAtLeast(0f)))
        drawRect(shade, topLeft = Offset(0f, top), size = Size(left.coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)))
        drawRect(shade, topLeft = Offset(right, top), size = Size((size.width - right).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)))
        val rect = Rect(left, top, right, bottom)
        drawRect(VideoFlowEditorColors.SelectionAccent, rect.topLeft, rect.size, style = Stroke(width = 3f))
        val handleRadius = 7f
        listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight).forEach {
            drawCircle(VideoFlowEditorColors.SelectionAccent, handleRadius, it)
            drawCircle(Color.White, handleRadius / 2f, it)
        }
    }
}

@Composable
fun TransformInteractionOverlay(
    centerX: Float,
    centerY: Float,
    scale: Float,
    onGesture: (dxNormalized: Float, dyNormalized: Float, zoom: Float, rotationDelta: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    onGesture(
                        pan.x / size.width.coerceAtLeast(1f),
                        pan.y / size.height.coerceAtLeast(1f),
                        zoom.coerceIn(0.5f, 2f),
                        rotation
                    )
                }
            }
    ) {
        val cx = centerX.coerceIn(0f, 1f) * size.width
        val cy = centerY.coerceIn(0f, 1f) * size.height
        val w = (size.width * 0.72f * scale.coerceIn(0.05f, 2f)).coerceAtMost(size.width * 1.5f)
        val h = (size.height * 0.58f * scale.coerceIn(0.05f, 2f)).coerceAtMost(size.height * 1.5f)
        val rect = Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        drawRect(VideoFlowEditorColors.SelectionAccent, rect.topLeft, rect.size, style = Stroke(width = 3f))
        if (abs(centerX - 0.5f) < 0.018f) {
            drawLine(VideoFlowEditorColors.SelectionAccent.copy(alpha = 0.8f), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 2f)
        }
        if (abs(centerY - 0.5f) < 0.018f) {
            drawLine(VideoFlowEditorColors.SelectionAccent.copy(alpha = 0.8f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 2f)
        }
    }
}
