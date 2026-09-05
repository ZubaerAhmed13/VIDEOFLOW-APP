package com.videoflow.app.domain.export

import com.videoflow.app.domain.editor.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportSettingsCodecTest {
    @Test
    fun settingsRoundTripIsDeterministic() {
        val settings = ExportSettings(
            resolutionPreset = ExportResolutionPreset.UHD_4K,
            frameRate = FrameRate.FPS_2997,
            videoCodec = VideoCodec.HEVC,
            quality = ExportQuality.MAXIMUM,
            bitrateMode = BitrateMode.VBR,
            videoBitrateOverride = 50_000_000,
            audioBitrate = 320_000,
            hdrPolicy = HdrPolicy.REQUIRE_PRESERVE
        )
        val encoded = ExportSettingsCodec.encode(settings)
        assertEquals(settings, ExportSettingsCodec.decode(encoded))
        assertEquals(encoded, ExportSettingsCodec.encode(ExportSettingsCodec.decode(encoded)))
    }
}
