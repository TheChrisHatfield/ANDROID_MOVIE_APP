package com.torrentmovie.app.ui

import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.torrentmovie.core.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SeedboxStatusChip(container: AppContainer) {
    var online by remember { mutableStateOf(false) }
    val settings = container.settingsRepository.load()
    val settingsKey = listOf(
        settings.rutorrentBaseUrl,
        settings.username,
        settings.password,
        settings.authScheme,
    ).joinToString("|")
    LaunchedEffect(settingsKey) {
        if (!container.settingsRepository.isSeedboxConfigured()) {
            online = false
            return@LaunchedEffect
        }
        online = withContext(Dispatchers.IO) { container.seedboxRepository.pingSeedbox() }
    }
    AssistChip(
        onClick = {},
        label = { Text(if (online) "Seedbox ●" else "Seedbox ○") },
    )
}
