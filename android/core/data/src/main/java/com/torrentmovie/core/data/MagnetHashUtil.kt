package com.torrentmovie.core.data

import java.security.MessageDigest
import java.util.Locale

object MagnetHashUtil {
    private const val BTIH_PREFIX = "xt=urn:btih:"

    fun extractInfoHash(magnet: String?): String? {
        if (magnet.isNullOrBlank()) return null
        val lower = magnet.lowercase(Locale.US)
        val start = lower.indexOf(BTIH_PREFIX)
        if (start < 0) return null
        var hash = magnet.substring(start + BTIH_PREFIX.length)
        val amp = hash.indexOf('&')
        if (amp >= 0) hash = hash.substring(0, amp)
        hash = hash.trim().uppercase(Locale.US)
        if (hash.length == 32) hash = base32ToHex(hash) ?: return null
        return if (hash.length == 40) hash else null
    }

    /** Stable Room primary key when btih cannot be parsed from the magnet URI. */
    fun storageKey(magnet: String, displayName: String, site: String): String {
        val hash = extractInfoHash(magnet)
        if (hash != null) return hash
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$magnet|$displayName|$site".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(40)
    }

    private fun base32ToHex(base32: String): String? {
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUV"
        var buffer = 0L
        var bits = 0
        val out = StringBuilder()
        for (ch in base32.uppercase(Locale.US)) {
            val value = alphabet.indexOf(ch)
            if (value < 0) return null
            buffer = (buffer shl 5) or value.toLong()
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.append(String.format("%02X", ((buffer shr bits) and 0xFF)))
            }
        }
        return if (out.length == 40) out.toString() else null
    }
}
