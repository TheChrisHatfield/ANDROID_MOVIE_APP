package com.torrentmovie.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.torrentmovie.app.ui.MainScaffold
import com.torrentmovie.app.ui.theme.AppBranding
import com.torrentmovie.app.ui.theme.TorrentMovieTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MovieTorrentApplication).container
        val brandRed = Color.parseColor(AppBranding.PANTONE_RED_HEX)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(brandRed),
            navigationBarStyle = SystemBarStyle.light(
                Color.parseColor(AppBranding.CREAM_HEX),
                Color.parseColor(AppBranding.CREAM_HEX),
            ),
        )
        setContent {
            TorrentMovieTheme {
                MainScaffold(container = container)
            }
        }
    }
}
