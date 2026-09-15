package com.torrentmovie.core.data

import com.torrentmovie.core.network.TorrentResultDto

/** In-memory stash so detail can use inline magnets without oversized nav args. */
class SearchResultStore {
    private val byId = mutableMapOf<String, TorrentResultDto>()

    fun put(result: TorrentResultDto) {
        byId[result.id] = result
    }

    fun get(resultId: String): TorrentResultDto? = byId[resultId]
}
