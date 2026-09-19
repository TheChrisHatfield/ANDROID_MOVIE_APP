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
    fun keepsFoldSelectionWhenRematchStaysOnDetail() {
        assertFalse(shouldClearPhoneFoldSelectionOnDetailDispose("detail/{resultId}?name={name}&site={site}"))
        assertFalse(shouldClearPhoneFoldSelectionOnDetailDispose("detail/abc?name=Inception&site=YTS"))
    }

    @Test
    fun clearsFoldSelectionWhenLeavingPhoneDetail() {
        assertTrue(shouldClearPhoneFoldSelectionOnDetailDispose("search"))
        assertTrue(shouldClearPhoneFoldSelectionOnDetailDispose("settings"))
        assertTrue(shouldClearPhoneFoldSelectionOnDetailDispose(null))
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

    @Test
    fun magnetFallbackPrefersStoreUrlThenSelectionUrl() {
        assertEquals(
            "https://yts.rs/movie/inception-2010",
            magnetFallbackDetailUrl(
                storeUrl = "https://yts.rs/movie/inception-2010",
                selectionUrl = "https://other.example/movie",
            ),
        )
        assertEquals(
            "https://1337x.to/torrent/1/movie/",
            magnetFallbackDetailUrl(storeUrl = null, selectionUrl = "https://1337x.to/torrent/1/movie/"),
        )
        assertNull(magnetFallbackDetailUrl(storeUrl = "  ", selectionUrl = null))
    }
}
