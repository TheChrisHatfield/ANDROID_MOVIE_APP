package com.torrentmovie.core.data.seedbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedboxTorrentStatusTest {
    private fun downloading(
        leftBytes: Long = 580L,
        downRate: Long = 512_000L,
        bytesDone: Long = 420L,
        sizeBytes: Long = 1000L,
    ) = SeedboxTorrentStatus(
        infoHash = "ABCDEF0123456789ABCDEF0123456789ABCDEF01",
        name = "Movie.2024.1080p",
        bytesDone = bytesDone,
        sizeBytes = sizeBytes,
        leftBytes = leftBytes,
        downRate = downRate,
        upRate = 0L,
        isOpen = true,
        isHashChecking = false,
    )

    @Test
    fun etaSecondsUsesLeftBytesAndDownRate() {
        assertEquals(1L, downloading().etaSeconds())
    }

    @Test
    fun etaSecondsNullWhenNotDownloading() {
        assertNull(downloading(downRate = 0L).etaSeconds())
        assertNull(
            downloading(leftBytes = 0L, bytesDone = 1000L, sizeBytes = 1000L).etaSeconds(),
        )
    }

    @Test
    fun formatEtaCoversSecondsMinutesAndHours() {
        assertEquals("~45s left", SeedboxTorrentStatus.formatEta(45L))
        assertEquals("~12m left", SeedboxTorrentStatus.formatEta(12L * 60L))
        assertEquals("~1h 5m left", SeedboxTorrentStatus.formatEta(65L * 60L))
        assertEquals("<1m left", SeedboxTorrentStatus.formatEta(0L))
    }

    @Test
    fun etaSummaryCountsDownBetweenPolls() {
        val status = downloading(leftBytes = 1_024_000L, downRate = 512_000L)
        assertEquals("~2s left", status.etaSummary(0L))
        assertEquals("~1s left", status.etaSummary(1L))
        assertEquals("<1m left", status.etaSummary(5L))
    }

    @Test
    fun etaSummaryShowsWaitingWhenStalled() {
        val status = downloading(downRate = 0L, leftBytes = 500L)
        assertEquals("waiting for peers", status.etaSummary(0L))
    }

    @Test
    fun isActivelyDownloading() {
        assertTrue(downloading().isActivelyDownloading())
        assertTrue(downloading(downRate = 0L, leftBytes = 100L).isActivelyDownloading())
        assertTrue(
            !downloading(leftBytes = 0L, bytesDone = 1000L, sizeBytes = 1000L).isActivelyDownloading(),
        )
    }
}
