package com.torrentmovie.core.data

import com.torrentmovie.core.network.TorrentResultDto

/** In-memory stash so detail can use inline magnets without oversized nav args. */
class SearchResultStore {
    private val byId = LinkedHashMap<String, TorrentResultDto>()
    private val maxEntries = 200

    fun put(result: TorrentResultDto) {
        synchronized(this) {
            byId[result.id] = result
            while (byId.size > maxEntries) {
                val oldest = byId.keys.firstOrNull() ?: break
                byId.remove(oldest)
            }
        }
    }

    fun get(resultId: String): TorrentResultDto? = synchronized(this) { byId[resultId] }
}
