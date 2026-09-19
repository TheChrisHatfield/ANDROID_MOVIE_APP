package com.torrentmovie.app.ui.util

import org.junit.Assert.assertFalse
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
}
