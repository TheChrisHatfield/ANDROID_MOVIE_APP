package com.torrentmovie.app.ui.search

import com.torrentmovie.core.network.MovieGroupDto
import com.torrentmovie.core.network.TorrentResultDto

internal data class DisplaySearchOutcome(
    val groups: List<MovieGroupDto>,
    val results: List<TorrentResultDto>,
)

/** Movie groups already include every release; do not append a duplicate card per flat row. */
internal fun normalizeKodiGroups(
    groups: List<MovieGroupDto>,
    flat: List<TorrentResultDto>,
): DisplaySearchOutcome {
    if (groups.isNotEmpty()) {
        return DisplaySearchOutcome(groups = groups, results = emptyList())
    }
    val synthetic = flat.map { result ->
        MovieGroupDto(
            group_key = "flat-${result.id}",
            title = result.name,
            year = null,
            overview = null,
            poster_url = result.posterUrl,
            trailer_youtube_key = null,
            release_count = 1,
            releases = listOf(result),
        )
    }
    return DisplaySearchOutcome(groups = synthetic, results = emptyList())
}
