package com.torrentmovie.app.ui.settings

import com.torrentmovie.core.data.AppSettings
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

/**
 * Search API bootstrap bumps SettingsRepository.revision while Settings is open.
 * Keep in-progress field edits; take repo values for untouched fields.
 */
internal fun mergeSettingsKeepingEdits(
    draft: AppSettings,
    lastLoaded: AppSettings,
    incoming: AppSettings,
): AppSettings {
    fun pick(draftVal: String, lastVal: String, incomingVal: String): String =
        if (draftVal == lastVal) incomingVal else draftVal
    fun pick(draftVal: Boolean, lastVal: Boolean, incomingVal: Boolean): Boolean =
        if (draftVal == lastVal) incomingVal else draftVal
    fun pick(draftVal: Int, lastVal: Int, incomingVal: Int): Int =
        if (draftVal == lastVal) incomingVal else draftVal
    return incoming.copy(
        searchApiBaseUrl = pick(draft.searchApiBaseUrl, lastLoaded.searchApiBaseUrl, incoming.searchApiBaseUrl),
        rutorrentBaseUrl = pick(draft.rutorrentBaseUrl, lastLoaded.rutorrentBaseUrl, incoming.rutorrentBaseUrl),
        username = pick(draft.username, lastLoaded.username, incoming.username),
        password = pick(draft.password, lastLoaded.password, incoming.password),
        authScheme = pick(draft.authScheme, lastLoaded.authScheme, incoming.authScheme),
        downloadDirectory = pick(
            draft.downloadDirectory,
            lastLoaded.downloadDirectory,
            incoming.downloadDirectory,
        ),
        movieSitesOnly = pick(draft.movieSitesOnly, lastLoaded.movieSitesOnly, incoming.movieSitesOnly),
        searchPages = pick(draft.searchPages, lastLoaded.searchPages, incoming.searchPages),
        tmdbApiKey = pick(draft.tmdbApiKey, lastLoaded.tmdbApiKey, incoming.tmdbApiKey),
        fetchMovieMetadata = pick(
            draft.fetchMovieMetadata,
            lastLoaded.fetchMovieMetadata,
            incoming.fetchMovieMetadata,
        ),
        disclaimerAccepted = incoming.disclaimerAccepted,
    )
}
