package com.torrentmovie.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MissyColors = lightColorScheme(
    primary = PantoneRed,
    onPrimary = OnPantone,
    primaryContainer = CardSurface,
    onPrimaryContainer = TextCharcoal,
    secondary = PantoneRedDark,
    onSecondary = OnPantone,
    secondaryContainer = SearchFieldGray,
    onSecondaryContainer = TextCharcoal,
    tertiary = PantoneRed,
    onTertiary = OnPantone,
    background = SurfaceWhite,
    onBackground = TextCharcoal,
    surface = SurfaceWhite,
    onSurface = TextCharcoal,
    surfaceVariant = SearchFieldGray,
    onSurfaceVariant = TextMuted,
    error = PantoneRedDark,
    onError = OnPantone,
    outline = DividerGray,
)

@Composable
fun TorrentMovieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MissyColors,
        shapes = MissyShapes,
        content = content,
    )
}
