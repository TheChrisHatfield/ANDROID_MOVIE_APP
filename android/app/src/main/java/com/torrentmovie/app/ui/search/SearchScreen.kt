package com.torrentmovie.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.torrentmovie.core.data.MagnetHashUtil
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.torrentmovie.core.data.AppContainer
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

    LaunchedEffect(state.loading) {
        if (state.loading) expandedGroupKey = null
    }
    LaunchedEffect(state.groups, state.loading, state.hasSearched) {
        if (!state.loading && state.hasSearched && expandedGroupKey == null) {
            val multi = state.groups.firstOrNull { it.releaseCount > 1 }
            expandedGroupKey = multi?.groupKey ?: state.groups.firstOrNull()?.groupKey
        }
    }
    LaunchedEffect(selectedResultId, state.groups) {
        val id = selectedResultId
        if (id != null) {
            state.groups.find { group -> group.releases.any { it.id == id } }
                ?.groupKey
                ?.let { expandedGroupKey = it }
        }
    }
    val uploaded by container.uploadedRepository.observeAll().collectAsState(initial = emptyList())

    val pullState = rememberPullRefreshState(
        refreshing = state.loading,
        onRefresh = { vm.search() },
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
        val key = MagnetHashUtil.storageKey(result.magnet ?: "", result.name, result.site)
        if (uploaded.any { it.infoHash.equals(key, ignoreCase = true) }) return true
        if (!result.magnet.isNullOrBlank()) return false
        return uploaded.any { it.displayName == result.name && it.site == result.site }
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
                if (state.query.isNotBlank()) {
                    vm.search()
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
            trailingIcon = {
                IconButton(onClick = { showFilters = true }) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filters")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        Button(
            onClick = { vm.search() },
            modifier = Modifier.padding(horizontal = 16.dp),
            enabled = !state.loading,
        ) { Text("Search") }

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
                        text = "Posters and descriptions need a free TMDB API key.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Sign up at themoviedb.org → Settings → API → paste the key below, Save, then search again.",
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
                !state.hasSearched && state.query.isNotBlank() && !state.loading -> {
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
                                onClick = { vm.search() },
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
                                selectedResultId = selectedResultId,
                                isAlreadyUploaded = ::isAlreadyUploaded,
                                onToggleExpand = {
                                    expandedGroupKey = if (expandedGroupKey == group.groupKey) {
                                        null
                                    } else {
                                        group.groupKey
                                    }
                                },
                                onOpenRelease = onOpenDetail,
                            )
                        }
                        items(state.results, key = { it.id }) { result ->
                            TorrentResultCard(
                                result = result,
                                alreadyUploaded = isAlreadyUploaded(result),
                                selected = result.id == selectedResultId,
                                onClick = { onOpenDetail(result) },
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
