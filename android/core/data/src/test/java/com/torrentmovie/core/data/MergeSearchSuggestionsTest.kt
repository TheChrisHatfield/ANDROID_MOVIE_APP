package com.torrentmovie.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MergeSearchSuggestionsTest {
    @Test
    fun allModeInterleavesMovieAndTvAndDropsDuplicateTitles() {
        val movie = listOf(
            MovieSearchSuggestion(1, "The Bear", 2012, null),
            MovieSearchSuggestion(2, "Inception", 2010, null),
        )
        val tv = listOf(
            MovieSearchSuggestion(3, "The Bear", 2022, null),
            MovieSearchSuggestion(4, "The Office", 2005, null),
        )
        val merged = mergeSearchSuggestions(movie, tv, limit = 4)
        assertEquals(listOf("The Bear", "The Office", "Inception"), merged.map { it.title })
        assertEquals(2012, merged.first().year)
    }
}
