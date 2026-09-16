package com.torrentmovie.app.ui.uploaded

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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

@Composable
fun UploadedScreen(container: AppContainer) {
    val items by container.uploadedRepository.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
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
                        "Local send history on this device.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
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
