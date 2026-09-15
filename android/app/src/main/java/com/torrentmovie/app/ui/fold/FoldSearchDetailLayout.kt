package com.torrentmovie.app.ui.fold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.torrentmovie.app.ui.detail.TorrentDetailScreen
import com.torrentmovie.app.ui.search.SearchScreen
import com.torrentmovie.app.ui.search.SearchViewModel
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.PendingFoldDetail
import com.torrentmovie.core.network.TorrentResultDto

@Composable
fun FoldSearchDetailLayout(
    container: AppContainer,
    searchViewModel: SearchViewModel,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoredName by rememberSaveable { mutableStateOf<String?>(null) }
    var restoredSite by rememberSaveable { mutableStateOf<String?>(null) }
    val state by searchViewModel.state.collectAsState()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isWide = screenWidthDp >= FoldDeviceProfile.TWO_PANE_MIN_WIDTH_DP
    var wasWide by rememberSaveable { mutableStateOf(isWide) }

    fun findRelease(id: String): TorrentResultDto? {
        return state.results.find { it.id == id }
            ?: state.groups.asSequence().flatMap { it.releases }.find { it.id == id }
            ?: container.searchResultStore.get(id)
    }

    val selected: TorrentResultDto? = selectedId?.let { id ->
        findRelease(id)
            ?: restoredName?.let { name ->
                TorrentResultDto(
                    id = id,
                    name = name,
                    site = restoredSite ?: "",
                )
            }
    }

    LaunchedEffect(Unit) {
        container.pendingFoldNarrowDetail = null
        container.pendingFoldDetail?.let { pending ->
            selectedId = pending.resultId
            restoredName = pending.name.takeIf { it.isNotBlank() }
            restoredSite = pending.site.takeIf { it.isNotBlank() }
            container.pendingFoldDetail = null
        }
    }

    LaunchedEffect(isWide) {
        if (wasWide && !isWide) {
            val id = selectedId
            if (id != null) {
                val result = findRelease(id)
                container.pendingFoldNarrowDetail = PendingFoldDetail(
                    resultId = id,
                    name = result?.name ?: restoredName ?: "",
                    site = result?.site ?: restoredSite ?: "",
                )
            }
        }
        wasWide = isWide
    }

    LaunchedEffect(state.groups, state.results.map { it.id }, state.hasSearched, state.loading) {
        if (state.loading) return@LaunchedEffect
        val id = selectedId ?: return@LaunchedEffect
        val allIds = state.results.map { it.id } +
            state.groups.flatMap { g -> g.releases.map { it.id } }
        if (state.hasSearched && (allIds.isEmpty() || id !in allIds)) {
            selectedId = null
            restoredName = null
            restoredSite = null
        }
    }

    Row(Modifier.fillMaxSize()) {
        SearchScreen(
            container = container,
            sharedViewModel = searchViewModel,
            selectedResultId = selectedId,
            onOpenDetail = { result ->
                container.pendingFoldNarrowDetail = null
                container.searchResultStore.put(result)
                selectedId = result.id
                restoredName = result.name
                restoredSite = result.site
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
                key(result.id) {
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
}
