package com.torrentmovie.core.data.seedbox

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

/**
 * Parses ruTorrent HTTPRPC `mode=list` JSON (default field set from httprpc/action.php).
 */
internal object HttprpcTorrentParser {
    private const val IDX_IS_OPEN = 0
    private const val IDX_IS_HASH_CHECKING = 1
    private const val IDX_GET_STATE = 3
    private const val IDX_NAME = 4
    private const val IDX_SIZE_BYTES = 5
    private const val IDX_BYTES_DONE = 8
    private const val IDX_UP_RATE = 11
    private const val IDX_DOWN_RATE = 12
    private const val IDX_LEFT_BYTES = 19

    fun parseListResponse(json: String): List<SeedboxTorrentStatus> {
        val root = JsonParser.parseString(json).asJsonObject
        val torrents = root.getAsJsonObject("t") ?: return emptyList()
        val result = ArrayList<SeedboxTorrentStatus>(torrents.size())
        for ((hashKey, value) in torrents.entrySet()) {
            if (!value.isJsonArray) continue
            parseEntry(hashKey, value.asJsonArray)?.let { result.add(it) }
        }
        return result
    }

    private fun parseEntry(hashKey: String, values: JsonArray): SeedboxTorrentStatus? {
        if (values.size() <= IDX_LEFT_BYTES) return null
        val infoHash = hashKey.trim().uppercase(Locale.US)
        if (!SeedboxTorrentStatus.isTrackableInfoHash(infoHash)) return null
        return SeedboxTorrentStatus(
            infoHash = infoHash,
            name = values.get(IDX_NAME).asStringOrEmpty(),
            bytesDone = values.get(IDX_BYTES_DONE).asLongOrZero(),
            sizeBytes = values.get(IDX_SIZE_BYTES).asLongOrZero(),
            leftBytes = values.get(IDX_LEFT_BYTES).asLongOrZero(),
            downRate = values.get(IDX_DOWN_RATE).asLongOrZero(),
            upRate = values.get(IDX_UP_RATE).asLongOrZero(),
            isOpen = values.get(IDX_IS_OPEN).asStringOrEmpty() != "0",
            isHashChecking = values.get(IDX_IS_HASH_CHECKING).asStringOrEmpty() != "0",
            isStarted = values.get(IDX_GET_STATE).asStringOrEmpty() != "0",
        )
    }

    private fun com.google.gson.JsonElement.asStringOrEmpty(): String {
        return if (isJsonNull) "" else asString
    }

    private fun com.google.gson.JsonElement.asLongOrZero(): Long {
        return try {
            when {
                isJsonNull -> 0L
                isJsonPrimitive && asJsonPrimitive.isNumber -> asJsonPrimitive.asLong
                else -> asString.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }
}
