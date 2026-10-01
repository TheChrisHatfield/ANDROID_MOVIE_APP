package com.torrentmovie.core.data.ondevice

import com.google.gson.JsonParser
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder

internal class MovieIndexers(private val http: IndexerHttp) {
    @Volatile private var ytsWorking: String? = null
    @Volatile private var x1337Working: String? = null

    fun searchAll(query: String, pages: Int): Pair<List<IndexerRow>, List<String>> {
        val jobs = listOf(
            "YTS" to { ytsSearch(query, pages) },
            "1337x" to { x1337Search(query, pages) },
            "The Pirate Bay" to { tpbSearch(query) },
            "TorrentGalaxy" to { tableSearch(TGX_MIRRORS, query, pages, ::parseTgx) },
            "MagnetDL" to { magnetDlSearch(query, pages) },
            "LimeTorrents" to { limeSearch(query, pages) },
        )
        val pool = java.util.concurrent.Executors.newFixedThreadPool(jobs.size.coerceAtMost(6))
        return try {
            val futures = jobs.map { (name, job) ->
                pool.submit(
                    java.util.concurrent.Callable {
                        name to runCatching { job() }
                    },
                )
            }
            val failed = mutableListOf<String>()
            val rows = mutableListOf<IndexerRow>()
            for (future in futures) {
                val (name, result) = future.get()
                result.fold(
                    onSuccess = { found -> if (found.isNotEmpty()) rows += found },
                    onFailure = { failed += name },
                )
            }
            rows to failed
        } finally {
            pool.shutdownNow()
        }
    }

    fun browse1337x(feed: String, pages: Int): Pair<List<IndexerRow>, Boolean> {
        val path = BROWSE_FEEDS[feed] ?: return emptyList<IndexerRow>() to true
        var lastError = true
        for (base in X1337_MIRRORS) {
            val collected = mutableListOf<IndexerRow>()
            for (page in 0 until pages) {
                val url = if (page <= 0) "$base/$path/" else "$base/$path/${page + 1}/"
                val html = http.getText(url) ?: break
                val pageRows = parse1337x(html, base)
                if (pageRows.isEmpty()) break
                collected += pageRows
            }
            if (collected.isNotEmpty()) return collected to false
            val fallback = BROWSE_FALLBACKS[feed]
            if (fallback != null) {
                val html = http.getText("$base/$fallback/1/")
                if (html != null) {
                    val pageRows = parse1337x(html, base)
                    if (pageRows.isNotEmpty()) return pageRows to false
                }
            }
            lastError = collected.isEmpty()
        }
        return emptyList<IndexerRow>() to lastError
    }

    fun browseGenre(genreId: String, pages: Int): List<IndexerRow> {
        val query = TmdbOnDevice.GENRE_QUERIES[genreId] ?: genreId
        val rows = mutableListOf<IndexerRow>()
        rows += x1337Search(query, pages)
        rows += ytsList(queryTerm = null, genre = ytsGenre(genreId), sort = "download_count", pages = pages)
        return rows
    }

    fun ytsSearch(query: String, pages: Int): List<IndexerRow> =
        ytsList(queryTerm = query, genre = null, sort = null, pages = pages)

    fun resolveMagnet(row: IndexerRow): String? {
        row.magnet?.takeIf { it.startsWith("magnet:") }?.let { return it }
        val detail = row.detailUrl ?: return null
        val html = http.getText(detail) ?: return null
        val soup = Jsoup.parse(html, detail)
        soup.select("a[href^=magnet:]").firstOrNull()?.attr("href")?.let { return it }
        return soup.select("a[href]").firstOrNull { it.attr("href").contains("magnet:") }?.attr("href")
    }

