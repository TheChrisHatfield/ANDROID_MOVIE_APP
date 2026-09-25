package com.torrentmovie.core.data.search

import org.junit.Assert.assertTrue
import org.junit.Test

class SearchApiWifiBootstrapTest {
    @Test
    fun networkAdaptDebounceCoversSsidAndCellularFlaps() {
        assertTrue(SearchApiWifiBootstrap.NETWORK_ADAPT_DEBOUNCE_MS in 250L..2_000L)
    }
}
