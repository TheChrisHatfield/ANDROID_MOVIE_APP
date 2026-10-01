package com.torrentmovie.core.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import retrofit2.HttpException
import java.io.IOException
import kotlinx.coroutines.CancellationException

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
                    "Magnet not found — search again and pick the release"
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

internal const val SUGGEST_CONNECT_TIMEOUT_SEC = 5L
internal const val SUGGEST_READ_TIMEOUT_SEC = 8L
internal const val SUGGEST_CALL_TIMEOUT_SEC = 10L
