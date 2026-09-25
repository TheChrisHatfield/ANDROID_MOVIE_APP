package com.torrentmovie.core.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchApiBootstrapTest {
    @Test
    fun prefersHealthyBundledUrl() {
        val url = SearchApiBootstrap.resolveAutoSearchApiUrl(
            bundledSearchApiUrl = "http://search.example.com:8765",
            isEmulator = false,
            wifiIpv4 = "192.168.1.10",
            probeHealthy = { it.contains("search.example.com") },
            discoverOnLan = { null },
        )
        assertEquals("http://search.example.com:8765", url)
    }

    @Test
    fun fallsBackToLanWhenBundledUnreachable() {
        val url = SearchApiBootstrap.resolveAutoSearchApiUrl(
            bundledSearchApiUrl = "http://bundled:8765",
            isEmulator = false,
            wifiIpv4 = "192.168.1.10",
            probeHealthy = { false },
            discoverOnLan = { "http://192.168.1.5:8765" },
        )
        assertEquals("http://192.168.1.5:8765", url)
    }

    @Test
    fun returnsNullWhenNothingWorks() {
        assertNull(
            SearchApiBootstrap.resolveAutoSearchApiUrl(
                bundledSearchApiUrl = "",
                isEmulator = false,
                wifiIpv4 = null,
                probeHealthy = { false },
                discoverOnLan = { null },
            ),
        )
    }

    @Test
    fun rediscoversLanWhenWifiSubnetChanges() {
        val url = SearchApiBootstrap.resolveAutoSearchApiUrl(
            bundledSearchApiUrl = "http://192.168.4.27:8765",
            isEmulator = false,
            wifiIpv4 = "192.168.8.10",
            probeHealthy = { it.contains("192.168.4.27") },
            discoverOnLan = { "http://192.168.8.5:8765" },
            currentUrl = "http://192.168.4.27:8765",
        )
        assertEquals("http://192.168.8.5:8765", url)
    }

    @Test
    fun keepsHealthyCurrentUrlOnSameWifiSubnet() {
        val url = SearchApiBootstrap.resolveAutoSearchApiUrl(
            bundledSearchApiUrl = "http://192.168.4.1:8765",
            isEmulator = false,
            wifiIpv4 = "192.168.4.10",
            probeHealthy = { it.contains("192.168.4.27") },
            discoverOnLan = { "http://lan:8765" },
            currentUrl = "http://192.168.4.27:8765",
        )
        assertEquals("http://192.168.4.27:8765", url)
    }

    @Test
    fun skipsLanScanOnCellularWhenCurrentUnreachable() {
        val url = SearchApiBootstrap.resolveAutoSearchApiUrl(
            bundledSearchApiUrl = "http://bundled:8765",
            isEmulator = false,
            wifiIpv4 = null,
            probeHealthy = { it.contains("bundled") },
            discoverOnLan = { error("LAN scan must not run on cellular") },
            currentUrl = "http://192.168.4.27:8765",
        )
        assertEquals("http://bundled:8765", url)
    }

    @Test
    fun emulatorFallsBackToLoopbackWhenLanMissing() {
        val url = SearchApiBootstrap.resolveAutoSearchApiUrl(
            bundledSearchApiUrl = "",
            isEmulator = true,
            wifiIpv4 = null,
            probeHealthy = { it.contains("10.0.2.2") },
            discoverOnLan = { null },
        )
        assertEquals("http://10.0.2.2:8765", url)
    }

    @Test
    fun doesNotKeepOffSubnetLanEvenWhenHealthProbeWouldSucceed() {
        assertNull(
            SearchApiBootstrap.resolveAutoSearchApiUrl(
                bundledSearchApiUrl = "http://192.168.4.27:8765",
                isEmulator = false,
                wifiIpv4 = "192.168.8.10",
                probeHealthy = { true },
                discoverOnLan = { null },
                currentUrl = "http://192.168.4.27:8765",
            ),
        )
    }
}
