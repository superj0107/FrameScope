package com.framescope.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun getAccentColor(colorIndex: Int): Color =
    PresetAccentColors.getOrElse(colorIndex) { PrimaryRed }

@Composable
fun FrameScopeTheme(
    colorIndex: Int = 0,
    content: @Composable () -> Unit
) {
    val accentColor = getAccentColor(colorIndex)
    val colorScheme = darkColorScheme(
        primary = accentColor,
        secondary = accentColor.copy(alpha = 0.8f),
        background = BackgroundDark,
        surface = SurfaceDark,
        onPrimary = BackgroundLight,
        onSecondary = BackgroundLight,
        onBackground = BackgroundLight,
        onSurface = BackgroundLight,
        surfaceVariant = SurfaceLight
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
