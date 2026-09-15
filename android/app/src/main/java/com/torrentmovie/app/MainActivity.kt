package com.torrentmovie.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.torrentmovie.app.ui.MainScaffold
import com.torrentmovie.app.ui.theme.TorrentMovieTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MovieTorrentApplication).container
        enableEdgeToEdge()
        setContent {
            TorrentMovieTheme {
                MainScaffold(container = container)
            }
        }
    }
}
