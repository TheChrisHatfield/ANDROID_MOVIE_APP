package com.torrentmovie.core.data.seedbox

import java.util.Locale
import kotlin.math.roundToInt

data class SeedboxTorrentStatus(
    val infoHash: String,
    val name: String,
    val bytesDone: Long,
    val sizeBytes: Long,
    val leftBytes: Long,
    val downRate: Long,
    val upRate: Long,
    val isOpen: Boolean,
    val isHashChecking: Boolean,
) {
    fun progressPercent(): Int? {
        if (sizeBytes <= 0L) return null
        return ((bytesDone.toDouble() / sizeBytes.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    fun progressFraction(): Float {
        val pct = progressPercent() ?: return 0f
        return pct / 100f
    }

    fun statusLabel(): String {
        if (isHashChecking) return "Checking hash"
        if (sizeBytes > 0L && leftBytes == 0L && bytesDone >= sizeBytes) {
            return if (upRate > 0L) "Seeding" else "Complete"
        }
        if (!isOpen) return "Paused"
        if (downRate > 0L || (sizeBytes > 0L && bytesDone < sizeBytes)) {
            val pct = progressPercent()
            return if (pct != null) "Downloading · $pct%" else "Downloading"
        }
        return "Queued"
    }

    fun rateSummary(): String? {
        val parts = mutableListOf<String>()
        formatRate(downRate)?.let { parts += "↓ $it" }
        formatRate(upRate)?.let { parts += "↑ $it" }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    companion object {
        fun isTrackableInfoHash(infoHash: String): Boolean {
            return infoHash.length == 40 &&
                infoHash.all { ch ->
                    ch.isDigit() || ch in 'A'..'F' || ch in 'a'..'f'
                }
        }

        fun formatRate(bytesPerSecond: Long): String? {
            if (bytesPerSecond <= 0L) return null
            val kb = bytesPerSecond / 1024.0
            return if (kb >= 1024.0) {
                String.format(Locale.US, "%.1f MB/s", kb / 1024.0)
            } else {
                String.format(Locale.US, "%.0f KB/s", kb)
            }
        }
    }
}

sealed class SeedboxListResult {
    data class Success(val statuses: Map<String, SeedboxTorrentStatus>) : SeedboxListResult()
    data class Failure(val message: String) : SeedboxListResult()
}

data class SeedboxProbeResult(
    val addReachable: Boolean,
    val httprpcAvailable: Boolean,
    val message: String,
) {
    val fullyOnline: Boolean get() = addReachable && httprpcAvailable
}
