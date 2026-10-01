package com.torrentmovie.core.data.ondevice

import com.torrentmovie.core.data.MovieSearchSuggestion
import com.torrentmovie.core.data.SearchException
import com.torrentmovie.core.data.SearchResult
import com.torrentmovie.core.network.MagnetResponseDto

internal class OnDeviceSearchEngine(
    private val http: IndexerHttp = IndexerHttp(),
) {
    private val indexers = MovieIndexers(http)
    private val cache = ResultCache()

    fun suggest(query: String, tmdbKey: String, limit: Int): List<MovieSearchSuggestion> {
        if (!TmdbOnDevice(http, tmdbKey).configured) return emptyList()
        return TmdbOnDevice(http, tmdbKey).suggest(query, limit)
    }

    fun isTmdbConfigured(tmdbKey: String): Boolean = tmdbKey.isNotBlank()

    fun search(
        query: String,
        pages: Int,
        limit: Int,
        minSeeds: Int?,
        maxSeeds: Int?,
        maxSize: String?,
        movieProfile: Boolean,
        enrich: Boolean,
        tmdbKey: String,
    ): SearchResult {
        val (raw, failed) = indexers.searchAll(query, pages.coerceIn(1, 10))
        return finish(raw, failed, limit, minSeeds, maxSeeds, maxSize, movieProfile, enrich, tmdbKey)
    }

    fun browse1337x(
        feed: String,
        pages: Int,
        limit: Int,
        minSeeds: Int?,
        maxSeeds: Int?,
        maxSize: String?,
        movieProfile: Boolean,
        enrich: Boolean,
        tmdbKey: String,
    ): SearchResult {
        if (feed == "top-100-television" && movieProfile) {
            return SearchResult(emptyList(), emptyList(), emptyList())
        }
        val (raw, errored) = indexers.browse1337x(feed, pages.coerceIn(1, 10))
        val failed = if (errored && raw.isEmpty()) listOf("1337x") else emptyList()
        if (raw.isEmpty() && errored) {
            throw SearchException("No sources available", 503)
        }
        return finish(raw, failed, limit, minSeeds, maxSeeds, maxSize, movieProfile, enrich, tmdbKey, interleave = false)
    }

    fun browseGenre(
        genre: String,
        pages: Int,
        limit: Int,
        minSeeds: Int?,
        maxSeeds: Int?,
        maxSize: String?,
        movieProfile: Boolean,
        enrich: Boolean,
        tmdbKey: String,
    ): SearchResult {
        val genreId = genre.trim().lowercase()
        val raw = indexers.browseGenre(genreId, pages.coerceIn(1, 10)).toMutableList()
        val tmdb = TmdbOnDevice(http, tmdbKey)
        val tmdbGenre = TmdbOnDevice.GENRE_TMDB_IDS[genreId]
        if (tmdb.configured && tmdbGenre != null) {
            val discover = tmdb.discover(tmdbGenre, page = 1, limit = 12) +
                tmdb.discover(tmdbGenre, page = 2, limit = 12)
            val extra = java.util.concurrent.Executors.newFixedThreadPool(4)
            try {
                discover.take(12).map { movie ->
                    extra.submit(
                        java.util.concurrent.Callable {
                            val q = if (movie.year != null) "${movie.title} ${movie.year}" else movie.title
                            indexers.ytsSearch(q, 1)
                        },
                    )
                }.forEach { raw += it.get() }
            } finally {
                extra.shutdownNow()
            }
        }
        return finish(raw, emptyList(), limit, minSeeds, maxSeeds, maxSize, movieProfile, enrich, tmdbKey)
    }

    fun resolveMagnet(resultId: String): MagnetResponseDto {
        cache.magnet(resultId)?.let { return it }
        val row = cache.row(resultId) ?: throw SearchException("Result not found or expired", 404)
        val magnet = indexers.resolveMagnet(row)
            ?: throw SearchException("Magnet unavailable", 404)
        cache.putMagnet(resultId, magnet)
        return MagnetResponseDto(resultId, magnet)
    }

    private fun finish(
        raw: List<IndexerRow>,
        failed: List<String>,
        limit: Int,
        minSeeds: Int?,
        maxSeeds: Int?,
        maxSize: String?,
        movieProfile: Boolean,
        enrich: Boolean,
        tmdbKey: String,
        interleave: Boolean = true,
    ): SearchResult {
        val filtered = SizeFilters.apply(raw, minSeeds, maxSeeds, maxSize, movieProfile)
        val ordered = if (interleave) {
            SizeFilters.interleaveBySite(filtered, limit)
        } else {
            SizeFilters.sortBySeedsDesc(filtered).take(limit)
        }
        if (ordered.isEmpty() && raw.isEmpty() &&
            failed.size >= MovieIndexers.MOVIE_SITE_NAMES.size
        ) {
            throw SearchException("No sources available", 503)
        }
        if (ordered.isEmpty()) {
            return SearchResult(emptyList(), failed, emptyList())
        }
        val dtos = cache.remember(ordered)
        val tmdb = TmdbOnDevice(http, tmdbKey)
        val groups = MovieGrouping.group(dtos, ordered, tmdb, enrich)
        return SearchResult(
            results = dtos,
            failedSites = failed,
            groups = groups,
            tmdbKeyRejected = false,
            tmdbEnrichmentCapped = false,
        )
    }
}
