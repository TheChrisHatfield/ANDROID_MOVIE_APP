package com.torrentmovie.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MovieMetadata(
    val title: String,
    val year: Int? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val trailerYoutubeKey: String? = null,
)

class MovieMetadataStore {
    private val byResultId = object : LinkedHashMap<String, MovieMetadata>(16, 0.75f, true) {}
    private val maxEntries = 500
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun bumpRevision() {
        _revision.value++
    }

    fun put(resultId: String, metadata: MovieMetadata) {
        synchronized(this) {
            byResultId[resultId] = metadata
            while (byResultId.size > maxEntries) {
                val oldest = byResultId.keys.firstOrNull() ?: break
                byResultId.remove(oldest)
            }
        }
    }

    fun get(resultId: String): MovieMetadata? = synchronized(this) {
        val meta = byResultId.remove(resultId) ?: return null
        byResultId[resultId] = meta
        meta
    }

    fun remove(resultId: String) {
        synchronized(this) { byResultId.remove(resultId) }
    }

    fun clear() {
        synchronized(this) { byResultId.clear() }
    }
}
