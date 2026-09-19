package com.torrentmovie.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MissyColors = lightColorScheme(
    primary = OnPantone,
    onPrimary = PantoneRed,
    primaryContainer = PantoneRedDark,
    onPrimaryContainer = OnPantone,
    secondary = Cream,
    onSecondary = PantoneRed,
    secondaryContainer = Cream,
    onSecondaryContainer = InkOnRed,
    tertiary = Cream,
    onTertiary = PantoneRed,
    background = PantoneRed,
    onBackground = OnPantone,
    surface = PantoneRed,
    onSurface = OnPantone,
    surfaceVariant = PantoneRedDark,
    onSurfaceVariant = OnPantone,
    error = WarningOnRed,
    onError = InkOnRed,
    outline = Cream,
)

@Composable
fun TorrentMovieTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MissyColors, content = content)
}
