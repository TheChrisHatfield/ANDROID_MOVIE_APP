package com.torrentmovie.core.data.seedbox

import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
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

    private fun normalizedBase(): String = normalizeSeedboxUrl(baseUrl)

    override suspend fun ping(): Boolean {
        return try {
            val request = Request.Builder()
                .url(normalizedBase())
                .apply { if (!useDigest) header("Authorization", basicAuthHeader()) }
                .get()
                .build()
            executeWithAuth(request).isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun addMagnet(magnet: String, downloadDirectory: String): SeedboxResult {
        return try {
            val body = FormBody.Builder()
                .add("url", magnet)
                .add("dir_edit", downloadDirectory)
                .build()
            val request = Request.Builder()
                .url("${normalizedBase()}php/addtorrent.php")
                .apply { if (!useDigest) header("Authorization", basicAuthHeader()) }
                .post(body)
                .build()
            val response = executeWithAuth(request)
            val text = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return SeedboxResult.Failure("HTTP ${response.code}", response.code)
            }
            if (text.contains("FailedDirectory", ignoreCase = true)) {
                return SeedboxResult.Failure("Invalid download directory")
            }
            if (text.contains("Failed", ignoreCase = true) && !text.contains("Success", ignoreCase = true)) {
                return SeedboxResult.Failure("ruTorrent rejected magnet")
            }
            if (!text.contains("Success", ignoreCase = true)) {
                return SeedboxResult.Failure("Unexpected ruTorrent response")
            }
            SeedboxResult.Success()
        } catch (e: Exception) {
            SeedboxResult.Failure(e.message ?: "Connection failed")
        }
    }

    private fun basicAuthHeader(): String = Credentials.basic(username, password)

    private fun executeWithAuth(request: Request): okhttp3.Response {
        val first = client.newCall(request).execute()
        if (!useDigest || first.code != 401) {
            return first
        }
        val challenge = first.header("WWW-Authenticate") ?: return first
        first.close()
        val uri = URI(request.url.toString())
        val path = uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
        val digest = DigestAuth.authorizationHeader(
            challenge,
            request.method,
            path,
            username,
            password,
        ) ?: return client.newCall(request).execute()
        val authed = request.newBuilder()
            .header("Authorization", digest)
            .build()
        return client.newCall(authed).execute()
    }
}
