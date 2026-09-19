package com.torrentmovie.app.ui.settings

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
}
