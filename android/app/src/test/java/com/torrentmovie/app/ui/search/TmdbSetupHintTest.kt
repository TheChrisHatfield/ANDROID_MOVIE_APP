package com.torrentmovie.app.ui.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbSetupHintTest {
    @Test
    fun suppressesHintOnOperatorBuildWithBundledKey() {
        assertFalse(
            shouldShowTmdbSetupHint(
                fetchMovieMetadata = true,
                groupsPresent = true,
                anyPosterInGroups = false,
                clientTmdbKeyBlank = true,
                serverTmdbConfigured = false,
                hasBundledTmdbApiKey = true,
            ),
        )
    }

    @Test
    fun showsHintWhenNoClientOrServerTmdb() {
        assertTrue(
            shouldShowTmdbSetupHint(
                fetchMovieMetadata = true,
                groupsPresent = true,
                anyPosterInGroups = false,
                clientTmdbKeyBlank = true,
                serverTmdbConfigured = false,
                hasBundledTmdbApiKey = false,
            ),
        )
    }
}
