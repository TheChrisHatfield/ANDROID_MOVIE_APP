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
    hasSearched: Boolean = false,
    lastExecutedQuery: String = "",
): Boolean {
    if (activeBrowseFeed != null || activeGenre != null || genrePanelExpanded) return false
    val trimmed = query.trim()
    if (trimmed.length < MIN_SEARCH_SUGGEST_CHARS) return false
    if (hasSearched && trimmed.equals(lastExecutedQuery.trim(), ignoreCase = true)) {
        return false
    }
    return true
}

internal const val MIN_SEARCH_SUGGEST_CHARS = 2
internal const val SEARCH_SUGGEST_DEBOUNCE_MS = 350L
