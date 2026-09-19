package com.torrentmovie.app.ui.search

import com.torrentmovie.core.data.MagnetHashUtil
import com.torrentmovie.core.data.db.UploadedMagnet
import com.torrentmovie.core.network.TorrentResultDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadedBadgeTest {
    private val magnet = "magnet:?xt=urn:btih:ABCDEF0123456789ABCDEF0123456789ABCDEF01&dn=film"
    private val uploaded = listOf(
        UploadedMagnet(
            infoHash = MagnetHashUtil.storageKey(magnet, "Film", "1337x"),
            displayName = "Film",
            site = "1337x",
            magnetUri = magnet,
            sentAt = 1L,
            downloadDirectory = "/movies",
        ),
    )

    @Test
    fun inlineMagnetShowsSent() {
        val result = TorrentResultDto(
            id = "1",
            name = "Film",
            site = "1337x",
            magnet = magnet,
        )
        assertTrue(isResultAlreadyUploaded(result, uploaded))
    }

    @Test
    fun lazyResolvedStoreMagnetShowsSent() {
        val result = TorrentResultDto(
            id = "1",
            name = "Film",
            site = "1337x",
            magnet = null,
        )
        assertTrue(isResultAlreadyUploaded(result, uploaded, storedMagnet = magnet))
    }

    @Test
    fun missingMagnetDoesNotShowSent() {
        val result = TorrentResultDto(
            id = "1",
            name = "Film",
            site = "1337x",
            magnet = null,
        )
        assertFalse(isResultAlreadyUploaded(result, uploaded, storedMagnet = null))
    }
}
