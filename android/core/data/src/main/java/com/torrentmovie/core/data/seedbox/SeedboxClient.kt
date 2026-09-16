package com.torrentmovie.core.data.seedbox

sealed class SeedboxResult {
    data class Success(val message: String = "Added to seedbox") : SeedboxResult()
    data class Failure(val message: String, val httpCode: Int? = null) : SeedboxResult()
}

interface SeedboxClient {
    suspend fun addMagnet(magnet: String, downloadDirectory: String): SeedboxResult
    suspend fun ping(): Boolean
    suspend fun listTorrentStatuses(): SeedboxListResult
}
