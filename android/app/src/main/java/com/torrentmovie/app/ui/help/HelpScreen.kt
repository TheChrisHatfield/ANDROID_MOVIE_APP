package com.torrentmovie.app.ui.help

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.torrentmovie.app.ui.adaptive.ResponsiveContent

@Composable
fun HelpScreen() {
    ResponsiveContent {
        Text("Legal")
        Text(
            "For content you have rights to access only. You supply your own seedbox and indexers.",
            modifier = Modifier.padding(top = 8.dp),
        )
        Text("Setup", modifier = Modifier.padding(top = 16.dp))
        Text("1. Search runs on this phone — needs internet (Wi-Fi or cellular), not a PC Search API")
        Text("2. Optional TMDB API key in Settings for posters, trailers, and title suggestions (stock builds already include a key)")
        Text("3. Configure ruTorrent URL, credentials, movie folder, and TV-show magnet folder in Settings")
        Text("4. Settings → Show: Movies (default), TV shows, or All — chips save immediately; then search or open Top TV")
    }
}
