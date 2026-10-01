package com.torrentmovie.core.data.ondevice

import com.torrentmovie.core.data.MovieSearchSuggestion
import com.torrentmovie.core.data.SearchContentFilter
import com.torrentmovie.core.data.SearchException
import com.torrentmovie.core.data.SearchResult
import com.torrentmovie.core.network.MagnetResponseDto

internal class OnDeviceSearchEngine(
    private val http: IndexerHttp = IndexerHttp(),
    private val cache: ResultCache = ResultCache(),
) {
    private val indexers = MovieIndexers(http)

    fun suggest(query: String, tmdbKey: String, limit: Int, searchTv: Boolean = false): List<MovieSearchSuggestion> {
        if (!TmdbOnDevice(http, tmdbKey, searchTv = searchTv).configured) return emptyList()
        return TmdbOnDevice(http, tmdbKey, searchTv = searchTv).suggest(query, limit)
    }

    fun isTmdbConfigured(tmdbKey: String): Boolean = tmdbKey.isNotBlank()

    fun search(
        query: String,
        pages: Int,
        limit: Int,
        minSeeds: Int?,
        maxSeeds: Int?,
        maxSize: String?,
        contentFilter: SearchContentFilter,
        enrich: Boolean,
        tmdbKey: String,
    ): SearchResult {
        val includeYts = contentFilter.usesMovieCatalog
        val (raw, failed) = indexers.searchAll(query, pages.coerceIn(1, 10), includeYts = includeYts)
        return finish(
            raw,
            failed,
            limit,
            minSeeds,
            maxSeeds,
            maxSize,
            contentFilter,
            enrich,
            tmdbKey,
            expectedSites = if (includeYts) 6 else 5,
        )
    }

    fun browse1337x(
        feed: String,
        pages: Int,
        limit: Int,
        minSeeds: Int?,
        maxSeeds: Int?,
        maxSize: String?,
        contentFilter: SearchContentFilter,
        enrich: Boolean,
        tmdbKey: String,
    ): SearchResult {
        if (feed == "top-100-television" && contentFilter == SearchContentFilter.MOVIES) {
            return SearchResult(emptyList(), emptyList(), emptyList())
        }
        val (raw, errored) = indexers.browse1337x(feed, pages.coerceIn(1, 10))
        val failed = if (errored && raw.isEmpty()) listOf("1337x") else emptyList()
        if (raw.isEmpty() && errored) {
            throw SearchException("No sources available", 503)
        }
        return finish(raw, failed, limit, minSeeds, maxSeeds, maxSize, contentFilter, enrich, tmdbKey, interleave = false)
    }

    fun browseGenre(
        genre: String,
        pages: Int,
        limit: Int,
        minSeeds: Int?,
        maxSeeds: Int?,
        maxSize: String?,
        contentFilter: SearchContentFilter,
        enrich: Boolean,
        tmdbKey: String,
    ): SearchResult {
        val genreId = genre.trim().lowercase()
        val searchTv = contentFilter == SearchContentFilter.TV
        val raw = indexers.browseGenre(
            genreId,
            pages.coerceIn(1, 10),
            includeYts = contentFilter.usesMovieCatalog,
        ).toMutableList()
        val tmdb = TmdbOnDevice(http, tmdbKey, searchTv = searchTv)
        if (tmdb.configured) {
            val extra = java.util.concurrent.Executors.newFixedThreadPool(4)
            try {
                val jobs = mutableListOf<java.util.concurrent.Callable<List<IndexerRow>>>()
                if (contentFilter.usesMovieCatalog) {
                    val movieId = TmdbOnDevice.genreDiscoverId(genreId, searchTv = false)
                    if (movieId != null) {
                        val movies = TmdbOnDevice(http, tmdbKey, searchTv = false)
                            .discover(movieId, page = 1, limit = 8)
                        jobs += movies.take(6).map { movie ->
                            java.util.concurrent.Callable {
                                val q = if (movie.year != null) "${movie.title} ${movie.year}" else movie.title
                                indexers.ytsSearch(q, 1)
                            }
                        }
                    }
                }
                if (contentFilter.usesTvCatalog) {
                    val tvId = TmdbOnDevice.genreDiscoverId(genreId, searchTv = true)
                    if (tvId != null) {
                        val shows = TmdbOnDevice(http, tmdbKey, searchTv = true)
                            .discover(tvId, page = 1, limit = 8)
                        jobs += shows.take(6).map { show ->
                            java.util.concurrent.Callable {
                                val q = if (show.year != null) "${show.title} ${show.year}" else show.title
                                indexers.keywordSearch(q, 1)
                            }
                        }
                    }
                }
                jobs.map { extra.submit(it) }.forEach { raw += it.get() }
            } finally {
                extra.shutdownNow()
            }
        }
        return finish(raw, emptyList(), limit, minSeeds, maxSeeds, maxSize, contentFilter, enrich, tmdbKey, tmdb = tmdb)
    }

    fun resolveMagnet(
        resultId: String,
        detailUrl: String? = null,
        site: String? = null,
        name: String? = null,
    ): MagnetResponseDto {
        cache.magnet(resultId)?.let { return it }
        val cachedRow = cache.row(resultId)
        val url = cachedRow?.detailUrl?.takeIf { it.isNotBlank() }
            ?: detailUrl?.trim()?.takeIf { it.isNotBlank() }
        val row = IndexerRow(
            name = cachedRow?.name?.takeIf { it.isNotBlank() }
                ?: name?.takeIf { it.isNotBlank() }
                ?: "-",
            site = cachedRow?.site?.takeIf { it.isNotBlank() }
                ?: site?.trim().orEmpty(),
            size = cachedRow?.size ?: "-",
            seeds = cachedRow?.seeds ?: "-",
            leeches = cachedRow?.leeches ?: "-",
            date = cachedRow?.date ?: "-",
            magnet = cachedRow?.magnet,
            detailUrl = url,
            posterUrl = cachedRow?.posterUrl,
            overview = cachedRow?.overview,
            trailerYoutubeKey = cachedRow?.trailerYoutubeKey,
        )
        if (row.magnet.isNullOrBlank() && url.isNullOrBlank()) {
            throw SearchException("Result not found or expired", 404)
        }
        val magnet = indexers.resolveMagnet(row)
            ?: throw SearchException("Magnet unavailable", 404)
        cache.putMagnet(resultId, magnet)
        cache.rememberRow(resultId, row.copy(magnet = magnet))
        return MagnetResponseDto(resultId, magnet)
    }

    private fun finish(
        raw: List<IndexerRow>,
        failed: List<String>,
        limit: Int,
        minSeeds: Int?,
        maxSeeds: Int?,
        maxSize: String?,
        contentFilter: SearchContentFilter,
        enrich: Boolean,
        tmdbKey: String,
        interleave: Boolean = true,
        tmdb: TmdbOnDevice? = null,
        expectedSites: Int = MovieIndexers.MOVIE_SITE_NAMES.size,
    ): SearchResult {
        val filtered = try {
            SizeFilters.apply(raw, minSeeds, maxSeeds, maxSize, contentFilter)
        } catch (_: IllegalArgumentException) {
            throw SearchException("Invalid max_size", 400)
        }
        val ordered = if (interleave) {
            SizeFilters.interleaveBySite(filtered, limit)
        } else {
            SizeFilters.sortBySeedsDesc(filtered).take(limit)
        }
        if (ordered.isEmpty() && raw.isEmpty() &&
            failed.size >= expectedSites && expectedSites > 0
        ) {
            throw SearchException("No sources available", 503)
        }
        if (ordered.isEmpty()) {
            return SearchResult(
                results = emptyList(),
                failedSites = failed,
                groups = emptyList(),
                tmdbKeyRejected = tmdb?.keyRejected == true,
            )
        }
        val dtos = cache.remember(ordered)
        val tmdbClient = tmdb ?: TmdbOnDevice(
            http,
            tmdbKey,
            searchTv = contentFilter == SearchContentFilter.TV,
        )
        val grouping = MovieGrouping.group(dtos, ordered, tmdbClient, enrich)
        return SearchResult(
            results = dtos,
            failedSites = failed,
            groups = grouping.groups,
            tmdbKeyRejected = tmdbClient.keyRejected,
            tmdbEnrichmentCapped = grouping.enrichmentCapped,
        )
    }
}
