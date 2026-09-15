package com.torrentmovie.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.torrentmovie.core.network.TorrentResultDto

@Composable
fun TorrentResultCard(
    result: TorrentResultDto,
    alreadyUploaded: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(result.name, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.padding(top = 4.dp)) {
                Text("${result.site} · ${result.size ?: "?"} · seeds ${result.seeds ?: "?"}")
            }
            if (alreadyUploaded) {
                Text("Already uploaded", color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}
