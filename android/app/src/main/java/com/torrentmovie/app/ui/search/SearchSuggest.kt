package com.torrentmovie.app.ui.search

/** Torrent search string after picking a TMDB suggestion (title + year improves indexer match). */
internal fun torrentSearchQuery(title: String, year: Int?): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return ""
    return if (year != null && year > 0) "$trimmed $year" else trimmed
}

internal fun shouldLoadSearchSuggestions(
    query: String,
    activeBrowseFeed: String?,
    activeGenre: String?,
    genrePanelExpanded: Boolean,
): Boolean {
    if (activeBrowseFeed != null || activeGenre != null || genrePanelExpanded) return false
    return query.trim().length >= MIN_SEARCH_SUGGEST_CHARS
}

internal const val MIN_SEARCH_SUGGEST_CHARS = 2
internal const val SEARCH_SUGGEST_DEBOUNCE_MS = 350L
