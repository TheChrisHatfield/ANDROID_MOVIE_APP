package com.torrentmovie.app.ui

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.torrentmovie.app.ui.theme.OnPantone
import com.torrentmovie.app.ui.theme.PantoneRed
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
fun SeedboxStatusChip(
    container: AppContainer,
    onOpenSettings: (() -> Unit)? = null,
) {
    var addReachable by remember { mutableStateOf(false) }
    var httprpcAvailable by remember { mutableStateOf(false) }
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
            addReachable = false
            httprpcAvailable = false
            return@LaunchedEffect
        }
        val probe = withContext(Dispatchers.IO) { container.seedboxRepository.probeSeedbox() }
        addReachable = probe.addReachable
        httprpcAvailable = probe.httprpcAvailable
    }
    AssistChip(
        onClick = {
            if (!configured || !addReachable) {
                onOpenSettings?.invoke()
            }
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = OnPantone.copy(alpha = 0.22f),
            labelColor = OnPantone,
        ),
        label = {
            Text(
                when {
                    !configured -> "Seedbox —"
                    addReachable && httprpcAvailable -> "Seedbox ●"
                    addReachable -> "Seedbox ◐"
                    else -> "Seedbox ○"
                },
            )
        },
    )
}
