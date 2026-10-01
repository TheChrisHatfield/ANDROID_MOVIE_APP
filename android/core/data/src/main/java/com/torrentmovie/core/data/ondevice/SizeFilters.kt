package com.torrentmovie.core.data.ondevice

import java.util.Locale

internal object SizeFilters {
    private val thousands = Regex("(?<=\\d),(?=\\d{3})")
    private val tvShow = Regex(
        """\b(?:s\d{1,2}e\d{1,2}|season\s+\d+|complete\s+series|tv\s+series|episodes?\s+\d+|mini\s*series)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val software = Regex(
        """\b(?:windows\s+\d+|macos|linux\s+distro|adobe|photoshop|microsoft\s+office|keygen|crackonly|audiobook|epub|ebook)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parseSize(sizeStr: String): Double {
        return try {
            val normalized = thousands.replace(sizeStr.trim().uppercase(Locale.US), "")
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

    fun seedCount(seeds: String?): Int? {
        if (seeds.isNullOrBlank() || seeds == "-") return null
        val normalized = seeds.replace(",", "").trim()
        return normalized.toIntOrNull()
    }

    fun isLikelyMovie(name: String): Boolean {
        val label = name.trim()
        if (label.isEmpty()) return false
        if (tvShow.containsMatchIn(label)) return false
        if (software.containsMatchIn(label)) return false
        return true
    }

    fun apply(
        rows: List<IndexerRow>,
        minSeeds: Int?,
        maxSeeds: Int?,
        maxSize: String?,
        movieProfile: Boolean,
    ): List<IndexerRow> {
        var filtered = if (movieProfile) rows.filter { isLikelyMovie(it.name) } else rows
        if (minSeeds != null && minSeeds > 0) {
            filtered = filtered.filter { seedCount(it.seeds)?.let { n -> n >= minSeeds } == true }
        }
        if (maxSeeds != null) {
            filtered = filtered.filter { seedCount(it.seeds)?.let { n -> n <= maxSeeds } == true }
        }
        if (!maxSize.isNullOrBlank()) {
            val maxBytes = parseSize(maxSize)
            if (maxBytes >= 0) {
                filtered = filtered.filter { row ->
                    (row.site == "YTS" && (row.size == "-" || row.size.isBlank())) ||
                        (row.size != "-" && parseSize(row.size).let { it >= 0 && it <= maxBytes })
                }
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
