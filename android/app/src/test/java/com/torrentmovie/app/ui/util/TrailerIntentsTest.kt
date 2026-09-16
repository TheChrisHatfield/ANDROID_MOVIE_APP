package com.torrentmovie.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerIntentsTest {
    @Test
    fun keepsBareVideoId() {
        assertEquals("abc123", normalizeYoutubeVideoId("abc123"))
    }

    @Test
    fun extractsIdFromWatchUrl() {
        assertEquals(
            "dQw4w9WgXcQ",
            normalizeYoutubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
    }

    @Test
    fun blankReturnsNull() {
        assertNull(normalizeYoutubeVideoId("   "))
    }

    @Test
    fun embedUrlUsesVideoIdAndOrigin() {
        assertEquals(
            "https://www.youtube.com/embed/abc123?autoplay=1&playsinline=1&rel=0&modestbranding=1&origin=https%3A%2F%2Fcom.torrentmovie.app",
            youtubeEmbedUrl("abc123"),
        )
    }

    @Test
    fun embedHtmlIncludesReferrerPolicy() {
        assert(youtubeEmbedHtml("abc123").contains("referrerpolicy=\"strict-origin-when-cross-origin\""))
    }
}
