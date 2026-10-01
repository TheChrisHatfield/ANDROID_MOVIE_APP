package com.torrentmovie.core.data

/** User-facing copy when on-device search cannot reach the network. */
object SearchApiMessages {
    fun blocked(autoConfigurationPending: Boolean): String {
        return if (autoConfigurationPending) {
            "Looking for movie sources on your connection…"
        } else {
            "Search needs internet. Check Wi-Fi or cellular and try again."
        }
    }
}
