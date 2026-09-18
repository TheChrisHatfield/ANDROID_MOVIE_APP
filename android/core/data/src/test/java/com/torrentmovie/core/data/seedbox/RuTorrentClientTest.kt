package com.torrentmovie.core.data.seedbox

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
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
    fun addMagnetSuccessOnRedirectLocation() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "/rutorrent/php/addtorrent.php?result[]=Success&json=1"),
        )
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
    fun addMagnetSuccessOnRedirectFollowJson() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "/rutorrent/php/addtorrent.php?json=1"),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"result":"Success"}"""))
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
    fun addMagnetSuccessOnJsonStatus() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"Success"}"""))
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

    @Test
    fun pingDoesNotCrashWithoutUrlScheme() = runBlocking {
        val client = RuTorrentClient(
            baseUrl = "seedbox.example.com/rutorrent",
            username = "u",
            password = "p",
        )
        val online = client.ping()
        assertTrue(!online)
    }

    @Test
    fun listTorrentStatusesParsesHttprpcJson() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "t": {
                    "ABCDEF0123456789ABCDEF0123456789ABCDEF01": [
                      "1","0","1","1","Test","2000","0","0","1000",
                      "0","0","0","0","262144","","0","0","0","0","1000"
                    ]
                  },
                  "cid": "1"
                }
                """.trimIndent(),
            ),
        )
        server.start()
        val client = RuTorrentClient(
            baseUrl = server.url("/rutorrent/").toString(),
            username = "u",
            password = "p",
        )
        val result = client.listTorrentStatuses()
        server.shutdown()
        assertTrue(result is SeedboxListResult.Success)
        val map = (result as SeedboxListResult.Success).statuses
        assertEquals(1, map.size)
        assertEquals(50, map.values.first().progressPercent())
    }
}
