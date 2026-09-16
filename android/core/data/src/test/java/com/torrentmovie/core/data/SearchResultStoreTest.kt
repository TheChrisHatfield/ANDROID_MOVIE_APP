package com.torrentmovie.core.data

import com.torrentmovie.core.network.TorrentResultDto
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultStoreTest {
    @Test
    fun putPreservesExistingMagnetWhenIncomingRowHasNone() {
        val store = SearchResultStore()
        store.put(
            TorrentResultDto(
                id = "a",
                name = "Movie 1080p",
                site = "1337x",
                magnet = "magnet:?xt=urn:btih:abc",
            ),
        )
        store.put(
            TorrentResultDto(
                id = "a",
                name = "Movie 1080p",
                site = "1337x",
                magnet = null,
            ),
        )
        assertEquals("magnet:?xt=urn:btih:abc", store.get("a")?.magnet)
    }
}
