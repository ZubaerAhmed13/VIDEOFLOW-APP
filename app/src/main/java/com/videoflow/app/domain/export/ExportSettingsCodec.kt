package com.videoflow.app.domain.export

import com.videoflow.app.domain.editor.FrameRate

/** Small versioned JSON codec for the primitive export settings model. No UI/Android types. */
object ExportSettingsCodec {
    fun encode(value: ExportSettings): String = buildString {
        append('{')
        append("\"v\":1")
        append(",\"resolution\":\"").append(value.resolutionPreset.name).append('"')
        value.customWidth?.let { append(",\"customWidth\":").append(it) }
        value.customHeight?.let { append(",\"customHeight\":").append(it) }
        value.frameRate?.let {
            append(",\"fpsN\":").append(it.numerator)
            append(",\"fpsD\":").append(it.denominator)
        }
        append(",\"videoCodec\":\"").append(value.videoCodec.name).append('"')
        append(",\"quality\":\"").append(value.quality.name).append('"')
        append(",\"bitrateMode\":\"").append(value.bitrateMode.name).append('"')
        value.videoBitrateOverride?.let { append(",\"videoBitrate\":").append(it) }
        append(",\"audioCodec\":\"").append(value.audioCodec.name).append('"')
        append(",\"audioBitrate\":").append(value.audioBitrate)
        append(",\"audioSampleRate\":").append(value.audioSampleRate)
        append(",\"audioChannels\":").append(value.audioChannels)
        append(",\"hdrPolicy\":\"").append(value.hdrPolicy.name).append('"')
        append('}')
    }

    fun decode(json: String): ExportSettings {
        fun string(name: String): String? = Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(json)?.groupValues?.get(1)
        fun int(name: String): Int? = Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*(-?\\d+)")
            .find(json)?.groupValues?.get(1)?.toIntOrNull()

        require(int("v") == 1) { "Unsupported export settings version" }
        val fpsN = int("fpsN")
        val fpsD = int("fpsD")
        return ExportSettings(
            resolutionPreset = ExportResolutionPreset.valueOf(requireNotNull(string("resolution"))),
            customWidth = int("customWidth"),
            customHeight = int("customHeight"),
            frameRate = if (fpsN != null && fpsD != null) FrameRate(fpsN, fpsD) else null,
            videoCodec = VideoCodec.valueOf(requireNotNull(string("videoCodec"))),
            quality = ExportQuality.valueOf(requireNotNull(string("quality"))),
            bitrateMode = BitrateMode.valueOf(requireNotNull(string("bitrateMode"))),
            videoBitrateOverride = int("videoBitrate"),
            audioCodec = AudioCodec.valueOf(requireNotNull(string("audioCodec"))),
            audioBitrate = requireNotNull(int("audioBitrate")),
            audioSampleRate = requireNotNull(int("audioSampleRate")),
            audioChannels = requireNotNull(int("audioChannels")),
            hdrPolicy = HdrPolicy.valueOf(requireNotNull(string("hdrPolicy")))
        )
    }
}
