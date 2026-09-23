package com.torrentmovie.core.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.torrentmovie.core.network.MagnetResponseDto
import com.torrentmovie.core.network.SearchApi
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class SearchRepository(private val settingsRepository: SettingsRepository) {
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Genre browse may wait on an in-flight refresh (45s) then fan out indexers.
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private var cachedBaseUrl: String? = null
    private var cachedApi: SearchApi? = null
    private var cachedRevision = -1

    @Synchronized
    private fun api(): SearchApi {
        val settings = settingsRepository.load()
        if (settings.searchApiBaseUrl.isBlank()) {
            throw SearchException("Configure Search API URL in Settings (e.g. http://<PC-IP>:8765)")
        }
        val revision = settingsRepository.revision.value
        val base = settings.searchApiBaseUrl.trimEnd('/') + "/"
        if (cachedApi != null && cachedBaseUrl == base && cachedRevision == revision) {
            return cachedApi!!
        }
        cachedRevision = revision
        cachedBaseUrl = base
        cachedApi = Retrofit.Builder()
            .baseUrl(base)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SearchApi::class.java)
        return cachedApi!!
    }

    suspend fun search(
        query: String,
        minSeeds: Int? = null,
        maxSeeds: Int? = null,
        maxSize: String? = null,
    ): SearchResult {
        val settings = settingsRepository.load()
        return try {
            val response = api().search(
                query = query,
                limit = minOf(settings.searchPages * 50, 200),
                pages = settings.searchPages,
                minSeeds = minSeeds,
                maxSeeds = maxSeeds,
                maxSize = maxSize,
                movieProfile = settings.movieSitesOnly,
                tmdbApiKey = settings.tmdbApiKey.takeIf { it.isNotBlank() },
                enrich = settings.fetchMovieMetadata,
            )
            SearchResult(
                results = response.results,
                failedSites = response.failedSites,
                groups = response.groups,
                tmdbKeyRejected = response.tmdbKeyRejected,
                tmdbEnrichmentCapped = response.tmdbEnrichmentCapped,
            )
        } catch (e: HttpException) {
            throw mapHttpError(e)
        } catch (e: IllegalArgumentException) {
            throw SearchException("Invalid search API URL — check Settings", cause = e)
        } catch (e: IOException) {
            throw mapNetworkError(e)
        }
    }

    suspend fun browse1337x(
        feed: String,
        minSeeds: Int? = null,
        maxSeeds: Int? = null,
        maxSize: String? = null,
    ): SearchResult {
        val settings = settingsRepository.load()
        return try {
            val response = api().browse1337x(
                feed = feed,
                limit = minOf(settings.searchPages * 50, 200),
                pages = settings.searchPages,
                minSeeds = minSeeds,
                maxSeeds = maxSeeds,
                maxSize = maxSize,
                movieProfile = settings.movieSitesOnly,
                tmdbApiKey = settings.tmdbApiKey.takeIf { it.isNotBlank() },
                enrich = settings.fetchMovieMetadata,
            )
            SearchResult(
                results = response.results,
                failedSites = response.failedSites,
                groups = response.groups,
                tmdbKeyRejected = response.tmdbKeyRejected,
                tmdbEnrichmentCapped = response.tmdbEnrichmentCapped,
            )
        } catch (e: HttpException) {
            throw mapHttpError(e)
        } catch (e: IllegalArgumentException) {
            throw SearchException("Invalid search API URL — check Settings", cause = e)
        } catch (e: IOException) {
            throw mapNetworkError(e)
        }
    }

    suspend fun warmGenrePools(genreIds: List<String>? = null) {
        val settings = settingsRepository.load()
        if (settings.searchApiBaseUrl.isBlank()) return
        try {
            val genres = genreIds?.joinToString(",")
            api().warmGenrePools(
                genres = genres,
                movieProfile = settings.movieSitesOnly,
            )
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            // Background prefetch — ignore failures
        }
    }

    suspend fun browseGenre(
        genre: String,
        minSeeds: Int? = null,
        maxSeeds: Int? = null,
        maxSize: String? = null,
        forceRefresh: Boolean = false,
    ): SearchResult {
        val settings = settingsRepository.load()
        suspend fun requestOnce(): SearchResult {
            val response = api().browseGenre(
                genre = genre,
                limit = minOf(settings.searchPages * 50, 200),
                pages = settings.searchPages,
                minSeeds = minSeeds,
                maxSeeds = maxSeeds,
                maxSize = maxSize,
                movieProfile = settings.movieSitesOnly,
                tmdbApiKey = settings.tmdbApiKey.takeIf { it.isNotBlank() },
                enrich = settings.fetchMovieMetadata,
                forceRefresh = forceRefresh,
            )
            return SearchResult(
                results = response.results,
                failedSites = response.failedSites,
                groups = response.groups,
                tmdbKeyRejected = response.tmdbKeyRejected,
                tmdbEnrichmentCapped = response.tmdbEnrichmentCapped,
            )
        }
        return try {
            requestOnce()
        } catch (e: HttpException) {
            val mapped = mapHttpError(e)
            if (!isTransientGenreRefresh(mapped.httpCode, mapped.message)) throw mapped
            delay(800)
            try {
                requestOnce()
            } catch (retry: HttpException) {
                throw mapHttpError(retry)
            } catch (retry: IllegalArgumentException) {
                throw SearchException("Invalid search API URL — check Settings", cause = retry)
            } catch (retry: IOException) {
                throw mapNetworkError(retry)
            }
        } catch (e: IllegalArgumentException) {
            throw SearchException("Invalid search API URL — check Settings", cause = e)
        } catch (e: IOException) {
            throw mapNetworkError(e)
        }
    }

    suspend fun recordGenreBranchFeedback(
        genreId: String,
        groupKey: String,
        success: Boolean,
    ) {
        val base = settingsRepository.load().searchApiBaseUrl.trim().removeSuffix("/")
        if (base.isBlank()) return
        try {
            api().postGenreBranchFeedback(
                url = "$base/v1/browse/genre/${genreId.trim().lowercase()}/feedback",
                groupKey = groupKey,
                success = success,
            )
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            // Ranking feedback is best-effort
        }
    }

    suspend fun isTmdbConfigured(): Boolean {
        return try {
            api().health().tmdbConfigured
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            false
        }
    }

    suspend fun resolveMagnet(
        resultId: String,
        detailUrl: String? = null,
        site: String? = null,
        name: String? = null,
    ): MagnetResponseDto {
        return try {
            api().getMagnet(resultId)
        } catch (e: HttpException) {
            val resolvedSite = site?.trim().orEmpty()
            if (e.code() == 404 && !detailUrl.isNullOrBlank() && resolvedSite.isNotEmpty()) {
                try {
                    return api().resolveMagnetByDetail(
                        site = resolvedSite,
                        detailUrl = detailUrl,
                        resultId = resultId,
                        name = name,
                    )
                } catch (fallback: HttpException) {
                    throw mapHttpError(fallback)
                } catch (fallback: IOException) {
                    throw mapNetworkError(fallback)
                }
            }
            throw mapHttpError(e)
        } catch (e: IllegalArgumentException) {
            throw SearchException("Invalid search API URL — check Settings", cause = e)
        } catch (e: IOException) {
            throw mapNetworkError(e)
        }
    }

    private fun mapNetworkError(e: IOException): SearchException {
        if (isCanceledNetwork(e)) {
            throw CancellationException(e.message).apply { initCause(e) }
        }
        val message = when (e) {
            is SocketTimeoutException -> "Search API timed out. Check the URL in Settings."
            is UnknownHostException -> "Cannot reach search API. Check the URL in Settings."
            else -> "Cannot connect to search API. Is it running on the configured host and port?"
        }
        return SearchException(message, cause = e)
    }

    private fun mapHttpError(e: HttpException): SearchException = mapSearchHttpError(e, gson)
}

