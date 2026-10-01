package com.torrentmovie.core.data.ondevice

internal data class IndexerRow(
    val name: String,
    val site: String,
    val size: String = "-",
    val seeds: String = "-",
    val leeches: String = "-",
    val date: String = "-",
    val magnet: String? = null,
    val detailUrl: String? = null,
    val posterUrl: String? = null,
    val overview: String? = null,
    val trailerYoutubeKey: String? = null,
)
