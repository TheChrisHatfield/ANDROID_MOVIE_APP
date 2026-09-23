package com.torrentmovie.core.data.search

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchApiLanDiscoveryTest {
    @Test
    fun probeAcceptsHealthOkJson() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setBody("""{"status":"ok","tmdb_configured":false}"""))
        val base = server.url("/").toString().removeSuffix("/")
        assertTrue(SearchApiLanDiscovery.probeSearchApiBaseUrl(base))
        server.shutdown()
    }

    @Test
    fun probeRejectsNonSearchServer() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setBody("not json"))
        val base = server.url("/").toString().removeSuffix("/")
        assertFalse(SearchApiLanDiscovery.probeSearchApiBaseUrl(base))
        server.shutdown()
    }

    @Test
    fun lanDiscoveryUsesAPhoneSafeProbeFanOut() {
        assertTrue(SearchApiLanDiscovery.PARALLEL_PROBES in 1..8)
    }
}
