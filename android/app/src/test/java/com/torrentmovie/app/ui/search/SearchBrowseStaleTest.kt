package com.torrentmovie.app.ui.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchBrowseStaleTest {
    @Test
    fun refreshSameGenreKeepsList() {
        assertTrue(keepStaleBrowseResults(previousId = "action", nextId = "action"))
    }

    @Test
    fun switchingGenreClearsList() {
        assertFalse(keepStaleBrowseResults(previousId = "action", nextId = "comedy"))
        assertFalse(keepStaleBrowseResults(previousId = null, nextId = "comedy"))
        assertFalse(keepStaleBrowseResults(previousId = "popular", nextId = "top"))
    }
}
