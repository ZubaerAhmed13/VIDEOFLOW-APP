package com.videoflow.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * VideoFlow's native Android theme intentionally mirrors the visual language of
 * the original VideoFlow Professional HTML workstation while remaining 100%
 * native Jetpack Compose.
 *
 * The Android product stays dark regardless of the device theme because the
 * editing canvas, media bin and timeline depend on stable luminance/contrast.
 */
private val VideoFlowDarkColors = darkColorScheme(
    primary = Color(0xFF32D583),
    onPrimary = Color(0xFF07120C),
    primaryContainer = Color(0xFF173126),
    onPrimaryContainer = Color(0xFFE9FFF4),
    secondary = Color(0xFF64A7FF),
    onSecondary = Color(0xFF07111D),
    secondaryContainer = Color(0xFF18283B),
    onSecondaryContainer = Color(0xFFE6F0FF),
    tertiary = Color(0xFF8FE9BA),
    onTertiary = Color(0xFF06130C),
    background = Color(0xFF0A0D12),
    onBackground = Color(0xFFE8EDF4),
    surface = Color(0xFF11161E),
    onSurface = Color(0xFFE8EDF4),
    surfaceVariant = Color(0xFF171D27),
    onSurfaceVariant = Color(0xFFAEB8C4),
    surfaceContainerLowest = Color(0xFF070A0E),
    surfaceContainerLow = Color(0xFF0E131A),
    surfaceContainer = Color(0xFF11161E),
    surfaceContainerHigh = Color(0xFF171D27),
    surfaceContainerHighest = Color(0xFF1C2430),
    outline = Color(0xFF293340),
    outlineVariant = Color(0xFF242C36),
    error = Color(0xFFFF6467),
    onError = Color(0xFF190001),
    errorContainer = Color(0xFF3A1719),
    onErrorContainer = Color(0xFFFFDAD9),
    inverseSurface = Color(0xFFE8EDF4),
    inverseOnSurface = Color(0xFF11161E),
    inversePrimary = Color(0xFF167A4C),
    scrim = Color.Black,
)

private val VideoFlowTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5f).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.25.sp,
    ),
)

private val VideoFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(5.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

@Composable
fun VideoFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VideoFlowDarkColors,
        typography = VideoFlowTypography,
        shapes = VideoFlowShapes,
        content = content,
    )
}