internal fun Exception.rethrowIfCancelled() {
    if (this is CancellationException) throw this
}

internal fun isCanceledNetwork(e: IOException): Boolean {
    val message = e.message.orEmpty()
    return message.contains("Canceled", ignoreCase = true) ||
        message.contains("cancelled", ignoreCase = true)
}

internal fun isTransientGenreRefresh(httpCode: Int?, message: String?): Boolean {
    return httpCode == 503 &&
        message.orEmpty().contains("still refreshing", ignoreCase = true)
}

internal fun mapSearchHttpError(e: HttpException, gson: Gson): SearchException {
    val detail = parseSearchErrorDetail(e, gson)
    return when (e.code()) {
        503 -> SearchException(detail ?: "No sources available", 503, e)
        404 -> SearchException(
            when {
                detail.equals("Not Found", ignoreCase = true) ->
                    "Search API endpoint missing — restart search service (uvicorn on :8765)"
                else -> detail ?: "Not found"
            },
            404,
            e,
        )
        else -> SearchException(detail ?: "Request failed (${e.code()})", e.code(), e)
    }
}

internal fun parseSearchErrorDetail(e: HttpException, gson: Gson): String? {
    val body = e.response()?.errorBody()?.string() ?: return null
    return try {
        val json = gson.fromJson(body, JsonObject::class.java)
        formatErrorDetail(json.get("detail"))
    } catch (_: Exception) {
        null
    }
}

internal fun formatErrorDetail(detail: JsonElement?): String? {
    if (detail == null || detail.isJsonNull) return null
    return when {
        detail.isJsonPrimitive -> detail.asString
        detail.isJsonArray -> {
            val messages = detail.asJsonArray.mapNotNull { item ->
                when {
                    item.isJsonObject -> item.asJsonObject.get("msg")?.asString
                    item.isJsonPrimitive -> item.asString
                    else -> null
                }
            }
            messages.takeIf { it.isNotEmpty() }?.joinToString("; ")
        }
        else -> null
    }
}