    private fun ytsList(
        queryTerm: String?,
        genre: String?,
        sort: String?,
        pages: Int,
    ): List<IndexerRow> {
        val ordered = ytsWorking?.let { listOf(it) + YTS_MIRRORS.filter { m -> m != it } } ?: YTS_MIRRORS
        for (base in ordered) {
            val collected = mutableListOf<IndexerRow>()
            var accepted = false
            for (page in 1..pages) {
                val params = mutableListOf("limit=20", "page=$page")
                if (!queryTerm.isNullOrBlank()) {
                    params += "query_term=${enc(queryTerm)}"
                }
                if (!genre.isNullOrBlank()) params += "genre=${enc(genre)}"
                if (!sort.isNullOrBlank()) params += "sort_by=$sort"
                val url = "$base/api/v2/list_movies.json?${params.joinToString("&")}"
                val body = http.getText(url) ?: break
                if (!ytsPayloadAccepted(body)) break
                accepted = true
                val pageRows = parseYtsJson(body, base)
                collected += pageRows
                if (pageRows.isEmpty()) break
            }
            if (accepted) {
                ytsWorking = base
                return collected
            }
        }
        return emptyList()
    }

    private fun x1337Search(query: String, pages: Int): List<IndexerRow> {
        val ordered = x1337Working?.let { listOf(it) + X1337_MIRRORS.filter { m -> m != it } } ?: X1337_MIRRORS
        for (base in ordered) {
            val collected = mutableListOf<IndexerRow>()
            for (page in 0 until pages) {
                val url = "$base/search/${enc(query)}/${page + 1}/"
                val html = http.getText(url) ?: break
                val pageRows = parse1337x(html, base)
                if (pageRows.isEmpty()) break
                collected += pageRows
            }
            if (collected.isNotEmpty()) {
                x1337Working = base
                return collected
            }
        }
        return emptyList()
    }

