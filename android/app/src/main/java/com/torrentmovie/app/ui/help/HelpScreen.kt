package com.torrentmovie.app.ui.help

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.torrentmovie.app.ui.adaptive.ResponsiveContent
import com.torrentmovie.core.data.AppSettings

@Composable
fun HelpScreen() {
    ResponsiveContent {
        Text("Legal")
        Text(
            "For content you have rights to access only. You supply your own seedbox and indexers.",
            modifier = Modifier.padding(top = 8.dp),
        )
        Text("Setup", modifier = Modifier.padding(top = 16.dp))
        Text("1. Run search API on your PC: see services/search/README.md")
        Text("2. Set Search API URL to ${AppSettings.EMULATOR_SEARCH_API} (emulator) or http://<LAN-IP>:8765 (phone/tablet)")
        Text("3. Optional TMDB API key in Settings for posters and trailers")
        Text("4. Configure ruTorrent URL, credentials, and magnet download folder in Settings")
    }
}
