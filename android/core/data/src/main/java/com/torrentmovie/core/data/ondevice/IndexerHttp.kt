package com.torrentmovie.core.data.ondevice

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal data class HttpTextResult(val code: Int, val body: String?)

internal class IndexerHttp(
    client: OkHttpClient? = null,
) {
    private val http = client ?: OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun get(url: String): ByteArray? = getText(url)?.toByteArray(Charsets.UTF_8)

    fun getText(url: String): String? {
        val result = fetch(url)
        return result.body.takeIf { result.code in 200..299 }
    }

    fun fetch(url: String): HttpTextResult {
        val first = fetchOnce(url)
        if (first.code in 200..299 || first.code == 401 || first.code == 403) {
            return first
        }
        if (first.code == -1 || first.code == 429 || first.code == 503) {
            return fetchOnce(url)
        }
        return first
    }

    private fun fetchOnce(url: String): HttpTextResult {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                HttpTextResult(response.code, response.body?.string())
            }
        } catch (_: Exception) {
            HttpTextResult(-1, null)
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
