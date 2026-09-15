package com.torrentmovie.app.ui.uploaded

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.db.UploadedMagnet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun UploadedScreen(container: AppContainer) {
    val items by container.uploadedRepository.observeAll().collectAsState(initial = emptyList())
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pullState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                withContext(Dispatchers.IO) { container.uploadedRepository.list() }
                refreshing = false
            }
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullState),
    ) {
        if (items.isEmpty()) {
            Text(
                "No uploads yet — search and send your first movie.",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.infoHash }) { entry ->
                    UploadedRow(
                        entry = entry,
                        onDelete = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    container.uploadedRepository.delete(entry.infoHash)
                                    container.seedboxRepository.clearSentWithoutPersist(entry.infoHash)
                                }
                            }
                        },
                    )
                }
            }
        }
        PullRefreshIndicator(
            refreshing = refreshing,
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun UploadedRow(entry: UploadedMagnet, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(entry.displayName) },
        supportingContent = {
            Text(
                "${entry.site} · ${DateFormat.getDateTimeInstance().format(Date(entry.sentAt))}",
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        },
    )
}
