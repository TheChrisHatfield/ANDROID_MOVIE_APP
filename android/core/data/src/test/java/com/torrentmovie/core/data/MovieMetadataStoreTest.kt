package com.torrentmovie.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MovieMetadataStoreTest {
    @Test
    fun putPreservesExistingPosterWhenIncomingHasNone() {
        val store = MovieMetadataStore()
        store.put(
            "a",
            MovieMetadata(title = "Film", posterUrl = "https://image.tmdb.org/p.jpg"),
        )
        store.put("a", MovieMetadata(title = "Film", posterUrl = null))
        assertEquals("https://image.tmdb.org/p.jpg", store.get("a")?.posterUrl)
    }

    @Test
    fun putReplacesPosterWhenIncomingHasOne() {
        val store = MovieMetadataStore()
        store.put("a", MovieMetadata(title = "Film", posterUrl = "old.jpg"))
        store.put("a", MovieMetadata(title = "Film", posterUrl = "new.jpg"))
        assertEquals("new.jpg", store.get("a")?.posterUrl)
    }
}
