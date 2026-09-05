package com.videoflow.app.domain.export

import com.videoflow.app.domain.editor.FrameRate
import com.videoflow.app.render.EncoderCapability
import com.videoflow.app.render.EncoderCapabilitySource
import com.videoflow.app.render.ExportCapabilityValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportCapabilityValidatorTest {
    @Test
    fun unsupported4k60IsExplicitProblemNotSilentDowngrade() {
        val source = FakeSource(emptyList())
        val resolved = ExportMath.resolve(
            ExportSize(3840, 2160),
            FrameRate.FPS_60,
            ExportSettings(resolutionPreset = ExportResolutionPreset.UHD_4K, frameRate = FrameRate.FPS_60)
        )
        val result = ExportCapabilityValidator.validate(resolved, sourceHasHdr = false, source)
        assertFalse(result.ready)
        assertTrue(result.problems.any { it.code == ExportFailureCode.UNSUPPORTED_RESOLUTION })
        assertEquals(3840, resolved.size.width)
        assertEquals(FrameRate.FPS_60, resolved.frameRate)
    }

    @Test
    fun hdrCannotSilentlyFallBackTo8BitH264() {
        val capability = capability(mime = VideoCodec.H264.mimeType, main10 = false)
        val resolved = ExportMath.resolve(
            ExportSize(3840, 2160), FrameRate.FPS_30,
            ExportSettings(videoCodec = VideoCodec.H264, hdrPolicy = HdrPolicy.PRESERVE_WHEN_COMPATIBLE)
        )
        val result = ExportCapabilityValidator.validate(resolved, sourceHasHdr = true, FakeSource(listOf(capability)))
        assertFalse(result.ready)
        assertTrue(result.problems.any { it.code == ExportFailureCode.UNSUPPORTED_HDR })
    }

    @Test
    fun main10HevcMayPreserveHdr() {
        val capability = capability(mime = VideoCodec.HEVC.mimeType, main10 = true)
        val resolved = ExportMath.resolve(
            ExportSize(3840, 2160), FrameRate.FPS_30,
            ExportSettings(videoCodec = VideoCodec.HEVC, hdrPolicy = HdrPolicy.REQUIRE_PRESERVE)
        )
        val result = ExportCapabilityValidator.validate(resolved, sourceHasHdr = true, FakeSource(listOf(capability)))
        assertTrue(result.ready)
    }

    @Test
    fun bitrateModeFallbackPrefersCqThenVbrThenCbr() {
        val c = capability(mime = VideoCodec.H264.mimeType, main10 = false, cq = false, vbr = true, cbr = true)
        assertEquals(BitrateMode.VBR, ExportCapabilityValidator.bestSupportedBitrateMode(c, BitrateMode.CQ))
    }

    private fun capability(
        mime: String,
        main10: Boolean,
        cq: Boolean = true,
        vbr: Boolean = true,
        cbr: Boolean = true
    ) = EncoderCapability(
        name = "codec",
        mimeType = mime,
        hardwareAccelerated = true,
        width = 3840,
        height = 2160,
        fps = 30.0,
        bitrateRange = 1_000_000L..120_000_000L,
        supportsCq = cq,
        supportsVbr = vbr,
        supportsCbr = cbr,
        supportsMain10 = main10
    )

    private class FakeSource(private val values: List<EncoderCapability>) : EncoderCapabilitySource {
        override fun encodersFor(mimeType: String, width: Int, height: Int, fps: Double): List<EncoderCapability> =
            values.filter { it.mimeType == mimeType }
    }
}
