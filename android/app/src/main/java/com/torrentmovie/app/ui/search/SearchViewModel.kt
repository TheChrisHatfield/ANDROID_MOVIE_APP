package com.torrentmovie.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.MovieMetadata
import com.torrentmovie.core.data.SearchException
import com.torrentmovie.core.data.SearchResult
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
    val activeBrowseFeed: String? = null,
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
    private var lastSearchActiveBrowseFeed: String? = null

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
                    _state.value = _state.value.copy(
                        loading = true,
                        error = null,
                        errorCode = null,
                        info = null,
                        results = emptyList(),
                        groups = emptyList(),
                    )
                    refreshCurrentResults()
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
        val clearingBrowse = X1337BrowseFeed.fromId(_state.value.activeBrowseFeed)?.let { feed ->
            trimmed != feed.label
        } == true
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
            activeBrowseFeed = if (clearingBrowse) null else _state.value.activeBrowseFeed,
        )
        if (revertingToLastSearch) {
            val labelFeed = X1337BrowseFeed.entriesList.find { it.label.equals(trimmed, ignoreCase = true) }
            if (labelFeed != null) {
                loadBrowse1337x(labelFeed)
            } else {
                search()
            }
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

    fun refreshCurrentResults() {
        val feed = X1337BrowseFeed.fromId(_state.value.activeBrowseFeed)
        if (feed != null) {
            loadBrowse1337x(feed)
        } else {
            search()
        }
    }

    fun loadBrowse1337x(feed: X1337BrowseFeed) {
        searchJob?.cancel()
        val settings = container.settingsRepository.load()
        if (settings.searchApiBaseUrl.isBlank()) {
            _state.value = _state.value.copy(
                query = feed.label,
                loading = false,
                error = "Configure Search API URL in Settings (e.g. http://<PC-IP>:8765)",
                errorCode = null,
                results = emptyList(),
                groups = emptyList(),
                info = null,
                hasSearched = true,
                showTmdbSetupHint = false,
                activeBrowseFeed = feed.id,
            )
            return
        }
        val generation = ++searchGeneration
        val label = feed.label
        _state.value = _state.value.copy(
            query = label,
            activeBrowseFeed = feed.id,
            loading = true,
            hasSearched = true,
            error = null,
            errorCode = null,
            info = null,
            results = emptyList(),
            groups = emptyList(),
        )
        searchJob = viewModelScope.launch {
            val minSeeds = _state.value.minSeeds
            val maxSeeds = _state.value.maxSeeds
            val maxSize = _state.value.maxSize
            val settingsKeyAtStart = searchSettingsKey()
            fun requestStillCurrent(): Boolean {
                return generation == searchGeneration &&
                    _state.value.activeBrowseFeed == feed.id &&
                    _state.value.minSeeds == minSeeds &&
                    _state.value.maxSeeds == maxSeeds &&
                    _state.value.maxSize == maxSize &&
                    searchSettingsKey() == settingsKeyAtStart
            }
            try {
                val outcome = container.searchRepository.browse1337x(
                    feed.id,
                    minSeeds = minSeeds,
                    maxSeeds = maxSeeds,
                    maxSize = maxSize,
                )
                if (!requestStillCurrent()) return@launch
                applySuccessfulOutcome(
                    outcome = outcome,
                    executedLabel = label,
                    minSeeds = minSeeds,
                    maxSeeds = maxSeeds,
                    maxSize = maxSize,
                    activeBrowseFeed = feed.id,
                    emptyResultsMessage = "No torrents in this 1337x list. Try another feed.",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SearchException) {
                if (!requestStillCurrent()) return@launch
                handleSearchFailure(
                    label, minSeeds, maxSeeds, maxSize, settingsKeyAtStart, e.message, e.httpCode,
                )
            } catch (e: Exception) {
                if (!requestStillCurrent()) return@launch
                handleSearchFailure(
                    label, minSeeds, maxSeeds, maxSize, settingsKeyAtStart, e.message, null,
                )
            } finally {
                if (generation == searchGeneration && _state.value.loading) {
                    _state.value = _state.value.copy(loading = false)
                }
            }
        }
    }

    fun search() {
        val activeFeed = X1337BrowseFeed.fromId(_state.value.activeBrowseFeed)
        if (activeFeed != null) {
            loadBrowse1337x(activeFeed)
            return
        }
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
                activeBrowseFeed = null,
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
                hasSearched = true,
                showTmdbSetupHint = false,
                activeBrowseFeed = _state.value.activeBrowseFeed,
            )
            return
        }
        val labelFeed = X1337BrowseFeed.entriesList.find { it.label.equals(q, ignoreCase = true) }
        if (labelFeed != null) {
            loadBrowse1337x(labelFeed)
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
                    activeBrowseFeed = null,
                    results = emptyList(),
                    groups = emptyList(),
                )
                val outcome = container.searchRepository.search(
                    q,
                    minSeeds = minSeeds,
                    maxSeeds = maxSeeds,
                    maxSize = maxSize,
                )
                if (!requestStillCurrent()) return@launch
                applySuccessfulOutcome(
                    outcome = outcome,
                    executedLabel = q,
                    minSeeds = minSeeds,
                    maxSeeds = maxSeeds,
                    maxSize = maxSize,
                    activeBrowseFeed = null,
                    emptyResultsMessage = "No results found. Try a broader query.",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SearchException) {
                if (!requestStillCurrent()) return@launch
                handleSearchFailure(
                    q, minSeeds, maxSeeds, maxSize, settingsKeyAtStart, e.message, e.httpCode,
                )
            } catch (e: Exception) {
                if (!requestStillCurrent()) return@launch
                handleSearchFailure(
                    q, minSeeds, maxSeeds, maxSize, settingsKeyAtStart, e.message, null,
                )
            } finally {
                if (generation == searchGeneration && _state.value.loading) {
                    _state.value = _state.value.copy(loading = false)
                }
            }
        }
    }

    private suspend fun applySuccessfulOutcome(
        outcome: SearchResult,
        executedLabel: String,
        minSeeds: Int?,
        maxSeeds: Int?,
        maxSize: String?,
        activeBrowseFeed: String?,
        emptyResultsMessage: String,
    ) {
        val infoMessages = mutableListOf<String>()
        if (minSeeds != null && activeBrowseFeed == null) {
            infoMessages += "Min seeds filter may hide YTS and other indexers without seed counts."
        }
        if (minSeeds != null && activeBrowseFeed != null) {
            infoMessages += "Min seeds filter may hide torrents without seed counts."
        }
        if (maxSeeds != null) {
            infoMessages += "Max seeds filter may hide indexers without seed counts."
        }
        if (maxSize != null && activeBrowseFeed != null) {
            infoMessages += "Max size filter may hide larger releases in this list."
        }
        if (
            activeBrowseFeed != null &&
            (minSeeds != null || maxSeeds != null || maxSize != null)
        ) {
            infoMessages += "If the list is empty, relax filters — they may hide all browse results."
        }
        if (outcome.tmdbEnrichmentCapped) {
            infoMessages += "TMDB enrichment limited to first 50 movie groups — later groups may lack posters."
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
        val emptyMessage = if (!hasAnyResults && info == null) emptyResultsMessage else null
        val inlineError = emptyMessage ?: if (!hasAnyResults && info != null) info else null
        val snackInfo = if (!hasAnyResults && info != null) null else info

        lastSearchedQuery = executedLabel
        lastSearchMinSeeds = minSeeds
        lastSearchMaxSeeds = maxSeeds
        lastSearchMaxSize = maxSize
        lastSearchSettingsKey = searchSettingsKey()
        lastSearchActiveBrowseFeed = activeBrowseFeed
        _state.value = _state.value.copy(
            loading = false,
            results = display.results,
            groups = display.groups,
            hasSearched = true,
            info = snackInfo,
            error = inlineError,
            errorCode = null,
            showTmdbSetupHint = needsTmdbSetup,
            lastExecutedQuery = executedLabel,
            activeBrowseFeed = activeBrowseFeed,
        )
    }

    private fun handleSearchFailure(
        q: String,
        minSeeds: Int?,
        maxSeeds: Int?,
        maxSize: String?,
        settingsKeyAtStart: String,
        message: String?,
        httpCode: Int?,
    ) {
        val preserveResults = shouldPreserveResultsOnError(
            q, minSeeds, maxSeeds, maxSize, settingsKeyAtStart,
        )
        lastSearchedQuery = q
        _state.value = _state.value.copy(
            loading = false,
            error = message ?: "Search failed",
            errorCode = httpCode,
            results = if (preserveResults) _state.value.results else emptyList(),
            groups = if (preserveResults) _state.value.groups else emptyList(),
            info = null,
            hasSearched = true,
            showTmdbSetupHint = false,
            lastExecutedQuery = if (preserveResults) _state.value.lastExecutedQuery else q,
            // Text search clears activeBrowseFeed in search() before failure; keep chip on browse errors.
            activeBrowseFeed = _state.value.activeBrowseFeed,
        )
    }

    private fun shouldPreserveResultsOnError(
        q: String,
        minSeeds: Int?,
        maxSeeds: Int?,
        maxSize: String?,
        settingsKeyAtStart: String,
    ): Boolean {
        return _state.value.hasSearched &&
            lastSearchedQuery == q &&
            lastSearchMinSeeds == minSeeds &&
            lastSearchMaxSeeds == maxSeeds &&
            lastSearchMaxSize == maxSize &&
            lastSearchSettingsKey == settingsKeyAtStart &&
            lastSearchActiveBrowseFeed == _state.value.activeBrowseFeed &&
            searchSettingsKey() == settingsKeyAtStart &&
            (_state.value.groups.isNotEmpty() || _state.value.results.isNotEmpty())
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
