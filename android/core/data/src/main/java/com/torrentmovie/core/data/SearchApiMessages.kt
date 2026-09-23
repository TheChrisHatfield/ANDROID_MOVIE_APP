package com.torrentmovie.core.data

/** User-facing copy when Search API URL is not yet usable (FR-040). */
object SearchApiMessages {
    fun blocked(autoConfigurationPending: Boolean): String {
        return if (autoConfigurationPending) {
            "Looking for the search service on your Wi-Fi (port 8765)…"
        } else {
            "Configure Search API URL in Settings (e.g. http://<PC-IP>:8765)"
        }
    }
}
