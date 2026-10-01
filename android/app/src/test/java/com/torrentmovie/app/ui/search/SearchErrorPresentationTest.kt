package com.torrentmovie.app.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchErrorPresentationTest {
    @Test
    fun empty503UsesApiRefreshingDetailInsteadOfGenericNoSources() {
        assertEquals(
            "Genre pool still refreshing — retry shortly",
            searchEmptyStateMessage(503, "Genre pool still refreshing — retry shortly"),
        )
    }

    @Test
    fun empty503MapsNoWorkingIndexersToNoSources() {
        assertEquals(
            "No sources available. Check your connection and try again.",
            searchEmptyStateMessage(503, "No working indexers"),
        )
    }

    @Test
    fun snackbarShowsWhenPreservedResultsAndError() {
        assertTrue(shouldSnackbarSearchError(hasVisibleResults = true))
        assertFalse(shouldSnackbarSearchError(hasVisibleResults = false))
    }

    @Test
    fun enrichmentCapMentionsPostersAndTrailers() {
        assertTrue(ENRICHMENT_CAPPED_MESSAGE.contains("trailer", ignoreCase = true))
        assertTrue(ENRICHMENT_CAPPED_MESSAGE.contains("poster", ignoreCase = true))
    }
}
