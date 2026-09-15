package com.torrentmovie.core.data

import com.torrentmovie.core.network.TorrentResultDto

data class SearchResult(
    val results: List<TorrentResultDto>,
    val failedSites: List<String> = emptyList(),
)
