package com.torrentmovie.app.ui.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchApiPresentationTest {
    @Test
    fun setupBannerHiddenWhenSearchIsOnDevice() {
        assertFalse(
            shouldShowSearchApiSetupBanner(
                searchApiBaseUrl = "",
                autoConfigurationPending = true,
            ),
        )
        assertFalse(
            shouldShowSearchApiSetupBanner(
                searchApiBaseUrl = "http://192.168.4.27:8765",
                autoConfigurationPending = false,
                searchApiUsableOnThisNetwork = false,
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
