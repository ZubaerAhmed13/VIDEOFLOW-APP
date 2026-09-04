package com.videoflow.app.domain.export

import com.videoflow.app.domain.editor.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportMathTest {
    @Test
    fun presetsResolveToProfessionalDimensions() {
        val project = ExportSize(1920, 1080)
        assertEquals(ExportSize(854, 480), ExportMath.resolveSize(ExportResolutionPreset.P480, project))
        assertEquals(ExportSize(1280, 720), ExportMath.resolveSize(ExportResolutionPreset.P720, project))
        assertEquals(ExportSize(1920, 1080), ExportMath.resolveSize(ExportResolutionPreset.P1080, project))
        assertEquals(ExportSize(2560, 1440), ExportMath.resolveSize(ExportResolutionPreset.P1440, project))
        assertEquals(ExportSize(2048, 1080), ExportMath.resolveSize(ExportResolutionPreset.DCI_2K, project))
        assertEquals(ExportSize(3840, 2160), ExportMath.resolveSize(ExportResolutionPreset.UHD_4K, project))
        assertEquals(ExportSize(4096, 2160), ExportMath.resolveSize(ExportResolutionPreset.DCI_4K, project))
    }

    @Test
    fun customDimensionsAreMadeEncoderSafeWithoutGrowing() {
        assertEquals(
            ExportSize(1918, 1078),
            ExportMath.resolveSize(ExportResolutionPreset.CUSTOM, ExportSize(1920, 1080), 1919, 1079)
        )
    }

    @Test
    fun rational2997FrameTimestampsDoNotRoundTo30() {
        val rate = FrameRate.FPS_2997
        assertEquals(0L, ExportMath.frameTimestampUs(0, rate))
        assertEquals(33_366L, ExportMath.frameTimestampUs(1, rate))
        assertEquals(1_001_000L, ExportMath.frameTimestampUs(30, rate))
        assertEquals(10_010_000L, ExportMath.frameTimestampUs(300, rate))
    }

    @Test
    fun rational5994LongTimelineDoesNotOverflowOrDriftByAccumulation() {
        val sixHoursFrames = ExportMath.frameCountForDuration(6L * 60 * 60 * 1_000_000, FrameRate.FPS_5994)
        val last = ExportMath.frameTimestampUs(sixHoursFrames, FrameRate.FPS_5994)
        assertTrue(sixHoursFrames > 1_000_000)
        assertTrue(last >= 6L * 60 * 60 * 1_000_000)
        assertTrue(last - 6L * 60 * 60 * 1_000_000 < 20_000)
    }

    @Test
    fun highQualityBitrateScalesWithResolutionFpsAndCodec() {
        val h264_1080 = ExportMath.selectVideoBitrate(ExportSize(1920, 1080), FrameRate.FPS_30, VideoCodec.H264, ExportQuality.HIGH)
        val h264_4k = ExportMath.selectVideoBitrate(ExportSize(3840, 2160), FrameRate.FPS_30, VideoCodec.H264, ExportQuality.HIGH)
        val hevc_4k = ExportMath.selectVideoBitrate(ExportSize(3840, 2160), FrameRate.FPS_30, VideoCodec.HEVC, ExportQuality.HIGH)
        assertTrue(h264_1080 in 10_000_000..16_000_000)
        assertTrue(h264_4k > h264_1080)
        assertTrue(hevc_4k < h264_4k)
    }

    @Test
    fun outputEstimateUses64BitAndSafetyMargin() {
        val estimate = ExportMath.estimateOutputSize(
            durationUs = 4L * 60 * 60 * 1_000_000,
            videoBitrate = 60_000_000,
            audioBitrate = 256_000
        )
        assertTrue(estimate.payloadBytes > 100_000_000_000L)
        assertTrue(estimate.requiredBytes > estimate.payloadBytes)
        assertEquals(0.15, estimate.safetyMarginFraction, 0.0)
    }

    @Test
    fun resolveUsesProjectFpsAndWarnableUpscaleIdentity() {
        val project = ExportSize(1920, 1080)
        val source = ExportMath.resolve(project, FrameRate.FPS_2997, ExportSettings())
        assertEquals(FrameRate.FPS_2997, source.frameRate)
        assertFalse(source.isUpscale)

        val upscaled = ExportMath.resolve(
            project,
            FrameRate.FPS_30,
            ExportSettings(resolutionPreset = ExportResolutionPreset.UHD_4K)
        )
        assertTrue(upscaled.isUpscale)
    }
}
