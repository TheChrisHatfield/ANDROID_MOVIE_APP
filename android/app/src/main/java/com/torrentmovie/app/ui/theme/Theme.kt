package com.torrentmovie.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MissyColors = lightColorScheme(
    primary = InkOnRed,
    onPrimary = Cream,
    primaryContainer = Cream,
    onPrimaryContainer = InkOnRed,
    secondary = Cream,
    onSecondary = InkOnRed,
    secondaryContainer = Cream,
    onSecondaryContainer = InkOnRed,
    tertiary = Cream,
    onTertiary = InkOnRed,
    background = PantoneRed,
    onBackground = InkOnRed,
    surface = PantoneRed,
    onSurface = InkOnRed,
    surfaceVariant = Cream,
    onSurfaceVariant = InkOnRed,
    error = InkOnRed,
    onError = Cream,
    outline = InkOnRed,
)

@Composable
fun TorrentMovieTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MissyColors, content = content)
}
