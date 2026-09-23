package com.torrentmovie.app.ui.search

/** Whether to show the "add TMDB in Settings" banner (suppressed on operator builds). */
internal fun shouldShowTmdbSetupHint(
    fetchMovieMetadata: Boolean,
    groupsPresent: Boolean,
    anyPosterInGroups: Boolean,
    clientTmdbKeyBlank: Boolean,
    serverTmdbConfigured: Boolean,
    hasBundledTmdbApiKey: Boolean,
): Boolean {
    if (!fetchMovieMetadata || !groupsPresent || anyPosterInGroups) return false
    if (hasBundledTmdbApiKey || !clientTmdbKeyBlank) return false
    return !serverTmdbConfigured
}

/** Extra /v1/health round-trip is only needed when the setup hint could still appear. */
internal fun shouldProbeServerTmdb(
    fetchMovieMetadata: Boolean,
    groupsPresent: Boolean,
    anyPosterInGroups: Boolean,
    clientTmdbKeyBlank: Boolean,
    hasBundledTmdbApiKey: Boolean,
): Boolean {
    if (!fetchMovieMetadata || !groupsPresent || anyPosterInGroups) return false
    if (hasBundledTmdbApiKey || !clientTmdbKeyBlank) return false
    return true
}
