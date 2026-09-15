package com.torrentmovie.app.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.torrentmovie.core.data.MagnetHashUtil
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
) {
    val vm: SearchViewModel = viewModel { SearchViewModel(container) }
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showFilters by remember { mutableStateOf(false) }
    val uploaded by container.uploadedRepository.observeAll().collectAsState(initial = emptyList())

    val pullState = rememberPullRefreshState(
        refreshing = state.loading,
        onRefresh = { vm.search() },
    )

    LaunchedEffect(state.error, state.info, state.errorCode) {
        if (state.errorCode != 503) {
            state.error
                ?.takeIf { state.results.isNotEmpty() || state.errorCode != null }
                ?.let { snackbar.showSnackbar(it) }
        }
        state.info?.let { snackbar.showSnackbar(it) }
    }

    fun isAlreadyUploaded(result: TorrentResultDto): Boolean {
        val hash = MagnetHashUtil.extractInfoHash(result.magnet)
        if (hash != null && uploaded.any { it.infoHash.equals(hash, ignoreCase = true) }) {
            return true
        }
        return uploaded.any { it.displayName == result.name && it.site == result.site }
    }

    if (showFilters) {
        SearchFilterSheet(
            minSeeds = state.minSeeds,
            maxSize = state.maxSize,
            onDismiss = { showFilters = false },
            onApply = { min, max ->
                vm.setMinSeeds(min)
                vm.setMaxSize(max)
                if (state.hasSearched && state.query.isNotBlank()) {
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullState),
        ) {
            when {
                state.hasSearched && state.results.isEmpty() && !state.loading -> {
                    val message = when (state.errorCode) {
                        503 -> "No sources available. Check the search API and try again."
                        else -> state.error ?: "No results found. Try another title or adjust filters."
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(message, style = MaterialTheme.typography.bodyLarge)
                        if (state.errorCode == 503) {
                            Button(
                                onClick = { vm.search() },
                                modifier = Modifier.padding(top = 12.dp),
                            ) { Text("Retry") }
                        }
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.results, key = { it.id }) { result ->
                            TorrentResultCard(
                                result = result,
                                alreadyUploaded = isAlreadyUploaded(result),
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
