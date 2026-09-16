package com.torrentmovie.core.data.seedbox

import java.net.URI

internal fun normalizeSearchApiUrl(raw: String): String {
    var url = raw.trim()
    if (url.isEmpty()) return url
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        url = "http://$url"
    }
    return url.trimEnd('/')
}

private fun defaultSeedboxScheme(host: String): String {
    return if (host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) "http://" else "https://"
}

internal fun normalizeSeedboxUrl(raw: String): String {
    var url = raw.trim()
    if (url.isEmpty()) return url
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        val host = url.substringBefore('/').substringBefore(':')
        url = "${defaultSeedboxScheme(host)}$url"
    }
    url = url.replace(Regex("""php/addtorrent\.php/?$""", RegexOption.IGNORE_CASE), "")
    url = url.replace(Regex("""index\.php/?$""", RegexOption.IGNORE_CASE), "")
    url = url.trimEnd('/')

    val uri = try {
        URI(url)
    } catch (_: Exception) {
        return "$url/"
    }
    val host = uri.host ?: return "$url/"
    val path = uri.path.orEmpty().trim('/')
    return buildString {
        append(uri.scheme ?: "https")
        append("://")
        append(host)
        if (uri.port != -1) {
            append(':')
            append(uri.port)
        }
        append('/')
        when {
            path.isEmpty() -> append("rutorrent/")
            path.equals("rutorrent", ignoreCase = true) -> append("rutorrent/")
            else -> append(path).append('/')
        }
    }
}

internal fun seedboxAddTorrentUrl(baseUrl: String): String {
    return "${normalizeSeedboxUrl(baseUrl)}php/addtorrent.php"
}

internal fun seedboxHttprpcUrl(baseUrl: String): String {
    return "${normalizeSeedboxUrl(baseUrl)}plugins/httprpc/action.php"
}
