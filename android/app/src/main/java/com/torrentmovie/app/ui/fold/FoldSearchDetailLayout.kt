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
import androidx.compose.ui.unit.dp
import com.torrentmovie.app.ui.detail.TorrentDetailScreen
import com.torrentmovie.app.ui.search.SearchScreen
import com.torrentmovie.app.ui.search.SearchViewModel
import com.torrentmovie.app.ui.util.SearchReleaseRematch
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.PendingFoldDetail
import com.torrentmovie.core.network.TorrentResultDto

@Composable
fun FoldSearchDetailLayout(
    container: AppContainer,
    searchViewModel: SearchViewModel,
    onOpenSettings: (() -> Unit)? = null,
    onOpenUploaded: ((storageKey: String) -> Unit)? = null,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoredName by rememberSaveable { mutableStateOf<String?>(null) }
    var restoredSite by rememberSaveable { mutableStateOf<String?>(null) }
    val state by searchViewModel.state.collectAsState()
    val settingsRevision by container.settingsRepository.revision.collectAsState()

    fun findRelease(id: String): TorrentResultDto? {
        val fromList = state.results.find { it.id == id }
            ?: state.groups.asSequence().flatMap { it.releases }.find { it.id == id }
        val fromStore = container.searchResultStore.get(id)
        return when {
            fromList == null -> fromStore
            fromStore == null -> fromList
            else -> fromList.copy(
                magnet = fromList.magnet?.takeIf { it.isNotBlank() } ?: fromStore.magnet,
            )
        }
    }

    fun syncFoldSelection(id: String?, name: String, site: String) {
        container.foldActiveSelection = if (id != null) {
            PendingFoldDetail(resultId = id, name = name, site = site)
        } else {
            null
        }
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
        val pending = container.pendingFoldDetail
        if (pending != null) {
            selectedId = pending.resultId
            restoredName = pending.name.takeIf { it.isNotBlank() }
            restoredSite = pending.site.takeIf { it.isNotBlank() }
            syncFoldSelection(pending.resultId, pending.name, pending.site)
            container.pendingFoldDetail = null
        } else if (selectedId == null) {
            container.foldActiveSelection?.let { active ->
                selectedId = active.resultId
                restoredName = active.name.takeIf { it.isNotBlank() }
                restoredSite = active.site.takeIf { it.isNotBlank() }
            }
        }
    }

    LaunchedEffect(selectedId, restoredName, restoredSite) {
        val id = selectedId
        if (id != null) {
            val result = findRelease(id)
            syncFoldSelection(
                id,
                result?.name ?: restoredName ?: "",
                result?.site ?: restoredSite ?: "",
            )
        } else {
            syncFoldSelection(null, "", "")
        }
    }

    LaunchedEffect(selectedId) {
        if (selectedId != null) return@LaunchedEffect
        container.foldActiveSelection?.let { active ->
            selectedId = active.resultId
            restoredName = active.name.takeIf { it.isNotBlank() }
            restoredSite = active.site.takeIf { it.isNotBlank() }
        }
    }

    var lastSearchFilterKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastSearchSettingsKey by rememberSaveable { mutableStateOf<String?>(null) }

    fun currentSearchSettingsKey(): String {
        val settings = container.settingsRepository.load()
        return listOf(
            settings.searchApiBaseUrl,
            settings.tmdbApiKey,
            settings.searchPages.toString(),
            settings.movieSitesOnly.toString(),
            settings.fetchMovieMetadata.toString(),
            state.minSeeds?.toString().orEmpty(),
            state.maxSeeds?.toString().orEmpty(),
            state.maxSize.orEmpty(),
        ).joinToString("|")
    }

    LaunchedEffect(settingsRevision, state.minSeeds, state.maxSeeds, state.maxSize) {
        val key = currentSearchSettingsKey()
        if (lastSearchSettingsKey != null && lastSearchSettingsKey != key && state.hasSearched) {
            selectedId = null
            restoredName = null
            restoredSite = null
            syncFoldSelection(null, "", "")
        }
        lastSearchSettingsKey = key
    }

    LaunchedEffect(state.query, state.lastExecutedQuery) {
        val query = state.query.trim()
        if (state.lastExecutedQuery.isNotBlank() && query != state.lastExecutedQuery.trim()) {
            selectedId = null
            restoredName = null
            restoredSite = null
            syncFoldSelection(null, "", "")
        }
    }

    LaunchedEffect(state.query, state.hasSearched) {
        if (!state.hasSearched) return@LaunchedEffect
        if (state.query.trim().isBlank()) {
            selectedId = null
            restoredName = null
            restoredSite = null
            syncFoldSelection(null, "", "")
        }
    }

    LaunchedEffect(
        state.minSeeds,
        state.maxSeeds,
        state.maxSize,
        state.hasSearched,
        state.loading,
        state.query,
    ) {
        if (state.loading || !state.hasSearched || state.query.isBlank()) return@LaunchedEffect
        val filterKey = listOf(
            state.minSeeds?.toString().orEmpty(),
            state.maxSeeds?.toString().orEmpty(),
            state.maxSize.orEmpty(),
        ).joinToString("|")
        if (lastSearchFilterKey != null && lastSearchFilterKey != filterKey) {
            selectedId = null
            restoredName = null
            restoredSite = null
            syncFoldSelection(null, "", "")
        }
        lastSearchFilterKey = filterKey
    }

    LaunchedEffect(state.error, state.groups, state.results, state.hasSearched, state.loading, state.query, state.lastExecutedQuery) {
        if (state.loading || !state.hasSearched) return@LaunchedEffect
        if (state.error != null && state.groups.isEmpty() && state.results.isEmpty()) {
            selectedId = null
            restoredName = null
            restoredSite = null
            syncFoldSelection(null, "", "")
        }
    }

    LaunchedEffect(state.groups, state.results.map { it.id }, state.hasSearched, state.loading) {
        if (state.loading || !state.hasSearched) return@LaunchedEffect
        val id = selectedId ?: return@LaunchedEffect
        val allReleases = state.results + state.groups.flatMap { it.releases }
        if (id in allReleases.map { it.id }) return@LaunchedEffect
        val anchor = findRelease(id)
        val matchName = anchor?.name ?: restoredName
        val matchSite = anchor?.site ?: restoredSite
        if (!matchName.isNullOrBlank()) {
            val rematched = SearchReleaseRematch.find(
                allReleases,
                id,
                matchName,
                matchSite.orEmpty(),
            )
            if (rematched != null) {
                val oldId = selectedId
                val oldRelease = oldId?.let { findRelease(it) }
                val oldMagnet = oldRelease?.magnet?.takeIf { it.isNotBlank() }
                    ?: oldId?.let { container.searchResultStore.get(it)?.magnet?.takeIf { m -> m.isNotBlank() } }
                selectedId = rematched.id
                restoredName = rematched.name
                restoredSite = rematched.site
                val merged = if (
                    !oldMagnet.isNullOrBlank() &&
                    rematched.magnet.isNullOrBlank()
                ) {
                    rematched.copy(magnet = oldMagnet)
                } else {
                    rematched
                }
                container.searchResultStore.put(merged)
                if (oldId != null && oldId != rematched.id) {
                    container.movieMetadataStore.get(oldId)?.let { meta ->
                        container.movieMetadataStore.put(rematched.id, meta)
                    }
                }
                return@LaunchedEffect
            }
        }
        if (container.searchResultStore.get(id) != null) return@LaunchedEffect
        selectedId = null
        restoredName = null
        restoredSite = null
        syncFoldSelection(null, "", "")
    }

    Row(Modifier.fillMaxSize()) {
        SearchScreen(
            container = container,
            sharedViewModel = searchViewModel,
            selectedResultId = selectedId,
            onOpenSettings = onOpenSettings,
            onOpenDetail = { result ->
                container.searchResultStore.put(result)
                selectedId = result.id
                restoredName = result.name
                restoredSite = result.site
                syncFoldSelection(result.id, result.name, result.site)
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
                        onResultExpired = {
                            selectedId = null
                            restoredName = null
                            restoredSite = null
                            syncFoldSelection(null, "", "")
                        },
                        onOpenUploaded = onOpenUploaded,
                    )
                }
            }
        }
    }
}
