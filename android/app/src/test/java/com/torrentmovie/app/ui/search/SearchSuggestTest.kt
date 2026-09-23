package com.torrentmovie.app.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestTest {
    @Test
    fun torrentQueryIncludesYearWhenPresent() {
        assertEquals("Inception 2010", torrentSearchQuery("Inception", 2010))
        assertEquals("Dune", torrentSearchQuery("Dune", null))
    }

    @Test
    fun suggestionsOnlyInPlainSearchMode() {
        assertTrue(
            shouldLoadSearchSuggestions(
                query = "inc",
                activeBrowseFeed = null,
                activeGenre = null,
                genrePanelExpanded = false,
            ),
        )
        assertFalse(
            shouldLoadSearchSuggestions(
                query = "inc",
                activeBrowseFeed = "trending",
                activeGenre = null,
                genrePanelExpanded = false,
            ),
        )
        assertFalse(
            shouldLoadSearchSuggestions(
                query = "i",
                activeBrowseFeed = null,
                activeGenre = null,
                genrePanelExpanded = false,
            ),
        )
        assertFalse(
            shouldLoadSearchSuggestions(
                query = "inc",
                activeBrowseFeed = null,
                activeGenre = null,
                genrePanelExpanded = true,
            ),
        )
    }
}
