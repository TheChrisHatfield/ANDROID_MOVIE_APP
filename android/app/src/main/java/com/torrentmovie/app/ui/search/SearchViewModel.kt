package com.torrentmovie.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.SearchException
import com.torrentmovie.core.network.TorrentResultDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<TorrentResultDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val errorCode: Int? = null,
    val info: String? = null,
    val minSeeds: Int? = null,
    val maxSize: String? = null,
    val hasSearched: Boolean = false,
)

class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var searchGeneration = 0
    private var lastSearchedQuery: String? = null

    fun setQuery(q: String) {
        val trimmed = q.trim()
        val stale = lastSearchedQuery != null && trimmed != lastSearchedQuery
        if (stale) {
            searchJob?.cancel()
            searchGeneration += 1
        }
        _state.value = _state.value.copy(
            query = q,
            loading = if (stale) false else _state.value.loading,
            results = if (stale) emptyList() else _state.value.results,
            error = if (stale) null else _state.value.error,
            errorCode = if (stale) null else _state.value.errorCode,
            info = if (stale) null else _state.value.info,
            hasSearched = if (stale) false else _state.value.hasSearched,
        )
    }

    fun setMinSeeds(value: Int?) {
        _state.value = _state.value.copy(minSeeds = value)
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
                error = null,
                errorCode = null,
                info = null,
                hasSearched = false,
            )
            return
        }
        val generation = ++searchGeneration
        searchJob = viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    loading = true,
                    error = null,
                    errorCode = null,
                    info = null,
                )
                val outcome = container.searchRepository.search(
                    q,
                    minSeeds = _state.value.minSeeds,
                    maxSize = _state.value.maxSize,
                )
                if (generation != searchGeneration || _state.value.query.trim() != q) return@launch
                val infoMessages = mutableListOf<String>()
                if (outcome.failedSites.isNotEmpty()) {
                    infoMessages += "Some sources failed: ${outcome.failedSites.joinToString()}"
                }
                val info = infoMessages.takeIf { it.isNotEmpty() }?.joinToString("\n")
                val emptyMessage = if (outcome.results.isEmpty() && info == null) {
                    "No results found. Try a broader query."
                } else null

                lastSearchedQuery = q
                _state.value = _state.value.copy(
                    loading = false,
                    results = outcome.results,
                    hasSearched = true,
                    info = info,
                    error = emptyMessage,
                    errorCode = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SearchException) {
                if (generation != searchGeneration || _state.value.query.trim() != q) return@launch
                lastSearchedQuery = q
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Search failed",
                    errorCode = e.httpCode,
                    results = emptyList(),
                    hasSearched = true,
                )
            } catch (e: Exception) {
                if (generation != searchGeneration || _state.value.query.trim() != q) return@launch
                lastSearchedQuery = q
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Search failed",
                    errorCode = null,
                    results = emptyList(),
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
