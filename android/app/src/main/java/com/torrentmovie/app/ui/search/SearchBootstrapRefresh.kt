package com.torrentmovie.app.ui.search

/** Retry search after FR-040 auto-config when the user already searched but the API was unreachable. */
internal fun shouldRefreshAfterSearchApiBootstrap(
    modeActive: Boolean,
    searchApiBaseUrl: String,
    errorMessage: String?,
): Boolean {
    if (!modeActive || searchApiBaseUrl.isBlank()) return false
    if (errorMessage?.contains("Configure Search API", ignoreCase = true) == true) return true
    return isSearchConnectivityError(errorMessage)
}

internal fun isSearchConnectivityError(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    val lower = message.lowercase()
    return lower.contains("connect") ||
        lower.contains("failed to connect") ||
        lower.contains("unable to resolve host") ||
        lower.contains("timeout") ||
        lower.contains("network") ||
        lower.contains("search api url")
}
