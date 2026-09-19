package com.torrentmovie.app.ui.search

import com.torrentmovie.core.network.MovieGroupDto
import com.torrentmovie.core.network.TorrentResultDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupSelectionTest {
    private val release = TorrentResultDto(id = "rel-1", name = "Film 1080p", site = "YTS")
    private val group = MovieGroupDto(
        group_key = "film|2010",
        title = "Film",
        year = 2010,
        release_count = 1,
        releases = listOf(release),
    )

    @Test
    fun singleReleaseCardIsSelectedWhenDetailMatches() {
        assertTrue(groupContainsSelectedRelease(group, "rel-1"))
        assertFalse(groupContainsSelectedRelease(group, "other"))
        assertFalse(groupContainsSelectedRelease(group, null))
    }
}
