package com.torrentmovie.core.data.ondevice

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
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

    @Test
    fun lookupIncludesYearQueryParams() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))
        server.start()
        try {
            val tmdb = TmdbOnDevice(
                http = IndexerHttp(OkHttpClient()),
                apiKey = "k",
                apiBase = server.url("/").toString().trimEnd('/'),
            )
            tmdb.lookup("Inception", 2010)
            val recorded = server.takeRequest()
            val path = recorded.path.orEmpty()
            assertTrue(path.contains("year=2010"))
            assertTrue(path.contains("primary_release_year=2010"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun tvModeHitsSearchTvAndParsesName() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{"results":[{"id":1396,"name":"The Office","first_air_date":"2005-03-24","poster_path":"/p.jpg"}]}""",
            ),
        )
        server.start()
        try {
            val tmdb = TmdbOnDevice(
                http = IndexerHttp(OkHttpClient()),
                apiKey = "k",
                apiBase = server.url("/").toString().trimEnd('/'),
                searchTv = true,
            )
            val hits = tmdb.searchMovies("the office", 5)
            val recorded = server.takeRequest()
            assertTrue(recorded.path.orEmpty().contains("/search/tv"))
            assertEquals("The Office", hits.single().title)
            assertEquals(2005, hits.single().year)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun lookupPreferTvUsesTvSearchEvenWhenCatalogIsMovies() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))
        server.start()
        try {
            val tmdb = TmdbOnDevice(
                http = IndexerHttp(OkHttpClient()),
                apiKey = "k",
                apiBase = server.url("/").toString().trimEnd('/'),
                searchTv = false,
            )
            tmdb.lookup("The Office", 2005, preferTv = true)
            val path = server.takeRequest().path.orEmpty()
            assertTrue(path.contains("/search/tv"))
            assertTrue(path.contains("first_air_date_year=2005"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun lookupFallsBackToTvWhenMovieSearchIsEmpty() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"results":[{"id":136315,"name":"The Bear","first_air_date":"2022-06-23"}]}""",
            ),
        )
        server.start()
        try {
            val tmdb = TmdbOnDevice(
                http = IndexerHttp(OkHttpClient()),
                apiKey = "k",
                apiBase = server.url("/").toString().trimEnd('/'),
            )
            val hit = tmdb.lookup("The Bear", 2022, preferTv = false)
            assertEquals("The Bear", hit?.title)
            assertTrue(hit?.fromTv == true)
            assertTrue(server.takeRequest().path.orEmpty().contains("/search/movie"))
            assertTrue(server.takeRequest().path.orEmpty().contains("/search/tv"))
        } finally {
            server.shutdown()
        }
    }
}
