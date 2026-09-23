package com.torrentmovie.app.ui.search

internal fun searchApiBlockedMessage(autoConfigurationPending: Boolean): String {
    return if (autoConfigurationPending) {
        "Looking for the search service on your Wi-Fi (port 8765)…"
    } else {
        "Configure Search API URL in Settings (e.g. http://<PC-IP>:8765)"
    }
}
