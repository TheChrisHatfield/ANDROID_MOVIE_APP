package com.torrentmovie.core.data.search

import com.torrentmovie.core.data.seedbox.normalizeSearchApiUrl
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import okhttp3.Request

/** Find a Missy's Movies search service on the local network (FR-040). */
object SearchApiLanDiscovery {
    private const val SEARCH_PORT = 8765
    private const val HEALTH_PATH = "/v1/health"
    private const val PARALLEL_PROBES = 24
    private const val DISCOVERY_TIMEOUT_SEC = 90L

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(750, TimeUnit.MILLISECONDS)
        .readTimeout(750, TimeUnit.MILLISECONDS)
        .callTimeout(1, TimeUnit.SECONDS)
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
        val hosts = LanNetworkAddress.candidateHosts(wifiIpv4)
        val found = AtomicReference<String?>(null)
        val pool = Executors.newFixedThreadPool(PARALLEL_PROBES)
        try {
            for (host in hosts) {
                pool.submit {
                    if (found.get() != null) return@submit
                    val candidate = normalizeSearchApiUrl("http://$host:$SEARCH_PORT")
                    if (probeSearchApiBaseUrl(candidate)) {
                        found.compareAndSet(null, candidate)
                    }
                }
            }
            pool.shutdown()
            pool.awaitTermination(DISCOVERY_TIMEOUT_SEC, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
        return found.get()
    }
}
