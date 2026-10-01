package com.torrentmovie.app.ui.search

import com.torrentmovie.core.data.SearchContentFilter

enum class X1337BrowseFeed(val id: String, val label: String, val buttonLabel: String) {
    TRENDING("trending", "1337x Trending", "Trending"),
    TOP_100("top-100", "1337x Top 100", "Top 100"),
    TOP_100_MOVIES("top-100-movies", "1337x Top 100 Movies", "Top Movies"),
    TOP_100_TV("top-100-television", "1337x Top 100 Television", "Top TV"),
    ;

    fun visibleFor(filter: SearchContentFilter): Boolean = when (this) {
        TOP_100_TV -> filter != SearchContentFilter.MOVIES
        TOP_100_MOVIES -> filter != SearchContentFilter.TV
        TOP_100 -> filter == SearchContentFilter.ALL
        TRENDING -> true
    }

    companion object {
        val entriesList: List<X1337BrowseFeed> = entries

        fun entriesFor(filter: SearchContentFilter): List<X1337BrowseFeed> =
            entries.filter { it.visibleFor(filter) }

        fun fromId(id: String?): X1337BrowseFeed? = entries.firstOrNull { it.id == id }
    }
}
