package com.torrentmovie.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedboxPendingPersistTest {
    @Test
    fun firstSendAfterRestartDoesNotClearPendingPersist() {
        assertFalse(seedboxAuthChanged(null, "url|user|pass|basic"))
    }

    @Test
    fun sameAuthDoesNotClearPendingPersist() {
        val key = "url|user|pass|basic"
        assertFalse(seedboxAuthChanged(key, key))
    }

    @Test
    fun credentialChangeClearsPendingPersist() {
        assertTrue(seedboxAuthChanged("url|user|pass|basic", "url|other|pass|basic"))
    }

    @Test
    fun restoredAuthAfterRestartDetectsCredentialChange() {
        val restored = "url|user|pass|basic"
        assertTrue(seedboxAuthChanged(restored, "url|newbox|pass|basic"))
        assertFalse(seedboxAuthChanged(restored, restored))
    }
}
