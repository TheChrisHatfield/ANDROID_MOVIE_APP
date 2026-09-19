package com.torrentmovie.app.ui.fold

import org.junit.Assert.assertFalse
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
}
