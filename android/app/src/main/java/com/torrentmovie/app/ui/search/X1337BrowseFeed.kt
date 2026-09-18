package com.torrentmovie.app.ui.search

enum class X1337BrowseFeed(val id: String, val label: String, val buttonLabel: String) {
    TRENDING("trending", "1337x Trending", "Trending"),
    TOP_100("top-100", "1337x Top 100", "Top 100"),
    TOP_100_MOVIES("top-100-movies", "1337x Top 100 Movies", "Top Movies"),
    TOP_100_TV("top-100-television", "1337x Top 100 Television", "Top TV"),
    ;

    companion object {
        val entriesList: List<X1337BrowseFeed> = entries

        fun fromId(id: String?): X1337BrowseFeed? = entries.firstOrNull { it.id == id }
    }
}
