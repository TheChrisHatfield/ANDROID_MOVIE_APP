package com.torrentmovie.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun putWithoutMergeReplacesBlankPosterAndTrailer() {
        val store = MovieMetadataStore()
        store.put(
            "a",
            MovieMetadata(
                title = "Film",
                overview = "Old plot",
                posterUrl = "https://image.tmdb.org/p.jpg",
                trailerYoutubeKey = "dQw4w9WgXcQ",
            ),
        )
        store.put(
            "a",
            MovieMetadata(title = "Film", posterUrl = null, trailerYoutubeKey = null),
            merge = false,
        )
        val stored = store.get("a")
        assertEquals("Film", stored?.title)
        assertNull(stored?.overview)
        assertNull(stored?.posterUrl)
        assertNull(stored?.trailerYoutubeKey)
    }

    @Test
    fun mergeKeepsTvCatalogFlag() {
        val store = MovieMetadataStore()
        store.put("a", MovieMetadata(title = "The Bear", isTv = true))
        store.put("a", MovieMetadata(title = "The Bear", posterUrl = "p.jpg"))
        assertEquals(true, store.get("a")?.isTv)
    }
}
