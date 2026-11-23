package com.lefiru.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LefiruColors = darkColorScheme(
    primary = Color(0xFFB39DFF),
    onPrimary = Color(0xFF1B0F3A),
    primaryContainer = Color(0xFF3B2A6B),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = Color(0xFF7FD8C4),
    onSecondary = Color(0xFF04211B),
    secondaryContainer = Color(0xFF1D443C),
    onSecondaryContainer = Color(0xFFC2F3E7),
    background = Color(0xFF0E0B14),
    onBackground = Color(0xFFE9E4F2),
    surface = Color(0xFF141020),
    onSurface = Color(0xFFE9E4F2),
    surfaceVariant = Color(0xFF231D33),
    onSurfaceVariant = Color(0xFFB9B1CC),
    surfaceContainer = Color(0xFF191428),
    surfaceContainerHigh = Color(0xFF211A33),
    outline = Color(0xFF5B5273),
)

@Composable
fun LefiruTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LefiruColors,
        content = content,
    )
}
