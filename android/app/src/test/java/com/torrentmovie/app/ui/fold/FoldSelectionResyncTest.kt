package com.torrentmovie.app.ui.fold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldSelectionResyncTest {
    @Test
    fun resyncsWhenSelectionExistsAndPayloadWasCleared() {
        assertTrue(needsFoldSelectionResync("abc", null))
    }

    @Test
    fun skipsWhenPayloadAlreadyMatchesSelection() {
        assertFalse(needsFoldSelectionResync("abc", "abc"))
    }

    @Test
    fun resyncsWhenPayloadPointsAtADifferentRelease() {
        assertTrue(needsFoldSelectionResync("abc", "other"))
    }

    @Test
    fun skipsWhenTwoPaneHasNoSelection() {
        assertFalse(needsFoldSelectionResync(null, null))
        assertFalse(needsFoldSelectionResync(null, "abc"))
        assertFalse(needsFoldSelectionResync("", null))
    }

    @Test
    fun restoresPhoneDetailWhenFoldingWithASelection() {
        assertTrue(shouldRestorePhoneDetailOnFold(true, "abc"))
    }

    @Test
    fun skipsPhoneDetailRestoreWithoutSelectionOrWhenStayingTwoPane() {
        assertFalse(shouldRestorePhoneDetailOnFold(true, null))
        assertFalse(shouldRestorePhoneDetailOnFold(true, ""))
        assertFalse(shouldRestorePhoneDetailOnFold(false, "abc"))
    }

    @Test
    fun twoPaneSelectionPayloadKeepsGenreSnapshot() {
        val payload = foldActivePayload("abc", "Movie", "YTS", "horror", "https://yts.rs/movie")
        assertEquals("horror", payload?.genreId)
        assertEquals("https://yts.rs/movie", payload?.detailUrl)
        assertNull(foldActivePayload(null, "Movie", "YTS", "horror"))
        assertNull(foldActivePayload("", "Movie", "YTS", "horror"))
    }

    @Test
    fun rankingFeedbackUsesLiveSelectedIdAfterRematch() {
        assertEquals("new-id", foldRankingFeedbackResultId("new-id", "old-id"))
        assertEquals("old-id", foldRankingFeedbackResultId(null, "old-id"))
        assertEquals("old-id", foldRankingFeedbackResultId("  ", "old-id"))
    }

    @Test
    fun clearsTwoPaneDetailWhenQueryNoLongerMatchesLastSearch() {
        assertTrue(
            shouldClearFoldSelectionOnQueryEdit(
                query = "Dune",
                lastExecutedQuery = "Inception",
            ),
        )
        assertTrue(
            shouldClearFoldSelectionOnQueryEdit(
                query = "  ",
                lastExecutedQuery = "Inception",
            ),
        )
        assertTrue(
            shouldClearFoldSelectionOnQueryEdit(
                query = "Dune",
                lastExecutedQuery = "Inception",
            ),
        )
        assertFalse(
            shouldClearFoldSelectionOnQueryEdit(
                query = "Inception",
                lastExecutedQuery = "Inception",
            ),
        )
        assertFalse(
            shouldClearFoldSelectionOnQueryEdit(
                query = "Inception",
                lastExecutedQuery = "",
            ),
        )
    }
}
