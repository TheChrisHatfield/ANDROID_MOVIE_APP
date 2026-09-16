package com.torrentmovie.core.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SearchApi {
    @GET("/v1/health")
    suspend fun health(): HealthResponseDto

    @GET("/v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 100,
        @Query("pages") pages: Int = 2,
        @Query("min_seeds") minSeeds: Int? = null,
        @Query("max_seeds") maxSeeds: Int? = null,
        @Query("max_size") maxSize: String? = null,
        @Query("parallel") parallel: Boolean = true,
        @Query("movie_profile") movieProfile: Boolean = true,
        @Query("group") group: Boolean = true,
        @Query("enrich") enrich: Boolean = true,
        @Query("tmdb_api_key") tmdbApiKey: String? = null,
    ): SearchResponseDto

    @GET("/v1/results/{id}/magnet")
    suspend fun getMagnet(@Path("id") resultId: String): MagnetResponseDto
}

data class TorrentResultDto(
    val id: String,
    val name: String,
    val site: String,
    val size: String? = null,
    val seeds: String? = null,
    val leeches: String? = null,
    val date: String? = null,
    val magnet: String? = null,
    val detail_url: String? = null,
    val poster_url: String? = null,
) {
    val posterUrl: String? get() = poster_url
}

data class MovieGroupDto(
    val group_key: String,
    val title: String,
    val year: Int? = null,
    val overview: String? = null,
    val poster_url: String? = null,
    val trailer_youtube_key: String? = null,
    val release_count: Int,
    val releases: List<TorrentResultDto>,
) {
    val groupKey: String get() = group_key
    val posterUrl: String? get() = poster_url
    val trailerYoutubeKey: String? get() = trailer_youtube_key
    val releaseCount: Int get() = release_count
}

data class SearchResponseDto(
    val query: String,
    val count: Int,
    val total_count: Int? = null,
    val results: List<TorrentResultDto>,
    val failed_sites: List<String> = emptyList(),
    val groups: List<MovieGroupDto> = emptyList(),
    val tmdb_key_rejected: Boolean = false,
    val tmdb_enrichment_capped: Boolean = false,
) {
    val failedSites: List<String> get() = failed_sites
    val tmdbKeyRejected: Boolean get() = tmdb_key_rejected
    val tmdbEnrichmentCapped: Boolean get() = tmdb_enrichment_capped
}

data class MagnetResponseDto(
    val id: String,
    val magnet: String,
)

data class HealthResponseDto(
    val status: String,
    val tmdb_configured: Boolean = false,
) {
    val tmdbConfigured: Boolean get() = tmdb_configured
}
