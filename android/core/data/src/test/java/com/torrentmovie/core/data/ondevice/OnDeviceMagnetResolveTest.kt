package com.torrentmovie.core.data.ondevice

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceMagnetResolveTest {
    @Test
    fun cacheMissStillResolvesMagnetFromDetailPage() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """<html><a href="magnet:?xt=urn:btih:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa">dl</a></html>""",
            ),
        )
        server.start()
        try {
            val engine = OnDeviceSearchEngine(IndexerHttp(OkHttpClient()))
            val resolved = engine.resolveMagnet(
                resultId = "not-in-cache",
                detailUrl = server.url("/torrent/1").toString(),
                site = "1337x",
                name = "Inception 2010",
            )
            assertTrue(resolved.magnet.startsWith("magnet:?xt=urn:btih:"))
        } finally {
            server.shutdown()
        }
    }
}
