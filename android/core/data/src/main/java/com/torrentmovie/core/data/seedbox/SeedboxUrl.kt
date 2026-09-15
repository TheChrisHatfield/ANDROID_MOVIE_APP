package com.torrentmovie.core.data.seedbox

internal fun normalizeSearchApiUrl(raw: String): String {
    var url = raw.trim()
    if (url.isEmpty()) return url
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        url = "http://$url"
    }
    return url.trimEnd('/')
}

internal fun normalizeSeedboxUrl(raw: String): String {
    var url = raw.trim()
    if (url.isEmpty()) return url
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        url = "https://$url"
    }
    if (!url.endsWith("/")) url += "/"
    return url
}
