package com.torrentmovie.core.data.seedbox

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestAuthTest {
    @Test
    fun parsesAuthIntQopChallenge() {
        val header = DigestAuth.authorizationHeader(
            wwwAuthenticate = """Digest realm="ruTorrent", nonce="abc123", qop="auth-int,auth"""",
            method = "GET",
            requestUri = "/rutorrent/",
            username = "user",
            password = "pass",
        )
        assertNotNull(header)
        assertTrue(header!!.contains("qop=auth-int"))
    }

    @Test
    fun parsesUnquotedQopChallenge() {
        val header = DigestAuth.authorizationHeader(
            wwwAuthenticate = """Digest realm="ruTorrent", nonce="abc123", qop=auth""",
            method = "GET",
            requestUri = "/rutorrent/",
            username = "user",
            password = "pass",
        )
        assertNotNull(header)
        assertTrue(header!!.contains("qop=auth"))
    }
}
