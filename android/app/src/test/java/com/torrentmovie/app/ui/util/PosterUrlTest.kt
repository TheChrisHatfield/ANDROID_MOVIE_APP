package com.torrentmovie.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosterUrlTest {
    @Test
    fun prefixesRelativeLocalPosterPath() {
        assertEquals(
            "http://10.0.2.2:8765/v1/posters/abc.jpg",
            resolvePosterUrl("/v1/posters/abc.jpg", "http://10.0.2.2:8765"),
        )
    }

    @Test
    fun rewritesStaleHostLocalPosterToCurrentApi() {
        assertEquals(
            "http://192.168.1.20:8765/v1/posters/abc.jpg",
            resolvePosterUrl(
                "http://10.0.2.2:8765/v1/posters/abc.jpg",
                "http://192.168.1.20:8765/",
            ),
        )
    }

    @Test
    fun leavesRemoteCdnPostersUnchanged() {
        assertEquals(
            "https://image.tmdb.org/t/p/w342/x.jpg",
            resolvePosterUrl(
                "https://image.tmdb.org/t/p/w342/x.jpg",
                "http://10.0.2.2:8765",
            ),
        )
    }

    @Test
    fun returnsNullForBlank() {
        assertNull(resolvePosterUrl(null, "http://10.0.2.2:8765"))
        assertNull(resolvePosterUrl("  ", "http://10.0.2.2:8765"))
    }
}