    private fun tpbSearch(query: String): List<IndexerRow> {
        val url = "https://apibay.org/q.php?q=${enc(query)}&cat=0"
        val body = http.getText(url) ?: return emptyList()
        return try {
            val arr = JsonParser.parseString(body).asJsonArray
            arr.mapNotNull { el ->
                val obj = el.asJsonObject
                val id = jsonPrimitiveString(obj, "id")
                if (id == null || id == "0") return@mapNotNull null
                val name = jsonPrimitiveString(obj, "name") ?: return@mapNotNull null
                if (name == "No results returned") return@mapNotNull null
                val hash = jsonPrimitiveString(obj, "info_hash").orEmpty()
                if (hash.isBlank() || hash == "0000000000000000000000000000000000000000") return@mapNotNull null
                val sizeRaw = jsonPrimitiveString(obj, "size")?.toLongOrNull() ?: 0L
                IndexerRow(
                    name = name,
                    site = "The Pirate Bay",
                    size = MagnetUtils.bytesToSizeLabel(sizeRaw),
                    seeds = jsonPrimitiveString(obj, "seeders") ?: "-",
                    leeches = jsonPrimitiveString(obj, "leechers") ?: "-",
                    magnet = MagnetUtils.fromHash(hash, name),
                    detailUrl = "https://tpb.party/description.php?id=$id",
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun limeSearch(query: String, pages: Int): List<IndexerRow> {
        for (base in LIME_MIRRORS) {
            val collected = mutableListOf<IndexerRow>()
            for (page in 0 until pages) {
                val url = "$base/search/all/${enc(query)}/seeds/${page + 1}/"
                val html = http.getText(url) ?: break
                val pageRows = parseLime(html, base)
                if (pageRows.isEmpty()) break
                collected += pageRows
            }
            if (collected.isNotEmpty()) return collected
        }
        return emptyList()
    }

    private fun magnetDlSearch(query: String, pages: Int): List<IndexerRow> {
        val letter = query.lowercase().firstOrNull { it.isLetterOrDigit() } ?: 'a'
        for (base in MAGNETDL_MIRRORS) {
            val collected = mutableListOf<IndexerRow>()
            for (page in 0 until pages) {
                val url = "$base/$letter/${enc(query)}/?page=${page + 1}"
                val html = http.getText(url) ?: break
                val pageRows = parseMagnetDl(html, base)
                if (pageRows.isEmpty()) break
                collected += pageRows
            }
            if (collected.isNotEmpty()) return collected
        }
        return emptyList()
    }

    private fun tableSearch(
        mirrors: List<String>,
        query: String,
        pages: Int,
        parse: (String, String) -> List<IndexerRow>,
    ): List<IndexerRow> {
        for (base in mirrors) {
            val collected = mutableListOf<IndexerRow>()
            for (page in 0 until pages) {
                val url = "$base/torrents.php?search=${enc(query)}&page=${page + 1}"
                val html = http.getText(url) ?: break
                val pageRows = parse(html, base)
                if (pageRows.isEmpty()) break
                collected += pageRows
            }
            if (collected.isNotEmpty()) return collected
        }
        return emptyList()
    }

    companion object {
        val MOVIE_SITE_NAMES = listOf(
            "YTS", "1337x", "The Pirate Bay", "TorrentGalaxy", "MagnetDL", "LimeTorrents",
        )
        val YTS_MIRRORS = listOf("https://yts.mx", "https://yts.lt", "https://yts.rs")
        val X1337_MIRRORS = listOf(
            "https://1337xx.to", "https://www.1337xx.to", "https://1337x.to",
            "https://1337x.st", "https://x1337x.ws", "https://1337x.is",
        )
        val TGX_MIRRORS = listOf("https://torrentgalaxy.to", "https://tgx.rs")
        val LIME_MIRRORS = listOf(
            "https://www.limetorrents.lol",
            "https://www.limetorrents.pro",
            "https://www.limetorrents.cyou",
            "https://www.limetorrents.zone",
        )
        val MAGNETDL_MIRRORS = listOf("https://www.magnetdl.com", "https://magnetdl.unblockit.boo")
        val BROWSE_FEEDS = mapOf(
            "trending" to "trending",
            "top-100" to "top-100",
            "top-100-movies" to "top-100-movies",
            "top-100-television" to "top-100-television",
        )
        val BROWSE_FALLBACKS = mapOf("top-100-movies" to "cat/Movies")

        fun ytsPayloadAccepted(body: String): Boolean {
            return try {
                val root = JsonParser.parseString(body).asJsonObject
                root.has("data") || root.get("status")?.asString.equals("ok", ignoreCase = true)
            } catch (_: Exception) {
                false
            }
        }

        fun jsonPrimitiveString(obj: com.google.gson.JsonObject, key: String): String? {
            val el = obj.get(key) ?: return null
            if (!el.isJsonPrimitive) return null
            val primitive = el.asJsonPrimitive
            return when {
                primitive.isNumber -> primitive.asNumber.toString()
                primitive.isBoolean -> primitive.asBoolean.toString()
                else -> primitive.asString
            }
        }

        fun parse1337x(html: String, base: String): List<IndexerRow> {
            val soup = Jsoup.parse(html, base)
            val table = soup.selectFirst("table.table-list") ?: return emptyList()
            return table.select("tr").drop(1).mapNotNull { row ->
                val nameTd = row.selectFirst("td.name") ?: return@mapNotNull null
                val link = nameTd.select("a[href]").firstOrNull { it.attr("href").contains("/torrent/") }
                    ?: nameTd.select("a[href]").lastOrNull()
                    ?: return@mapNotNull null
                val sizeRaw = row.selectFirst("td.size")?.text()?.trim().orEmpty()
                val size = if (sizeRaw.contains("B")) sizeRaw.substringBefore("B") + "B" else sizeRaw.ifBlank { "-" }
                IndexerRow(
                    name = link.text().trim(),
                    site = "1337x",
                    size = size,
                    seeds = row.selectFirst("td.seeds")?.text()?.trim() ?: "-",
                    leeches = row.selectFirst("td.leeches")?.text()?.trim() ?: "-",
                    date = row.selectFirst("td.coll-date")?.text()?.trim() ?: "-",
                    detailUrl = abs(base, link.attr("href")),
                )
            }
        }

        fun parseYtsJson(body: String, base: String): List<IndexerRow> {
            return try {
                val data = JsonParser.parseString(body).asJsonObject.getAsJsonObject("data") ?: return emptyList()
                val moviesEl = data.get("movies")
                if (moviesEl == null || moviesEl.isJsonNull || !moviesEl.isJsonArray) return emptyList()
                val movies = moviesEl.asJsonArray
                movies.flatMap { el ->
                    val movie = el.asJsonObject
                    val title = movie.get("title_long")?.asString
                        ?: movie.get("title")?.asString
                        ?: return@flatMap emptyList()
                    val year = movie.get("year")?.asInt
                    val poster = movie.get("medium_cover_image")?.asString
                        ?: movie.get("large_cover_image")?.asString
                    val overview = movie.get("description_full")?.asString
                        ?: movie.get("description_intro")?.asString
                    val trailer = movie.get("yt_trailer_code")?.asString?.takeIf { it.length == 11 }
                    val slug = movie.get("slug")?.asString
                    val url = movie.get("url")?.asString ?: slug?.let { "$base/movies/$it" }
                    val torrents = movie.getAsJsonArray("torrents") ?: return@flatMap emptyList()
                    torrents.mapNotNull { tEl ->
                        val t = tEl.asJsonObject
                        val hash = jsonPrimitiveString(t, "hash") ?: return@mapNotNull null
                        val quality = jsonPrimitiveString(t, "quality")
                        val label = if (quality.isNullOrBlank()) title else "$title [$quality]"
                        IndexerRow(
                            name = label,
                            site = "YTS",
                            size = jsonPrimitiveString(t, "size") ?: "-",
                            seeds = jsonPrimitiveString(t, "seeds") ?: "0",
                            leeches = jsonPrimitiveString(t, "peers") ?: "-",
                            date = year?.toString() ?: "-",
                            magnet = MagnetUtils.fromHash(hash, label),
                            detailUrl = url,
                            posterUrl = poster,
                            overview = overview?.take(400),
                            trailerYoutubeKey = trailer,
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun parseTgx(html: String, base: String): List<IndexerRow> {
            val soup = Jsoup.parse(html, base)
            val table = soup.selectFirst("table.tgxtable") ?: return emptyList()
            return table.select("tr.tgxtablerow").mapNotNull { row ->
                val cols = row.select("td")
                if (cols.size < 10) return@mapNotNull null
                val link = cols[1].selectFirst("a") ?: return@mapNotNull null
                IndexerRow(
                    name = link.text().trim(),
                    site = "TorrentGalaxy",
                    size = cols[5].text().trim(),
                    seeds = cols[7].text().trim(),
                    leeches = cols[8].text().trim(),
                    date = cols[4].text().trim(),
                    detailUrl = abs(base, link.attr("href")),
                )
            }
        }

        fun parseLime(html: String, base: String): List<IndexerRow> {
            val soup = Jsoup.parse(html, base)
            val table = soup.getElementById("table2") ?: soup.selectFirst("table.table2") ?: return emptyList()
            return table.select("tr").drop(1).mapNotNull { row ->
                val cols = row.select("td")
                if (cols.size < 5) return@mapNotNull null
                val link = cols[0].select("a[href]").firstOrNull { it.attr("href").startsWith("/") }
                    ?: cols[0].selectFirst("a[href]")
                IndexerRow(
                    name = link?.text()?.trim() ?: cols[0].text().trim(),
                    site = "LimeTorrents",
                    size = cols[2].text().trim(),
                    seeds = cols[3].text().replace(",", "").trim(),
                    leeches = cols[4].text().replace(",", "").trim(),
                    date = cols[1].text().trim(),
                    detailUrl = link?.attr("href")?.let { abs(base, it) },
                )
            }
        }

        fun parseMagnetDl(html: String, base: String): List<IndexerRow> {
            val soup = Jsoup.parse(html, base)
            val table = soup.selectFirst("table.download") ?: return emptyList()
            return table.select("tr").drop(1).mapNotNull { row ->
                val cols = row.select("td")
                if (cols.size < 7) return@mapNotNull null
                val link = cols[0].selectFirst("a") ?: return@mapNotNull null
                IndexerRow(
                    name = link.text().trim(),
                    site = "MagnetDL",
                    size = cols[3].text().trim(),
                    seeds = cols[4].text().trim(),
                    leeches = cols[5].text().trim(),
                    date = cols[2].text().trim(),
                    detailUrl = abs(base, link.attr("href")),
                )
            }
        }

        private fun abs(base: String, href: String): String {
            if (href.startsWith("http")) return href
            return URI(base.trimEnd('/') + "/").resolve(href).toString()
        }

        private fun enc(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

        private fun ytsGenre(genreId: String): String = when (genreId) {
            "sci-fi" -> "Sci-Fi"
            else -> genreId.replaceFirstChar { it.uppercase() }
        }
    }
}
