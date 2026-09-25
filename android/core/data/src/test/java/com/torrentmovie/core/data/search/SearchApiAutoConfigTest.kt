package com.torrentmovie.core.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchApiAutoConfigTest {
    @Test
    fun firstInstallNeedsAutoConfiguration() {
        assertTrue(
            SearchApiAutoConfig.needsInitialAutoConfiguration(
                hasManualOverride = false,
                storedSearchApiUrl = null,
                isEmulator = false,
            ),
        )
    }

    @Test
    fun userSavedLanUrlStopsInitialAndNetworkAdapt() {
        assertFalse(
            SearchApiAutoConfig.needsInitialAutoConfiguration(
                hasManualOverride = true,
                storedSearchApiUrl = "http://192.168.1.20:8765",
                isEmulator = false,
            ),
        )
        assertFalse(
            SearchApiAutoConfig.shouldAdaptToNetworkChanges(
                hasManualOverride = true,
                storedSearchApiUrl = "http://192.168.1.20:8765",
                isEmulator = false,
            ),
        )
    }

    @Test
    fun leftoverEmulatorUrlOnPhoneNeedsAutoInstall() {
        assertTrue(
            SearchApiAutoConfig.needsInitialAutoConfiguration(
                hasManualOverride = true,
                storedSearchApiUrl = "http://10.0.2.2:8765",
                isEmulator = false,
            ),
        )
        assertTrue(
            SearchApiAutoConfig.shouldAdaptToNetworkChanges(
                hasManualOverride = true,
                storedSearchApiUrl = "http://10.0.2.2:8765",
                isEmulator = false,
            ),
        )
    }

    @Test
    fun emulatorKeepsLoopbackAsConfigured() {
        assertFalse(
            SearchApiAutoConfig.needsInitialAutoConfiguration(
                hasManualOverride = true,
                storedSearchApiUrl = "http://10.0.2.2:8765",
                isEmulator = true,
            ),
        )
        assertFalse(
            SearchApiAutoConfig.shouldAdaptToNetworkChanges(
                hasManualOverride = true,
                storedSearchApiUrl = "http://10.0.2.2:8765",
                isEmulator = true,
            ),
        )
    }

    @Test
    fun blankStoredUrlNeedsAutoInstall() {
        assertTrue(
            SearchApiAutoConfig.needsInitialAutoConfiguration(
                hasManualOverride = true,
                storedSearchApiUrl = "   ",
                isEmulator = false,
            ),
        )
    }

    @Test
    fun autoInstalledLanUrlKeepsAdaptingWhenWifiOrCellularChanges() {
        assertFalse(
            SearchApiAutoConfig.needsInitialAutoConfiguration(
                hasManualOverride = false,
                storedSearchApiUrl = "http://192.168.4.27:8765",
                isEmulator = false,
            ),
        )
        assertTrue(
            SearchApiAutoConfig.shouldAdaptToNetworkChanges(
                hasManualOverride = false,
                storedSearchApiUrl = "http://192.168.4.27:8765",
                isEmulator = false,
            ),
        )
    }

    @Test
    fun savingOtherSettingsDoesNotLockAutoSearchUrl() {
        assertFalse(
            SearchApiAutoConfig.manualFlagAfterSettingsSave(
                previousStoredUrl = "http://192.168.4.27:8765",
                newUrl = "http://192.168.4.27:8765",
                wasManual = false,
                bundledUrl = "http://192.168.4.27:8765",
            ),
        )
    }

    @Test
    fun editingSearchUrlInSettingsLocksOverride() {
        assertTrue(
            SearchApiAutoConfig.manualFlagAfterSettingsSave(
                previousStoredUrl = "http://192.168.4.27:8765",
                newUrl = "http://192.168.1.9:8765",
                wasManual = false,
                bundledUrl = "http://192.168.4.27:8765",
            ),
        )
    }

    @Test
    fun firstSaveOfBundledUrlStaysAuto() {
        assertFalse(
            SearchApiAutoConfig.manualFlagAfterSettingsSave(
                previousStoredUrl = null,
                newUrl = "http://192.168.4.27:8765",
                wasManual = false,
                bundledUrl = "http://192.168.4.27:8765",
            ),
        )
    }

    @Test
    fun shouldKeepCurrentUrlOnlyOnSameWifiSubnet() {
        assertTrue(
            SearchApiAutoConfig.shouldKeepCurrentUrl(
                currentUrl = "http://192.168.4.27:8765",
                wifiIpv4 = "192.168.4.10",
                currentHealthy = true,
            ),
        )
        assertFalse(
            SearchApiAutoConfig.shouldKeepCurrentUrl(
                currentUrl = "http://192.168.4.27:8765",
                wifiIpv4 = "192.168.8.10",
                currentHealthy = true,
            ),
        )
        assertFalse(
            SearchApiAutoConfig.shouldKeepCurrentUrl(
                currentUrl = "http://192.168.4.27:8765",
                wifiIpv4 = null,
                currentHealthy = true,
            ),
        )
        assertTrue(
            SearchApiAutoConfig.shouldKeepCurrentUrl(
                currentUrl = "http://search.example.com:8765",
                wifiIpv4 = "192.168.4.10",
                currentHealthy = true,
            ),
        )
    }

    @Test
    fun shouldNotAttemptSearchToOffSubnetLanOnCellular() {
        assertFalse(
            SearchApiAutoConfig.shouldAttemptSearch(
                url = "http://192.168.4.27:8765",
                wifiIpv4 = null,
                isEmulator = false,
            ),
        )
        assertFalse(
            SearchApiAutoConfig.shouldAttemptSearch(
                url = "http://192.168.4.27:8765",
                wifiIpv4 = "192.168.8.10",
                isEmulator = false,
            ),
        )
        assertTrue(
            SearchApiAutoConfig.shouldAttemptSearch(
                url = "http://192.168.4.27:8765",
                wifiIpv4 = "192.168.4.10",
                isEmulator = false,
            ),
        )
    }

    @Test
    fun unpersistedDefaultHidesOffSubnetLanBake() {
        assertEquals(
            "",
            SearchApiAutoConfig.unpersistedDefaultUrl(
                bundledUrl = "http://192.168.4.27:8765",
                wifiIpv4 = "192.168.8.10",
                isEmulator = false,
                emulatorUrl = "http://10.0.2.2:8765",
            ),
        )
        assertEquals(
            "http://192.168.4.27:8765",
            SearchApiAutoConfig.unpersistedDefaultUrl(
                bundledUrl = "http://192.168.4.27:8765",
                wifiIpv4 = "192.168.4.10",
                isEmulator = false,
                emulatorUrl = "http://10.0.2.2:8765",
            ),
        )
        assertEquals(
            "http://search.example.com:8765",
            SearchApiAutoConfig.unpersistedDefaultUrl(
                bundledUrl = "http://search.example.com:8765",
                wifiIpv4 = null,
                isEmulator = false,
                emulatorUrl = "http://10.0.2.2:8765",
            ),
        )
    }
}
