package com.lyse.mediaplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PlayerAccent,
    onPrimary = PlayerAccentDark,
    secondary = PlayerAccent,
    onSecondary = PlayerAccentDark,
    tertiary = Color(0xFF4D5F90),
    background = PlayerBackground,
    onBackground = PlayerText,
    surface = PlayerSurface,
    onSurface = PlayerText,
    surfaceVariant = PlayerSurfaceVariant,
    onSurfaceVariant = PlayerTextMuted,
    error = PlayerError,
    onError = Color.White,
    primaryContainer = Color(0xFFB1F0E3),
    onPrimaryContainer = Color(0xFF00201A),
    secondaryContainer = Color(0xFFB1F0E3),
    onSecondaryContainer = Color(0xFF00201A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun MediaPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
