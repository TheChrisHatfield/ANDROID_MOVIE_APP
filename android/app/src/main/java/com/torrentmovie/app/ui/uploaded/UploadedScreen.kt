package com.torrentmovie.app.ui.uploaded

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.torrentmovie.core.data.AppContainer
import kotlinx.coroutines.Dispatchers
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
    val pullState = rememberPullRefreshState(
        refreshing = state.refreshing,
        onRefresh = { vm.refreshStatuses() },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullState),
    ) {
        if (state.rows.isEmpty()) {
            Text(
                "No uploads yet — search and send your first movie.",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                    UploadedRow(
                        row = row,
                        onDelete = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    container.uploadedRepository.delete(row.entry.infoHash)
                                    container.seedboxRepository.clearSentWithoutPersist(row.entry.infoHash)
                                }
                                vm.refreshStatuses()
                            }
                        },
                    )
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

@Composable
private fun UploadedRow(row: UploadedRowUi, onDelete: () -> Unit) {
    ListItem(
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
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        },
    )
}
