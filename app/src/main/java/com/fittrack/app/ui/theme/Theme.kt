package com.fittrack.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.fittrack.app.data.preferences.DEFAULT_THEME_HUE_DEGREES
import com.fittrack.app.util.normalizeHueDegrees

private fun hsv(hue: Float, saturation: Float, value: Float): Color =
    Color.hsv(normalizeHueDegrees(hue), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))

private fun darkColorSchemeForHue(hue: Float) = darkColorScheme(
    primary = hsv(hue, 0.74f, 0.96f),
    onPrimary = Color.Black,
    primaryContainer = hsv(hue, 0.56f, 0.38f),
    onPrimaryContainer = Color.White,
    secondary = hsv(hue + 22f, 0.62f, 0.88f),
    onSecondary = Color.Black,
    secondaryContainer = hsv(hue + 22f, 0.45f, 0.33f),
    onSecondaryContainer = Color.White,
    tertiary = hsv(hue + 48f, 0.48f, 0.84f),
    onTertiary = Color.Black,
    tertiaryContainer = hsv(hue + 48f, 0.36f, 0.31f),
    onTertiaryContainer = Color.White,
    background = hsv(hue, 0.25f, 0.17f),
    onBackground = Color.White,
    surface = hsv(hue + 8f, 0.22f, 0.23f),
    onSurface = Color.White,
    surfaceVariant = hsv(hue + 12f, 0.20f, 0.30f),
    onSurfaceVariant = Color(0xFFE9E1E8)
)

@Composable
fun FitTrackTheme(
    primaryHueDegrees: Float = DEFAULT_THEME_HUE_DEGREES,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = darkColorSchemeForHue(primaryHueDegrees),
        content = content
    )
}
