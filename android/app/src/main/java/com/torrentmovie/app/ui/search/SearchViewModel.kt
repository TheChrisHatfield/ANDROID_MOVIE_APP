package com.torrentmovie.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.SearchException
import com.torrentmovie.core.network.TorrentResultDto
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

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun setMinSeeds(value: Int?) {
        _state.value = _state.value.copy(minSeeds = value)
    }

    fun setMaxSize(value: String?) {
        _state.value = _state.value.copy(maxSize = value?.takeIf { it.isNotBlank() })
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                results = emptyList(),
                error = null,
                errorCode = null,
                info = null,
            )
            try {
                val outcome = container.searchRepository.search(
                    q,
                    minSeeds = _state.value.minSeeds,
                    maxSize = _state.value.maxSize,
                )
                val infoMessages = mutableListOf<String>()
                if (outcome.failedSites.isNotEmpty()) {
                    infoMessages += "Some sources failed: ${outcome.failedSites.joinToString()}"
                }
                if (_state.value.minSeeds != null && _state.value.minSeeds!! > 0) {
                    infoMessages += "Releases with unknown seeds (e.g. YTS) are hidden when min seeds is set"
                }
                val info = infoMessages.takeIf { it.isNotEmpty() }?.joinToString("\n")
                val emptyMessage = if (outcome.results.isEmpty() && info == null) {
                    "No results found. Try a broader query."
                } else null

                _state.value = _state.value.copy(
                    loading = false,
                    results = outcome.results,
                    hasSearched = true,
                    info = info,
                    error = emptyMessage,
                    errorCode = null,
                )
            } catch (e: SearchException) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Search failed",
                    errorCode = e.httpCode,
                    results = emptyList(),
                    hasSearched = true,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Search failed",
                    errorCode = null,
                    results = emptyList(),
                    hasSearched = true,
                )
            }
        }
    }
}
