package com.torrentmovie.app.ui.util

internal const val LOCAL_POSTER_PATH_PREFIX = "/v1/posters/"

/** Point local `/v1/posters/...` paths (and stale-host copies) at the current Search API. */
fun resolvePosterUrl(url: String?, searchApiBaseUrl: String?): String? {
    val raw = url?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val base = searchApiBaseUrl?.trim()?.trimEnd('/').orEmpty()
    if (raw.startsWith(LOCAL_POSTER_PATH_PREFIX)) {
        return if (base.isEmpty()) raw else base + raw
    }
    if (base.isEmpty()) return raw
    val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return raw
    val path = uri.rawPath ?: uri.path.orEmpty()
    if (!path.startsWith(LOCAL_POSTER_PATH_PREFIX)) return raw
    val query = uri.rawQuery?.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
    return base + path + query
}
