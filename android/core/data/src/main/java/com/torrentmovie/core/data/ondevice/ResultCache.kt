package com.torrentmovie.core.data.ondevice

import com.torrentmovie.core.network.MagnetResponseDto
import com.torrentmovie.core.network.TorrentResultDto
import java.util.UUID

internal class ResultCache {
    private val lock = Any()
    private val byId = LinkedHashMap<String, CachedResult>(64, 0.75f, true)
    private val magnets = LinkedHashMap<String, String>(64, 0.75f, true)

    fun remember(rows: List<IndexerRow>): List<TorrentResultDto> {
        return rows.map { row ->
            val id = UUID.randomUUID().toString()
            val dto = TorrentResultDto(
                id = id,
                name = row.name,
                site = row.site,
                size = row.size,
                seeds = row.seeds,
                leeches = row.leeches,
                date = row.date,
                magnet = row.magnet,
                detail_url = row.detailUrl,
                poster_url = row.posterUrl,
            )
            synchronized(lock) {
                prune()
                evictIfNeeded()
                byId[id] = CachedResult(dto, row, System.currentTimeMillis())
                row.magnet?.takeIf { it.isNotBlank() }?.let { magnets[id] = it }
            }
            dto
        }
    }

    fun magnet(id: String): MagnetResponseDto? {
        synchronized(lock) {
            prune()
            magnets[id]?.let { return MagnetResponseDto(id, it) }
            return byId[id]?.row?.magnet?.let { MagnetResponseDto(id, it) }
        }
    }

    fun row(id: String): IndexerRow? = synchronized(lock) {
        prune()
        byId[id]?.row
    }

    fun putMagnet(id: String, magnet: String) {
        synchronized(lock) { magnets[id] = magnet }
    }

    fun rememberRow(id: String, row: IndexerRow, dto: TorrentResultDto? = null) {
        synchronized(lock) {
            prune()
            evictIfNeeded()
            val stored = dto ?: TorrentResultDto(
                id = id,
                name = row.name,
                site = row.site,
                size = row.size,
                seeds = row.seeds,
                leeches = row.leeches,
                date = row.date,
                magnet = row.magnet,
                detail_url = row.detailUrl,
                poster_url = row.posterUrl,
            )
            byId[id] = CachedResult(stored, row, System.currentTimeMillis())
            row.magnet?.takeIf { it.isNotBlank() }?.let { magnets[id] = it }
        }
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - TTL_MS
        val stale = byId.entries.filter { it.value.storedAt < cutoff }.map { it.key }
        stale.forEach {
            byId.remove(it)
            magnets.remove(it)
        }
    }

    private fun evictIfNeeded() {
        while (byId.size >= MAX) {
            val oldest = byId.entries.iterator()
            if (!oldest.hasNext()) break
            val key = oldest.next().key
            oldest.remove()
            magnets.remove(key)
        }
    }

    private data class CachedResult(
        val dto: TorrentResultDto,
        val row: IndexerRow,
        val storedAt: Long,
    )

    companion object {
        private const val MAX = 500
        private const val TTL_MS = 3_600_000L
    }
}
