package com.lyse.mediaplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PlayerAccent,
    onPrimary = PlayerAccentDark,
    secondary = PlayerAccent,
    onSecondary = PlayerAccentDark,
    tertiary = Color(0xFFB7C9FF),
    background = PlayerBackground,
    onBackground = PlayerText,
    surface = PlayerSurface,
    onSurface = PlayerText,
    surfaceVariant = PlayerSurfaceVariant,
    onSurfaceVariant = PlayerTextMuted,
    error = PlayerError,
    onError = Color(0xFF690005)
)

@Composable
fun MediaPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
