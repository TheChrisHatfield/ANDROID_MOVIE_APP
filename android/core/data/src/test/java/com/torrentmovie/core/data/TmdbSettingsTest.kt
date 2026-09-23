package com.torrentmovie.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TmdbSettingsTest {
    @Test
    fun bundledKeyUsedUntilUserStoresOverride() {
        assertEquals("bundled-key", resolveTmdbApiKey(false, null, "bundled-key"))
        assertEquals("user-key", resolveTmdbApiKey(true, "user-key", "bundled-key"))
        assertEquals("", resolveTmdbApiKey(true, "", "bundled-key"))
    }
}
