package com.torrentmovie.core.data.seedbox

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
    if (!url.endsWith("/")) url += "/"
    return url
}
