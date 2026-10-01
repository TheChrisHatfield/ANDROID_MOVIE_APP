package com.torrentmovie.core.data

enum class SearchContentFilter {
    MOVIES,
    TV,
    ALL,
    ;

    val storedValue: String
        get() = when (this) {
            MOVIES -> "movies"
            TV -> "tv"
            ALL -> "all"
        }

    companion object {
        fun fromStored(raw: String?, movieSitesOnlyLegacy: Boolean): SearchContentFilter {
            return when (raw?.trim()?.lowercase()) {
                "tv" -> TV
                "all" -> ALL
                "movies" -> MOVIES
                else -> if (movieSitesOnlyLegacy) MOVIES else ALL
            }
        }
    }
}
