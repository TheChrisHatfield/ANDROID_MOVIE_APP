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

    @Test
    fun putPreservesExistingDetailUrlWhenIncomingRowHasNone() {
        val store = SearchResultStore()
        store.put(
            TorrentResultDto(
                id = "a",
                name = "Movie 1080p",
                site = "1337x",
                detail_url = "https://1337x.to/torrent/1/movie/",
            ),
        )
        store.put(
            TorrentResultDto(
                id = "a",
                name = "Movie 1080p",
                site = "1337x",
                magnet = "magnet:?xt=urn:btih:abc",
            ),
        )
        assertEquals("https://1337x.to/torrent/1/movie/", store.get("a")?.detail_url)
        assertEquals("magnet:?xt=urn:btih:abc", store.get("a")?.magnet)
    }

    @Test
    fun putPreservesExistingSiteWhenIncomingRowHasBlankSite() {
        val store = SearchResultStore()
        store.put(
            TorrentResultDto(
                id = "a",
                name = "Movie 1080p",
                site = "1337x",
            ),
        )
        store.put(
            TorrentResultDto(
                id = "a",
                name = "Movie 1080p",
                site = "",
            ),
        )
        assertEquals("1337x", store.get("a")?.site)
    }
}
