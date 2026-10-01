package com.torrentmovie.core.data.ondevice

import com.torrentmovie.core.network.MovieGroupDto
import com.torrentmovie.core.network.TorrentResultDto
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

internal data class GroupingOutcome(
    val groups: List<MovieGroupDto>,
    val enrichmentCapped: Boolean,
)

internal object MovieGrouping {
    fun group(
        dtos: List<TorrentResultDto>,
        rows: List<IndexerRow>,
        tmdb: TmdbOnDevice?,
        enrich: Boolean,
    ): GroupingOutcome {
        val buckets = linkedMapOf<String, MutableList<Pair<TorrentResultDto, IndexerRow>>>()
        dtos.zip(rows).forEach { (dto, row) ->
            val (title, parsedYear) = TitleParse.parse(row.name)
            val year = parsedYear ?: row.date.trim().toIntOrNull()?.takeIf { it in 1900..2100 }
            val key = TitleParse.groupKey(title.ifBlank { row.name }, year)
            buckets.getOrPut(key) { mutableListOf() }.add(dto to row)
        }
        val lookups = ConcurrentHashMap<String, TmdbMovie>()
        if (enrich && tmdb?.configured == true) {
            val keys = buckets.keys.take(50)
            val pool = Executors.newFixedThreadPool(4)
            try {
                keys.map { key ->
                    pool.submit {
                        val firstRow = buckets[key]?.first()?.second ?: return@submit
                        val (parsedTitle, parsedYear) = TitleParse.parse(firstRow.name)
                        val title = parsedTitle.ifBlank { firstRow.name }
                        val year = parsedYear ?: firstRow.date.trim().toIntOrNull()
                        tmdb.lookup(
                            title,
                            year,
                            fetchTrailer = buckets[key].orEmpty().none {
                                !it.second.trailerYoutubeKey.isNullOrBlank()
                            },
                            preferTv = SizeFilters.isLikelyTvShow(firstRow.name),
                        )?.let { lookups[key] = it }
                    }
                }.forEach { it.get() }
            } finally {
                pool.shutdownNow()
            }
        }
        val groups = buckets.map { (key, items) ->
            val firstRow = items.first().second
            val (parsedTitle, parsedYear) = TitleParse.parse(firstRow.name)
            var title = parsedTitle.ifBlank { firstRow.name }
            var year = parsedYear ?: firstRow.date.trim().toIntOrNull()
            var overview = items.mapNotNull { it.second.overview }.firstOrNull()
            var poster = items.mapNotNull { it.second.posterUrl }.firstOrNull()
            var trailer = items.mapNotNull { it.second.trailerYoutubeKey }.firstOrNull()
            lookups[key]?.let { info ->
                title = info.title
                year = info.year ?: year
                if (overview.isNullOrBlank()) overview = info.overview
                if (poster.isNullOrBlank()) poster = info.posterUrl
                if (trailer.isNullOrBlank()) trailer = info.trailerKey
            }
            val releases = items
                .sortedByDescending { SizeFilters.seedCount(it.second.seeds) ?: -1 }
                .map { (dto, _) ->
                    if (poster != null && dto.poster_url.isNullOrBlank()) dto.copy(poster_url = poster) else dto
                }
            MovieGroupDto(
                group_key = key,
                title = title,
                year = year,
                overview = overview,
                poster_url = poster,
                trailer_youtube_key = trailer,
                release_count = releases.size,
                releases = releases,
            )
        }
        return GroupingOutcome(
            groups = groups,
            enrichmentCapped = enrich && tmdb?.configured == true && buckets.size > 50,
        )
    }
}
