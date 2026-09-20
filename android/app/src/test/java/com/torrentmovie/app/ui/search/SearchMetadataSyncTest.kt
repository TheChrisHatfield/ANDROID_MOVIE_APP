package com.torrentmovie.app.ui.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMetadataSyncTest {
    @Test
    fun keepsMetadataOnRefreshWithSameSettings() {
        assertFalse(
            shouldReplaceStoredMetadata(
                lastCommittedSettingsKey = "a|b|2|true|true",
                currentSettingsKey = "a|b|2|true|true",
                fetchMovieMetadata = true,
            ),
        )
    }

    @Test
    fun replacesMetadataWhenSettingsChange() {
        assertTrue(
            shouldReplaceStoredMetadata(
                lastCommittedSettingsKey = "http://old:8765|",
                currentSettingsKey = "http://new:8765|",
                fetchMovieMetadata = true,
            ),
        )
    }

    @Test
    fun replacesMetadataWhenEnrichmentDisabled() {
        assertTrue(
            shouldReplaceStoredMetadata(
                lastCommittedSettingsKey = "same|key",
                currentSettingsKey = "same|key",
                fetchMovieMetadata = false,
            ),
        )
    }

    @Test
    fun keepsMetadataOnFirstSearchCommit() {
        assertFalse(
            shouldReplaceStoredMetadata(
                lastCommittedSettingsKey = null,
                currentSettingsKey = "a|b",
                fetchMovieMetadata = true,
            ),
        )
    }
}
