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

    @Test
    fun stripsEpisodeCodesForShowGrouping() {
        val (title, year) = TitleParse.parse("The.Office.S05E03.720p.HDTV.x264")
        assertEquals("The Office", title)
        org.junit.Assert.assertNull(year)
        assertEquals("the-office", TitleParse.groupKey(title, year))
    }

    @Test
    fun stripsCompleteSeasonPacksAndHdtvTag() {
        val (pack, _) = TitleParse.parse("Breaking Bad Season 1 Complete 1080p")
        assertEquals("Breaking Bad", pack)
        val (series, _) = TitleParse.parse("Breaking Bad Complete Series")
        assertEquals("Breaking Bad", series)
        val (hdtvMovie, year) = TitleParse.parse("Some Movie 2012 720p HDTV x264")
        assertEquals("Some Movie", hdtvMovie)
        assertEquals(2012, year)
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
        assertEquals("1.8 GB", rows[0].size)
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
    fun ytsSkipsTorrentsWithTruncatedHash() {
        val json = """
            {"data":{"movies":[{
              "title":"Inception",
              "torrents":[{"hash":"short","quality":"720p","size":"1 GB","seeds":1,"peers":0}]
            }]}}
        """.trimIndent()
        assertTrue(MovieIndexers.parseYtsJson(json, "https://yts.mx").isEmpty())
    }

    @Test
    fun acceptsYtsOkPayloadWithNoMovies() {
        val body = """{"status":"ok","data":{"movie_count":0,"movies":null}}"""
        assertTrue(MovieIndexers.ytsPayloadAccepted(body))
        assertTrue(MovieIndexers.parseYtsJson(body, "https://yts.mx").isEmpty())
    }

    @Test
    fun tpbIgnoresErrorObjectPayload() {
        assertTrue(MovieIndexers.parseTpbJson("""{"error":"rate limited"}""").isEmpty())
    }

    @Test
    fun jsonPrimitiveReadsNumericTorrentBayIds() {
        val obj = com.google.gson.JsonParser.parseString("""{"id":42,"seeders":9}""").asJsonObject
        assertEquals("42", MovieIndexers.jsonPrimitiveString(obj, "id"))
        assertEquals("9", MovieIndexers.jsonPrimitiveString(obj, "seeders"))
    }

    @Test
    fun extractMagnetFromClipboardAndHtmlEntities() {
        val html = """
            <div data-clipboard-text="magnet:?xt=urn:btih:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa&amp;dn=Inception"></div>
        """.trimIndent()
        val magnet = MovieIndexers.extractMagnet(html, "https://1337xx.to")
        assertTrue(magnet!!.startsWith("magnet:?xt=urn:btih:"))
        assertTrue(magnet.contains("&dn="))
        assertTrue(!magnet.contains("&amp;"))
    }

    @Test
    fun extractMagnetFromInlineScriptHash() {
        val html = """<script>var m="magnet:?xt=urn:btih:cccccccccccccccccccccccccccccccccccccccc";</script>"""
        val magnet = MovieIndexers.extractMagnet(html, "https://1337xx.to")
        assertTrue(magnet!!.startsWith("magnet:?xt=urn:btih:cccccccccccccccc"))
    }

    @Test
    fun parseLimeSkipsHeaderNameRow() {
        val html = """
            <table id="table2">
            <tr><td>Name</td><td>Date</td><td>Size</td><td>Seeds</td><td>Leeches</td></tr>
            <tr><td><a href="/inception.html">Inception 2010</a></td><td>Jan</td><td>1 GB</td><td>9</td><td>1</td></tr>
            </table>
        """.trimIndent()
        val rows = MovieIndexers.parseLime(html, "https://www.limetorrents.lol")
        assertEquals(1, rows.size)
        assertEquals("Inception 2010", rows[0].name)
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
            SizeFilters.apply(emptyList(), null, null, "10G", com.torrentmovie.core.data.SearchContentFilter.ALL)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid max_size", e.message)
        }
    }

    @Test
    fun glued1337xSizeParsesAsGigabytes() {
        val bytes = SizeFilters.parseSize("1.8 GB12")
        assertTrue(bytes > 1_000_000_000)
        assertEquals("1.8 GB", SizeFilters.extractSizeLabel("1.8 GB12"))
    }

    @Test
    fun maxSizeKeepsGlued1337xSize() {
        val rows = listOf(
            IndexerRow(name = "Inception", site = "1337x", size = "1.8 GB12", seeds = "12"),
        )
        val kept = SizeFilters.apply(rows, null, null, "4 GB", com.torrentmovie.core.data.SearchContentFilter.ALL)
        assertEquals(1, kept.size)
    }

    @Test
    fun tvFilterKeepsEpisodesAndDropsMovies() {
        val rows = listOf(
            IndexerRow(name = "Inception 2010 1080p", site = "YTS"),
            IndexerRow(name = "The Office S05E03 720p HDTV", site = "1337x"),
            IndexerRow(name = "Breaking Bad Season 1 Complete", site = "1337x"),
        )
        val tv = SizeFilters.apply(rows, null, null, null, com.torrentmovie.core.data.SearchContentFilter.TV)
        assertEquals(2, tv.size)
        assertTrue(tv.all { SizeFilters.isLikelyTvShow(it.name) })
        val movies = SizeFilters.apply(rows, null, null, null, com.torrentmovie.core.data.SearchContentFilter.MOVIES)
        assertEquals(1, movies.size)
        assertEquals("Inception 2010 1080p", movies[0].name)
    }

    @Test
    fun hdtvAloneIsNotATvShow() {
        org.junit.Assert.assertFalse(
            SizeFilters.isLikelyTvShow("Some Movie 2012 720p HDTV x264"),
        )
        org.junit.Assert.assertTrue(
            SizeFilters.isLikelyTvShow("The Office S05E03 720p HDTV"),
        )
    }

    @Test
    fun seedCountReadsKiloAndTrailingLabel() {
        org.junit.Assert.assertEquals(1200, SizeFilters.seedCount("1.2K"))
        org.junit.Assert.assertEquals(12, SizeFilters.seedCount("12 seeds"))
    }
}
