package com.firmmy.dashcam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DashCamColorScheme = darkColorScheme(
    primary = Color(0xFFFFB693),
    onPrimary = Color(0xFF561F00),
    primaryContainer = Color(0xFFFF6B00),
    onPrimaryContainer = Color(0xFF572000),
    secondary = Color(0xFF98CBFF),
    onSecondary = Color(0xFF003354),
    secondaryContainer = Color(0xFF00A2FD),
    onSecondaryContainer = Color(0xFF003558),
    tertiary = Color(0xFF4AE183),
    onTertiary = Color(0xFF003919),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = Color(0xFF10141A),
    onBackground = Color(0xFFDFE2EB),
    surface = Color(0xFF10141A),
    onSurface = Color(0xFFDFE2EB),
    surfaceVariant = Color(0xFF31353C),
    onSurfaceVariant = Color(0xFFE2BFB0),
    outline = Color(0xFFA98A7D),
    outlineVariant = Color(0xFF5A4136),
)

@Composable
fun DashCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DashCamColorScheme,
        content = content,
    )
}
