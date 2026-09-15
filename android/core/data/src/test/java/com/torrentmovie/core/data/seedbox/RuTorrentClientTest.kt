package com.torrentmovie.core.data.seedbox

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertTrue
import org.junit.Test

class RuTorrentClientTest {
    @Test
    fun addMagnetSuccessOnSuccessBody() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("status=Success"))
        server.start()
        val client = RuTorrentClient(
            baseUrl = server.url("/rutorrent/").toString(),
            username = "u",
            password = "p",
        )
        val result = client.addMagnet(
            "magnet:?xt=urn:btih:ABCDEF0123456789ABCDEF0123456789ABCDEF01",
            "/movies/",
        )
        server.shutdown()
        assertTrue(result is SeedboxResult.Success)
    }

    @Test
    fun addMagnetFailsOnEmptyBody() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        server.start()
        val client = RuTorrentClient(
            baseUrl = server.url("/rutorrent/").toString(),
            username = "u",
            password = "p",
        )
        val result = client.addMagnet(
            "magnet:?xt=urn:btih:ABCDEF0123456789ABCDEF0123456789ABCDEF01",
            "/movies/",
        )
        server.shutdown()
        assertTrue(result is SeedboxResult.Failure)
    }
}
