package com.torrentmovie.core.data.seedbox

import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

class RuTorrentClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    private val authScheme: String = "basic",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : SeedboxClient {

    private val useDigest = authScheme.equals("digest", ignoreCase = true)

    private val addTorrentClient: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun ping(): Boolean {
        return try {
            val request = Request.Builder()
                .url(seedboxAddTorrentUrl(baseUrl))
                .apply { if (!useDigest) header("Authorization", basicAuthHeader()) }
                .get()
                .build()
            executeWithAuth(request).use { response ->
                when {
                    response.code == 401 || response.code == 403 -> false
                    response.code == 405 -> true
                    !response.isSuccessful -> false
                    else -> {
                        val text = response.body?.string()?.trim() ?: ""
                        !(text.contains("<html", ignoreCase = true) &&
                            text.contains("login", ignoreCase = true))
                    }
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun listTorrentStatuses(): SeedboxListResult {
        return try {
            val body = "mode=list".toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val request = Request.Builder()
                .url(seedboxHttprpcUrl(baseUrl))
                .apply { if (!useDigest) header("Authorization", basicAuthHeader()) }
                .post(body)
                .build()
            executeWithAuth(request).use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    !response.isSuccessful -> {
                        val msg = when (response.code) {
                            401 -> "Authentication failed — check seedbox credentials"
                            403 -> "ruTorrent rejected status request"
                            404 -> "HTTPRPC plugin not found — check ruTorrent base URL"
                            else -> "Seedbox status HTTP ${response.code}"
                        }
                        SeedboxListResult.Failure(msg)
                    }
                    text.isBlank() || !text.trimStart().startsWith("{") ->
                        SeedboxListResult.Failure("Unexpected ruTorrent status response")
                    else -> {
                        val statuses = HttprpcTorrentParser.parseListResponse(text)
                            .associateBy { it.infoHash.uppercase() }
                        SeedboxListResult.Success(statuses)
                    }
                }
            }
        } catch (e: Exception) {
            SeedboxListResult.Failure(e.message ?: "Could not load seedbox status")
        }
    }

    override suspend fun addMagnet(magnet: String, downloadDirectory: String): SeedboxResult {
        return try {
            val body = FormBody.Builder()
                .add("url", magnet)
                .add("dir_edit", downloadDirectory)
                .build()
            val request = Request.Builder()
                .url(seedboxAddTorrentUrl(baseUrl))
                .apply { if (!useDigest) header("Authorization", basicAuthHeader()) }
                .post(body)
                .build()
            return executeWithAuth(request, addTorrentClient).use { response ->
                val text = response.body?.string() ?: ""
                when {
                    response.code in 300..399 &&
                        isAddTorrentSuccess(response, text) -> SeedboxResult.Success()
                    !response.isSuccessful -> {
                        val msg = when (response.code) {
                            401 -> "Authentication failed — check username, password, and auth scheme"
                            403 -> "Forbidden — ruTorrent rejected the request"
                            404 -> "ruTorrent URL not found — use https://<user>.<slot>.seedhost.eu/rutorrent/ in Settings (not addtorrent.php)"
                            else -> "HTTP ${response.code}"
                        }
                        SeedboxResult.Failure(msg, response.code)
                    }
                    text.contains("FailedDirectory", ignoreCase = true) ->
                        SeedboxResult.Failure("Invalid download directory")
                    text.contains("Failed", ignoreCase = true) &&
                        !text.contains("Success", ignoreCase = true) ->
                        SeedboxResult.Failure("ruTorrent rejected magnet")
                    isAddTorrentSuccess(response, text) -> SeedboxResult.Success()
                    else -> SeedboxResult.Failure("Unexpected ruTorrent response")
                }
            }
        } catch (e: Exception) {
            SeedboxResult.Failure(e.message ?: "Connection failed")
        }
    }

    private fun basicAuthHeader(): String = Credentials.basic(username, password)

    private fun isAddTorrentSuccess(response: okhttp3.Response, text: String): Boolean {
        if (text.contains("Success", ignoreCase = true)) return true
        val location = response.header("Location").orEmpty()
        if (location.contains("status=Success", ignoreCase = true)) return true
        if (Regex(""""status"\s*:\s*"Success"""", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return true
        }
        return false
    }

    private fun executeWithAuth(
        request: Request,
        httpClient: OkHttpClient = client,
    ): okhttp3.Response {
        val first = httpClient.newCall(request).execute()
        if (!useDigest || first.code != 401) {
            return first
        }
        val challenge = first.header("WWW-Authenticate") ?: return first
        first.body?.close()
        first.close()
        val uri = URI(request.url.toString())
        val path = uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
        val digest = DigestAuth.authorizationHeader(
            challenge,
            request.method,
            path,
            username,
            password,
        ) ?: throw IllegalStateException("Digest authentication failed")
        val authed = request.newBuilder()
            .header("Authorization", digest)
            .build()
        return httpClient.newCall(authed).execute()
    }
}
