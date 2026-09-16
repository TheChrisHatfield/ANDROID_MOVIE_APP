package com.torrentmovie.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerIntentsTest {
    @Test
    fun keepsBareVideoId() {
        assertEquals("dQw4w9WgXcQ", normalizeYoutubeVideoId("dQw4w9WgXcQ"))
    }

    @Test
    fun rejectsShortOrOpaqueSlugs() {
        assertNull(normalizeYoutubeVideoId("abc123"))
        assertNull(normalizeYoutubeVideoId("yt_trailer_code"))
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
            "https://www.youtube.com/embed/dQw4w9WgXcQ?autoplay=1&mute=0&playsinline=1&rel=0&modestbranding=1&controls=1&enablejsapi=1&fs=1&origin=https://com.torrentmovie.app",
            youtubeEmbedUrl("dQw4w9WgXcQ"),
        )
    }

    @Test
    fun appOriginMatchesPackage() {
        assertEquals("https://com.torrentmovie.app", YOUTUBE_APP_ORIGIN)
    }
}
