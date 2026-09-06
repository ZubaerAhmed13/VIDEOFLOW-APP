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
    secondary = Color(0xFF54657A),
    surface = Color(0xFFF8F9FC),
    surfaceContainer = Color(0xFFF0F2F7),
    background = Color(0xFFFDFDFF),
    error = VideoFlowDesignTokens.Error
)

private val DarkColors = darkColorScheme(
    primary = VideoFlowDesignTokens.BrandPrimaryDark,
    secondary = Color(0xFFAAB8CE),
    surface = Color(0xFF17191E),
    surfaceContainer = Color(0xFF20232A),
    background = Color(0xFF101216),
    error = Color(0xFFFFB4AB)
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
