package com.torrentmovie.app.ui

import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.torrentmovie.core.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SeedboxStatusChip(container: AppContainer) {
    var online by remember { mutableStateOf(false) }
    val settingsRevision by container.settingsRepository.revision.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val configured = container.settingsRepository.isSeedboxConfigured()

    LaunchedEffect(settingsRevision, resumeTick, configured) {
        if (!configured) {
            online = false
            return@LaunchedEffect
        }
        online = withContext(Dispatchers.IO) { container.seedboxRepository.pingSeedbox() }
    }
    AssistChip(
        onClick = {},
        label = {
            Text(
                when {
                    !configured -> "Seedbox —"
                    online -> "Seedbox ●"
                    else -> "Seedbox ○"
                },
            )
        },
    )
}
