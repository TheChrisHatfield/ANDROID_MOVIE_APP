package com.torrentmovie.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsAuthSchemeTest {
    @Test
    fun trimsAndLowercasesAuthScheme() {
        assertEquals("digest", SettingsRepository.normalizeAuthScheme(" digest "))
        assertEquals("basic", SettingsRepository.normalizeAuthScheme("BASIC"))
    }
}
