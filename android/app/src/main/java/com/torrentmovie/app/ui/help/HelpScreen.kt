package com.torrentmovie.app.ui.help

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.torrentmovie.core.data.AppSettings

@Composable
fun HelpScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Legal")
        Text(
            "For content you have rights to access only. You supply your own seedbox and indexers.",
            modifier = Modifier.padding(top = 8.dp),
        )
        Text("Setup", modifier = Modifier.padding(top = 16.dp))
        Text("1. Run search API on your PC: see services/search/README.md")
        Text("2. Set Search API URL to ${AppSettings.DEFAULT_SEARCH_API} (emulator)")
        Text("3. Configure ruTorrent URL, credentials, and magnet download folder in Settings")
    }
}
