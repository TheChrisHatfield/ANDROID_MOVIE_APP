package com.torrentmovie.core.data.search

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
}
