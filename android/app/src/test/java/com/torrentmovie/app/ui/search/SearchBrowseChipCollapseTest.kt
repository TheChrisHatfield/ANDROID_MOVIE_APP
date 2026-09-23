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
                movieSitesOnly = true,
            ),
        )
    }
}
