package com.torrentmovie.core.data

data class PendingFoldDetail(
    val resultId: String,
    val name: String = "",
    val site: String = "",
    val genreId: String? = null,
    val detailUrl: String? = null,
)
