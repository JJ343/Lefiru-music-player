package com.lefiru.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * GitHub / VS Code inspired dark palette: a near-black canvas, neutral graphite
 * surfaces and borders, and a blue accent. The teal secondary keeps a bit of
 * Lefiru's original identity (and powers the splash glow).
 */
private val LefiruColors = darkColorScheme(
    primary = Color(0xFF58A6FF),
    onPrimary = Color(0xFF0D1117),
    primaryContainer = Color(0xFF1F3A5F),
    onPrimaryContainer = Color(0xFFC9E3FF),
    secondary = Color(0xFF39D2C0),
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF0F3B36),
    onSecondaryContainer = Color(0xFFB7F5EC),
    tertiary = Color(0xFFC586C0),
    onTertiary = Color(0xFF1A0F1A),
    background = Color(0xFF010409),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    surfaceContainerLowest = Color(0xFF010409),
    surfaceContainerLow = Color(0xFF0D1117),
    surfaceContainer = Color(0xFF161B22),
    surfaceContainerHigh = Color(0xFF1C2128),
    surfaceContainerHighest = Color(0xFF21262D),
    outline = Color(0xFF30363D),
    outlineVariant = Color(0xFF21262D),
    error = Color(0xFFF85149),
    onError = Color(0xFF0D1117),
    errorContainer = Color(0xFF67060C),
    onErrorContainer = Color(0xFFFFDCD6),
)

@Composable
fun LefiruTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LefiruColors,
        content = content,
    )
}
