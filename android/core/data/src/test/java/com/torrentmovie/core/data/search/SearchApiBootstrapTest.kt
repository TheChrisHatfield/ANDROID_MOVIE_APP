package com.torrentmovie.core.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchApiBootstrapTest {
    @Test
    fun prefersHealthyBundledUrl() {
        val url = SearchApiBootstrap.resolveAutoSearchApiUrl(
            bundledSearchApiUrl = "http://bundled:8765",
            isEmulator = false,
            wifiIpv4 = "192.168.1.10",
            probeHealthy = { it.contains("bundled") },
            discoverOnLan = { "http://lan:8765" },
        )
        assertEquals("http://bundled:8765", url)
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
}
