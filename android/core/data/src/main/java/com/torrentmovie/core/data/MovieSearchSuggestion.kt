package com.torrentmovie.core.data

/** TMDB title pick before torrent indexer search (FR-041). */
data class MovieSearchSuggestion(
    val tmdbId: Int,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
)

internal fun mergeSearchSuggestions(
    movie: List<MovieSearchSuggestion>,
    tv: List<MovieSearchSuggestion>,
    limit: Int,
): List<MovieSearchSuggestion> {
    if (limit <= 0) return emptyList()
    val seen = mutableSetOf<String>()
    val out = mutableListOf<MovieSearchSuggestion>()
    val movieQ = ArrayDeque(movie)
    val tvQ = ArrayDeque(tv)
    while (out.size < limit && (movieQ.isNotEmpty() || tvQ.isNotEmpty())) {
        fun take(from: ArrayDeque<MovieSearchSuggestion>) {
            while (from.isNotEmpty() && out.size < limit) {
                val next = from.removeFirst()
                val key = next.title.trim().lowercase()
                if (key.isEmpty() || !seen.add(key)) continue
                out += next
                return
            }
        }
        take(movieQ)
        take(tvQ)
    }
    return out
}
