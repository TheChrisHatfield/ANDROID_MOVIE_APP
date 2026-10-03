package com.torrentmovie.core.data

import com.torrentmovie.core.data.ondevice.OnDeviceSearchEngine
import com.torrentmovie.core.network.MagnetResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchRepository(private val settingsRepository: SettingsRepository) {
    private val engine = OnDeviceSearchEngine()

    suspend fun search(
        query: String,
        minSeeds: Int? = null,
        maxSeeds: Int? = null,
        maxSize: String? = null,
    ): SearchResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.load()
        engine.search(
            query = query,
            pages = settings.searchPages,
            limit = minOf(settings.searchPages * 50, 200),
            minSeeds = minSeeds,
            maxSeeds = maxSeeds,
            maxSize = maxSize,
            contentFilter = settings.contentFilter,
            enrich = settings.fetchMovieMetadata,
            tmdbKey = settings.tmdbApiKey,
        )
    }

    suspend fun suggest(query: String, limit: Int = 8): List<MovieSearchSuggestion> =
        withContext(Dispatchers.IO) {
            val settings = settingsRepository.load()
            engine.suggest(
                query,
                settings.tmdbApiKey,
                limit,
                contentFilter = settings.contentFilter,
            )
        }

    suspend fun browse1337x(
        feed: String,
        minSeeds: Int? = null,
        maxSeeds: Int? = null,
        maxSize: String? = null,
    ): SearchResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.load()
        engine.browse1337x(
            feed = feed,
            pages = settings.searchPages,
            limit = minOf(settings.searchPages * 50, 200),
            minSeeds = minSeeds,
            maxSeeds = maxSeeds,
            maxSize = maxSize,
            contentFilter = settings.contentFilter,
            enrich = settings.fetchMovieMetadata,
            tmdbKey = settings.tmdbApiKey,
        )
    }

    suspend fun warmGenrePools(@Suppress("UNUSED_PARAMETER") genreIds: List<String>? = null) {
        // On-device genre search fills on demand; no PC pool to warm.
    }

    suspend fun browseGenre(
        genre: String,
        minSeeds: Int? = null,
        maxSeeds: Int? = null,
        maxSize: String? = null,
        @Suppress("UNUSED_PARAMETER") forceRefresh: Boolean = false,
    ): SearchResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.load()
        engine.browseGenre(
            genre = genre,
            pages = settings.searchPages,
            limit = minOf(settings.searchPages * 50, 200),
            minSeeds = minSeeds,
            maxSeeds = maxSeeds,
            maxSize = maxSize,
            contentFilter = settings.contentFilter,
            enrich = settings.fetchMovieMetadata,
            tmdbKey = settings.tmdbApiKey,
        )
    }

    suspend fun recordGenreBranchFeedback(
        @Suppress("UNUSED_PARAMETER") genreId: String,
        @Suppress("UNUSED_PARAMETER") groupKey: String,
        @Suppress("UNUSED_PARAMETER") success: Boolean,
    ) {
        // Ranking lives on-device per session; no remote feedback endpoint.
    }

    suspend fun isTmdbConfigured(): Boolean {
        return engine.isTmdbConfigured(settingsRepository.load().tmdbApiKey)
    }

    suspend fun resolveMagnet(
        resultId: String,
        detailUrl: String? = null,
        site: String? = null,
        name: String? = null,
    ): MagnetResponseDto = withContext(Dispatchers.IO) {
        engine.resolveMagnet(resultId, detailUrl, site, name)
    }
}
