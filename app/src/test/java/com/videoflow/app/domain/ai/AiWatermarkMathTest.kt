package com.videoflow.app.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWatermarkMathTest {
    @Test
    fun roiInterpolation_preservesSizeAndInterpolatesPosition() {
        val effect = AiWatermarkEffect(
            id = "e", projectId = "p", clipId = "c",
            clipLocalStartUs = 0, clipLocalEndUs = 10_000_000,
            roi = NormalizedRoi(0.1f, 0.2f, 0.3f, 0.4f),
            motionAnchors = listOf(
                RoiMotionAnchor(0, 0.2f, 0.3f),
                RoiMotionAnchor(10_000_000, 0.8f, 0.7f)
            )
        )
        val middle = effect.roiAt(5_000_000)
        assertEquals(0.2f, middle.width, 0.0001f)
        assertEquals(0.2f, middle.height, 0.0001f)
        assertEquals(0.5f, (middle.left + middle.right) / 2f, 0.0001f)
        assertEquals(0.5f, (middle.top + middle.bottom) / 2f, 0.0001f)
    }

    @Test
    fun movingRoiFeather_usesCurrentFrameTargetNotStartTarget() {
        val effect = AiWatermarkEffect(
            id = "moving", projectId = "p", clipId = "c",
            clipLocalStartUs = 0L, clipLocalEndUs = 2_000_000L,
            roi = NormalizedRoi(0.10f, 0.20f, 0.30f, 0.40f),
            motionAnchors = listOf(
                RoiMotionAnchor(0L, 0.20f, 0.30f),
                RoiMotionAnchor(1_500_000L, 0.75f, 0.65f)
            ),
            featherPx = 4
        )
        val startTarget = AiWatermarkMath.toPixelRect(effect.roiAt(0L), 100, 100)
        val movedTarget = AiWatermarkMath.toPixelRect(effect.roiAt(1_500_000L), 100, 100)
        val movedCenterX = (movedTarget.left + movedTarget.right) / 2
        val movedCenterY = (movedTarget.top + movedTarget.bottom) / 2

        assertEquals(0f, AiWatermarkMath.featherWeight(movedCenterX, movedCenterY, startTarget, 4), 0.0001f)
        assertEquals(1f, AiWatermarkMath.featherWeight(movedCenterX, movedCenterY, movedTarget, 4), 0.0001f)
        assertEquals(0f, AiWatermarkMath.featherWeight(movedTarget.left, movedCenterY, movedTarget, 4), 0.0001f)
    }

    @Test
    fun pixelAndOpenGlMapping_flipsOnlyVerticalAxis() {
        val pixel = AiWatermarkMath.toPixelRect(NormalizedRoi(0.25f, 0.25f, 0.75f, 0.75f), 1920, 1080)
        assertEquals(PixelRect(480, 270, 1440, 810), pixel)
        assertEquals(PixelRect(480, 270, 1440, 810), AiWatermarkMath.toOpenGlRect(pixel, 1080))

        val top = AiWatermarkMath.toPixelRect(NormalizedRoi(0f, 0f, 0.5f, 0.25f), 100, 100)
        assertEquals(PixelRect(0, 75, 50, 100), AiWatermarkMath.toOpenGlRect(top, 100))
    }

    @Test
    fun fourKLargeRoi_isPartitionedIntoBoundedOriginalResolutionTiles() {
        val target = PixelRect(240, 160, 3600, 2000)
        val tiles = AiWatermarkMath.planTiles(target, 3840, 2160, modelSize = 512, contextPx = 48)
        assertTrue(tiles.size > 1)
        assertTrue(tiles.all { it.read.width <= 512 && it.read.height <= 512 })
        assertTrue(tiles.all { it.core.left >= target.left && it.core.right <= target.right })

        val coveredArea = tiles.sumOf { it.core.width.toLong() * it.core.height }
        assertEquals(target.width.toLong() * target.height, coveredArea)
    }

    @Test
    fun eightKFullFrame_doesNotCreateWholeFrameInferenceBuffer() {
        val target = PixelRect(0, 0, 7680, 4320)
        val tiles = AiWatermarkMath.planTiles(target, 7680, 4320)
        assertTrue(tiles.size > 100)
        assertTrue(tiles.maxOf { it.read.width } <= 512)
        assertTrue(tiles.maxOf { it.read.height } <= 512)
        assertTrue(AiWatermarkMath.estimatedWorkingSetBytes() < 16L * 1024 * 1024)
    }

    @Test
    fun catalogMatchesChecksumPinnedDualLamaPack() {
        assertEquals(62_074_990L, AiModelCatalog.FINAL_512.expectedBytes)
        assertEquals("cab19978adc306622fe37ef60d4a52103b99c98141d499c2a2366a7ed1255dbe", AiModelCatalog.FINAL_512.sha256)
        assertEquals(512, AiModelCatalog.FINAL_512.inferenceSize)
        assertEquals(256, AiModelCatalog.PREVIEW_DYNAMIC.inferenceSize)
    }
}
