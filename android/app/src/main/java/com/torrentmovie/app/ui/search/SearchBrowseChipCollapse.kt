package com.torrentmovie.app.ui.search

/** Collapse genre/list chips while scrolling so results keep more screen space. */
internal fun shouldCollapseBrowseChipsOnScroll(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    scrollOffsetThresholdPx: Int = 32,
): Boolean = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > scrollOffsetThresholdPx

internal fun shouldShowCollapsedBrowseBar(collapsed: Boolean): Boolean = collapsed

internal fun collapsedBrowseBarLabel(
    genrePanelExpanded: Boolean,
    activeGenre: String?,
    activeBrowseFeed: String?,
    movieSitesOnly: Boolean,
): String {
    val genre = X1337MovieGenre.fromId(activeGenre)
    if (genre != null) {
        return "Genre: ${genre.buttonLabel}"
    }
    val feed = X1337BrowseFeed.fromId(activeBrowseFeed)
    if (feed != null) {
        return "List: ${feed.buttonLabel}"
    }
    if (genrePanelExpanded) {
        return "Pick a genre"
    }
    val feeds = X1337BrowseFeed.entriesFor(movieSitesOnly)
    if (feeds.size == 1) {
        return "List: ${feeds.first().buttonLabel}"
    }
    return "Browse lists & genres"
}

internal fun expandBrowseChipsActionLabel(genrePanelExpanded: Boolean): String =
    if (genrePanelExpanded) "Show genres" else "Show lists"
