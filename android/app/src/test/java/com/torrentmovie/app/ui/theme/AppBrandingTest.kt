package com.torrentmovie.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

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

    @Test
    fun bodyTextMeetsContrastOnWhiteContent() {
        assertTrue(contrastRatio(TextCharcoal, SurfaceWhite) >= 4.5)
    }

    @Test
    fun chromeLabelsMeetContrastOnPantoneRed() {
        // App bar title uses large/bold type; WCAG AA large text ≥ 3:1
        assertTrue(contrastRatio(OnPantone, PantoneRed) >= 3.0)
    }
}

internal fun contrastRatio(a: Color, b: Color): Double {
    fun linear(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    fun luminance(color: Color): Double {
        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }
    val first = luminance(a)
    val second = luminance(b)
    val lighter = maxOf(first, second)
    val darker = minOf(first, second)
    return (lighter + 0.05) / (darker + 0.05)
}
