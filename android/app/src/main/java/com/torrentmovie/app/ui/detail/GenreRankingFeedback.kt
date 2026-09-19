package com.torrentmovie.app.ui.detail

internal fun isGenreRankingSendFailure(message: String): Boolean {
    val ignored = listOf(
        "Already uploaded",
        "Configure seedbox",
        "Set download folder",
        "Magnet missing info hash",
        "failed to save locally",
        "history save failed",
    )
    return ignored.none { message.contains(it, ignoreCase = true) }
}
