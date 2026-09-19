package com.torrentmovie.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneDetailHandoffTest {
    @Test
    fun keepsDetailWhenNavTitleSurvivesStoreMiss() {
        assertFalse(shouldPopExpiredPhoneDetail(storeHit = false, rematchHit = false, navName = "Inception 2010"))
    }

    @Test
    fun keepsDetailWhenStoreOrRematchStillHasTheRelease() {
        assertFalse(shouldPopExpiredPhoneDetail(storeHit = true, rematchHit = false, navName = ""))
        assertFalse(shouldPopExpiredPhoneDetail(storeHit = false, rematchHit = true, navName = ""))
    }

    @Test
    fun popsOnlyWhenNothingCanRebuildTheDetailShell() {
        assertTrue(shouldPopExpiredPhoneDetail(storeHit = false, rematchHit = false, navName = ""))
        assertTrue(shouldPopExpiredPhoneDetail(storeHit = false, rematchHit = false, navName = "   "))
    }

    @Test
    fun prefersGenreCapturedWhenOpeningDetail() {
        assertEquals(
            "horror",
            snapshotGenreAtDetailOpen(capturedGenreId = "horror", currentGenreId = null),
        )
        assertEquals(
            "horror",
            snapshotGenreAtDetailOpen(capturedGenreId = "horror", currentGenreId = "action"),
        )
    }

    @Test
    fun fallsBackToCurrentGenreWhenOpenDidNotCaptureOne() {
        assertEquals(
            "action",
            snapshotGenreAtDetailOpen(capturedGenreId = null, currentGenreId = "action"),
        )
        assertNull(snapshotGenreAtDetailOpen(capturedGenreId = "  ", currentGenreId = null))
    }

    @Test
    fun rankingFeedbackFallsBackToLastSearchGenre() {
        assertEquals(
            "horror",
            resolveGenreForRankingFeedback(override = null, activeGenre = null, lastSearchGenre = "horror"),
        )
        assertEquals(
            "action",
            resolveGenreForRankingFeedback(override = "action", activeGenre = "horror", lastSearchGenre = "comedy"),
        )
        assertNull(resolveGenreForRankingFeedback(override = "  ", activeGenre = null, lastSearchGenre = null))
    }
}
