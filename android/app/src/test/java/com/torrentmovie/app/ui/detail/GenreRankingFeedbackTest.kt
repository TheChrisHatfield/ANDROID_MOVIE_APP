package com.torrentmovie.app.ui.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreRankingFeedbackTest {
    @Test
    fun duplicateDoesNotCountAsRankingFailure() {
        assertFalse(
            isGenreRankingSendFailure("Already uploaded — remove from Uploaded list to re-send"),
        )
    }

    @Test
    fun configErrorsDoNotCountAsRankingFailure() {
        assertFalse(isGenreRankingSendFailure("Configure seedbox URL and credentials in Settings"))
        assertFalse(isGenreRankingSendFailure("Set download folder in Settings before sending"))
        assertFalse(isGenreRankingSendFailure("Magnet missing info hash — cannot send or track status"))
        assertFalse(isGenreRankingSendFailure("Sent to seedbox but failed to save locally — tap send again"))
    }

    @Test
    fun seedboxHttpFailureCountsAsRankingFailure() {
        assertTrue(isGenreRankingSendFailure("Authentication failed — check username, password, and auth scheme"))
        assertTrue(isGenreRankingSendFailure("ruTorrent rejected magnet"))
        assertTrue(isGenreRankingSendFailure("Connection failed"))
    }

    @Test
    fun persistRetryDoesNotRecordRankingSuccessAgain() {
        assertTrue(shouldRecordGenreRankingSuccess(wasRetryingPersist = false))
        assertFalse(shouldRecordGenreRankingSuccess(wasRetryingPersist = true))
    }
}
