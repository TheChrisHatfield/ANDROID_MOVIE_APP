package com.torrentmovie.core.data

import com.google.gson.Gson
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

class SearchRepository(private val settingsRepository: SettingsRepository) {
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private var cachedBaseUrl: String? = null
    private var cachedApi: SearchApi? = null
    private var cachedRevision = -1

    private fun api(): SearchApi {
        val revision = settingsRepository.revision.value
        val base = settingsRepository.load().searchApiBaseUrl.trimEnd('/') + "/"
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
        pages: Int = 2,
    ): SearchResult {
        val settings = settingsRepository.load()
        return try {
            val response = api().search(
                query = query,
                pages = pages,
                minSeeds = minSeeds,
                maxSeeds = maxSeeds,
                maxSize = maxSize,
                movieProfile = settings.movieSitesOnly,
            )
            SearchResult(
                results = response.results,
                failedSites = response.failedSites,
            )
        } catch (e: HttpException) {
            throw mapHttpError(e)
        } catch (e: IllegalArgumentException) {
            throw SearchException("Invalid search API URL — check Settings", cause = e)
        } catch (e: IOException) {
            throw mapNetworkError(e)
        }
    }

    suspend fun resolveMagnet(resultId: String): MagnetResponseDto {
        return try {
            api().getMagnet(resultId)
        } catch (e: HttpException) {
            throw mapHttpError(e)
        } catch (e: IllegalArgumentException) {
            throw SearchException("Invalid search API URL — check Settings", cause = e)
        } catch (e: IOException) {
            throw mapNetworkError(e)
        }
    }

    private fun mapNetworkError(e: IOException): SearchException {
        val message = when (e) {
            is SocketTimeoutException -> "Search API timed out. Check the URL in Settings."
            is UnknownHostException -> "Cannot reach search API. Check the URL in Settings."
            else -> "Cannot connect to search API. Is it running on the configured host and port?"
        }
        return SearchException(message, cause = e)
    }

    private fun mapHttpError(e: HttpException): SearchException = mapSearchHttpError(e, gson)
}

internal fun mapSearchHttpError(e: HttpException, gson: Gson): SearchException {
    val detail = parseSearchErrorDetail(e, gson)
    return when (e.code()) {
        503 -> SearchException(detail ?: "No sources available", 503, e)
        404 -> SearchException(detail ?: "Not found", 404, e)
        else -> SearchException(detail ?: "Request failed (${e.code()})", e.code(), e)
    }
}

internal fun parseSearchErrorDetail(e: HttpException, gson: Gson): String? {
    val body = e.response()?.errorBody()?.string() ?: return null
    return try {
        val json = gson.fromJson(body, JsonObject::class.java)
        json.get("detail")?.asString
    } catch (_: Exception) {
        null
    }
}
