package com.torrentmovie.core.data

import com.torrentmovie.core.network.TorrentResultDto

/** In-memory stash so detail can use inline magnets without oversized nav args. */
class SearchResultStore {
    private data class Entry(val result: TorrentResultDto, var lastAccess: Long)

    private val byId = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {}
    private val maxEntries = 500
    private val ttlMs = 3_600_000L

    fun put(result: TorrentResultDto) {
        synchronized(this) {
            pruneExpired()
            val now = System.currentTimeMillis()
            val existing = byId[result.id]?.result
            val merged = when {
                existing == null -> result
                else -> result.copy(
                    magnet = result.magnet?.takeIf { it.isNotBlank() } ?: existing.magnet,
                    detail_url = result.detail_url?.takeIf { it.isNotBlank() } ?: existing.detail_url,
                )
            }
            byId[merged.id] = Entry(merged, now)
            evictOverflow()
        }
    }

    fun get(resultId: String): TorrentResultDto? = synchronized(this) {
        pruneExpired()
        val entry = byId[resultId] ?: return null
        entry.lastAccess = System.currentTimeMillis()
        entry.result
    }

    fun remove(resultId: String) {
        synchronized(this) { byId.remove(resultId) }
    }

    private fun pruneExpired() {
        val cutoff = System.currentTimeMillis() - ttlMs
        byId.entries.removeIf { it.value.lastAccess < cutoff }
    }

    private fun evictOverflow() {
        while (byId.size > maxEntries) {
            val oldest = byId.keys.firstOrNull() ?: break
            byId.remove(oldest)
        }
    }
}
