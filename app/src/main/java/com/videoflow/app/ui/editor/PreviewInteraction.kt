package com.videoflow.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
    aspectRatio: Float? = null,
    onCropChange: (CropRect) -> Unit,
    onCropCommit: (CropRect) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val hitPx = with(density) { 28.dp.toPx() }
    val latestCrop by rememberUpdatedState(crop)
    val latestAspect by rememberUpdatedState(aspectRatio)
    var mode by remember { mutableStateOf(CropDragMode.MOVE) }
    var working by remember { mutableStateOf(crop) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { p ->
                        working = latestCrop
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        val left = working.left * width
                        val right = working.right * width
                        val top = working.top * height
                        val bottom = working.bottom * height
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
                    onDragCancel = { working = latestCrop },
                    onDragEnd = { onCropCommit(working) },
                    onDrag = { change, drag ->
                        change.consume()
                        val widthPx = size.width.toFloat().coerceAtLeast(1f)
                        val heightPx = size.height.toFloat().coerceAtLeast(1f)
                        val dx = drag.x / widthPx
                        val dy = drag.y / heightPx
                        val minSize = 0.02f
                        var l = working.left
                        var r = working.right
                        var t = working.top
                        var b = working.bottom
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
                        val constrained = constrainCropAspect(CropRect(l, t, r, b), latestAspect, mode, minSize)
                        working = constrained
                        onCropChange(constrained)
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

private fun constrainCropAspect(
    source: CropRect,
    normalizedAspect: Float?,
    mode: CropDragMode,
    minSize: Float
): CropRect {
    val aspect = normalizedAspect?.takeIf { it.isFinite() && it > 0f } ?: return source
    if (mode == CropDragMode.MOVE) return source
    var l = source.left
    var r = source.right
    var t = source.top
    var b = source.bottom
    var width = (r - l).coerceAtLeast(minSize)
    var height = (b - t).coerceAtLeast(minSize)
    if (width / height > aspect) width = height * aspect else height = width / aspect
    width = width.coerceAtMost(1f)
    height = height.coerceAtMost(1f)

    when (mode) {
        CropDragMode.TOP_LEFT -> { l = r - width; t = b - height }
        CropDragMode.TOP_RIGHT -> { r = l + width; t = b - height }
        CropDragMode.BOTTOM_LEFT -> { l = r - width; b = t + height }
        CropDragMode.BOTTOM_RIGHT -> { r = l + width; b = t + height }
        CropDragMode.LEFT -> {
            l = r - width
            val cy = (t + b) / 2f
            t = cy - height / 2f; b = cy + height / 2f
        }
        CropDragMode.RIGHT -> {
            r = l + width
            val cy = (t + b) / 2f
            t = cy - height / 2f; b = cy + height / 2f
        }
        CropDragMode.TOP -> {
            t = b - height
            val cx = (l + r) / 2f
            l = cx - width / 2f; r = cx + width / 2f
        }
        CropDragMode.BOTTOM -> {
            b = t + height
            val cx = (l + r) / 2f
            l = cx - width / 2f; r = cx + width / 2f
        }
        CropDragMode.MOVE -> Unit
    }

    val shiftX = when {
        l < 0f -> -l
        r > 1f -> 1f - r
        else -> 0f
    }
    l += shiftX; r += shiftX
    val shiftY = when {
        t < 0f -> -t
        b > 1f -> 1f - b
        else -> 0f
    }
    t += shiftY; b += shiftY
    return CropRect(l.coerceIn(0f, 1f), t.coerceIn(0f, 1f), r.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
}

@Composable
fun TransformInteractionOverlay(
    centerX: Float,
    centerY: Float,
    scale: Float,
    onGesture: (dxNormalized: Float, dyNormalized: Float, zoom: Float, rotationDelta: Float) -> Unit,
    onGestureEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val latestGesture by rememberUpdatedState(onGesture)
    val latestEnd by rememberUpdatedState(onGestureEnd)
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        val rotation = event.calculateRotation()
                        if (pan != Offset.Zero || zoom != 1f || rotation != 0f) {
                            val widthPx = size.width.toFloat().coerceAtLeast(1f)
                            val heightPx = size.height.toFloat().coerceAtLeast(1f)
                            latestGesture(
                                pan.x / widthPx,
                                pan.y / heightPx,
                                zoom.coerceIn(0.5f, 2f),
                                rotation
                            )
                            event.changes.forEach { if (it.pressed) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    latestEnd()
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
