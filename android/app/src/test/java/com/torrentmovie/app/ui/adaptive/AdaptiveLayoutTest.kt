package com.torrentmovie.app.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun phoneAndFoldCoverStaySinglePane() {
        assertFalse(AdaptiveLayout.useTwoPaneSearchDetail(360))
        assertFalse(AdaptiveLayout.useTwoPaneSearchDetail(373))
        assertFalse(AdaptiveLayout.useNavigationRail(411))
    }

    @Test
    fun foldInnerAndTabletUseTwoPane() {
        assertTrue(AdaptiveLayout.useTwoPaneSearchDetail(673))
        assertTrue(AdaptiveLayout.useTwoPaneSearchDetail(800))
        assertTrue(AdaptiveLayout.useNavigationRail(840))
    }

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
