package com.torrentmovie.app.ui.detail

import com.torrentmovie.core.data.MovieMetadata
import com.torrentmovie.core.network.TorrentResultDto

/** Prefer grouped metadata, then the stored release poster so detail still shows art after LRU eviction. */
internal fun detailHeaderMetadata(
    stored: MovieMetadata?,
    release: TorrentResultDto?,
    name: String,
): MovieMetadata? {
    val releasePoster = release?.posterUrl?.takeIf { it.isNotBlank() }
    if (stored == null) {
        if (releasePoster == null) return null
        return MovieMetadata(
            title = name.ifBlank { release.name },
            posterUrl = releasePoster,
        )
    }
    if (!stored.posterUrl.isNullOrBlank() || releasePoster == null) {
        return stored
    }
    return stored.copy(posterUrl = releasePoster)
}
