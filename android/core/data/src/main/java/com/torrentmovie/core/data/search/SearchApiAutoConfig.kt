package com.torrentmovie.core.data.search

import com.torrentmovie.core.data.seedbox.normalizeSearchApiUrl

/** FR-040: first-install plus re-bind when Wi-Fi/SSID/cellular changes. */
object SearchApiAutoConfig {
    fun isEmulatorLoopback(url: String): Boolean {
        return normalizeSearchApiUrl(url).contains("10.0.2.2")
    }

    fun needsInitialAutoConfiguration(
        @Suppress("UNUSED_PARAMETER") hasManualOverride: Boolean,
        storedSearchApiUrl: String?,
        isEmulator: Boolean,
    ): Boolean {
        val stored = normalizeSearchApiUrl(storedSearchApiUrl.orEmpty())
        if (stored.isBlank()) return true
        return isEmulatorLoopback(stored) && !isEmulator
    }

    /** Keep listening after auto-install; never replace an explicit Settings URL. */
    fun shouldAdaptToNetworkChanges(
        hasManualOverride: Boolean,
        storedSearchApiUrl: String?,
        isEmulator: Boolean,
    ): Boolean {
        if (needsInitialAutoConfiguration(hasManualOverride, storedSearchApiUrl, isEmulator)) {
            return true
        }
        return !hasManualOverride
    }

    /**
     * Saving seedbox/TMDB in Settings must not freeze an auto-filled Search API URL.
     * Manual only if the user already locked it or they changed the Search API field.
     */
    fun manualFlagAfterSettingsSave(
        previousStoredUrl: String?,
        newUrl: String,
        wasManual: Boolean,
        bundledUrl: String,
    ): Boolean {
        val newNorm = normalizeSearchApiUrl(newUrl)
        if (newNorm.isBlank()) return false
        if (wasManual) return true
        val prev = normalizeSearchApiUrl(previousStoredUrl.orEmpty())
        if (prev.isNotBlank()) return prev != newNorm
        val bundled = normalizeSearchApiUrl(bundledUrl)
        if (bundled.isNotBlank() && bundled.equals(newNorm, ignoreCase = true)) return false
        if (isEmulatorLoopback(newNorm)) return false
        return true
    }
}
