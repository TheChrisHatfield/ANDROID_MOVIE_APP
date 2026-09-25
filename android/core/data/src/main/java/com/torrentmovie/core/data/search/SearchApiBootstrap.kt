package com.torrentmovie.core.data.search

import com.torrentmovie.core.data.AppSettings
import com.torrentmovie.core.data.seedbox.normalizeSearchApiUrl

/** FR-040 resolution order: bundled (if healthy) → LAN → emulator. */
object SearchApiBootstrap {
    fun resolveAutoSearchApiUrl(
        bundledSearchApiUrl: String,
        isEmulator: Boolean,
        wifiIpv4: String?,
        probeHealthy: (String) -> Boolean,
        discoverOnLan: (String) -> String? = SearchApiLanDiscovery::discoverOnLan,
        currentUrl: String = "",
    ): String? {
        val current = normalizeSearchApiUrl(currentUrl)
        if (current.isNotBlank() && probeHealthy(current)) return current
        val bundled = normalizeSearchApiUrl(bundledSearchApiUrl)
        if (bundled.isNotBlank() && probeHealthy(bundled)) return bundled
        if (!wifiIpv4.isNullOrBlank()) {
            discoverOnLan(wifiIpv4)?.let { return it }
        }
        if (isEmulator) {
            val emulator = normalizeSearchApiUrl(AppSettings.EMULATOR_SEARCH_API)
            if (emulator.isNotBlank() && probeHealthy(emulator)) return emulator
        }
        return null
    }
}
