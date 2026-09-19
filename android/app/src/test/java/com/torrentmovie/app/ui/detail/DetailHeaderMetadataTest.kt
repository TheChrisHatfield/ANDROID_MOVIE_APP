package com.torrentmovie.app.ui.detail

import com.torrentmovie.core.data.MovieMetadata
import com.torrentmovie.core.network.TorrentResultDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailHeaderMetadataTest {
    @Test
    fun usesReleasePosterWhenMetadataStoreMisses() {
        val header = detailHeaderMetadata(
            stored = null,
            release = TorrentResultDto(
                id = "a",
                name = "Film 1080p",
                site = "YTS",
                poster_url = "https://image.tmdb.org/p.jpg",
            ),
            name = "Film 1080p",
        )
        assertEquals("https://image.tmdb.org/p.jpg", header?.posterUrl)
        assertEquals("Film 1080p", header?.title)
    }

    @Test
    fun fillsBlankMetadataPosterFromRelease() {
        val header = detailHeaderMetadata(
            stored = MovieMetadata(title = "Film", overview = "Plot", posterUrl = null),
            release = TorrentResultDto(
                id = "a",
                name = "Film 1080p",
                site = "YTS",
                poster_url = "https://yts.rs/p.jpg",
            ),
            name = "Film 1080p",
        )
        assertEquals("https://yts.rs/p.jpg", header?.posterUrl)
        assertEquals("Plot", header?.overview)
    }

    @Test
    fun keepsStoredPosterWhenBothExist() {
        val header = detailHeaderMetadata(
            stored = MovieMetadata(title = "Film", posterUrl = "https://image.tmdb.org/p.jpg"),
            release = TorrentResultDto(
                id = "a",
                name = "Film 1080p",
                site = "YTS",
                poster_url = "https://yts.rs/other.jpg",
            ),
            name = "Film 1080p",
        )
        assertEquals("https://image.tmdb.org/p.jpg", header?.posterUrl)
    }

    @Test
    fun returnsNullWhenNeitherSourceHasMetadata() {
        assertNull(detailHeaderMetadata(null, null, "Film"))
        assertNull(
            detailHeaderMetadata(
                stored = null,
                release = TorrentResultDto(id = "a", name = "Film", site = "YTS"),
                name = "Film",
            ),
        )
    }
}
