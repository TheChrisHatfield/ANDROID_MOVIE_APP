package com.torrentmovie.app.ui.settings

import com.torrentmovie.core.data.AppSettings
import com.torrentmovie.core.data.SearchContentFilter
import com.torrentmovie.core.data.seedbox.SeedboxProbeResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSaveTest {
    @Test
    fun messagesStayHonestForProbeOutcomes() {
        assertEquals("Settings saved", settingsSavedMessage(seedboxConfigured = false, probe = null))
        assertEquals(
            "Settings saved · seedbox ready",
            settingsSavedMessage(
                seedboxConfigured = true,
                probe = SeedboxProbeResult(addReachable = true, httprpcAvailable = true, message = "Seedbox ready"),
            ),
        )
        assertEquals(
            "Settings saved · send OK, live status unavailable",
            settingsSavedMessage(
                seedboxConfigured = true,
                probe = SeedboxProbeResult(
                    addReachable = true,
                    httprpcAvailable = false,
                    message = "HTTPRPC plugin not found",
                ),
            ),
        )
        assertEquals(
            "Settings saved · Seedbox unreachable — check URL and credentials",
            settingsSavedMessage(
                seedboxConfigured = true,
                probe = SeedboxProbeResult(
                    addReachable = false,
                    httprpcAvailable = false,
                    message = "Seedbox unreachable — check URL and credentials",
                ),
            ),
        )
    }

    @Test
    fun bootstrapRevisionDoesNotWipeTypedSearchApi() {
        val last = AppSettings(searchApiBaseUrl = "http://192.168.4.27:8765")
        val draft = last.copy(searchApiBaseUrl = "http://192.168.8.")
        val incoming = last.copy(searchApiBaseUrl = "http://192.168.8.5:8765")
        val merged = mergeSettingsKeepingEdits(draft, last, incoming)
        assertEquals("http://192.168.8.", merged.searchApiBaseUrl)
    }

    @Test
    fun contentFilterChipChangeIsKeptWhenBootstrapMerges() {
        val last = AppSettings(contentFilter = SearchContentFilter.MOVIES)
        val draft = last.copy(contentFilter = SearchContentFilter.TV)
        val incoming = last.copy(searchApiBaseUrl = "http://192.168.8.5:8765")
        val merged = mergeSettingsKeepingEdits(draft, last, incoming)
        assertEquals(SearchContentFilter.TV, merged.contentFilter)
    }

    @Test
    fun uneditedSearchApiTakesAutoDiscoveredUrl() {
        val last = AppSettings(searchApiBaseUrl = "")
        val incoming = last.copy(searchApiBaseUrl = "http://192.168.8.5:8765")
        val merged = mergeSettingsKeepingEdits(last, last, incoming)
        assertEquals("http://192.168.8.5:8765", merged.searchApiBaseUrl)
    }
}
