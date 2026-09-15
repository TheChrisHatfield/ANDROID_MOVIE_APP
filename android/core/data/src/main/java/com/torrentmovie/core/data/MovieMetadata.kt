package com.torrentmovie.core.data

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

    fun clear() {
        synchronized(this) { byResultId.clear() }
    }
}
