package com.torrentmovie.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentmovie.app.ui.util.resolveGenreForRankingFeedback
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.MovieMetadata
import com.torrentmovie.core.data.MovieSearchSuggestion
import com.torrentmovie.core.data.SearchException
import com.torrentmovie.core.data.SearchResult
import com.torrentmovie.core.network.MovieGroupDto
import com.torrentmovie.core.network.TorrentResultDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    val genrePanelExpanded: Boolean = false,
    val activeGenre: String? = null,
    val suggestions: List<MovieSearchSuggestion> = emptyList(),
    val suggestionsLoading: Boolean = false,
)

class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private fun searchApiBlockedMessage(): String =
        container.settingsRepository.searchApiBlockedMessage()
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var suggestJob: Job? = null
    private var searchGeneration = 0
    private var suggestGeneration = 0
    private var lastSearchedQuery: String? = null
    private var lastSearchMinSeeds: Int? = null
    private var lastSearchMaxSeeds: Int? = null
    private var lastSearchMaxSize: String? = null
    private var lastSearchSettingsKey: String? = null
    private var lastSearchActiveBrowseFeed: String? = null
    private var lastSearchActiveGenre: String? = null

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
            var observedSettingsKey: String? = null
            var observedBootstrapGen = 0
            container.settingsRepository.revision.collect {
                val settings = container.settingsRepository.load()
                val activeFeed = X1337BrowseFeed.fromId(_state.value.activeBrowseFeed)
                if (settings.movieSitesOnly && activeFeed != null && !activeFeed.visibleFor(true)) {
                    loadBrowse1337x(X1337BrowseFeed.TRENDING)
                }
                val key = searchSettingsKey()
                val modeActive = _state.value.hasSearched ||
                    _state.value.genrePanelExpanded ||
                    _state.value.activeBrowseFeed != null ||
                    _state.value.activeGenre != null
                val settingsChanged = observedSettingsKey != null && key != observedSettingsKey
                val apiReadyAfterBootstrap = observedSettingsKey == null &&
                    shouldRefreshAfterSearchApiBootstrap(
                        modeActive = modeActive,
                        searchApiBaseUrl = settings.searchApiBaseUrl,
                        errorMessage = _state.value.error,
                    )
                val bootstrapGen = container.settingsRepository.searchApiBootstrapGeneration.value
                val bootstrapJustPersisted = bootstrapGen > observedBootstrapGen
                if (bootstrapJustPersisted) {
                    observedBootstrapGen = bootstrapGen
                }
                val retryAfterBootstrapPersist = bootstrapJustPersisted &&
                    shouldRefreshAfterBootstrapPersist(modeActive, _state.value.error)
                observedSettingsKey = key
                if (
                    settings.searchApiBaseUrl.isNotBlank() &&
                    (bootstrapJustPersisted || settingsChanged)
                ) {
                    refreshSearchSuggestions(_state.value.query)
                }
                if ((settingsChanged || apiReadyAfterBootstrap || retryAfterBootstrapPersist) &&
                    modeActive
                ) {
                    _state.value = _state.value.copy(
                        loading = true,
                        error = null,
                        errorCode = null,
                        info = null,
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
            suggestJob?.cancel()
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
        val clearingGenre = X1337MovieGenre.fromId(_state.value.activeGenre)?.let { genre ->
            trimmed != genre.label
        } == true
        val modeCleared = clearingBrowse || clearingGenre
        _state.value = _state.value.copy(
            query = q,
            loading = if (stale || modeCleared) false else _state.value.loading,
            results = if (stale || modeCleared) emptyList() else _state.value.results,
            groups = if (stale || modeCleared) emptyList() else _state.value.groups,
            error = if (stale || modeCleared) null else _state.value.error,
            errorCode = if (stale || modeCleared) null else _state.value.errorCode,
            info = if (stale || modeCleared) null else _state.value.info,
            hasSearched = if (stale || modeCleared) false else _state.value.hasSearched,
            showTmdbSetupHint = if (stale || modeCleared) false else _state.value.showTmdbSetupHint,
            activeBrowseFeed = if (clearingBrowse) null else _state.value.activeBrowseFeed,
            genrePanelExpanded = if (clearingGenre) false else _state.value.genrePanelExpanded,
            activeGenre = if (clearingGenre) null else _state.value.activeGenre,
            lastExecutedQuery = if (modeCleared) "" else _state.value.lastExecutedQuery,
            suggestions = if (stale || modeCleared) emptyList() else _state.value.suggestions,
            suggestionsLoading = if (stale || modeCleared) false else _state.value.suggestionsLoading,
        )
        refreshSearchSuggestions(q)
        if (revertingToLastSearch) {
            val movieSitesOnly = container.settingsRepository.load().movieSitesOnly
            val labelFeed = X1337BrowseFeed.entriesList
                .find { it.label.equals(trimmed, ignoreCase = true) }
                ?.takeIf { it.visibleFor(movieSitesOnly) }
            if (labelFeed != null) {
                loadBrowse1337x(labelFeed)
            } else {
                val labelGenre = X1337MovieGenre.entriesList.find { it.label.equals(trimmed, ignoreCase = true) }
                if (labelGenre != null && _state.value.genrePanelExpanded) {
                    loadGenreBrowse(labelGenre)
                } else {
                    search()
                }
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

    fun selectSearchSuggestion(suggestion: MovieSearchSuggestion) {
        suggestJob?.cancel()
        val q = torrentSearchQuery(suggestion.title, suggestion.year)
        _state.value = _state.value.copy(
            query = q,
            suggestions = emptyList(),
            suggestionsLoading = false,
        )
        search()
    }

    fun dismissSearchSuggestions() {
        suggestJob?.cancel()
        _state.value = _state.value.copy(suggestions = emptyList(), suggestionsLoading = false)
    }

    private fun refreshSearchSuggestions(raw: String) {
        suggestJob?.cancel()
        val trimmed = raw.trim()
        val snapshot = _state.value
        if (
            !shouldLoadSearchSuggestions(
                trimmed,
                snapshot.activeBrowseFeed,
                snapshot.activeGenre,
                snapshot.genrePanelExpanded,
                snapshot.hasSearched,
                snapshot.lastExecutedQuery,
            )
        ) {
            _state.value = snapshot.copy(suggestions = emptyList(), suggestionsLoading = false)
            return
        }
        if (container.settingsRepository.load().searchApiBaseUrl.isBlank()) {
            _state.value = snapshot.copy(suggestions = emptyList(), suggestionsLoading = false)
            return
        }
        val generation = ++suggestGeneration
        suggestJob = viewModelScope.launch {
            delay(SEARCH_SUGGEST_DEBOUNCE_MS)
            if (generation != suggestGeneration) return@launch
            _state.value = _state.value.copy(
                suggestions = emptyList(),
                suggestionsLoading = true,
            )
            try {
                val items = container.searchRepository.suggest(trimmed)
                if (generation == suggestGeneration) {
                    _state.value = _state.value.copy(
                        suggestions = items,
                        suggestionsLoading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (generation == suggestGeneration) {
                    _state.value = _state.value.copy(
                        suggestions = emptyList(),
                        suggestionsLoading = false,
                    )
                }
            }
        }
    }

    fun recordGenreBranchFeedback(
        resultId: String,
        success: Boolean,
        genreIdOverride: String? = null,
    ) {
        val genreId = resolveGenreForRankingFeedback(
            genreIdOverride,
            _state.value.activeGenre,
            lastSearchActiveGenre,
        ) ?: return
        val fromGroupRelease = _state.value.groups
            .flatMap { it.releases }
            .firstOrNull { it.id == resultId }
            ?.branchKey
        val fromFlat = _state.value.results.firstOrNull { it.id == resultId }?.branchKey
        val fromStore = container.searchResultStore.get(resultId)?.branchKey
        val fromGroup = _state.value.groups.firstOrNull { group ->
            group.releases.any { it.id == resultId }
        }?.groupKey?.takeUnless { it.startsWith("flat-") }
        val groupKey = fromGroupRelease ?: fromFlat ?: fromStore ?: fromGroup ?: return
        viewModelScope.launch {
            container.searchRepository.recordGenreBranchFeedback(genreId, groupKey, success)
        }
    }

    fun refreshCurrentResults(forceRefresh: Boolean = true) {
        val genre = X1337MovieGenre.fromId(_state.value.activeGenre)
        if (genre != null) {
            loadGenreBrowse(genre, forceRefresh = forceRefresh)
            return
        }
        val feed = X1337BrowseFeed.fromId(_state.value.activeBrowseFeed)
        if (feed != null) {
            loadBrowse1337x(feed)
            return
        }
        if (_state.value.genrePanelExpanded) {
            val q = _state.value.query.trim()
            if (q.isNotEmpty() && _state.value.hasSearched) {
                search()
                return
            }
            searchJob?.cancel()
            val generation = ++searchGeneration
            _state.value = _state.value.copy(loading = true)
            searchJob = viewModelScope.launch {
                try {
                    container.searchRepository.warmGenrePools()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Non-blocking prefetch
                } finally {
                    if (generation == searchGeneration && _state.value.loading) {
                        _state.value = _state.value.copy(loading = false)
                    }
                }
            }
            return
        }
        val q = _state.value.query.trim()
        if (q.isNotEmpty() && _state.value.hasSearched) {
            search()
            return
        }
        _state.value = _state.value.copy(loading = false)
    }

    fun expandGenrePanel() {
        searchJob?.cancel()
        suggestJob?.cancel()
        searchGeneration += 1
        suggestGeneration += 1
        lastSearchedQuery = null
        _state.value = _state.value.copy(
            genrePanelExpanded = true,
            activeBrowseFeed = null,
            activeGenre = null,
            query = "",
            results = emptyList(),
            groups = emptyList(),
            error = null,
            errorCode = null,
            info = null,
            hasSearched = false,
            showTmdbSetupHint = false,
            lastExecutedQuery = "",
            loading = false,
            suggestions = emptyList(),
            suggestionsLoading = false,
        )
        searchJob = viewModelScope.launch {
            try {
                container.searchRepository.warmGenrePools()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // LOD-style background prefetch — non-blocking
            }
        }
    }

    fun collapseGenrePanel() {
        searchJob?.cancel()
        suggestJob?.cancel()
        searchGeneration += 1
        suggestGeneration += 1
        lastSearchedQuery = null
        _state.value = _state.value.copy(
            genrePanelExpanded = false,
            activeGenre = null,
            query = "",
            results = emptyList(),
            groups = emptyList(),
            error = null,
            errorCode = null,
            info = null,
            hasSearched = false,
            showTmdbSetupHint = false,
            lastExecutedQuery = "",
            loading = false,
            suggestions = emptyList(),
            suggestionsLoading = false,
        )
    }

    fun loadBrowse1337x(feed: X1337BrowseFeed) {
        searchJob?.cancel()
        suggestJob?.cancel()
        val settings = container.settingsRepository.load()
        if (settings.searchApiBaseUrl.isBlank()) {
            _state.value = _state.value.copy(
                query = feed.label,
                loading = false,
                error = searchApiBlockedMessage(),
                errorCode = null,
                results = emptyList(),
                groups = emptyList(),
                info = null,
                hasSearched = true,
                showTmdbSetupHint = false,
                activeBrowseFeed = feed.id,
                genrePanelExpanded = false,
                activeGenre = null,
                suggestions = emptyList(),
                suggestionsLoading = false,
            )
            return
        }
        val generation = ++searchGeneration
        val label = feed.label
        val keepStale = keepStaleBrowseResults(_state.value.activeBrowseFeed, feed.id)
        val previousResults = if (keepStale) _state.value.results else emptyList()
        val previousGroups = if (keepStale) _state.value.groups else emptyList()
        _state.value = _state.value.copy(
            query = label,
            activeBrowseFeed = feed.id,
            genrePanelExpanded = false,
            activeGenre = null,
            loading = true,
            hasSearched = true,
            error = null,
            errorCode = null,
            info = null,
            results = previousResults,
            groups = previousGroups,
            suggestions = emptyList(),
            suggestionsLoading = false,
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
                    generation = generation,
                    outcome = outcome,
                    executedLabel = label,
                    minSeeds = minSeeds,
                    maxSeeds = maxSeeds,
                    maxSize = maxSize,
                    activeBrowseFeed = feed.id,
                    activeGenre = null,
                    emptyResultsMessage = "No torrents in this 1337x list. Try another feed.",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SearchException) {
                if (!requestStillCurrent()) return@launch
                handleSearchFailure(
                    label, minSeeds, maxSeeds, maxSize, settingsKeyAtStart, e.message, e.httpCode,
                    previousResults, previousGroups,
                )
            } catch (e: Exception) {
                if (!requestStillCurrent()) return@launch
                handleSearchFailure(
                    label, minSeeds, maxSeeds, maxSize, settingsKeyAtStart, e.message, null,
                    previousResults, previousGroups,
                )
            } finally {
                if (generation == searchGeneration && _state.value.loading) {
                    _state.value = _state.value.copy(loading = false)
                }
            }
        }
    }

    fun loadGenreBrowse(genre: X1337MovieGenre, forceRefresh: Boolean = false) {
        searchJob?.cancel()
        suggestJob?.cancel()
        val settings = container.settingsRepository.load()
        if (settings.searchApiBaseUrl.isBlank()) {
            _state.value = _state.value.copy(
                query = genre.label,
                loading = false,
                error = searchApiBlockedMessage(),
                errorCode = null,
                results = emptyList(),
                groups = emptyList(),
                info = null,
                hasSearched = true,
                showTmdbSetupHint = false,
                activeBrowseFeed = null,
                genrePanelExpanded = true,
                activeGenre = genre.id,
                suggestions = emptyList(),
                suggestionsLoading = false,
            )
            return
        }
        val generation = ++searchGeneration
        val label = genre.label
        val keepStale = keepStaleBrowseResults(_state.value.activeGenre, genre.id)
        val previousResults = if (keepStale) _state.value.results else emptyList()
        val previousGroups = if (keepStale) _state.value.groups else emptyList()
        _state.value = _state.value.copy(
            query = label,
            activeBrowseFeed = null,
            genrePanelExpanded = true,
            activeGenre = genre.id,
            loading = true,
            hasSearched = true,
            error = null,
            errorCode = null,
            info = null,
            results = previousResults,
            groups = previousGroups,
            suggestions = emptyList(),
            suggestionsLoading = false,
        )
        searchJob = viewModelScope.launch {
            val minSeeds = _state.value.minSeeds
            val maxSeeds = _state.value.maxSeeds
            val maxSize = _state.value.maxSize
            val settingsKeyAtStart = searchSettingsKey()
            fun requestStillCurrent(): Boolean {
                return generation == searchGeneration &&
                    _state.value.activeGenre == genre.id &&
                    _state.value.minSeeds == minSeeds &&
                    _state.value.maxSeeds == maxSeeds &&
                    _state.value.maxSize == maxSize &&
                    searchSettingsKey() == settingsKeyAtStart
            }
            try {
                val outcome = container.searchRepository.browseGenre(
                    genre.id,
                    minSeeds = minSeeds,
                    maxSeeds = maxSeeds,
                    maxSize = maxSize,
                    forceRefresh = forceRefresh,
                )
                if (!requestStillCurrent()) return@launch
                applySuccessfulOutcome(
                    generation = generation,
                    outcome = outcome,
                    executedLabel = label,
                    minSeeds = minSeeds,
                    maxSeeds = maxSeeds,
                    maxSize = maxSize,
                    activeBrowseFeed = null,
                    activeGenre = genre.id,
                    emptyResultsMessage = "No torrents for this genre. Try another genre or relax filters.",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SearchException) {
                if (!requestStillCurrent()) return@launch
                handleSearchFailure(
                    label, minSeeds, maxSeeds, maxSize, settingsKeyAtStart, e.message, e.httpCode,
                    previousResults, previousGroups,
                )
            } catch (e: Exception) {
                if (!requestStillCurrent()) return@launch
                handleSearchFailure(
                    label, minSeeds, maxSeeds, maxSize, settingsKeyAtStart, e.message, null,
                    previousResults, previousGroups,
                )
            } finally {
                if (generation == searchGeneration && _state.value.loading) {
                    _state.value = _state.value.copy(loading = false)
                }
            }
        }
    }

    fun search() {
        val activeGenre = X1337MovieGenre.fromId(_state.value.activeGenre)
        if (activeGenre != null) {
            loadGenreBrowse(activeGenre)
            return
        }
        val activeFeed = X1337BrowseFeed.fromId(_state.value.activeBrowseFeed)
        if (activeFeed != null) {
            loadBrowse1337x(activeFeed)
            return
        }
        val q = _state.value.query.trim()
        searchJob?.cancel()
        if (q.isEmpty()) {
            if (
                _state.value.genrePanelExpanded &&
                _state.value.activeGenre == null &&
                _state.value.activeBrowseFeed == null
            ) {
                _state.value = _state.value.copy(loading = false)
                return
            }
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
                genrePanelExpanded = false,
                activeGenre = null,
            )
            return
        }
        val settings = container.settingsRepository.load()
        if (settings.searchApiBaseUrl.isBlank()) {
            _state.value = _state.value.copy(
                loading = false,
                error = searchApiBlockedMessage(),
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
        val movieSitesOnly = container.settingsRepository.load().movieSitesOnly
        val labelFeed = if (!_state.value.genrePanelExpanded) {
            X1337BrowseFeed.entriesList
                .find { it.label.equals(q, ignoreCase = true) }
                ?.takeIf { it.visibleFor(movieSitesOnly) }
        } else {
            null
        }
        if (labelFeed != null) {
            loadBrowse1337x(labelFeed)
            return
        }
        val labelGenre = if (_state.value.genrePanelExpanded || _state.value.activeGenre != null) {
            X1337MovieGenre.entriesList.find { it.label.equals(q, ignoreCase = true) }
        } else {
            null
        }
        if (labelGenre != null) {
            loadGenreBrowse(labelGenre)
            return
        }
        val generation = ++searchGeneration
        val minSeeds = _state.value.minSeeds
        val maxSeeds = _state.value.maxSeeds
        val maxSize = _state.value.maxSize
        val settingsKeyAtStart = searchSettingsKey()
        val keepStaleResults = keepStaleBrowseResults(lastSearchedQuery, q)
        val previousResults = if (keepStaleResults) _state.value.results else emptyList()
        val previousGroups = if (keepStaleResults) _state.value.groups else emptyList()
        _state.value = _state.value.copy(
            loading = true,
            error = null,
            errorCode = null,
            info = null,
            activeBrowseFeed = null,
            activeGenre = null,
            genrePanelExpanded = false,
            results = previousResults,
            groups = previousGroups,
            suggestions = emptyList(),
            suggestionsLoading = false,
        )
        suggestJob?.cancel()
        searchJob = viewModelScope.launch {
            fun requestStillCurrent(): Boolean {
                return generation == searchGeneration &&
                    _state.value.query.trim() == q &&
                    _state.value.minSeeds == minSeeds &&
                    _state.value.maxSeeds == maxSeeds &&
                    _state.value.maxSize == maxSize &&
                    searchSettingsKey() == settingsKeyAtStart
            }
            try {
                val outcome = container.searchRepository.search(
                    q,
                    minSeeds = minSeeds,
                    maxSeeds = maxSeeds,
                    maxSize = maxSize,
                )
                if (!requestStillCurrent()) return@launch
                applySuccessfulOutcome(
                    generation = generation,
                    outcome = outcome,
                    executedLabel = q,
                    minSeeds = minSeeds,
                    maxSeeds = maxSeeds,
                    maxSize = maxSize,
                    activeBrowseFeed = null,
                    activeGenre = null,
                    emptyResultsMessage = "No results found. Try a broader query.",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SearchException) {
                if (!requestStillCurrent()) return@launch
                handleSearchFailure(
                    q, minSeeds, maxSeeds, maxSize, settingsKeyAtStart, e.message, e.httpCode,
                    previousResults, previousGroups,
                )
            } catch (e: Exception) {
                if (!requestStillCurrent()) return@launch
                handleSearchFailure(
                    q, minSeeds, maxSeeds, maxSize, settingsKeyAtStart, e.message, null,
                    previousResults, previousGroups,
                )
            } finally {
                if (generation == searchGeneration && _state.value.loading) {
                    _state.value = _state.value.copy(loading = false)
                }
            }
        }
    }

    private suspend fun applySuccessfulOutcome(
        generation: Int,
        outcome: SearchResult,
        executedLabel: String,
        minSeeds: Int?,
        maxSeeds: Int?,
        maxSize: String?,
        activeBrowseFeed: String?,
        activeGenre: String?,
        emptyResultsMessage: String,
    ) {
        val infoMessages = mutableListOf<String>()
        if (minSeeds != null && activeBrowseFeed == null && activeGenre == null) {
            infoMessages += "Min seeds filter may hide YTS and other indexers without seed counts."
        }
        if (minSeeds != null && (activeBrowseFeed != null || activeGenre != null)) {
            infoMessages += "Min seeds filter may hide torrents without seed counts."
        }
        if (maxSeeds != null) {
            infoMessages += "Max seeds filter may hide indexers without seed counts."
        }
        if (maxSize != null && (activeBrowseFeed != null || activeGenre != null)) {
            infoMessages += "Max size filter may hide larger releases in this list."
        }
        if (
            activeBrowseFeed != null &&
            (minSeeds != null || maxSeeds != null || maxSize != null)
        ) {
            infoMessages += "If the list is empty, relax filters — they may hide all browse results."
        }
        if (
            activeGenre != null &&
            (minSeeds != null || maxSeeds != null || maxSize != null)
        ) {
            infoMessages += "If the list is empty, relax filters — they may hide all genre results."
        }
        if (outcome.tmdbEnrichmentCapped) {
            infoMessages += ENRICHMENT_CAPPED_MESSAGE
        }
        if (outcome.tmdbKeyRejected) {
            infoMessages += "TMDB key in Settings was rejected — using server key or no enrichment."
        }
        if (outcome.failedSites.isNotEmpty()) {
            infoMessages += "Some sources failed: ${outcome.failedSites.joinToString()}"
        }
        val display = normalizeKodiGroups(outcome.groups, outcome.results)
        if (!shouldCommitSearchOutcome(generation, searchGeneration)) return
        val settings = container.settingsRepository.load()
        val anyPoster = display.groups.any { group ->
            !group.posterUrl.isNullOrBlank() ||
                group.releases.any { !it.posterUrl.isNullOrBlank() }
        }
        val hasBundledTmdb = container.settingsRepository.hasBundledTmdbApiKey()
        val serverTmdbConfigured = if (
            shouldProbeServerTmdb(
                fetchMovieMetadata = settings.fetchMovieMetadata,
                groupsPresent = display.groups.isNotEmpty(),
                anyPosterInGroups = anyPoster,
                clientTmdbKeyBlank = settings.tmdbApiKey.isBlank(),
                hasBundledTmdbApiKey = hasBundledTmdb,
            )
        ) {
            try {
                container.searchRepository.isTmdbConfigured()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        } else {
            true
        }
        val needsTmdbSetup = shouldShowTmdbSetupHint(
            fetchMovieMetadata = settings.fetchMovieMetadata,
            groupsPresent = display.groups.isNotEmpty(),
            anyPosterInGroups = anyPoster,
            clientTmdbKeyBlank = settings.tmdbApiKey.isBlank(),
            serverTmdbConfigured = serverTmdbConfigured,
            hasBundledTmdbApiKey = hasBundledTmdb,
        )
        if (!shouldCommitSearchOutcome(generation, searchGeneration)) return
        val info = infoMessages.takeIf { it.isNotEmpty() }?.joinToString("\n")
        val settingsKey = searchSettingsKey()
        val replaceStoredMetadata = shouldReplaceStoredMetadata(
            lastCommittedSettingsKey = lastSearchSettingsKey,
            currentSettingsKey = settingsKey,
            fetchMovieMetadata = settings.fetchMovieMetadata,
        )
        val allReleases = display.groups.flatMap { it.releases }
        allReleases.forEach {
            container.searchResultStore.put(it, replaceBlankPoster = replaceStoredMetadata)
        }
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
                container.movieMetadataStore.put(
                    release.id,
                    metadata,
                    merge = !replaceStoredMetadata,
                )
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
        lastSearchSettingsKey = settingsKey
        lastSearchActiveBrowseFeed = activeBrowseFeed
        lastSearchActiveGenre = activeGenre
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
            activeGenre = activeGenre,
            genrePanelExpanded = activeGenre != null || _state.value.genrePanelExpanded,
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
        previousResults: List<TorrentResultDto> = emptyList(),
        previousGroups: List<MovieGroupDto> = emptyList(),
    ) {
        val preserveResults = shouldPreserveResultsOnError(
            q, minSeeds, maxSeeds, maxSize, settingsKeyAtStart,
            previousResults, previousGroups,
        )
        lastSearchedQuery = q
        _state.value = _state.value.copy(
            loading = false,
            error = message ?: "Search failed",
            errorCode = httpCode,
            results = if (preserveResults) previousResults else emptyList(),
            groups = if (preserveResults) previousGroups else emptyList(),
            info = null,
            hasSearched = true,
            showTmdbSetupHint = false,
            lastExecutedQuery = if (preserveResults) _state.value.lastExecutedQuery else q,
            activeBrowseFeed = _state.value.activeBrowseFeed,
            activeGenre = _state.value.activeGenre,
            genrePanelExpanded = _state.value.genrePanelExpanded,
        )
    }

    private fun shouldPreserveResultsOnError(
        q: String,
        minSeeds: Int?,
        maxSeeds: Int?,
        maxSize: String?,
        settingsKeyAtStart: String,
        previousResults: List<TorrentResultDto>,
        previousGroups: List<MovieGroupDto>,
    ): Boolean {
        return _state.value.hasSearched &&
            lastSearchedQuery == q &&
            lastSearchMinSeeds == minSeeds &&
            lastSearchMaxSeeds == maxSeeds &&
            lastSearchMaxSize == maxSize &&
            lastSearchSettingsKey == settingsKeyAtStart &&
            lastSearchActiveBrowseFeed == _state.value.activeBrowseFeed &&
            lastSearchActiveGenre == _state.value.activeGenre &&
            searchSettingsKey() == settingsKeyAtStart &&
            (previousGroups.isNotEmpty() || previousResults.isNotEmpty())
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
