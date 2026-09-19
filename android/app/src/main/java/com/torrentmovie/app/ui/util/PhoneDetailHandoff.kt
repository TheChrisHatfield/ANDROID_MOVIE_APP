package com.torrentmovie.app.ui.util

/**
 * Phone detail may outlive the in-memory result stash (1h TTL). Keep the route
 * when nav still has a title so send/magnet can continue from name/site args.
 */
internal fun shouldPopExpiredPhoneDetail(
    storeHit: Boolean,
    rematchHit: Boolean,
    navName: String,
): Boolean = !storeHit && !rematchHit && navName.isBlank()

internal fun shouldClearPhoneFoldSelectionOnDetailDispose(currentRoute: String?): Boolean {
    return currentRoute?.startsWith("detail/") != true
}

internal fun snapshotGenreAtDetailOpen(
    capturedGenreId: String?,
    currentGenreId: String?,
): String? = capturedGenreId?.takeIf { it.isNotBlank() } ?: currentGenreId?.takeIf { it.isNotBlank() }

internal fun resolveGenreForRankingFeedback(
    override: String?,
    activeGenre: String?,
    lastSearchGenre: String?,
): String? = listOf(override, activeGenre, lastSearchGenre).firstOrNull { !it.isNullOrBlank() }
