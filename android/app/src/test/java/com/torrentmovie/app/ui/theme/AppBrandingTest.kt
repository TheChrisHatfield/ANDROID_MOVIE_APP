package com.torrentmovie.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class AppBrandingTest {
    @Test
    fun displayNameIsMissysMovies() {
        assertEquals("Missy's Movies", AppBranding.DISPLAY_NAME)
    }

    @Test
    fun brandFillIsPantoneRed032C() {
        assertEquals("#EF3340", AppBranding.PANTONE_RED_HEX)
        assertEquals(Color(0xFFEF3340), PantoneRed)
    }
}
