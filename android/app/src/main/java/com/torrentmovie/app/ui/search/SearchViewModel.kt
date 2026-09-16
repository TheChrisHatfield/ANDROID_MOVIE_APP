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
    val showTmdbSetupHint: Boolean = false,
    val lastExecutedQuery: String = "",
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
    private var lastSearchSettingsKey: String? = null

    private fun searchSettingsKey(): String {
        val settings = container.settingsRepository.load()
        return listOf(
            settings.searchApiBaseUrl,
            settings.tmdbApiKey,
            settings.searchPages.toString(),
            settings.movieSitesOnly.toString(),
            settings.fetchMovieMetadata.toString(),
            _state.value.minSeeds?.toString().orEmpty(),
            _state.value.maxSeeds?.toString().orEmpty(),
            _state.value.maxSize.orEmpty(),
        ).joinToString("|")
    }

    init {
        lastSearchSettingsKey = searchSettingsKey()
        viewModelScope.launch {
            container.settingsRepository.revision.drop(1).collect {
                val key = searchSettingsKey()
                if (key != lastSearchSettingsKey && _state.value.hasSearched) {
                    lastSearchSettingsKey = key
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
                showTmdbSetupHint = if (stale) false else _state.value.showTmdbSetupHint,
            )
        if (revertingToLastSearch) {
            search()
        }
    }

    fun setMinSeeds(value: Int?) {
        _state.value = _state.value.copy(
            minSeeds = value?.takeIf { it >= 0 },
        )
    }

    fun setMaxSeeds(value: Int?) {
        _state.value = _state.value.copy(
            maxSeeds = value?.takeIf { it >= 0 },
        )
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
                showTmdbSetupHint = false,
            )
            return
        }
        val settings = container.settingsRepository.load()
        if (settings.searchApiBaseUrl.isBlank()) {
            _state.value = _state.value.copy(
                loading = false,
                error = "Configure Search API URL in Settings (e.g. http://<PC-IP>:8765)",
                errorCode = null,
                results = emptyList(),
                groups = emptyList(),
                info = null,
                hasSearched = false,
                showTmdbSetupHint = false,
            )
            return
        }
        val generation = ++searchGeneration
        searchJob = viewModelScope.launch {
            val minSeeds = _state.value.minSeeds
            val maxSeeds = _state.value.maxSeeds
            val maxSize = _state.value.maxSize
            val settingsKeyAtStart = searchSettingsKey()
            fun requestStillCurrent(): Boolean {
                return generation == searchGeneration &&
                    _state.value.query.trim() == q &&
                    _state.value.minSeeds == minSeeds &&
                    _state.value.maxSeeds == maxSeeds &&
                    _state.value.maxSize == maxSize &&
                    searchSettingsKey() == settingsKeyAtStart
            }
            try {
                _state.value = _state.value.copy(
                    loading = true,
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
                if (maxSeeds != null) {
                    infoMessages += "Max seeds filter may hide indexers without seed counts."
                }
                if (outcome.tmdbKeyRejected) {
                    infoMessages += "TMDB key in Settings was rejected — using server key or no enrichment."
                }
                if (outcome.failedSites.isNotEmpty()) {
                    infoMessages += "Some sources failed: ${outcome.failedSites.joinToString()}"
                }
                val display = normalizeKodiGroups(outcome.groups, outcome.results)
                val settings = container.settingsRepository.load()
                val needsTmdbSetup = settings.fetchMovieMetadata &&
                    display.groups.isNotEmpty() &&
                    display.groups.none { group ->
                        !group.posterUrl.isNullOrBlank() ||
                            group.releases.any { !it.posterUrl.isNullOrBlank() }
                    } &&
                    settings.tmdbApiKey.isBlank() &&
                    !try {
                        container.searchRepository.isTmdbConfigured()
                    } catch (_: Exception) {
                        false
                    }
                val info = infoMessages.takeIf { it.isNotEmpty() }?.joinToString("\n")
                val allReleases = display.groups.flatMap { it.releases }
                allReleases.forEach { container.searchResultStore.put(it) }
                display.groups.forEach { group ->
                    val releasePoster = group.releases.firstOrNull { !it.posterUrl.isNullOrBlank() }?.posterUrl
                    val metadata = MovieMetadata(
                        title = group.title,
                        year = group.year,
                        overview = group.overview,
                        posterUrl = group.posterUrl ?: releasePoster,
                        trailerYoutubeKey = group.trailerYoutubeKey,
                    )
                    group.releases.forEach { release ->
                        container.movieMetadataStore.put(release.id, metadata)
                    }
                }
                container.movieMetadataStore.bumpRevision()

                val hasAnyResults = display.groups.isNotEmpty()
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
                lastSearchSettingsKey = searchSettingsKey()
                _state.value = _state.value.copy(
                    loading = false,
                    results = display.results,
                    groups = display.groups,
                    hasSearched = true,
                    info = snackInfo,
                    error = inlineError,
                    errorCode = null,
                    showTmdbSetupHint = needsTmdbSetup,
                    lastExecutedQuery = q,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SearchException) {
                if (!requestStillCurrent()) return@launch
                val preserveResults = _state.value.hasSearched &&
                    lastSearchedQuery == q &&
                    (_state.value.groups.isNotEmpty() || _state.value.results.isNotEmpty())
                lastSearchedQuery = q
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Search failed",
                    errorCode = e.httpCode,
                    results = if (preserveResults) _state.value.results else emptyList(),
                    groups = if (preserveResults) _state.value.groups else emptyList(),
                    info = null,
                    hasSearched = true,
                    showTmdbSetupHint = false,
                    lastExecutedQuery = if (preserveResults) _state.value.lastExecutedQuery else q,
                )
            } catch (e: Exception) {
                if (!requestStillCurrent()) return@launch
                val preserveResults = _state.value.hasSearched &&
                    lastSearchedQuery == q &&
                    (_state.value.groups.isNotEmpty() || _state.value.results.isNotEmpty())
                lastSearchedQuery = q
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Search failed",
                    errorCode = null,
                    results = if (preserveResults) _state.value.results else emptyList(),
                    groups = if (preserveResults) _state.value.groups else emptyList(),
                    info = null,
                    hasSearched = true,
                    showTmdbSetupHint = false,
                    lastExecutedQuery = if (preserveResults) _state.value.lastExecutedQuery else q,
                )
            } finally {
                if (generation == searchGeneration && _state.value.loading) {
                    _state.value = _state.value.copy(loading = false)
                }
            }
        }
    }

    private data class DisplaySearchOutcome(
        val groups: List<MovieGroupDto>,
        val results: List<TorrentResultDto>,
    )

    private fun normalizeKodiGroups(
        groups: List<MovieGroupDto>,
        flat: List<TorrentResultDto>,
    ): DisplaySearchOutcome {
        val synthetic = flat.map { result ->
            MovieGroupDto(
                group_key = "flat-${result.id}",
                title = result.name,
                year = null,
                overview = null,
                poster_url = result.posterUrl,
                trailer_youtube_key = null,
                release_count = 1,
                releases = listOf(result),
            )
        }
        return DisplaySearchOutcome(groups = groups + synthetic, results = emptyList())
    }
}
