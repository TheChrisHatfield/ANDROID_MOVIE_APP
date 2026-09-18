package com.torrentmovie.app.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun twoPaneActivatesAt600dp() {
        assertFalse(AdaptiveLayout.useTwoPaneSearchDetail(599))
        assertTrue(AdaptiveLayout.useTwoPaneSearchDetail(600))
        assertTrue(AdaptiveLayout.useTwoPaneSearchDetail(1200))
    }

    @Test
    fun navigationRailMatchesTwoPaneBreakpoint() {
        assertFalse(AdaptiveLayout.useNavigationRail(480))
        assertTrue(AdaptiveLayout.useNavigationRail(600))
    }

    @Test
    fun expandedLayoutUsesNarrowerListPane() {
        assertEquals(0.42f, AdaptiveLayout.searchListPaneWeight(700), 0.001f)
        assertEquals(0.35f, AdaptiveLayout.searchListPaneWeight(900), 0.001f)
    }
}
