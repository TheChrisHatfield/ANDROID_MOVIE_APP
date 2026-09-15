package com.torrentmovie.core.data

import com.torrentmovie.core.network.MovieGroupDto
import com.torrentmovie.core.network.TorrentResultDto

data class SearchResult(
    val results: List<TorrentResultDto>,
    val failedSites: List<String> = emptyList(),
    val groups: List<MovieGroupDto> = emptyList(),
)
