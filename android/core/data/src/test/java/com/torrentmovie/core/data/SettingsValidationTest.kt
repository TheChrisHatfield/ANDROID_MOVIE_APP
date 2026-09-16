package com.torrentmovie.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsValidationTest {
    private fun validSettings() = AppSettings(
        searchApiBaseUrl = "http://10.0.2.2:8765",
        rutorrentBaseUrl = "http://seedbox.example/rutorrent/",
        username = "user",
        password = "pass",
        authScheme = "digest",
        downloadDirectory = "/home/user/downloads/",
    )

    @Test
    fun rejectsRelativeDownloadPath() {
        assertEquals(
            "Download folder must be an absolute path (start with /)",
            validateAppSettings(validSettings().copy(downloadDirectory = "downloads/MOVIES")),
        )
    }

    @Test
    fun acceptsValidSettings() {
        assertNull(validateAppSettings(validSettings()))
    }

    @Test
    fun allowsBlankSearchApiForSeedboxOnlySave() {
        assertNull(validateAppSettings(validSettings().copy(searchApiBaseUrl = "")))
    }

    @Test
    fun allowsBlankDownloadDirectoryUntilConfigured() {
        assertNull(validateAppSettings(validSettings().copy(downloadDirectory = "")))
    }

    @Test
    fun clampsInvalidLoadedAuthScheme() {
        assertEquals("basic", SettingsRepository.normalizeLoadedAuthScheme("diges"))
        assertEquals("digest", SettingsRepository.normalizeLoadedAuthScheme("DIGEST"))
    }

    @Test
    fun rejectsInvalidSearchPages() {
        assertEquals(
            "Search pages must be between 1 and 10",
            validateAppSettings(validSettings().copy(searchPages = 0)),
        )
        assertEquals(
            "Search pages must be between 1 and 10",
            validateAppSettings(validSettings().copy(searchPages = 11)),
        )
    }
}
