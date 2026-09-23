package com.torrentmovie.core.data

/** Resolve TMDB key: explicit user save wins; else operator-bundled key for OOTB builds. */
internal fun resolveTmdbApiKey(
    userHasStoredKey: Boolean,
    storedKey: String?,
    bundledKey: String,
): String {
    if (userHasStoredKey) {
        return storedKey.orEmpty().trim()
    }
    return bundledKey.trim()
}
