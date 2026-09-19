package com.torrentmovie.app.ui.search

/** Keep the current list only when refreshing the same 1337x feed or genre. */
internal fun keepStaleBrowseResults(previousId: String?, nextId: String?): Boolean {
    return !previousId.isNullOrBlank() && previousId == nextId
}

/** Drop HTTP search results if a newer query/browse already replaced this job. */
internal fun shouldCommitSearchOutcome(startedGeneration: Int, currentGeneration: Int): Boolean {
    return startedGeneration == currentGeneration
}
