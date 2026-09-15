package com.torrentmovie.app.ui.fold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.torrentmovie.app.ui.detail.TorrentDetailScreen
import com.torrentmovie.app.ui.search.SearchScreen
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.network.TorrentResultDto

@Composable
fun FoldSearchDetailLayout(container: AppContainer) {
    var selected by remember { mutableStateOf<TorrentResultDto?>(null) }

    Row(Modifier.fillMaxSize()) {
        SearchScreen(
            container = container,
            onOpenDetail = { result ->
                container.searchResultStore.put(result)
                selected = result
            },
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight(),
        )
        VerticalDivider(modifier = Modifier.fillMaxHeight())
        Box(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight(),
        ) {
            val result = selected
            if (result == null) {
                Text(
                    text = "Select a search result to view details and send to seedbox.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            } else {
                TorrentDetailScreen(
                    container = container,
                    resultId = result.id,
                    name = result.name,
                    site = result.site,
                    initialMagnet = result.magnet,
                )
            }
        }
    }
}
