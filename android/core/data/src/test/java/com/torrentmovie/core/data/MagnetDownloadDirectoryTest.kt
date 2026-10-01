package com.torrentmovie.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetDownloadDirectoryTest {
    @Test
    fun moviesStayInMoviesFolder() {
        val settings = AppSettings()
        assertEquals(
            AppSettings.DEFAULT_DOWNLOAD_DIR,
            settings.magnetDownloadDirectory("Inception 2010 1080p"),
        )
    }

    @Test
    fun episodeTitlesUseTvShowsFolder() {
        val settings = AppSettings()
        assertEquals(
            AppSettings.DEFAULT_TV_DOWNLOAD_DIR,
            settings.magnetDownloadDirectory("The Office S05E03 720p HDTV"),
        )
        assertTrue(AppSettings.DEFAULT_TV_DOWNLOAD_DIR.contains("TVSHOWS"))
    }

    @Test
    fun tvContentFilterUsesTvFolderEvenWithoutEpisodeTokens() {
        val settings = AppSettings(contentFilter = SearchContentFilter.TV)
        assertEquals(
            AppSettings.DEFAULT_TV_DOWNLOAD_DIR,
            settings.magnetDownloadDirectory("The Bear"),
        )
    }

    @Test
    fun rejectsRelativeTvFolder() {
        assertEquals(
            "TV download folder must be an absolute path (start with /)",
            validateAppSettings(
                AppSettings(
                    rutorrentBaseUrl = "http://seedbox.example/rutorrent/",
                    username = "u",
                    password = "p",
                    tvDownloadDirectory = "downloads/TVSHOWS",
                ),
            ),
        )
    }
}
