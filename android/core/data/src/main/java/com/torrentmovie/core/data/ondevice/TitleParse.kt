package com.torrentmovie.core.data.ondevice

internal object TitleParse {
    private val yearRe = Regex("""\b((?:19|20)\d{2})\b""")
    private val qualityRe = Regex(
        """\b(?:\d{3,4}p|4k|2160p|1080p|720p|480p|bluray|blu-ray|web[- ]?dl|webrip|hdrip|dvdrip|x264|x265|hevc|h\.?264|h\.?265|aac|dts|remux|proper|repack|extended|imax)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val episodeRe = Regex(
        """\b(?:s\d{1,2}e\d{1,2}|s\d{1,2}(?!\d)|\d{1,2}x\d{2}|season\s+\d+)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val noiseRe = Regex(
        """\[.*?\]|\(.*?\)|\{.*?\}|\+.*$|\b(?:yify|rarbg|ettv|eztv|galaxy|torrent|sample)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(rawName: String): Pair<String, Int?> {
        val name = rawName.trim()
        if (name.isEmpty()) return "" to null
        val qualityMatch = qualityRe.find(name)
        val prefixEnd = qualityMatch?.range?.first ?: name.length
        val prefix = name.substring(0, prefixEnd)
        val yearMatches = yearRe.findAll(prefix).toList()
        val year = yearMatches.lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
        var titleCut = prefixEnd
        if (yearMatches.isNotEmpty()) {
            titleCut = yearMatches.last().range.first
        }
        episodeRe.find(prefix)?.let { ep ->
            titleCut = minOf(titleCut, ep.range.first)
        }
        var titlePart = if (titleCut > 0) name.substring(0, titleCut) else name
        titlePart = noiseRe.replace(titlePart, " ")
        titlePart = titlePart.replace(Regex("[._]+"), " ")
        titlePart = titlePart.replace(Regex("\\s+"), " ").trim(' ', '-')
        titlePart = titlePart.trimEnd('(', '[', '{', ' ').trim(' ', '-', '.', '_')
        if (titlePart.isEmpty()) titlePart = name
        return titlePart to year
    }

    fun groupKey(title: String, year: Int?): String {
        val slug = title.lowercase().trim().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            .ifBlank { "unknown" }
        return if (year != null) "$slug-$year" else slug
    }
}
