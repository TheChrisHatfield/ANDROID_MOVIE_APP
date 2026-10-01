package com.torrentmovie.core.data.ondevice

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class IndexerHttpCharsetTest {
    @Test
    fun decodesHtmlUsingResponseCharsetNotUtf8Bytes() {
        val server = MockWebServer()
        val latin1 = byteArrayOf(0xC9.toByte()) // É in ISO-8859-1
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html; charset=ISO-8859-1")
                .setBody(okio.Buffer().write(latin1)),
        )
        server.start()
        try {
            val text = IndexerHttp(OkHttpClient()).getText(server.url("/").toString())
            assertEquals("É", text)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun retriesOnceOn503() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503).setBody("busy"))
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
        try {
            val text = IndexerHttp(OkHttpClient()).getText(server.url("/page").toString())
            assertEquals("ok", text)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun retriesOnceOn502() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
        try {
            val text = IndexerHttp(OkHttpClient()).getText(server.url("/page").toString())
            assertEquals("ok", text)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
