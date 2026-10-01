package com.torrentmovie.core.data.ondevice

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbOnDeviceTest {
    @Test
    fun marksKeyRejectedOnUnauthorized() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"status_code":7}"""))
        server.start()
        try {
            val tmdb = TmdbOnDevice(
                http = IndexerHttp(OkHttpClient()),
                apiKey = "invalid",
                apiBase = server.url("/").toString().trimEnd('/'),
            )
            tmdb.searchMovies("inception", 5)
            assertTrue(tmdb.keyRejected)
        } finally {
            server.shutdown()
        }
    }
}
