package com.torrentmovie.app.ui.uploaded

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.widthIn
import com.torrentmovie.app.ui.adaptive.AdaptiveLayout
import com.torrentmovie.core.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun UploadedScreen(
    container: AppContainer,
    sharedViewModel: UploadedViewModel? = null,
) {
    val vm: UploadedViewModel = sharedViewModel ?: viewModel { UploadedViewModel(container) }
    val state by vm.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val pullState = rememberPullRefreshState(
        refreshing = state.refreshing,
        onRefresh = { vm.refreshStatuses() },
    )
    val listState = rememberLazyListState()
    val highlightSeq by container.uploadedHighlightSeq.collectAsState()
    var highlightKey by remember { mutableStateOf(container.pendingUploadedHighlight) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            vm.setScreenVisible(true)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    vm.setScreenVisible(true)
                }
                Lifecycle.Event.ON_PAUSE -> vm.setScreenVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.setScreenVisible(false)
        }
    }

    LaunchedEffect(highlightSeq) {
        if (highlightSeq == 0L) return@LaunchedEffect
        container.pendingUploadedHighlight?.let { highlightKey = it }
    }

    LaunchedEffect(highlightKey, state.rows) {
        val key = highlightKey ?: return@LaunchedEffect
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val index = state.rows.indexOfFirst { it.entry.infoHash.equals(key, ignoreCase = true) }
            if (index >= 0) {
                listState.animateScrollToItem(index + 1)
                delay(2500)
                highlightKey = null
                container.pendingUploadedHighlight = null
                return@LaunchedEffect
            }
            delay(250)
        }
        highlightKey = null
        container.pendingUploadedHighlight = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullState),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = AdaptiveLayout.CONTENT_MAX_WIDTH_DP.dp),
            ) {
            if (state.rows.isEmpty()) {
                Text(
                    "No uploads yet — search and send your first movie.",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                ) {
                    item {
                        Text(
                            if (state.seedboxConfigured) {
                                "Sent from this device · live status from ruTorrent"
                            } else {
                                "Sent from this device · configure seedbox for live status"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        state.statusError?.let { error ->
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                    items(state.rows, key = { it.entry.infoHash }) { row ->
                        val highlighted = highlightKey != null &&
                            row.entry.infoHash.equals(highlightKey, ignoreCase = true)
                        UploadedRow(
                            row = row,
                            highlighted = highlighted,
                            onRemove = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        container.uploadedRepository.delete(row.entry.infoHash)
                                        container.seedboxRepository.clearSentWithoutPersist(row.entry.infoHash)
                                    }
                                    vm.refreshStatuses()
                                    snackbar.showSnackbar("Removed from list · torrent remains on seedbox")
                                }
                            },
                        )
                    }
                }
            }
            }
            PullRefreshIndicator(
                refreshing = state.refreshing,
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun UploadedRow(
    row: UploadedRowUi,
    highlighted: Boolean = false,
    onRemove: () -> Unit,
) {
    ListItem(
        modifier = if (highlighted) {
            Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
        } else {
            Modifier
        },
        headlineContent = { Text(row.entry.displayName) },
        supportingContent = {
            Column {
                Text(
                    "${row.entry.site} · ${DateFormat.getDateTimeInstance().format(Date(row.entry.sentAt))}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    row.statusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (row.showProgress) {
                    LinearProgressIndicator(
                        progress = { row.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove from list")
            }
        },
    )
}
