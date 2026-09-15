package com.torrentmovie.core.data.seedbox

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

internal object DigestAuth {
    private val nonceCounter = AtomicInteger(0)

    fun authorizationHeader(
        wwwAuthenticate: String,
        method: String,
        requestUri: String,
        username: String,
        password: String,
    ): String? {
        val params = parseChallenge(wwwAuthenticate) ?: return null
        val realm = params["realm"] ?: return null
        val nonce = params["nonce"] ?: return null
        val qopOptions = params["qop"]
            ?.split(',')
            ?.map { it.trim().removeSurrounding("\"") }
            ?: emptyList()
        val qop = qopOptions.firstOrNull { it == "auth" }
            ?: qopOptions.firstOrNull { it == "auth-int" }
        val opaque = params["opaque"]
        val algorithm = params["algorithm"]?.uppercase() ?: "MD5"
        if (algorithm != "MD5") return null

        val ha1 = md5("$username:$realm:$password")
        val ha2 = md5("$method:$requestUri")
        val nc = "%08x".format(nonceCounter.incrementAndGet())
        val cnonce = md5(System.nanoTime().toString()).take(16)
        val response = if (qop == "auth" || qop == "auth-int") {
            md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }

        val parts = mutableListOf(
            "username=\"$username\"",
            "realm=\"$realm\"",
            "nonce=\"$nonce\"",
            "uri=\"$requestUri\"",
            "response=\"$response\"",
        )
        if (opaque != null) parts += "opaque=\"$opaque\""
        if (qop == "auth" || qop == "auth-int") {
            parts += "qop=$qop"
            parts += "nc=$nc"
            parts += "cnonce=\"$cnonce\""
        }
        return "Digest ${parts.joinToString(", ")}"
    }

    private fun parseChallenge(header: String): Map<String, String>? {
        if (!header.startsWith("Digest ", ignoreCase = true)) return null
        val out = mutableMapOf<String, String>()
        val regex = Regex("""(\w+)=(?:"([^"]*)"|([^,\s]+))""")
        for (match in regex.findAll(header)) {
            val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
            out[match.groupValues[1].lowercase()] = value
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
