package com.torrentmovie.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MagnetHashUtilTest {
    @Test
    fun extractsHexHash() {
        val magnet = "magnet:?xt=urn:btih:ABCDEF0123456789ABCDEF0123456789ABCDEF01&dn=test"
        assertEquals("ABCDEF0123456789ABCDEF0123456789ABCDEF01", MagnetHashUtil.extractInfoHash(magnet))
    }

    @Test
    fun storageKeyFallsBackWhenHashMissing() {
        val key = MagnetHashUtil.storageKey("not-a-magnet", "Movie", "YTS")
        assertEquals(40, key.length)
        assertNotNull(key)
    }
}
