package com.torrentmovie.core.data.search

import com.torrentmovie.core.data.seedbox.normalizeSearchApiUrl
import java.net.URI

/** FR-040: first-install plus re-bind when Wi-Fi/SSID/cellular changes. */
object SearchApiAutoConfig {
    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun isEmulatorLoopback(url: String): Boolean {
        return normalizeSearchApiUrl(url).contains("10.0.2.2")
    }

    fun ipv4Host(url: String): String? {
        val normalized = normalizeSearchApiUrl(url)
        if (normalized.isBlank()) return null
        return try {
            val host = URI(normalized).host?.trim().orEmpty()
            host.takeIf { IPV4.matches(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun slash24(ipv4: String): String? {
        val parts = ipv4.split('.')
        if (parts.size != 4) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}"
    }

    fun isOnWifiSubnet(url: String, wifiIpv4: String?): Boolean {
        if (wifiIpv4.isNullOrBlank()) return false
        val host = ipv4Host(url) ?: return false
        return slash24(host) != null && slash24(host) == slash24(wifiIpv4)
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

    fun shouldNotifyRebound(recoveredFromUnreachable: Boolean): Boolean = recoveredFromUnreachable

    /** Do not hang on a private LAN URL that cannot be this phone's Wi-Fi. */
    fun shouldAttemptSearch(
        url: String,
        wifiIpv4: String?,
        isEmulator: Boolean,
        wifiAvailable: Boolean = !wifiIpv4.isNullOrBlank(),
    ): Boolean {
        val normalized = normalizeSearchApiUrl(url)
        if (normalized.isBlank()) return false
        if (isEmulatorLoopback(normalized)) return isEmulator
        val host = ipv4Host(normalized)
        if (host == null) return true
        if (!wifiIpv4.isNullOrBlank()) {
            return isOnWifiSubnet(normalized, wifiIpv4)
        }
        // Wi-Fi is up but IPv4 was not readable (OEM /32, IPv6-only link) — still try.
        return wifiAvailable
    }

    /**
     * Unpersisted default: hosted bundled URLs always; RFC1918 only on that Wi-Fi /24.
     */
    fun unpersistedDefaultUrl(
        bundledUrl: String,
        wifiIpv4: String?,
        isEmulator: Boolean,
        emulatorUrl: String,
    ): String {
        val bundled = normalizeSearchApiUrl(bundledUrl)
        if (bundled.isNotBlank()) {
            val host = ipv4Host(bundled)
            if (host == null) return bundled
            if (isOnWifiSubnet(bundled, wifiIpv4)) return bundled
        }
        return if (isEmulator) emulatorUrl else ""
    }

    /** Keep a LAN URL only while the phone is still on that Wi-Fi subnet. Hostnames stay if healthy. */
    fun shouldKeepCurrentUrl(
        currentUrl: String,
        wifiIpv4: String?,
        currentHealthy: Boolean,
    ): Boolean {
        if (!currentHealthy || currentUrl.isBlank()) return false
        val host = ipv4Host(currentUrl)
        if (host == null) return true
        if (wifiIpv4.isNullOrBlank()) return false
        return isOnWifiSubnet(currentUrl, wifiIpv4)
    }
}
