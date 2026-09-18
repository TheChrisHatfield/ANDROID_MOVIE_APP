package com.torrentmovie.app.ui.util

import com.torrentmovie.core.network.TorrentResultDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchReleaseRematchTest {
    private val releases = listOf(
        TorrentResultDto(id = "1", name = "Inception 2010", site = "YTS"),
        TorrentResultDto(id = "2", name = "Inception 2010", site = "1337x"),
    )

    @Test
    fun rematchById() {
        assertEquals("1", SearchReleaseRematch.find(releases, "1", "", "")?.id)
    }

    @Test
    fun rematchRequiresSiteWhenMultipleNameMatches() {
        assertEquals("2", SearchReleaseRematch.find(releases, "old", "Inception 2010", "1337x")?.id)
        assertNull(SearchReleaseRematch.find(releases, "old", "Inception 2010", ""))
    }

    @Test
    fun rematchSiteIsCaseInsensitive() {
        assertEquals(
            "2",
            SearchReleaseRematch.find(releases, "old", "Inception 2010", "1337X")?.id,
        )
    }

    @Test
    fun rematchSingleNameMatchWithoutSite() {
        val single = listOf(TorrentResultDto(id = "9", name = "Solo Film", site = "YTS"))
        assertEquals("9", SearchReleaseRematch.find(single, "old", "Solo Film", "")?.id)
    }
}
