package com.torrentmovie.core.data.seedbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttprpcTorrentParserTest {
    @Test
    fun parseListResponseMapsHashAndProgressFields() {
        val json = """
            {
              "t": {
                "ABCDEF0123456789ABCDEF0123456789ABCDEF01": [
                  "1", "0", "1", "1",
                  "Movie.2024.1080p",
                  "1000",
                  "0", "0",
                  "420",
                  "0", "0", "0", "512000",
                  "262144",
                  "", "0", "0", "0", "0",
                  "580"
                ]
              },
              "cid": "1234567890"
            }
        """.trimIndent()

        val statuses = HttprpcTorrentParser.parseListResponse(json)
        assertEquals(1, statuses.size)
        val status = statuses.first()
        assertEquals("ABCDEF0123456789ABCDEF0123456789ABCDEF01", status.infoHash)
        assertEquals("Movie.2024.1080p", status.name)
        assertEquals(420L, status.bytesDone)
        assertEquals(1000L, status.sizeBytes)
        assertEquals(580L, status.leftBytes)
        assertEquals(512000L, status.downRate)
        assertEquals(42, status.progressPercent())
        assertTrue(status.statusLabel().startsWith("Downloading"))
    }

    @Test
    fun parseListResponseReturnsEmptyWhenTorrentObjectMissing() {
        assertTrue(HttprpcTorrentParser.parseListResponse("""{"cid":"1"}""").isEmpty())
    }
}
