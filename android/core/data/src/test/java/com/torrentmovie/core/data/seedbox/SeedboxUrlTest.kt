package com.torrentmovie.core.data.seedbox

import org.junit.Assert.assertEquals
import org.junit.Test

class SeedboxUrlTest {
    @Test
    fun addsHttpsWhenSchemeMissing() {
        assertEquals(
            "https://snow.seedhost.eu/rutorrent/",
            normalizeSeedboxUrl("snow.seedhost.eu/rutorrent"),
        )
    }

    @Test
    fun preservesExistingScheme() {
        assertEquals(
            "http://10.0.0.1/rutorrent/",
            normalizeSeedboxUrl("http://10.0.0.1/rutorrent"),
        )
    }
}
