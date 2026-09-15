package com.torrentmovie.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.MovieMetadata
import com.torrentmovie.core.data.SearchException
import com.torrentmovie.core.network.MovieGroupDto
import com.torrentmovie.core.network.TorrentResultDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<TorrentResultDto> = emptyList(),
    val groups: List<MovieGroupDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val errorCode: Int? = null,
    val info: String? = null,
    val minSeeds: Int? = null,
    val maxSeeds: Int? = null,
    val maxSize: String? = null,
    val hasSearched: Boolean = false,
)

class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var searchGeneration = 0
    private var lastSearchedQuery: String? = null
    private var lastSearchMinSeeds: Int? = null
    private var lastSearchMaxSeeds: Int? = null
    private var lastSearchMaxSize: String? = null

    init {
        viewModelScope.launch {
            container.settingsRepository.revision.drop(1).collect {
                if (_state.value.hasSearched && !_state.value.loading) {
                    search()
                }
            }
        }
    }

    fun setQuery(q: String) {
        val trimmed = q.trim()
        val stale = lastSearchedQuery != null && trimmed != lastSearchedQuery
        if (stale) {
            searchJob?.cancel()
            searchGeneration += 1
        }
        val revertingToLastSearch = !stale &&
            trimmed == lastSearchedQuery &&
            trimmed.isNotEmpty() &&
            _state.value.results.isEmpty() &&
            _state.value.groups.isEmpty() &&
            !_state.value.loading
        _state.value = _state.value.copy(
            query = q,
            loading = if (stale) false else _state.value.loading,
            results = if (stale) emptyList() else _state.value.results,
            groups = if (stale) emptyList() else _state.value.groups,
            error = if (stale) null else _state.value.error,
            errorCode = if (stale) null else _state.value.errorCode,
            info = if (stale) null else _state.value.info,
            hasSearched = if (stale) false else _state.value.hasSearched,
        )
        if (revertingToLastSearch) {
            search()
        }
    }

    fun setMinSeeds(value: Int?) {
        _state.value = _state.value.copy(minSeeds = value?.takeIf { it > 0 })
    }

    fun setMaxSeeds(value: Int?) {
        _state.value = _state.value.copy(maxSeeds = value?.takeIf { it > 0 })
    }

    fun setMaxSize(value: String?) {
        _state.value = _state.value.copy(maxSize = value?.takeIf { it.isNotBlank() })
    }

    fun search() {
        val q = _state.value.query.trim()
        searchJob?.cancel()
        if (q.isEmpty()) {
            searchGeneration += 1
            lastSearchedQuery = null
            _state.value = _state.value.copy(
                loading = false,
                results = emptyList(),
                groups = emptyList(),
                error = null,
                errorCode = null,
                info = null,
                hasSearched = false,
            )
            return
        }
        val generation = ++searchGeneration
        searchJob = viewModelScope.launch {
            val minSeeds = _state.value.minSeeds
            val maxSeeds = _state.value.maxSeeds
            val maxSize = _state.value.maxSize
            val settingsRevision = container.settingsRepository.revision.value
            fun requestStillCurrent(): Boolean {
                return generation == searchGeneration &&
                    _state.value.query.trim() == q &&
                    _state.value.minSeeds == minSeeds &&
                    _state.value.maxSeeds == maxSeeds &&
                    _state.value.maxSize == maxSize &&
                    container.settingsRepository.revision.value == settingsRevision
            }
            try {
                val sameSearch = lastSearchedQuery == q &&
                    lastSearchMinSeeds == minSeeds &&
                    lastSearchMaxSeeds == maxSeeds &&
                    lastSearchMaxSize == maxSize
                if (!sameSearch) {
                    container.movieMetadataStore.clear()
                }
                _state.value = _state.value.copy(
                    loading = true,
                    results = emptyList(),
                    groups = emptyList(),
                    error = null,
                    errorCode = null,
                    info = null,
                )
                val outcome = container.searchRepository.search(
                    q,
                    minSeeds = minSeeds,
                    maxSeeds = maxSeeds,
                    maxSize = maxSize,
                )
                if (!requestStillCurrent()) return@launch
                val infoMessages = mutableListOf<String>()
                if (minSeeds != null) {
                    infoMessages += "Min seeds filter may hide YTS and other indexers without seed counts."
                }
                if (outcome.failedSites.isNotEmpty()) {
                    infoMessages += "Some sources failed: ${outcome.failedSites.joinToString()}"
                }
                val info = infoMessages.takeIf { it.isNotEmpty() }?.joinToString("\n")
                val allReleases = outcome.groups.flatMap { it.releases } + outcome.results
                allReleases.forEach { container.searchResultStore.put(it) }
                outcome.groups.forEach { group ->
                    val metadata = MovieMetadata(
                        title = group.title,
                        year = group.year,
                        overview = group.overview,
                        posterUrl = group.posterUrl,
                        trailerYoutubeKey = group.trailerYoutubeKey,
                    )
                    group.releases.forEach { release ->
                        container.movieMetadataStore.put(release.id, metadata)
                    }
                }

                val hasAnyResults = outcome.groups.isNotEmpty() || outcome.results.isNotEmpty()
                val emptyMessage = if (!hasAnyResults && info == null) {
                    "No results found. Try a broader query."
                } else null
                val inlineError = emptyMessage
                    ?: if (!hasAnyResults && info != null) info else null
                val snackInfo = if (!hasAnyResults && info != null) null else info

                lastSearchedQuery = q
                lastSearchMinSeeds = minSeeds
                lastSearchMaxSeeds = maxSeeds
                lastSearchMaxSize = maxSize
                _state.value = _state.value.copy(
                    loading = false,
                    results = outcome.results,
                    groups = outcome.groups,
                    hasSearched = true,
                    info = snackInfo,
                    error = inlineError,
                    errorCode = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SearchException) {
                if (!requestStillCurrent()) return@launch
                lastSearchedQuery = q
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Search failed",
                    errorCode = e.httpCode,
                    results = emptyList(),
                    groups = emptyList(),
                    hasSearched = true,
                )
            } catch (e: Exception) {
                if (!requestStillCurrent()) return@launch
                lastSearchedQuery = q
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Search failed",
                    errorCode = null,
                    results = emptyList(),
                    groups = emptyList(),
                    hasSearched = true,
                )
            } finally {
                if (generation == searchGeneration && _state.value.loading) {
                    _state.value = _state.value.copy(loading = false)
                }
            }
        }
    }
}
