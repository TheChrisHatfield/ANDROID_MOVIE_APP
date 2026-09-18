package com.torrentmovie.app.ui.util

import com.torrentmovie.core.network.TorrentResultDto

object SearchReleaseRematch {
    fun find(
        releases: List<TorrentResultDto>,
        resultId: String,
        name: String,
        site: String,
    ): TorrentResultDto? {
        releases.find { it.id == resultId }?.let { return it }
        if (name.isBlank()) return null
        val nameMatches = releases.filter { it.name.equals(name, ignoreCase = true) }
        if (site.isNotBlank()) {
            return nameMatches.find { it.site == site }
        }
        return nameMatches.singleOrNull()
    }
}
