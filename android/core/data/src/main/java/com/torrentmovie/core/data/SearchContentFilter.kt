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

    /** YTS and movie TMDB discover — off in TV-only mode. */
    val usesMovieCatalog: Boolean get() = this != TV

    /** TV TMDB discover and Top TV browse — off in Movies-only mode. */
    val usesTvCatalog: Boolean get() = this != MOVIES

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
