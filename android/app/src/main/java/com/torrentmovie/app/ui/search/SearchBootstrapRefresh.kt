package com.torrentmovie.app.ui.search

/** Retry search after FR-040 auto-config when the user already searched but the API was unreachable. */
internal fun shouldRefreshAfterSearchApiBootstrap(
    modeActive: Boolean,
    searchApiBaseUrl: String,
    errorMessage: String?,
): Boolean {
    if (!modeActive || searchApiBaseUrl.isBlank()) return false
    if (errorMessage?.contains("Configure Search API", ignoreCase = true) == true) return true
    if (errorMessage?.contains("Looking for the search service", ignoreCase = true) == true) return true
    return isSearchConnectivityError(errorMessage)
}

/** After FR-040 persist, retry only when the user already has a failed search in flight. */
internal fun shouldRefreshAfterBootstrapPersist(modeActive: Boolean, errorMessage: String?): Boolean {
    return shouldRefreshAfterSearchApiBootstrap(
        modeActive = modeActive,
        searchApiBaseUrl = "http://bootstrap.local",
        errorMessage = errorMessage,
    )
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
