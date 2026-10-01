package com.torrentmovie.core.data.ondevice

import com.torrentmovie.core.data.SearchContentFilter
import java.util.Locale

internal object SizeFilters {
    private val thousands = Regex("(?<=\\d),(?=\\d{3})")
    private val tvShow = Regex(
        """\b(?:s\d{1,2}e\d{1,2}|s\d{1,2}(?!\d)|season\s+\d+|complete\s+series|complete\s+season|tv\s+series|episodes?\s+\d+|mini\s*series|\d{1,2}x\d{2}|hdtv)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val software = Regex(
        """\b(?:windows\s+\d+|macos|linux\s+distro|adobe|photoshop|microsoft\s+office|keygen|crackonly|audiobook|epub|ebook)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val sizeToken = Regex(
        """(\d+(?:[.,]\d+)?)\s*(TIB|TB|GIB|GB|MIB|MB|KIB|KB|B)(?![A-Z])""",
        RegexOption.IGNORE_CASE,
    )

    /** Pulls "1.8 GB" out of 1337x cells that glue size and seed count ("1.8 GB12"). */
    fun extractSizeLabel(sizeStr: String): String? {
        val match = sizeToken.find(sizeStr.trim()) ?: return null
        return "${match.groupValues[1]} ${match.groupValues[2].uppercase(Locale.US)}"
    }

    fun parseSize(sizeStr: String): Double {
        return try {
            val labeled = extractSizeLabel(sizeStr) ?: sizeStr
            val normalized = thousands.replace(labeled.trim().uppercase(Locale.US), "")
            val suffixes = listOf(
                "TIB" to 1024.0 * 1024 * 1024 * 1024,
                "TB" to 1024.0 * 1024 * 1024 * 1024,
                "GIB" to 1024.0 * 1024 * 1024,
                "GB" to 1024.0 * 1024 * 1024,
                "MIB" to 1024.0 * 1024,
                "MB" to 1024.0 * 1024,
                "KIB" to 1024.0,
                "KB" to 1024.0,
            )
            for ((suffix, factor) in suffixes) {
                if (normalized.endsWith(suffix)) {
                    return normalized.dropLast(suffix.length).trim().toDouble() * factor
                }
            }
            if (normalized.endsWith("B")) {
                return normalized.dropLast(1).trim().toDouble()
            }
            normalized.toDouble()
        } catch (_: Exception) {
            -1.0
        }
    }

    /** User-supplied max_size/min_size; null when the label is not a valid unit filter. */
    fun parseFilterBytes(raw: String): Double? {
        val normalized = thousands.replace(raw.trim().uppercase(Locale.US), "")
        val hasUnit = listOf("TIB", "TB", "GIB", "GB", "MIB", "MB", "KIB", "KB", "B").any {
            normalized.endsWith(it)
        }
        if (!hasUnit) return null
        val value = parseSize(raw)
        return value.takeIf { it >= 0 }
    }

    fun seedCount(seeds: String?): Int? {
        if (seeds.isNullOrBlank() || seeds == "-") return null
        val normalized = seeds.replace(",", "").trim()
        val kilo = Regex("""^(\d+(?:\.\d+)?)\s*[kK]\b""").find(normalized)
        if (kilo != null) {
            return (kilo.groupValues[1].toDouble() * 1000).toInt()
        }
        return Regex("""^\d+""").find(normalized)?.value?.toIntOrNull()
    }

    fun isLikelyTvShow(name: String): Boolean {
        val label = name.trim()
        return label.isNotEmpty() && tvShow.containsMatchIn(label)
    }

    fun isLikelyMovie(name: String): Boolean {
        val label = name.trim()
        if (label.isEmpty()) return false
        if (isLikelyTvShow(label)) return false
        if (software.containsMatchIn(label)) return false
        return true
    }

    fun apply(
        rows: List<IndexerRow>,
        minSeeds: Int?,
        maxSeeds: Int?,
        maxSize: String?,
        contentFilter: SearchContentFilter,
    ): List<IndexerRow> {
        var filtered = when (contentFilter) {
            SearchContentFilter.MOVIES -> rows.filter { isLikelyMovie(it.name) }
            SearchContentFilter.TV -> rows.filter { isLikelyTvShow(it.name) }
            SearchContentFilter.ALL -> rows
        }
        if (minSeeds != null && minSeeds > 0) {
            filtered = filtered.filter { seedCount(it.seeds)?.let { n -> n >= minSeeds } == true }
        }
        if (maxSeeds != null) {
            filtered = filtered.filter { seedCount(it.seeds)?.let { n -> n <= maxSeeds } == true }
        }
        if (!maxSize.isNullOrBlank()) {
            val maxBytes = parseFilterBytes(maxSize)
                ?: throw IllegalArgumentException("Invalid max_size")
            filtered = filtered.filter { row ->
                (row.site == "YTS" && (row.size == "-" || row.size.isBlank())) ||
                    (row.size != "-" && parseSize(row.size).let { it >= 0 && it <= maxBytes })
            }
        }
        return filtered
    }

    fun sortBySeedsDesc(rows: List<IndexerRow>): List<IndexerRow> {
        return rows.sortedByDescending { seedCount(it.seeds) ?: -1 }
    }

    fun interleaveBySite(rows: List<IndexerRow>, limit: Int): List<IndexerRow> {
        if (rows.isEmpty()) return emptyList()
        val order = MovieIndexers.MOVIE_SITE_NAMES.map { it.lowercase() }
        val bySite = linkedMapOf<String, MutableList<IndexerRow>>()
        for (row in rows) {
            val key = row.site.lowercase()
            bySite.getOrPut(key) { mutableListOf() }.add(row)
        }
        bySite.values.forEach { bucket ->
            bucket.sortByDescending { seedCount(it.seeds) ?: -1 }
        }
        val siteKeys = bySite.keys.sortedWith(
            compareBy<String> { site ->
                val idx = order.indexOf(site)
                if (idx >= 0) idx else order.size
            }.thenBy { it },
        )
        val indices = siteKeys.associateWith { 0 }.toMutableMap()
        val merged = mutableListOf<IndexerRow>()
        while (merged.size < limit) {
            var progressed = false
            for (site in siteKeys) {
                if (merged.size >= limit) break
                val bucket = bySite[site] ?: continue
                val index = indices[site] ?: 0
                if (index < bucket.size) {
                    merged.add(bucket[index])
                    indices[site] = index + 1
                    progressed = true
                }
            }
            if (!progressed) break
        }
        return merged
    }
}
