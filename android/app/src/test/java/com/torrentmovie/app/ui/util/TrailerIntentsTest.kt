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
}
