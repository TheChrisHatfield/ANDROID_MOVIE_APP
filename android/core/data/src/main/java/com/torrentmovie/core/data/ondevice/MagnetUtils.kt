package com.torrentmovie.core.data.ondevice

import java.net.URLEncoder

internal object MagnetUtils {
    fun fromHash(infoHash: String, name: String): String {
        val hash = infoHash.trim().uppercase()
        val dn = URLEncoder.encode(name, Charsets.UTF_8.name()).replace("+", "%20")
        return "magnet:?xt=urn:btih:$hash&dn=$dn"
    }

    fun bytesToSizeLabel(raw: Long): String {
        val gib = 1024.0 * 1024 * 1024
        val mib = 1024.0 * 1024
        return when {
            raw >= gib -> String.format("%.2f GB", raw / gib)
            raw >= mib -> String.format("%.2f MB", raw / mib)
            else -> "$raw B"
        }
    }
}
