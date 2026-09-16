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

    @Test
    fun lanIpWithoutSchemeUsesHttp() {
        assertEquals(
            "http://192.168.1.10/rutorrent/",
            normalizeSeedboxUrl("192.168.1.10/rutorrent"),
        )
    }

    @Test
    fun hostOnlyAppendsRutorrentPath() {
        assertEquals(
            "https://chris82.snow.seedhost.eu/rutorrent/",
            normalizeSeedboxUrl("https://chris82.snow.seedhost.eu"),
        )
    }

    @Test
    fun stripsAccidentalAddTorrentEndpoint() {
        assertEquals(
            "https://chris82.snow.seedhost.eu/rutorrent/",
            normalizeSeedboxUrl("https://chris82.snow.seedhost.eu/rutorrent/php/addtorrent.php"),
        )
    }

    @Test
    fun addTorrentUrlUsesNormalizedBase() {
        assertEquals(
            "https://chris82.snow.seedhost.eu/rutorrent/php/addtorrent.php",
            seedboxAddTorrentUrl("https://chris82.snow.seedhost.eu"),
        )
    }

    @Test
    fun searchApiAddsHttpWhenSchemeMissing() {
        assertEquals(
            "http://192.168.1.5:8765",
            normalizeSearchApiUrl("192.168.1.5:8765"),
        )
    }
}
