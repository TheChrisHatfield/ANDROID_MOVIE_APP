package com.torrentmovie.core.data

/** TMDB title pick before torrent indexer search (FR-041). */
data class MovieSearchSuggestion(
    val tmdbId: Int,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
)
