package com.giannisliu.melodyfitness.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6F50),
    onPrimary = Color(0xFFF8F4EA),
    primaryContainer = Color(0xFFCFE7D8),
    onPrimaryContainer = Color(0xFF16392B),
    secondary = Color(0xFFB36A2C),
    background = Color(0xFFF7F2E8),
    surface = Color(0xFFFFFBF4),
    surfaceVariant = Color(0xFFE7DECD),
    onSurface = Color(0xFF1F1A16),
    onSurfaceVariant = Color(0xFF5F574E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD5B4),
    secondary = Color(0xFFF0B171),
    background = Color(0xFF171411),
    surface = Color(0xFF221D19),
    surfaceVariant = Color(0xFF3D362F),
)

@Composable
fun MelodyFitnessTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
