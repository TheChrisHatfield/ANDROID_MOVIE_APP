package com.torrentmovie.app.ui.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchBootstrapRefreshTest {
    @Test
    fun refreshesWhenApiConfiguredAfterConfigureMessage() {
        assertTrue(
            shouldRefreshAfterSearchApiBootstrap(
                modeActive = true,
                searchApiBaseUrl = "http://10.0.2.2:8765",
                errorMessage = "Configure Search API URL in Settings",
            ),
        )
    }

    @Test
    fun refreshesWhenApiPresentButConnectionFailed() {
        assertTrue(
            shouldRefreshAfterSearchApiBootstrap(
                modeActive = true,
                searchApiBaseUrl = "http://10.0.2.2:8765",
                errorMessage = "Failed to connect to search API",
            ),
        )
    }

    @Test
    fun skipsRefreshWhenIdle() {
        assertFalse(
            shouldRefreshAfterSearchApiBootstrap(
                modeActive = false,
                searchApiBaseUrl = "http://10.0.2.2:8765",
                errorMessage = "Failed to connect",
            ),
        )
    }

    @Test
    fun connectivityClassifierRecognizesNetworkErrors() {
        assertTrue(isSearchConnectivityError("Network error talking to search API"))
        assertFalse(isSearchConnectivityError("No results found. Try a broader query."))
    }

    @Test
    fun bootstrapPersistSkipsRetryWhenSearchSucceeded() {
        assertFalse(shouldRefreshAfterBootstrapPersist(modeActive = true, errorMessage = null))
    }
}
