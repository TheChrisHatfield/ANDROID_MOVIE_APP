package com.torrentmovie.app.ui.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchApiPresentationTest {
    @Test
    fun setupBannerUntilAutoConfigPersisted() {
        assertTrue(
            shouldShowSearchApiSetupBanner(
                searchApiBaseUrl = "http://10.0.2.2:8765",
                autoConfigurationPending = true,
            ),
        )
        assertFalse(
            shouldShowSearchApiSetupBanner(
                searchApiBaseUrl = "http://192.168.1.5:8765",
                autoConfigurationPending = false,
            ),
        )
        assertTrue(
            shouldShowSearchApiSetupBanner(
                searchApiBaseUrl = "",
                autoConfigurationPending = false,
            ),
        )
    }

    @Test
    fun autoConfigMessageIsRecognizedForBootstrapRetry() {
        val message = searchApiBlockedMessage(autoConfigurationPending = true)
        assertTrue(
            shouldRefreshAfterSearchApiBootstrap(
                modeActive = true,
                searchApiBaseUrl = "http://10.0.2.2:8765",
                errorMessage = message,
            ),
        )
    }
}
