package com.torrentmovie.core.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SearchApi {
    @GET("/v1/health")
    suspend fun health(): Map<String, String>

    @GET("/v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 50,
        @Query("min_seeds") minSeeds: Int? = null,
        @Query("max_size") maxSize: String? = null,
        @Query("parallel") parallel: Boolean = true,
        @Query("movie_profile") movieProfile: Boolean = true,
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
)

data class SearchResponseDto(
    val query: String,
    val count: Int,
    val results: List<TorrentResultDto>,
    val failed_sites: List<String> = emptyList(),
) {
    val failedSites: List<String> get() = failed_sites
}

data class MagnetResponseDto(
    val id: String,
    val magnet: String,
)
