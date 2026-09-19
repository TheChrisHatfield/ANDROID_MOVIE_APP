package com.torrentmovie.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.MagnetHashUtil
import com.torrentmovie.core.network.TorrentResultDto

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun SearchScreen(
    container: AppContainer,
    onOpenDetail: (TorrentResultDto) -> Unit,
    modifier: Modifier = Modifier,
    sharedViewModel: SearchViewModel? = null,
    selectedResultId: String? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val vm: SearchViewModel = sharedViewModel ?: viewModel { SearchViewModel(container) }
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showFilters by remember { mutableStateOf(false) }
    var expandedGroupKey by remember { mutableStateOf<String?>(null) }
    var playingTrailerGroupKey by remember { mutableStateOf<String?>(null) }
    var highlightedReleaseId by remember { mutableStateOf<String?>(null) }
    val activeReleaseId = selectedResultId ?: highlightedReleaseId

    LaunchedEffect(state.loading, state.query) {
        if (state.loading) {
            expandedGroupKey = null
            playingTrailerGroupKey = null
            highlightedReleaseId = null
        }
    }
    LaunchedEffect(state.activeBrowseFeed) {
        playingTrailerGroupKey = null
        expandedGroupKey = null
        highlightedReleaseId = null
    }
    LaunchedEffect(state.activeGenre) {
        if (state.activeGenre != null) {
            playingTrailerGroupKey = null
            expandedGroupKey = null
            highlightedReleaseId = null
        }
    }
    LaunchedEffect(selectedResultId, state.groups) {
        val id = selectedResultId
        if (id != null) {
            playingTrailerGroupKey = null
            state.groups.find { group -> group.releases.any { it.id == id } }
                ?.groupKey
                ?.let { expandedGroupKey = it }
        }
        if (
            playingTrailerGroupKey != null &&
            state.groups.none { it.groupKey == playingTrailerGroupKey }
        ) {
            playingTrailerGroupKey = null
        }
    }
    val uploaded by container.uploadedRepository.observeAll().collectAsState(initial = emptyList())

    val pullState = rememberPullRefreshState(
        refreshing = state.loading,
        onRefresh = { vm.refreshCurrentResults() },
    )

    var lastSnackbarKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.error, state.info, state.errorCode, state.results.size, state.groups.size) {
        val inlineErrorCode = state.errorCode == 503 || state.errorCode == 400
        val hasVisibleResults = state.results.isNotEmpty() || state.groups.isNotEmpty()
        state.error
            ?.takeIf { !inlineErrorCode && hasVisibleResults }
            ?.let { msg ->
                val key = "err:$msg:${state.errorCode}"
                if (key != lastSnackbarKey) {
                    snackbar.showSnackbar(msg)
                    lastSnackbarKey = key
                }
            }
        state.info?.let { msg ->
            val key = "info:$msg"
            if (key != lastSnackbarKey) {
                snackbar.showSnackbar(msg)
                lastSnackbarKey = key
            }
        }
        if (state.error == null && state.info == null) {
            lastSnackbarKey = null
        }
    }

    fun isAlreadyUploaded(result: TorrentResultDto): Boolean {
        val magnet = result.magnet?.takeIf { it.isNotBlank() }
        if (magnet != null) {
            val key = MagnetHashUtil.storageKey(magnet, result.name, result.site)
            return uploaded.any { it.infoHash.equals(key, ignoreCase = true) }
        }
        return uploaded.any { it.displayName == result.name && it.site == result.site }
    }

    fun openDetail(result: TorrentResultDto) {
        playingTrailerGroupKey = null
        highlightedReleaseId = result.id
        onOpenDetail(result)
    }

    if (showFilters) {
        SearchFilterSheet(
            minSeeds = state.minSeeds,
            maxSeeds = state.maxSeeds,
            maxSize = state.maxSize,
            onDismiss = { showFilters = false },
            onApply = { min, maxSeeds, maxSize ->
                vm.setMinSeeds(min)
                vm.setMaxSeeds(maxSeeds)
                vm.setMaxSize(maxSize)
                if (state.activeGenre != null || state.activeBrowseFeed != null ||
                    (state.hasSearched && state.query.isNotBlank())
                ) {
                    vm.refreshCurrentResults()
                }
            },
        )
    }

    Column(modifier.fillMaxSize()) {
        SnackbarHost(snackbar)
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            label = { Text("Search movies") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            trailingIcon = {
                IconButton(onClick = { showFilters = true }) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filters")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )
        BrowseGenreChipRow(
            genrePanelExpanded = state.genrePanelExpanded,
            activeGenre = state.activeGenre,
            activeBrowseFeed = state.activeBrowseFeed,
            loading = state.loading,
            onCollapseGenrePanel = vm::collapseGenrePanel,
            onExpandGenrePanel = vm::expandGenrePanel,
            onLoadGenre = vm::loadGenreBrowse,
            onLoadBrowse = vm::loadBrowse1337x,
        )
        if (state.activeBrowseFeed == null && state.activeGenre == null) {
            Button(
                onClick = { vm.search() },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                enabled = !state.loading && state.query.isNotBlank(),
            ) { Text("Search") }
        }

        if (state.showTmdbSetupHint) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = "No posters for these results.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "YTS releases include posters automatically. For all indexers, add a free TMDB key in Settings (themoviedb.org).",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (onOpenSettings != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(onClick = onOpenSettings) {
                                Text("Open Settings")
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullState),
        ) {
            when {
                state.loading && state.groups.isEmpty() && state.results.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = when {
                                state.activeGenre != null -> "Loading genre…"
                                state.activeBrowseFeed != null -> "Loading 1337x list…"
                                else -> "Searching…"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                !state.hasSearched && state.query.isNotBlank() && !state.loading &&
                    state.activeBrowseFeed == null && state.activeGenre == null &&
                    !state.genrePanelExpanded -> {
                    Text(
                        text = "Tap Search to find movies",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(24.dp),
                    )
                }
                state.hasSearched && state.groups.isEmpty() &&
                    state.results.isEmpty() && !state.loading -> {
                    val message = when (state.errorCode) {
                        503 -> "No sources available. Check the search API and try again."
                        400 -> state.error ?: "Invalid search request. Check filters and try again."
                        else -> state.error ?: "No results found. Try another title or adjust filters."
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(message, style = MaterialTheme.typography.bodyLarge)
                        if (state.errorCode == 503 || state.errorCode == 400) {
                            Button(
                                onClick = { vm.refreshCurrentResults() },
                                modifier = Modifier.padding(top = 12.dp),
                            ) { Text("Retry") }
                        }
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.groups, key = { it.groupKey }) { group ->
                            MovieGroupCard(
                                group = group,
                                expanded = expandedGroupKey == group.groupKey,
                                selectedResultId = activeReleaseId,
                                trailerPlaying = playingTrailerGroupKey == group.groupKey,
                                isAlreadyUploaded = ::isAlreadyUploaded,
                                onToggleExpand = {
                                    expandedGroupKey = if (expandedGroupKey == group.groupKey) {
                                        null
                                    } else {
                                        group.groupKey
                                    }
                                },
                                onToggleTrailer = {
                                    val selectedInGroup = selectedResultId != null &&
                                        group.releases.any { it.id == selectedResultId }
                                    if (selectedInGroup) return@MovieGroupCard
                                    playingTrailerGroupKey = if (playingTrailerGroupKey == group.groupKey) {
                                        null
                                    } else {
                                        group.groupKey
                                    }
                                },
                                onOpenRelease = ::openDetail,
                            )
                        }
                        items(state.results, key = { it.id }) { result ->
                            TorrentResultCard(
                                result = result,
                                alreadyUploaded = isAlreadyUploaded(result),
                                selected = result.id == activeReleaseId,
                                onClick = { openDetail(result) },
                            )
                        }
                    }
                }
            }
            PullRefreshIndicator(
                refreshing = state.loading,
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowseGenreChipRow(
    genrePanelExpanded: Boolean,
    activeGenre: String?,
    activeBrowseFeed: String?,
    loading: Boolean,
    onCollapseGenrePanel: () -> Unit,
    onExpandGenrePanel: () -> Unit,
    onLoadGenre: (X1337MovieGenre) -> Unit,
    onLoadBrowse: (X1337BrowseFeed) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (genrePanelExpanded) {
            FilterChip(
                selected = false,
                onClick = onCollapseGenrePanel,
                label = { Text("Lists") },
                enabled = !loading,
            )
            X1337MovieGenre.entriesList.forEach { genre ->
                FilterChip(
                    selected = activeGenre == genre.id,
                    onClick = { onLoadGenre(genre) },
                    label = { Text(genre.buttonLabel) },
                    enabled = !loading,
                )
            }
        } else {
            X1337BrowseFeed.entriesList.forEach { feed ->
                FilterChip(
                    selected = activeBrowseFeed == feed.id,
                    onClick = { onLoadBrowse(feed) },
                    label = { Text(feed.buttonLabel) },
                    enabled = !loading,
                )
            }
            FilterChip(
                selected = false,
                onClick = onExpandGenrePanel,
                label = { Text("By Genre") },
                enabled = !loading,
            )
        }
    }
}
