package com.videoflow.app.domain.export

import com.videoflow.app.domain.editor.FrameRate
import com.videoflow.app.domain.editor.PlanBuilder
import com.videoflow.app.domain.editor.ProjectSettings
import com.videoflow.app.domain.editor.TimelineState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Step3CertificationContractTest {
    @Test
    fun everyRequiredResolutionPresetResolvesExactly() {
        val project = ExportSize(1920, 1080)
        val expected = linkedMapOf(
            ExportResolutionPreset.P480 to ExportSize(854, 480),
            ExportResolutionPreset.P720 to ExportSize(1280, 720),
            ExportResolutionPreset.P1080 to ExportSize(1920, 1080),
            ExportResolutionPreset.P1440 to ExportSize(2560, 1440),
            ExportResolutionPreset.DCI_2K to ExportSize(2048, 1080),
            ExportResolutionPreset.UHD_4K to ExportSize(3840, 2160),
            ExportResolutionPreset.DCI_4K to ExportSize(4096, 2160)
        )
        expected.forEach { (preset, size) ->
            assertEquals(size, ExportMath.resolveSize(preset, project))
        }
    }

    @Test
    fun allRequiredFrameRatesRemainDistinctRationals() {
        val rates = listOf(
            FrameRate(24_000, 1_001),
            FrameRate.FPS_24,
            FrameRate.FPS_25,
            FrameRate.FPS_2997,
            FrameRate.FPS_30,
            FrameRate(50, 1),
            FrameRate.FPS_5994,
            FrameRate.FPS_60
        )
        assertEquals(8, rates.distinct().size)
        assertEquals(29.97002997002997, FrameRate.FPS_2997.fps, 1e-12)
        assertEquals(59.94005994005994, FrameRate.FPS_5994.fps, 1e-12)
    }

    @Test
    fun projectBackgroundIsPartOfImmutableFinalRenderPlan() {
        val background = 0xFF123456L
        val settings = ProjectSettings(
            projectId = "project",
            width = 1920,
            height = 1080,
            frameRate = FrameRate.FPS_30,
            backgroundArgb = background,
            createdAt = 1L,
            updatedAt = 2L
        )
        val render = PlanBuilder.render(
            settings,
            TimelineState(
                projectId = "project",
                tracks = emptyList(),
                clips = emptyList()
            )
        )
        assertEquals(background, render.backgroundArgb)
    }

    @Test
    fun multiHourHighBitrateEstimateStays64BitBeyondFourGiB() {
        val estimate = ExportMath.estimateOutputSize(
            durationUs = 8L * 60L * 60L * 1_000_000L,
            videoBitrate = 100_000_000,
            audioBitrate = 320_000
        )
        assertTrue(estimate.payloadBytes > 4L * 1024L * 1024L * 1024L)
        assertTrue(estimate.requiredBytes > estimate.payloadBytes)
    }

    @Test
    fun frameTimestampCalculationRemainsStableAfterEightHours() {
        val rate = FrameRate.FPS_2997
        val frameCount = ExportMath.frameCountForDuration(8L * 60L * 60L * 1_000_000L, rate)
        val timestamp = ExportMath.frameTimestampUs(frameCount, rate)
        val target = 8L * 60L * 60L * 1_000_000L
        assertTrue(timestamp >= target)
        assertTrue(timestamp - target < 40_000L)
    }
}
