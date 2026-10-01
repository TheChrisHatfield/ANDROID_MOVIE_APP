package com.torrentmovie.core.data.ondevice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleParseTest {
    @Test
    fun stripsYearAndQuality() {
        val (title, year) = TitleParse.parse("Inception.2010.1080p.BluRay.x264-YIFY")
        assertEquals("Inception", title)
        assertEquals(2010, year)
        assertEquals("inception-2010", TitleParse.groupKey(title, year))
    }
}

class MovieIndexerParseTest {
    @Test
    fun parses1337xTable() {
        val html = """
            <table class="table-list">
            <tr><th></th></tr>
            <tr>
              <td class="name"><a href="/icon"></a><a href="/torrent/1/Inception-2010/">Inception 2010 1080p</a></td>
              <td class="seeds">12</td>
              <td class="leeches">3</td>
              <td class="size">1.8 GB12</td>
              <td class="coll-date">Jan 1</td>
            </tr>
            </table>
        """.trimIndent()
        val rows = MovieIndexers.parse1337x(html, "https://1337xx.to")
        assertEquals(1, rows.size)
        assertEquals("Inception 2010 1080p", rows[0].name)
        assertEquals("12", rows[0].seeds)
        assertTrue(rows[0].detailUrl!!.contains("/torrent/1/"))
    }

    @Test
    fun parsesYtsJsonTorrents() {
        val json = """
            {"data":{"movies":[{
              "title":"Inception","title_long":"Inception (2010)","year":2010,
              "medium_cover_image":"https://img/p.jpg",
              "yt_trailer_code":"YoHD9XEEkd0",
              "torrents":[{"hash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","quality":"1080p","size":"1.6 GB","seeds":50,"peers":2}]
            }]}}
        """.trimIndent()
        val rows = MovieIndexers.parseYtsJson(json, "https://yts.mx")
        assertEquals(1, rows.size)
        assertEquals("YTS", rows[0].site)
        assertTrue(rows[0].magnet!!.startsWith("magnet:?xt=urn:btih:"))
        assertEquals("YoHD9XEEkd0", rows[0].trailerYoutubeKey)
    }

    @Test
    fun ytsKeepsValidMovieWhenSiblingHasNullTorrents() {
        val json = """
            {"data":{"movies":[
              {"title":"Broken","torrents":null},
              {"title":"Inception","year":2010,
               "torrents":[{"hash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","quality":"720p","size":"1 GB","seeds":1,"peers":0}]}
            ]}}
        """.trimIndent()
        val rows = MovieIndexers.parseYtsJson(json, "https://yts.mx")
        assertEquals(1, rows.size)
        assertEquals("Inception [720p]", rows[0].name)
    }

    @Test
    fun acceptsYtsOkPayloadWithNoMovies() {
        val body = """{"status":"ok","data":{"movie_count":0,"movies":null}}"""
        assertTrue(MovieIndexers.ytsPayloadAccepted(body))
        assertTrue(MovieIndexers.parseYtsJson(body, "https://yts.mx").isEmpty())
    }

    @Test
    fun jsonPrimitiveReadsNumericTorrentBayIds() {
        val obj = com.google.gson.JsonParser.parseString("""{"id":42,"seeders":9}""").asJsonObject
        assertEquals("42", MovieIndexers.jsonPrimitiveString(obj, "id"))
        assertEquals("9", MovieIndexers.jsonPrimitiveString(obj, "seeders"))
    }
}

class SizeFilterTest {
    @Test
    fun maxSizeFilterRequiresUnitSuffix() {
        org.junit.Assert.assertNull(SizeFilters.parseFilterBytes("10G"))
        org.junit.Assert.assertNull(SizeFilters.parseFilterBytes("abc"))
        assertTrue(SizeFilters.parseFilterBytes("2 GB")!! > 1_000_000)
    }

    @Test
    fun applyRejectsInvalidMaxSize() {
        try {
            SizeFilters.apply(emptyList(), null, null, "10G", false)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid max_size", e.message)
        }
    }
}
