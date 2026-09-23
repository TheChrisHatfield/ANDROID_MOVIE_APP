package com.torrentmovie.core.data.search

import com.torrentmovie.core.data.seedbox.normalizeSearchApiUrl
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/** Find a Missy's Movies search service on the local network (FR-040). */
object SearchApiLanDiscovery {
    private const val SEARCH_PORT = 8765
    private const val HEALTH_PATH = "/v1/health"

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .build()

    fun probeSearchApiBaseUrl(baseUrl: String): Boolean {
        val normalized = normalizeSearchApiUrl(baseUrl.trim())
        if (normalized.isBlank()) return false
        val url = normalized.removeSuffix("/") + HEALTH_PATH
        return try {
            probeClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                resp.isSuccessful && resp.body?.string()?.contains("ok", ignoreCase = true) == true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun discoverOnLan(wifiIpv4: String): String? {
        for (host in LanNetworkAddress.candidateHosts(wifiIpv4)) {
            val candidate = normalizeSearchApiUrl("http://$host:$SEARCH_PORT")
            if (probeSearchApiBaseUrl(candidate)) return candidate
        }
        return null
    }
}
