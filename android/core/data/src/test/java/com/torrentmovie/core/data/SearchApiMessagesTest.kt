package com.torrentmovie.core.data

import org.junit.Assert.assertTrue
import org.junit.Test

class SearchApiMessagesTest {
    @Test
    fun discoveryMessageMentionsWifiScan() {
        val message = SearchApiMessages.blocked(autoConfigurationPending = true)
        assertTrue(message.contains("Looking for the search service", ignoreCase = true))
    }
}
