package com.videoflow.app.domain.effects

enum class VisualEffectType(val label: String, val category: String) {
    BLUR("Blur", "Basic"), VIGNETTE("Vignette", "Basic"), SHARPEN("Sharpen", "Basic"),
    FADE("Fade", "Film"), MONOCHROME("Monochrome", "Film"), SEPIA("Sepia", "Film"),
    GRAIN("Film grain", "Film"), RGB_SPLIT("RGB split", "Glitch"), GLITCH("Glitch", "Glitch"),
    VHS("VHS", "Retro"), CHROMATIC("Chromatic aberration", "Retro"),
    PULSE("Pulse", "Light"), FLICKER("Flicker", "Light"),
    ZOOM_PULSE("Zoom pulse", "Motion"), SHAKE("Shake", "Motion"), VIBRATION("Camera vibration", "Motion")
}

/** Time is clip-local timeline microseconds; ranges are half open. */
data class VideoEffectNode(
    val id: String, val clipId: String, val type: VisualEffectType,
    val startUs: Long, val endUs: Long, val intensity: Float = 0.5f,
    val enabled: Boolean = true, val order: Int = 0
) {
    init { require(id.isNotBlank() && clipId.isNotBlank()); require(startUs >= 0 && endUs > startUs); require(intensity in 0f..1f) }
    fun activeAt(timeUs: Long) = enabled && intensity > 0f && timeUs >= startUs && timeUs < endUs
}

enum class Adjustment(val label: String) {
    EXPOSURE("Exposure"), BRIGHTNESS("Brightness"), CONTRAST("Contrast"), HIGHLIGHTS("Highlights"),
    SHADOWS("Shadows"), SATURATION("Saturation"), TEMPERATURE("Temperature"), TINT("Tint"),
    SHARPEN("Sharpen"), CLARITY("Clarity"), VIGNETTE("Vignette")
}

/** Zero is an identity operation. Values are normalized, with exposure spanning +/-2 stops. */
data class EnhanceParameters(val values: Map<Adjustment, Float> = emptyMap()) {
    init { require(values.all { (_, v) -> v.isFinite() && v in -1f..1f }) }
    operator fun get(key: Adjustment) = values[key] ?: 0f
    fun with(key: Adjustment, value: Float) = EnhanceParameters(values + (key to value))
    val isIdentity get() = values.values.all { it == 0f }
}

data class VisualEdits(
    val effects: List<VideoEffectNode> = emptyList(),
    val enhance: Map<String, EnhanceParameters> = emptyMap()
) {
    init { require(effects.map { it.id }.distinct().size == effects.size) }
    fun ordered(clipId: String) = effects.filter { it.clipId == clipId && it.enabled && it.intensity > 0f }
        .sortedWith(compareBy<VideoEffectNode> { it.order }.thenBy { it.id })
    fun changesPixels(clipIds: Set<String>) = effects.any { it.clipId in clipIds && it.enabled && it.intensity > 0f } ||
        enhance.any { (id, values) -> id in clipIds && !values.isIdentity }
}

/** Bounded statistics from representative frames; recommendations stay editable and conservative. */
object AutoEnhanceRecommendation {
    fun fromLuminance(mean: Double, darkFraction: Double, brightFraction: Double): EnhanceParameters {
        require(listOf(mean, darkFraction, brightFraction).all { it.isFinite() && it in 0.0..1.0 })
        return EnhanceParameters(mapOf(
            Adjustment.EXPOSURE to ((0.42 - mean) * 0.45).coerceIn(-0.12, 0.12).toFloat(),
            Adjustment.SHADOWS to (darkFraction * 0.3).coerceIn(0.0, 0.15).toFloat(),
            Adjustment.HIGHLIGHTS to (-brightFraction * 0.3).coerceIn(-0.15, 0.0).toFloat()
        ))
    }
}
