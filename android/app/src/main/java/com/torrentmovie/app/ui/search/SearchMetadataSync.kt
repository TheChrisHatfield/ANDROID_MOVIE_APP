package com.torrentmovie.app.ui.search

/** Drop cached posters/metadata when enrichment is off or search settings changed. */
internal fun shouldReplaceStoredMetadata(
    lastCommittedSettingsKey: String?,
    currentSettingsKey: String,
    fetchMovieMetadata: Boolean,
): Boolean {
    if (!fetchMovieMetadata) return true
    return lastCommittedSettingsKey != null && lastCommittedSettingsKey != currentSettingsKey
}
