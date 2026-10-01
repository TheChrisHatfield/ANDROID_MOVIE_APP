package com.torrentmovie.app.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchBrowseChipCollapseTest {
    @Test
    fun collapsesAfterScrollThreshold() {
        assertFalse(shouldCollapseBrowseChipsOnScroll(0, 0))
        assertFalse(shouldCollapseBrowseChipsOnScroll(0, 20))
        assertTrue(shouldCollapseBrowseChipsOnScroll(0, 40))
        assertTrue(shouldCollapseBrowseChipsOnScroll(1, 0))
    }

    @Test
    fun collapsedBarVisibleWheneverCollapsed() {
        assertFalse(shouldShowCollapsedBrowseBar(collapsed = false))
        assertTrue(shouldShowCollapsedBrowseBar(collapsed = true))
    }

    @Test
    fun collapsedLabelReflectsActiveGenre() {
        assertEquals(
            "Genre: Horror",
            collapsedBrowseBarLabel(
                genrePanelExpanded = true,
                activeGenre = "horror",
                activeBrowseFeed = null,
                contentFilter = com.torrentmovie.core.data.SearchContentFilter.MOVIES,
            ),
        )
    }

    @Test
    fun tvFilterShowsTopTvChipAndHidesTopMovies() {
        val tv = X1337BrowseFeed.entriesFor(com.torrentmovie.core.data.SearchContentFilter.TV)
        assertTrue(tv.contains(X1337BrowseFeed.TOP_100_TV))
        assertFalse(tv.contains(X1337BrowseFeed.TOP_100_MOVIES))
        val movies = X1337BrowseFeed.entriesFor(com.torrentmovie.core.data.SearchContentFilter.MOVIES)
        assertFalse(movies.contains(X1337BrowseFeed.TOP_100_TV))
        assertTrue(movies.contains(X1337BrowseFeed.TOP_100_MOVIES))
    }

    @Test
    fun landscapeChromeCollapsesOnlyWhenSidewaysAndScrolled() {
        assertFalse(shouldCollapseLandscapeSearchChrome(false, 1, 0))
        assertFalse(shouldCollapseLandscapeSearchChrome(true, 0, 0))
        assertTrue(shouldCollapseLandscapeSearchChrome(true, 1, 0))
        assertTrue(shouldCollapseLandscapeSearchChrome(true, 0, 40))
    }
}
