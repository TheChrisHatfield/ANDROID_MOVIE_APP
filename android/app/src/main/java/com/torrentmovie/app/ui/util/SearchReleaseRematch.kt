package com.torrentmovie.app.ui.util

import com.torrentmovie.core.network.TorrentResultDto

object SearchReleaseRematch {
    fun find(
        releases: List<TorrentResultDto>,
        resultId: String,
        name: String,
        site: String,
        detailUrl: String? = null,
    ): TorrentResultDto? {
        releases.find { it.id == resultId }?.let { return it }
        val normalizedDetail = detailUrl?.trim()?.takeIf { it.isNotBlank() }
        if (!normalizedDetail.isNullOrBlank()) {
            val detailMatches = releases.filter {
                it.detail_url?.trim()?.equals(normalizedDetail, ignoreCase = true) == true
            }
            if (detailMatches.size == 1) return detailMatches.first()
            if (site.isNotBlank()) {
                detailMatches.find { it.site.equals(site, ignoreCase = true) }?.let { return it }
            }
        }
        if (name.isBlank()) return null
        val nameMatches = releases.filter { it.name.equals(name, ignoreCase = true) }
        if (site.isNotBlank()) {
            return nameMatches.find { it.site.equals(site, ignoreCase = true) }
        }
        return nameMatches.singleOrNull()
    }
}
