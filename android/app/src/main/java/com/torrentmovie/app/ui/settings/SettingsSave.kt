package com.torrentmovie.app.ui.settings

import com.torrentmovie.core.data.seedbox.SeedboxProbeResult

internal fun settingsSavedMessage(
    seedboxConfigured: Boolean,
    probe: SeedboxProbeResult?,
): String {
    if (!seedboxConfigured || probe == null) return "Settings saved"
    return when {
        probe.fullyOnline -> "Settings saved · seedbox ready"
        probe.addReachable -> "Settings saved · send OK, live status unavailable"
        else -> "Settings saved · ${probe.message}"
    }
}
