package com.torrentmovie.app.ui.search

import com.torrentmovie.core.network.MovieGroupDto
import com.torrentmovie.core.network.TorrentResultDto
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchKodiDisplayTest {
    @Test
    fun doesNotDuplicateGroupedReleasesAsExtraCards() {
        val release = TorrentResultDto(id = "1", name = "Inception 1080p", site = "YTS")
        val grouped = MovieGroupDto(
            group_key = "inception-2010",
            title = "Inception",
            year = 2010,
            overview = null,
            poster_url = null,
            trailer_youtube_key = null,
            release_count = 1,
            releases = listOf(release),
        )
        val display = normalizeKodiGroups(listOf(grouped), listOf(release))
        assertEquals(1, display.groups.size)
        assertEquals("inception-2010", display.groups[0].groupKey)
    }

    @Test
    fun synthesizesCardsWhenApiReturnsUngroupedRows() {
        val row = TorrentResultDto(id = "2", name = "Solo", site = "1337x")
        val display = normalizeKodiGroups(emptyList(), listOf(row))
        assertEquals(1, display.groups.size)
        assertEquals("flat-2", display.groups[0].groupKey)
    }
}
