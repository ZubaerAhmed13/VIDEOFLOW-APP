package com.videoflow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.videoflow.app.ui.product.AppAppearance

object VideoFlowDesignTokens {
    val BrandPrimary = Color(0xFF4263EB)
    val BrandPrimaryDark = Color(0xFF8EA6FF)
    val Warning = Color(0xFFF0B429)
    val Success = Color(0xFF2E9D67)
    val Error = Color(0xFFC74747)
}

private val LightColors = lightColorScheme(
    primary = VideoFlowDesignTokens.BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE4FF),
    onPrimaryContainer = Color(0xFF10245F),
    secondary = Color(0xFF54657A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE7F7),
    onSecondaryContainer = Color(0xFF1A2A3A),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF181B20),
    surfaceVariant = Color(0xFFE3E6EC),
    onSurfaceVariant = Color(0xFF444A53),
    surfaceContainer = Color(0xFFF0F2F7),
    background = Color(0xFFFDFDFF),
    onBackground = Color(0xFF181B20),
    outline = Color(0xFF747A84),
    outlineVariant = Color(0xFFC4C8D0),
    error = VideoFlowDesignTokens.Error,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = VideoFlowDesignTokens.BrandPrimaryDark,
    onPrimary = Color(0xFF0E214F),
    primaryContainer = Color(0xFF24396F),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = Color(0xFFAAB8CE),
    onSecondary = Color(0xFF182535),
    secondaryContainer = Color(0xFF2D3B4D),
    onSecondaryContainer = Color(0xFFDCE7F6),
    surface = Color(0xFF17191E),
    onSurface = Color(0xFFF4F7FA),
    surfaceVariant = Color(0xFF2B3037),
    onSurfaceVariant = Color(0xFFC7D0DA),
    surfaceContainer = Color(0xFF20232A),
    background = Color(0xFF101216),
    onBackground = Color(0xFFF4F7FA),
    outline = Color(0xFF929AA5),
    outlineVariant = Color(0xFF454B54),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun VideoFlowTheme(
    appearance: AppAppearance = AppAppearance.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (appearance) {
        AppAppearance.SYSTEM -> isSystemInDarkTheme()
        AppAppearance.LIGHT -> false
        AppAppearance.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
