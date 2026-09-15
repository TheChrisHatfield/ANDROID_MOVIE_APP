package com.torrentmovie.core.data

import com.torrentmovie.core.network.TorrentResultDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchResultStoreTest {
    @Test
    fun getRefreshesAccessOrder() {
        val store = SearchResultStore()
        store.put(TorrentResultDto("a", "Alpha", "YTS"))
        store.put(TorrentResultDto("b", "Beta", "YTS"))
        assertNotNull(store.get("a"))
        store.put(TorrentResultDto("c", "Gamma", "YTS"))
        store.put(TorrentResultDto("d", "Delta", "YTS"))
        store.put(TorrentResultDto("e", "Echo", "YTS"))
        assertNotNull(store.get("a"))
    }

    @Test
    fun removeDropsEntry() {
        val store = SearchResultStore()
        store.put(TorrentResultDto("x", "X", "YTS"))
        store.remove("x")
        assertNull(store.get("x"))
    }
}
