package com.torrentmovie.app.ui.search

import com.torrentmovie.core.data.MagnetHashUtil
import com.torrentmovie.core.data.db.UploadedMagnet
import com.torrentmovie.core.network.TorrentResultDto

internal fun isResultAlreadyUploaded(
    result: TorrentResultDto,
    uploaded: List<UploadedMagnet>,
    storedMagnet: String? = null,
): Boolean {
    val magnet = result.magnet?.takeIf { it.isNotBlank() }
        ?: storedMagnet?.takeIf { it.isNotBlank() }
        ?: return false
    val key = MagnetHashUtil.storageKey(magnet, result.name, result.site)
    return uploaded.any { it.infoHash.equals(key, ignoreCase = true) }
}
